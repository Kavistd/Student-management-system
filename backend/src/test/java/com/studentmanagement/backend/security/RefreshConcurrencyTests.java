package com.studentmanagement.backend.security;

import com.studentmanagement.backend.entity.RefreshToken;
import com.studentmanagement.backend.entity.Role;
import com.studentmanagement.backend.entity.User;
import com.studentmanagement.backend.exception.InvalidRefreshTokenException;
import com.studentmanagement.backend.repository.RefreshTokenRepository;
import com.studentmanagement.backend.repository.UserRepository;
import com.studentmanagement.backend.service.AuthService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import static com.studentmanagement.backend.exception.InvalidRefreshTokenException.Reason.*;
import static org.assertj.core.api.Assertions.*;

/** Real overlapping transactions against isolated H2; not evidence of PostgreSQL lock semantics. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:sms-concurrency;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000",
        "spring.datasource.hikari.maximum-pool-size=5"
})
class RefreshConcurrencyTests {
    @Autowired RefreshTokenService tokens;
    @Autowired AuthService auth;
    @Autowired UserRepository users;
    @Autowired RefreshTokenRepository repository;
    @Autowired PlatformTransactionManager transactionManager;
    @Autowired MockMvc mvc;
    @Autowired com.studentmanagement.backend.service.StudentService studentService;
    @Autowired com.studentmanagement.backend.service.UserService userService;
    @Autowired com.studentmanagement.backend.repository.StudentRepository students;
    private TransactionTemplate tx;

    @BeforeEach
    void setup() { tx = new TransactionTemplate(transactionManager); }

    private User user() {
        User user = new User();
        user.setUsername(UUID.randomUUID().toString());
        user.setEmail(user.getUsername() + "@example.com");
        user.setPassword("unused-test-hash");
        user.setRole(Role.STUDENT);
        return users.save(user);
    }

    private String issue(User user) { return tokens.issue(user, true).value(); }
    private RefreshToken row(String token) { return repository.findByTokenHash(RefreshTokenService.hash(token)).orElseThrow(); }
    private long active(String token) {
        String family = row(token).getFamilyId();
        return repository.findAll().stream().filter(t -> t.getFamilyId().equals(family) && t.getRevokedAt() == null).count();
    }
    private Object refresh(String token) {
        try { return auth.refresh(token); }
        catch (InvalidRefreshTokenException ex) { return ex.getReason(); }
    }
    private void age(String token) {
        tx.executeWithoutResult(status -> {
            RefreshToken old = row(token);
            old.setRevokedAt(Instant.now().minusSeconds(60));
            repository.save(old);
        });
    }
    private static void await(CountDownLatch latch) {
        try { assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue(); }
        catch (InterruptedException ex) { Thread.currentThread().interrupt(); throw new IllegalStateException(ex); }
    }

    /** The first transaction holds its user lock while the second transaction actually starts. */
    private Object overlap(Runnable first, Supplier<Object> second, boolean rollback) throws Exception {
        CountDownLatch held = new CountDownLatch(1), release = new CountDownLatch(1), started = new CountDownLatch(1);
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<?> owner = executor.submit(() -> tx.executeWithoutResult(status -> {
                first.run();
                held.countDown();
                await(release);
                if (rollback) status.setRollbackOnly();
            }));
            try {
                await(held);
                Future<Object> contender = executor.submit(() -> tx.execute(status -> {
                    started.countDown();
                    return second.get();
                }));
                await(started);
                assertThatThrownBy(() -> contender.get(200, TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);
                release.countDown();
                owner.get(5, TimeUnit.SECONDS);
                return contender.get(5, TimeUnit.SECONDS);
            } finally { release.countDown(); }
        }
    }

    @Test
    void simultaneousSameTokenHasOnlyOneSuccessorAndKeepsAbsoluteExpiry() throws Exception {
        String original = issue(user());
        Instant expires = row(original).getExpiresAt();
        AtomicReference<AuthService.Session> winner = new AtomicReference<>();
        Object loser = overlap(() -> winner.set(auth.refresh(original)), () -> refresh(original), false);
        assertThat(loser).isEqualTo(CONCURRENT_ROTATION);
        assertThat(active(original)).isEqualTo(1);
        RefreshToken successor = row(winner.get().refreshToken().value());
        assertThat(successor.getExpiresAt()).isEqualTo(expires);
        assertThat(successor.getTokenHash()).isNotEqualTo(winner.get().refreshToken().value());
    }

    @Test
    void logoutWaitsForRotationAndRevokesItsSuccessor() throws Exception {
        String original = issue(user());
        AtomicReference<String> successor = new AtomicReference<>();
        overlap(() -> successor.set(auth.refresh(original).refreshToken().value()), () -> { auth.logout(original); return true; }, false);
        assertThat(active(original)).isZero();
        assertThat(refresh(original)).isEqualTo(REVOKED);
        assertThat(refresh(successor.get())).isEqualTo(REVOKED);
    }

    @Test
    void rotationWaitsForLogoutAndCannotResurrectSession() throws Exception {
        String original = issue(user());
        Object result = overlap(() -> auth.logout(original), () -> refresh(original), false);
        assertThat(result).isEqualTo(REVOKED);
        assertThat(active(original)).isZero();
    }

    @Test
    void ancestorReplayWaitsForRotationAndRevocationCommits() throws Exception {
        String original = issue(user());
        String current = auth.refresh(original).refreshToken().value();
        age(original);
        AtomicReference<String> successor = new AtomicReference<>();
        Object result = overlap(() -> successor.set(auth.refresh(current).refreshToken().value()), () -> refresh(original), false);
        assertThat(result).isEqualTo(REPLAYED);
        assertThat(active(original)).isZero();
        assertThat(refresh(successor.get())).isEqualTo(REVOKED);
    }

    @Test
    void rotationWaitsForAncestorReplayAndCannotResurrectSession() throws Exception {
        String original = issue(user());
        String current = auth.refresh(original).refreshToken().value();
        age(original);
        Object result = overlap(() -> assertThat(refresh(original)).isEqualTo(REPLAYED), () -> refresh(current), false);
        assertThat(result).isEqualTo(REVOKED);
        assertThat(active(original)).isZero();
    }

    @Test
    void rollbackRestoresOriginalTokenAndAllowsWaitingRotation() throws Exception {
        String original = issue(user());
        AtomicReference<String> rolledBack = new AtomicReference<>();
        Object result = overlap(() -> rolledBack.set(auth.refresh(original).refreshToken().value()), () -> refresh(original), true);
        assertThat(result).isInstanceOf(AuthService.Session.class);
        assertThat(repository.findByTokenHash(RefreshTokenService.hash(rolledBack.get()))).isEmpty();
        assertThat(active(original)).isEqualTo(1);
    }

    @Test
    void unrelatedUserCanRefreshBeforeFirstUserCommits() throws Exception {
        String first = issue(user()), second = issue(user());
        CountDownLatch held = new CountDownLatch(1), release = new CountDownLatch(1);
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<?> owner = executor.submit(() -> tx.executeWithoutResult(status -> {
                auth.refresh(first); held.countDown(); await(release);
            }));
            try {
                await(held);
                Object result = executor.submit(() -> auth.refresh(second)).get(3, TimeUnit.SECONDS);
                assertThat(result).isInstanceOf(AuthService.Session.class);
            } finally { release.countDown(); }
            owner.get(5, TimeUnit.SECONDS);
        }
    }

    @Test
    void issuanceUsesSameUserLockWithoutRevokingOtherLoginFamilies() throws Exception {
        User user = user();
        String original = issue(user);
        Object issued = overlap(() -> auth.logout(original), () -> tokens.issue(user, false), false);
        assertThat(issued).isInstanceOf(RefreshTokenService.IssuedToken.class);
        String separateSession = ((RefreshTokenService.IssuedToken) issued).value();
        assertThat(refresh(original)).isEqualTo(REVOKED);
        assertThat(refresh(separateSession)).isInstanceOf(AuthService.Session.class);
    }

    @Test
    void simultaneousHttpRefreshReturnsRaceWithoutClearingWinningCookies() throws Exception {
        User user = user();
        String original = issue(user);
        BrowserSession left = new BrowserSession(mvc, "10.90.0.1"), right = new BrowserSession(mvc, "10.90.0.2");
        for (BrowserSession browser : List.of(left, right)) { browser.setCookie("refresh_token", original); browser.get("/api/auth/csrf"); }
        CountDownLatch held = new CountDownLatch(1), release = new CountDownLatch(1), started = new CountDownLatch(2);
        try (ExecutorService executor = Executors.newFixedThreadPool(3)) {
            Future<?> gate = executor.submit(() -> tx.executeWithoutResult(status -> {
                users.lockById(user.getId()).orElseThrow(); held.countDown(); await(release);
            }));
            try {
                await(held);
                var one = executor.submit(() -> { started.countDown(); return left.post("/api/auth/refresh", null); });
                var two = executor.submit(() -> { started.countDown(); return right.post("/api/auth/refresh", null); });
                await(started);
                assertThatThrownBy(() -> one.get(200, TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);
                assertThatThrownBy(() -> two.get(200, TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);
                release.countDown(); gate.get(5, TimeUnit.SECONDS);
                var results = List.of(one.get(5, TimeUnit.SECONDS), two.get(5, TimeUnit.SECONDS));
                assertThat(results.stream().map(r -> r.getResponse().getStatus()).toList()).containsExactlyInAnyOrder(200, 401);
                var loser = results.stream().filter(r -> r.getResponse().getStatus() == 401).findFirst().orElseThrow();
                assertThat(loser.getResponse().getContentAsString()).contains("REFRESH_RACE");
                assertThat(BrowserSession.setCookieHeaders(loser)).isEmpty();
                var winner = results.stream().filter(r -> r.getResponse().getStatus() == 200).findFirst().orElseThrow();
                assertThat(BrowserSession.setCookieHeader(winner, "refresh_token")).contains("HttpOnly");
                assertThat(active(original)).isEqualTo(1);
            } finally { release.countDown(); }
        }
    }

    @Test
    void expiredAndUnknownTokensAreTerminal() {
        String original = issue(user());
        tx.executeWithoutResult(status -> {
            RefreshToken token = row(original); token.setExpiresAt(Instant.now().minusSeconds(1)); repository.save(token);
        });
        assertThat(refresh(original)).isEqualTo(EXPIRED);
        assertThat(refresh("unknown-test-token")).isEqualTo(INVALID);
    }

    @Test
    void replayRevocationCommitsWhenExceptionLeavesAuthTransaction() {
        String original = issue(user());
        String current = auth.refresh(original).refreshToken().value();
        age(original);
        assertThatThrownBy(() -> auth.refresh(original)).isInstanceOfSatisfying(InvalidRefreshTokenException.class,
                ex -> assertThat(ex.getReason()).isEqualTo(REPLAYED));
        assertThat(active(current)).isZero();
    }

    private Long studentFor(User user) {
        var student = new com.studentmanagement.backend.entity.Student();
        student.setFirstName("Test"); student.setLastName("Student"); student.setEmail(user.getEmail());
        student.setUser(user); return students.save(student).getId();
    }

    @Test
    void accountDeletionWaitsForRefreshThenDeletesAllSuccessors() throws Exception {
        User user = user(); Long studentId = studentFor(user); String original = issue(user);
        AtomicReference<String> successor = new AtomicReference<>();
        overlap(() -> successor.set(auth.refresh(original).refreshToken().value()),
                () -> { studentService.deleteStudent(studentId); return true; }, false);
        assertThat(users.findById(user.getId())).isEmpty();
        assertThat(students.findById(studentId)).isEmpty();
        assertThat(repository.findByTokenHash(RefreshTokenService.hash(original))).isEmpty();
        assertThat(repository.findByTokenHash(RefreshTokenService.hash(successor.get()))).isEmpty();
        assertThat(refresh(successor.get())).isEqualTo(INVALID);
    }

    @Test
    void refreshWaitsForAccountDeletionAndCannotIssueCredentials() throws Exception {
        User user = user(); Long studentId = studentFor(user); String original = issue(user);
        Object result = overlap(() -> userService.deleteUser(user.getId()), () -> refresh(original), false);
        assertThat(result).isEqualTo(INVALID);
        assertThat(users.findById(user.getId())).isEmpty();
        assertThat(students.findById(studentId)).isEmpty();
        assertThat(repository.findByTokenHash(RefreshTokenService.hash(original))).isEmpty();
    }
}
