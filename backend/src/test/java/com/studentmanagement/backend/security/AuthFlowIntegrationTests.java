package com.studentmanagement.backend.security;

import com.jayway.jsonpath.JsonPath;
import com.studentmanagement.backend.entity.RefreshToken;
import com.studentmanagement.backend.entity.Role;
import com.studentmanagement.backend.entity.User;
import com.studentmanagement.backend.repository.RefreshTokenRepository;
import com.studentmanagement.backend.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end checks of the cookie-based session against an in-memory H2
 * database: login, cookies, CSRF, refresh rotation, logout, session
 * restoration, authorization, role escalation, and rate limiting.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthFlowIntegrationTests {

    private static final AtomicInteger SEQUENCE = new AtomicInteger();
    private static final String PASSWORD = "correct-horse-battery";

    private static final String ACCESS = "access_token";
    private static final String REFRESH = "refresh_token";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    // ---- helpers -------------------------------------------------------------

    /** Each test uses its own client address so per-client rate limits never interfere. */
    private BrowserSession newBrowser() {
        return new BrowserSession(mockMvc, "10.0.0." + SEQUENCE.incrementAndGet());
    }

    private String uniqueEmail(String prefix) {
        return prefix + SEQUENCE.incrementAndGet() + "@example.com";
    }

    private String registerStudent(BrowserSession browser, String email) throws Exception {
        int n = SEQUENCE.incrementAndGet();
        MvcResult result = browser.post("/api/auth/register", """
                {"firstName":"Test","lastName":"Student","username":"student%d",
                 "email":"%s","password":"%s"}
                """.formatted(n, email, PASSWORD));
        assertThat(result.getResponse().getStatus()).isEqualTo(201);
        return email;
    }

    private User createAdminFixture() {
        User admin = new User();
        admin.setUsername("admin" + SEQUENCE.incrementAndGet());
        admin.setEmail(uniqueEmail("admin"));
        admin.setPassword(passwordEncoder.encode(PASSWORD));
        admin.setRole(Role.ADMIN);
        return userRepository.save(admin);
    }

    private MvcResult login(BrowserSession browser, String email, String password, boolean rememberMe)
            throws Exception {
        return browser.post("/api/auth/login", """
                {"email":"%s","password":"%s","rememberMe":%s}
                """.formatted(email, password, rememberMe));
    }

    private BrowserSession signedInStudent() throws Exception {
        BrowserSession browser = newBrowser();
        String email = registerStudent(browser, uniqueEmail("student"));
        assertThat(login(browser, email, PASSWORD, false).getResponse().getStatus()).isEqualTo(200);
        return browser;
    }

    private BrowserSession signedInAdmin() throws Exception {
        BrowserSession browser = newBrowser();
        User admin = createAdminFixture();
        assertThat(login(browser, admin.getEmail(), PASSWORD, false).getResponse().getStatus()).isEqualTo(200);
        return browser;
    }

    private static int status(MvcResult result) {
        return result.getResponse().getStatus();
    }

    private static String body(MvcResult result) throws Exception {
        return result.getResponse().getContentAsString();
    }

    // ---- registration ----------------------------------------------------------

    @Test
    void registrationAlwaysCreatesStudentEvenIfRoleIsSent() throws Exception {
        BrowserSession browser = newBrowser();
        String email = uniqueEmail("escalate");

        MvcResult result = browser.post("/api/auth/register", """
                {"firstName":"Eve","lastName":"Attacker","username":"eve%d",
                 "email":"%s","password":"%s","role":"ADMIN"}
                """.formatted(SEQUENCE.incrementAndGet(), email, PASSWORD));

        assertThat(status(result)).isEqualTo(201);
        assertThat(userRepository.findAllByEmailIgnoreCase(email))
                .singleElement()
                .extracting(User::getRole)
                .isEqualTo(Role.STUDENT);
    }

    @Test
    void registrationRejectsDuplicateEmailIgnoringCase() throws Exception {
        BrowserSession browser = newBrowser();
        String email = registerStudent(browser, uniqueEmail("dup"));

        MvcResult result = browser.post("/api/auth/register", """
                {"firstName":"A","lastName":"B","username":"dup%d","email":"%s","password":"%s"}
                """.formatted(SEQUENCE.incrementAndGet(), email.toUpperCase(), PASSWORD));

        assertThat(status(result)).isEqualTo(409);
        assertThat(body(result)).contains("Email is already registered");
    }

    // ---- CSRF ------------------------------------------------------------------

    @Test
    void unsafeRequestsWithoutCsrfHeaderAreRejected() throws Exception {
        BrowserSession browser = newBrowser();
        browser.get("/api/auth/csrf");
        assertThat(browser.cookie(BrowserSession.CSRF_COOKIE)).isNotBlank();

        MvcResult result = browser.postWithoutCsrfHeader("/api/auth/login", """
                {"email":"nobody@example.com","password":"whatever-password"}
                """);

        assertThat(status(result)).isEqualTo(403);
        assertThat(JsonPath.<String>read(body(result), "$.code")).isEqualTo("CSRF_TOKEN_INVALID");
    }

    @Test
    void cookieAuthenticatedRequestsStillRequireCsrfHeader() throws Exception {
        // Guards against the resource-server default that exempts bearer-token
        // requests from CSRF, which would be unsafe with cookie-held tokens.
        BrowserSession admin = signedInAdmin();

        MvcResult withoutHeader = admin.postWithoutCsrfHeader("/api/courses", """
                {"courseCode":"CSRF%d","courseName":"Should not be created"}
                """.formatted(SEQUENCE.incrementAndGet()));
        assertThat(status(withoutHeader)).isEqualTo(403);
        assertThat(JsonPath.<String>read(body(withoutHeader), "$.code")).isEqualTo("CSRF_TOKEN_INVALID");

        MvcResult withHeader = admin.post("/api/courses", """
                {"courseCode":"CSRF%d","courseName":"Created with CSRF header"}
                """.formatted(SEQUENCE.incrementAndGet()));
        assertThat(status(withHeader)).isEqualTo(201);
    }

    @Test
    void csrfCookieIsReadableByScriptButAuthCookiesAreNot() throws Exception {
        BrowserSession browser = newBrowser();
        MvcResult csrf = browser.get("/api/auth/csrf");
        assertThat(BrowserSession.setCookieHeader(csrf, BrowserSession.CSRF_COOKIE))
                .doesNotContainIgnoringCase("HttpOnly");

        String email = registerStudent(browser, uniqueEmail("cookie"));
        MvcResult login = login(browser, email, PASSWORD, false);
        assertThat(BrowserSession.setCookieHeader(login, ACCESS)).contains("HttpOnly");
        assertThat(BrowserSession.setCookieHeader(login, REFRESH)).contains("HttpOnly");
    }

    @Test
    void csrfTokenRotatesOnSignInButNotOnEveryRequest() throws Exception {
        BrowserSession browser = newBrowser();
        String email = registerStudent(browser, uniqueEmail("rotate"));
        String beforeLogin = browser.cookie(BrowserSession.CSRF_COOKIE);

        login(browser, email, PASSWORD, false);
        String afterLogin = browser.cookie(BrowserSession.CSRF_COOKIE);
        assertThat(afterLogin).isNotBlank().isNotEqualTo(beforeLogin);

        for (int i = 0; i < 3; i++) {
            MvcResult me = browser.get("/api/auth/me");
            assertThat(status(me)).isEqualTo(200);
            assertThat(BrowserSession.setCookieHeader(me, BrowserSession.CSRF_COOKIE)).isNull();
        }
        assertThat(browser.cookie(BrowserSession.CSRF_COOKIE)).isEqualTo(afterLogin);

        assertThat(status(browser.post("/api/auth/logout", null))).isEqualTo(204);
        assertThat(browser.cookie(BrowserSession.CSRF_COOKIE)).isNotBlank().isNotEqualTo(afterLogin);
    }

    // ---- login -----------------------------------------------------------------

    @Test
    void loginSetsHttpOnlyCookiesAndNeverReturnsTokens() throws Exception {
        BrowserSession browser = newBrowser();
        String email = registerStudent(browser, uniqueEmail("login"));

        MvcResult result = login(browser, "  " + email.toUpperCase() + "  ", PASSWORD, false);

        assertThat(status(result)).isEqualTo(200);
        assertThat(JsonPath.<String>read(body(result), "$.email")).isEqualTo(email);
        assertThat(JsonPath.<String>read(body(result), "$.role")).isEqualTo("STUDENT");
        assertThat(body(result))
                .doesNotContain(browser.cookie(ACCESS))
                .doesNotContain(browser.cookie(REFRESH))
                .doesNotContainIgnoringCase("password");

        assertThat(BrowserSession.setCookieHeader(result, ACCESS))
                .contains("HttpOnly", "SameSite=Lax", "Path=/", "Max-Age=900");
        assertThat(BrowserSession.setCookieHeader(result, REFRESH))
                .contains("HttpOnly", "SameSite=Strict", "Path=/api/auth");
    }

    @Test
    void rememberMeControlsRefreshCookiePersistence() throws Exception {
        BrowserSession browser = newBrowser();
        String email = registerStudent(browser, uniqueEmail("remember"));

        MvcResult session = login(browser, email, PASSWORD, false);
        assertThat(BrowserSession.setCookieHeader(session, REFRESH)).doesNotContain("Max-Age");

        MvcResult persistent = login(newBrowser(), email, PASSWORD, true);
        assertThat(BrowserSession.setCookieHeader(persistent, REFRESH)).contains("Max-Age=604800");

        // Rotation keeps the chosen persistence.
        BrowserSession remembered = newBrowser();
        login(remembered, email, PASSWORD, true);
        MvcResult rotated = remembered.post("/api/auth/refresh", null);
        assertThat(BrowserSession.setCookieHeader(rotated, REFRESH)).contains("Max-Age=");
    }

    @Test
    void rememberMeIsOptionalAndDefaultsToSessionCookie() throws Exception {
        BrowserSession browser = newBrowser();
        String email = registerStudent(browser, uniqueEmail("optional"));

        MvcResult result = browser.post("/api/auth/login", """
                {"email":"%s","password":"%s"}
                """.formatted(email, PASSWORD));

        assertThat(status(result)).isEqualTo(200);
        assertThat(BrowserSession.setCookieHeader(result, REFRESH)).doesNotContain("Max-Age");
    }

    @Test
    void wrongPasswordAndUnknownEmailFailIdentically() throws Exception {
        BrowserSession browser = newBrowser();
        String email = registerStudent(browser, uniqueEmail("wrong"));

        MvcResult wrongPassword = login(browser, email, "not-the-password", false);
        MvcResult unknownEmail = login(newBrowser(), uniqueEmail("ghost"), PASSWORD, false);

        assertThat(status(wrongPassword)).isEqualTo(401);
        assertThat(status(unknownEmail)).isEqualTo(401);
        assertThat(body(wrongPassword)).isEqualTo(body(unknownEmail));
        assertThat(browser.cookie(ACCESS)).isNull();
    }

    // ---- session ---------------------------------------------------------------

    @Test
    void meRequiresAValidAccessToken() throws Exception {
        BrowserSession anonymous = newBrowser();
        MvcResult unauthenticated = anonymous.get("/api/auth/me");
        assertThat(status(unauthenticated)).isEqualTo(401);
        assertThat(JsonPath.<String>read(body(unauthenticated), "$.code")).isEqualTo("UNAUTHENTICATED");

        BrowserSession student = signedInStudent();
        assertThat(status(student.get("/api/auth/me"))).isEqualTo(200);

        // Tampering with the signature invalidates the token.
        String[] parts = student.cookie(ACCESS).split("\\.");
        String forgedPayload = Base64.getUrlEncoder().withoutPadding()
                .encodeToString("{\"sub\":\"1\",\"role\":\"ADMIN\",\"iss\":\"student-management-system\"}".getBytes());
        student.setCookie(ACCESS, parts[0] + "." + forgedPayload + "." + parts[2]);
        assertThat(status(student.get("/api/auth/me"))).isEqualTo(401);
    }

    @Test
    void refreshRotatesTokenAndRestoresSessionAfterAccessTokenLoss() throws Exception {
        BrowserSession browser = signedInStudent();
        String firstRefresh = browser.cookie(REFRESH);

        // Simulates the access cookie expiring (or a browser restart with Remember me).
        browser.dropCookie(ACCESS);
        assertThat(status(browser.get("/api/auth/me"))).isEqualTo(401);

        MvcResult refreshed = browser.post("/api/auth/refresh", null);
        assertThat(status(refreshed)).isEqualTo(200);
        assertThat(browser.cookie(REFRESH)).isNotEqualTo(firstRefresh);
        assertThat(browser.cookie(ACCESS)).isNotBlank();
        assertThat(status(browser.get("/api/auth/me"))).isEqualTo(200);
    }

    @Test
    void reusingARotatedRefreshTokenRevokesTheWholeSession() throws Exception {
        BrowserSession browser = signedInStudent();
        String stolen = browser.cookie(REFRESH);

        assertThat(status(browser.post("/api/auth/refresh", null))).isEqualTo(200);
        String current = browser.cookie(REFRESH);

        // Move the rotation outside the race grace window, then replay the old token.
        RefreshToken rotated = refreshTokenRepository.findByTokenHash(RefreshTokenService.hash(stolen)).orElseThrow();
        rotated.setRevokedAt(Instant.now().minus(1, ChronoUnit.MINUTES));
        refreshTokenRepository.save(rotated);

        BrowserSession attacker = newBrowser();
        attacker.setCookie(REFRESH, stolen);
        assertThat(status(attacker.post("/api/auth/refresh", null))).isEqualTo(401);

        // The legitimate holder's newer token is revoked too.
        BrowserSession victim = newBrowser();
        victim.setCookie(REFRESH, current);
        assertThat(status(victim.post("/api/auth/refresh", null))).isEqualTo(401);
    }

    @Test
    void concurrentRefreshWithinGraceWindowDoesNotRevokeOrClearCookies() throws Exception {
        BrowserSession browser = signedInStudent();
        String original = browser.cookie(REFRESH);
        assertThat(status(browser.post("/api/auth/refresh", null))).isEqualTo(200);
        String current = browser.cookie(REFRESH);

        BrowserSession otherTab = newBrowser();
        otherTab.setCookie(REFRESH, original);
        MvcResult raced = otherTab.post("/api/auth/refresh", null);
        assertThat(status(raced)).isEqualTo(401);
        assertThat(BrowserSession.setCookieHeader(raced, REFRESH)).isNull();

        BrowserSession sameUser = newBrowser();
        sameUser.setCookie(REFRESH, current);
        assertThat(status(sameUser.post("/api/auth/refresh", null))).isEqualTo(200);
    }

    @Test
    void expiredRefreshTokenIsRejectedServerSide() throws Exception {
        BrowserSession browser = signedInStudent();
        RefreshToken token = refreshTokenRepository
                .findByTokenHash(RefreshTokenService.hash(browser.cookie(REFRESH))).orElseThrow();
        token.setExpiresAt(Instant.now().minusSeconds(1));
        refreshTokenRepository.save(token);

        MvcResult result = browser.post("/api/auth/refresh", null);
        assertThat(status(result)).isEqualTo(401);
        assertThat(BrowserSession.setCookieHeader(result, REFRESH)).contains("Max-Age=0");
    }

    @Test
    void logoutRevokesRefreshTokenAndClearsCookies() throws Exception {
        BrowserSession browser = signedInStudent();
        String refresh = browser.cookie(REFRESH);

        MvcResult result = browser.post("/api/auth/logout", null);
        assertThat(status(result)).isEqualTo(204);
        assertThat(BrowserSession.setCookieHeader(result, ACCESS)).contains("Max-Age=0");
        assertThat(BrowserSession.setCookieHeader(result, REFRESH)).contains("Max-Age=0");
        assertThat(browser.cookie(ACCESS)).isNull();

        BrowserSession replay = newBrowser();
        replay.setCookie(REFRESH, refresh);
        assertThat(status(replay.post("/api/auth/refresh", null))).isEqualTo(401);
    }

    // ---- authorization ---------------------------------------------------------

    @Test
    void anonymousRequestsToProtectedEndpointsGet401() throws Exception {
        BrowserSession anonymous = newBrowser();
        assertThat(status(anonymous.get("/api/students"))).isEqualTo(401);
        assertThat(status(anonymous.get("/api/courses"))).isEqualTo(401);
        assertThat(status(anonymous.get("/api/users"))).isEqualTo(401);
        assertThat(status(anonymous.get("/api/students/me"))).isEqualTo(401);
    }

    @Test
    void studentAccessIsLimitedToOwnRecordAndCourseCatalog() throws Exception {
        BrowserSession student = signedInStudent();
        String email = JsonPath.read(body(student.get("/api/auth/me")), "$.email");

        MvcResult me = student.get("/api/students/me");
        assertThat(status(me)).isEqualTo(200);
        assertThat(JsonPath.<String>read(body(me), "$.email")).isEqualTo(email);

        assertThat(status(student.get("/api/courses"))).isEqualTo(200);
        assertThat(status(student.post("/api/courses", """
                {"courseCode":"NOPE%d","courseName":"Student cannot create"}
                """.formatted(SEQUENCE.incrementAndGet())))).isEqualTo(403);
        assertThat(status(student.get("/api/students"))).isEqualTo(403);
        assertThat(status(student.get("/api/students/1"))).isEqualTo(403);
        assertThat(status(student.get("/api/users"))).isEqualTo(403);
    }

    @Test
    void adminCanManageRecordsButHasNoStudentSelfEndpoint() throws Exception {
        BrowserSession admin = signedInAdmin();

        assertThat(status(admin.get("/api/students"))).isEqualTo(200);
        assertThat(status(admin.get("/api/users"))).isEqualTo(200);
        assertThat(status(admin.get("/api/courses"))).isEqualTo(200);
        assertThat(status(admin.get("/api/students/me"))).isEqualTo(403);
    }

    @Test
    void userApiCannotCreatePromoteOrModifyAdmins() throws Exception {
        BrowserSession admin = signedInAdmin();

        MvcResult createAdmin = admin.post("/api/users", """
                {"username":"rogue%d","email":"%s","password":"%s","role":"admin"}
                """.formatted(SEQUENCE.incrementAndGet(), uniqueEmail("rogue"), PASSWORD));
        assertThat(status(createAdmin)).isEqualTo(403);

        MvcResult createStudent = admin.post("/api/users", """
                {"username":"made%d","email":"%s","password":"%s","role":"STUDENT"}
                """.formatted(SEQUENCE.incrementAndGet(), uniqueEmail("made"), PASSWORD));
        assertThat(status(createStudent)).isEqualTo(201);
        Integer studentUserId = JsonPath.read(body(createStudent), "$.id");

        MvcResult promote = admin.put("/api/users/" + studentUserId, """
                {"username":"made-admin","email":"%s","password":"%s","role":"ADMIN"}
                """.formatted(uniqueEmail("promoted"), PASSWORD));
        assertThat(status(promote)).isEqualTo(403);
        assertThat(userRepository.findById(studentUserId.longValue()).orElseThrow().getRole())
                .isEqualTo(Role.STUDENT);

        User otherAdmin = createAdminFixture();
        assertThat(status(admin.delete("/api/users/" + otherAdmin.getId()))).isEqualTo(403);
        assertThat(userRepository.findById(otherAdmin.getId())).isPresent();
    }

    // ---- rate limiting -----------------------------------------------------------

    @Test
    void repeatedFailuresLockTheEmailEvenForTheCorrectPassword() throws Exception {
        BrowserSession browser = newBrowser();
        String email = registerStudent(browser, uniqueEmail("ratelimit"));

        for (int attempt = 0; attempt < 5; attempt++) {
            assertThat(status(login(browser, email, "wrong-password-" + attempt, false))).isEqualTo(401);
        }

        MvcResult locked = login(newBrowser(), email, PASSWORD, false);
        assertThat(status(locked)).isEqualTo(429);
        assertThat(locked.getResponse().getHeader("Retry-After")).isNotBlank();
        assertThat(JsonPath.<String>read(body(locked), "$.code")).isEqualTo("RATE_LIMITED");
    }
}
