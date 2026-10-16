package com.studentmanagement.backend.security;

import com.jayway.jsonpath.JsonPath;
import com.studentmanagement.backend.entity.RefreshToken;
import com.studentmanagement.backend.entity.Role;
import com.studentmanagement.backend.entity.User;
import com.studentmanagement.backend.repository.RefreshTokenRepository;
import com.studentmanagement.backend.repository.UserRepository;
import com.studentmanagement.backend.repository.StudentRepository;
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

    @Autowired
    private StudentRepository studentRepository;

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
                 "email":"%s","password":"%s","phone":"+94 77 123 4567"}
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
                 "email":"%s","password":"%s","phone":"+94 77 123 4567","role":"ADMIN"}
                """.formatted(SEQUENCE.incrementAndGet(), email, PASSWORD));

        assertThat(status(result)).isEqualTo(201);
        assertThat(userRepository.findAllByEmailIgnoreCase(email))
                .singleElement()
                .extracting(User::getRole)
                .isEqualTo(Role.STUDENT);
        User account = userRepository.findAllByEmailIgnoreCase(email).getFirst();
        var linked = studentRepository.findByUser_Id(account.getId()).orElseThrow();
        assertThat(linked.getEmail()).isEqualTo(email);
        assertThat(linked.getFirstName()).isEqualTo("Eve");
        assertThat(studentRepository.findAllByEmailIgnoreCase(email)).hasSize(1);
    }

    @Test
    void registrationStoresTheContactNumberOnTheStudentRecord() throws Exception {
        BrowserSession browser = newBrowser();
        String email = uniqueEmail("phone");

        MvcResult result = browser.post("/api/auth/register", """
                {"firstName":"Phone","lastName":"Owner","username":"phone%d",
                 "email":"%s","password":"%s","phone":"  +94 (77)   123-4567 "}
                """.formatted(SEQUENCE.incrementAndGet(), email, PASSWORD));

        assertThat(status(result)).isEqualTo(201);
        User account = userRepository.findAllByEmailIgnoreCase(email).getFirst();
        assertThat(studentRepository.findByUser_Id(account.getId()).orElseThrow().getPhone())
                .isEqualTo("+94 (77) 123-4567");

        assertThat(status(login(browser, email, PASSWORD, false))).isEqualTo(200);
        assertThat(JsonPath.<String>read(body(browser.get("/api/students/me")), "$.phone"))
                .isEqualTo("+94 (77) 123-4567");
    }

    @Test
    void registrationRequiresAValidContactNumber() throws Exception {
        BrowserSession browser = newBrowser();
        String email = uniqueEmail("nophone");

        for (String phone : new String[] {null, "", "not-a-number", "12345", "1234567890123456"}) {
            String phoneJson = phone == null ? "" : ",\"phone\":\"" + phone + "\"";
            MvcResult result = browser.post("/api/auth/register", """
                    {"firstName":"No","lastName":"Phone","username":"nophone%d",
                     "email":"%s","password":"%s"%s}
                    """.formatted(SEQUENCE.incrementAndGet(), email, PASSWORD, phoneJson));

            assertThat(status(result)).as("phone=%s", phone).isEqualTo(400);
            assertThat(JsonPath.<String>read(body(result), "$.errors.phone")).as("phone=%s", phone).isNotBlank();
        }
        assertThat(userRepository.findAllByEmailIgnoreCase(email)).isEmpty();
    }

    @Test
    void registrationRejectsDuplicateEmailIgnoringCase() throws Exception {
        BrowserSession browser = newBrowser();
        String email = registerStudent(browser, uniqueEmail("dup"));

        MvcResult result = browser.post("/api/auth/register", """
                {"firstName":"A","lastName":"B","username":"dup%d","email":"%s","password":"%s","phone":"+94 77 123 4567"}
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
        assertThat(JsonPath.<String>read(body(raced), "$.code")).isEqualTo("REFRESH_RACE");

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
        MvcResult revoked = replay.post("/api/auth/refresh", null);
        assertThat(status(revoked)).isEqualTo(401);
        assertThat(JsonPath.<String>read(body(revoked), "$.code")).isEqualTo("SESSION_EXPIRED");
        assertThat(BrowserSession.setCookieHeader(revoked, REFRESH)).contains("Max-Age=0");
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
        assertThat(status(createStudent)).isEqualTo(403);
        String registeredEmail = registerStudent(newBrowser(), uniqueEmail("registered"));
        Long studentUserId = userRepository.findAllByEmailIgnoreCase(registeredEmail).getFirst().getId();

        MvcResult promote = admin.put("/api/users/" + studentUserId, """
                {"username":"made-admin","email":"%s","password":"%s","role":"ADMIN"}
                """.formatted(uniqueEmail("promoted"), PASSWORD));
        assertThat(status(promote)).isEqualTo(403);
        assertThat(userRepository.findById(studentUserId).orElseThrow().getRole())
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

    @Test
    void registrationRejectsOversizedUnicodeWithPasswordFieldError() throws Exception {
        BrowserSession browser = newBrowser();
        String password = "\u0dc3".repeat(24) + "a";
        String email = uniqueEmail("oversized");
        MvcResult result = browser.post("/api/auth/register", """
                {"firstName":"A","lastName":"B","username":"bytes%d","email":"%s","password":"%s","phone":"+94 77 123 4567"}
                """.formatted(SEQUENCE.incrementAndGet(), email, password));
        assertThat(status(result)).isEqualTo(400);
        assertThat(JsonPath.<String>read(body(result), "$.code")).isEqualTo("VALIDATION_ERROR");
        assertThat(JsonPath.<String>read(body(result), "$.errors.password")).contains("72 UTF-8 bytes");
        assertThat(userRepository.findAllByEmailIgnoreCase(email)).isEmpty();
    }

    @Test
    void userManagementRejectsInvalidRoleAndPasswordAsBadRequest() throws Exception {
        BrowserSession admin = signedInAdmin();
        String email = registerStudent(newBrowser(), uniqueEmail("invalidrole"));
        Long userId = userRepository.findAllByEmailIgnoreCase(email).getFirst().getId();
        String payload = """
                {"username":"input%d","email":"%s","password":"%s","role":"%s"}
                """;
        MvcResult role = admin.put("/api/users/" + userId, payload.formatted(SEQUENCE.incrementAndGet(), email, PASSWORD, "INVALID"));
        assertThat(status(role)).isEqualTo(400);
        assertThat(JsonPath.<String>read(body(role), "$.code")).isEqualTo("VALIDATION_ERROR");
        assertThat(JsonPath.<String>read(body(role), "$.errors.role")).contains("Invalid role");
        MvcResult password = admin.put("/api/users/" + userId, payload.formatted(SEQUENCE.incrementAndGet(), email, "a".repeat(73), "STUDENT"));
        assertThat(status(password)).isEqualTo(400);
        assertThat(JsonPath.<String>read(body(password), "$.errors.password")).contains("72 UTF-8 bytes");
    }

    @Test
    void duplicateAndMissingResourcesUseJsonErrorCodes() throws Exception {
        BrowserSession browser = newBrowser();
        String email = registerStudent(browser, uniqueEmail("conflict"));
        MvcResult duplicate = browser.post("/api/auth/register", """
                {"firstName":"A","lastName":"B","username":"duplicate%d","email":"%s","password":"%s","phone":"+94 77 123 4567"}
                """.formatted(SEQUENCE.incrementAndGet(), email, PASSWORD));
        assertThat(status(duplicate)).isEqualTo(409);
        assertThat(JsonPath.<String>read(body(duplicate), "$.code")).isEqualTo("DUPLICATE_RECORD");
        MvcResult missing = signedInAdmin().get("/api/students/9223372036854775807");
        assertThat(status(missing)).isEqualTo(404);
        assertThat(JsonPath.<String>read(body(missing), "$.code")).isEqualTo("RESOURCE_NOT_FOUND");
        MvcResult invalidId = signedInAdmin().get("/api/students/not-a-number");
        assertThat(status(invalidId)).isEqualTo(400);
        assertThat(JsonPath.<String>read(body(invalidId), "$.code")).isEqualTo("INVALID_INPUT");
    }

    @Test
    void malformedAndBeanValidationErrorsUseJson400() throws Exception {
        BrowserSession browser = newBrowser();
        MvcResult malformed = browser.post("/api/auth/register", "{broken");
        assertThat(status(malformed)).isEqualTo(400);
        assertThat(JsonPath.<String>read(body(malformed), "$.code")).isEqualTo("INVALID_INPUT");
        MvcResult invalid = browser.post("/api/auth/register", "{}");
        assertThat(status(invalid)).isEqualTo(400);
        assertThat(JsonPath.<String>read(body(invalid), "$.code")).isEqualTo("VALIDATION_ERROR");
        assertThat(JsonPath.<String>read(body(invalid), "$.errors.password")).isNotBlank();
    }

    @Test
    void securityErrorCodesAndOversizedLoginRemainGeneric() throws Exception {
        BrowserSession anonymous = newBrowser();
        MvcResult unauthenticated = anonymous.get("/api/students");
        assertThat(status(unauthenticated)).isEqualTo(401);
        assertThat(JsonPath.<String>read(body(unauthenticated), "$.code")).isEqualTo("UNAUTHENTICATED");
        MvcResult csrf = anonymous.postWithoutCsrfHeader("/api/auth/login", "{}");
        assertThat(status(csrf)).isEqualTo(403);
        assertThat(JsonPath.<String>read(body(csrf), "$.code")).isEqualTo("CSRF_TOKEN_INVALID");
        MvcResult forbidden = signedInStudent().get("/api/users");
        assertThat(status(forbidden)).isEqualTo(403);
        assertThat(JsonPath.<String>read(body(forbidden), "$.code")).isEqualTo("FORBIDDEN");
        String email = registerStudent(anonymous, uniqueEmail("longlogin"));
        MvcResult oversized = login(anonymous, email, "a".repeat(73), false);
        MvcResult unknown = login(newBrowser(), uniqueEmail("unknown"), "a".repeat(73), false);
        assertThat(status(oversized)).isEqualTo(401);
        assertThat(body(oversized)).isEqualTo(body(unknown));
        assertThat(JsonPath.<String>read(body(oversized), "$.message")).isEqualTo("Invalid email or password.");
    }

    @Test
    void adminCannotCreateStudentsOrAccountsEvenWithValidCsrf() throws Exception {
        BrowserSession admin = signedInAdmin();
        long beforeUsers = userRepository.count(), beforeStudents = studentRepository.count();
        MvcResult student = admin.post("/api/students", """
                {"firstName":"Manual","lastName":"Student","email":"%s"}
                """.formatted(uniqueEmail("manual")));
        assertThat(status(student)).isEqualTo(403);
        assertThat(JsonPath.<String>read(body(student), "$.code")).isEqualTo("FORBIDDEN");
        for (String role : java.util.List.of("STUDENT", "ADMIN")) {
            MvcResult account = admin.post("/api/users", """
                    {"username":"manual%d","email":"%s","password":"%s","role":"%s"}
                    """.formatted(SEQUENCE.incrementAndGet(), uniqueEmail("manualaccount"), PASSWORD, role));
            assertThat(status(account)).isEqualTo(403);
        }
        assertThat(userRepository.count()).isEqualTo(beforeUsers);
        assertThat(studentRepository.count()).isEqualTo(beforeStudents);
        assertThat(java.util.Arrays.stream(com.studentmanagement.backend.service.impl.UserServiceImpl.class.getMethods())
                .map(java.lang.reflect.Method::getName)).doesNotContain("createUser");
    }

    @Test
    void registrationPersistenceFailureRollsBackBothRecords() throws Exception {
        String email = uniqueEmail("rollback");
        // Valid request reaches persistence; the second insert exceeds the entity column length.
        MvcResult result = newBrowser().post("/api/auth/register", """
                {"firstName":"%s","lastName":"Student","username":"rollback%d","email":"%s","password":"%s","phone":"+94 77 123 4567"}
                """.formatted("A".repeat(256), SEQUENCE.incrementAndGet(), email, PASSWORD));
        assertThat(status(result)).isEqualTo(409);
        assertThat(userRepository.findAllByEmailIgnoreCase(email)).isEmpty();
        assertThat(studentRepository.findAllByEmailIgnoreCase(email)).isEmpty();
    }

    @Test
    void legacyUnlinkedStudentIsNotClaimedByMatchingSignupEmail() throws Exception {
        String email = uniqueEmail("legacy");
        var legacy = new com.studentmanagement.backend.entity.Student();
        legacy.setFirstName("Legacy"); legacy.setLastName("Record"); legacy.setEmail(email);
        Long legacyId = studentRepository.save(legacy).getId();
        MvcResult result = newBrowser().post("/api/auth/register", """
                {"firstName":"Claim","lastName":"Attempt","username":"claim%d","email":"%s","password":"%s","phone":"+94 77 123 4567"}
                """.formatted(SEQUENCE.incrementAndGet(), email.toUpperCase(), PASSWORD));
        assertThat(status(result)).isEqualTo(409);
        assertThat(userRepository.findAllByEmailIgnoreCase(email)).isEmpty();
        var unchanged = studentRepository.findById(legacyId).orElseThrow();
        assertThat(unchanged.getUser()).isNull();
        assertThat(unchanged.getFirstName()).isEqualTo("Legacy");
    }

    @Test
    void standaloneLegacyUserIsNotModifiedOrGivenStudentOnSignup() throws Exception {
        User legacy = new User(); legacy.setUsername("standalone" + SEQUENCE.incrementAndGet());
        String email = uniqueEmail("standalone"); legacy.setEmail(email);
        String existingHash = passwordEncoder.encode(PASSWORD); legacy.setPassword(existingHash); legacy.setRole(Role.STUDENT);
        Long id = userRepository.save(legacy).getId();
        MvcResult result = newBrowser().post("/api/auth/register", """
                {"firstName":"Claim","lastName":"Attempt","username":"claim%d","email":"%s","password":"%s","phone":"+94 77 123 4567"}
                """.formatted(SEQUENCE.incrementAndGet(), email, PASSWORD));
        assertThat(status(result)).isEqualTo(409);
        assertThat(studentRepository.findByUser_Id(id)).isEmpty();
        assertThat(userRepository.findById(id).orElseThrow().getPassword()).isEqualTo(existingHash);
    }

    @Test
    void adminCanViewEditAndDeleteRegisteredStudentAccount() throws Exception {
        BrowserSession student = newBrowser();
        String email = registerStudent(student, uniqueEmail("managed"));
        User account = userRepository.findAllByEmailIgnoreCase(email).getFirst();
        Long id = studentRepository.findByUser_Id(account.getId()).orElseThrow().getId();
        BrowserSession admin = signedInAdmin();
        assertThat(status(admin.get("/api/students"))).isEqualTo(200);
        assertThat(status(admin.get("/api/students/" + id))).isEqualTo(200);
        MvcResult edited = admin.put("/api/students/" + id, """
                {"firstName":"Updated","lastName":"Student","email":"%s","phone":"0700000000","course":"Computer Science"}
                """.formatted(email));
        assertThat(status(edited)).isEqualTo(200);
        assertThat(JsonPath.<String>read(body(edited), "$.firstName")).isEqualTo("Updated");
        assertThat(studentRepository.findByUser_Id(account.getId()).orElseThrow().getId()).isEqualTo(id);
        assertThat(status(login(student, email, PASSWORD, false))).isEqualTo(200);
        assertThat(JsonPath.<String>read(body(student.get("/api/students/me")), "$.firstName")).isEqualTo("Updated");
        assertThat(status(admin.delete("/api/students/" + id))).isEqualTo(204);
        assertThat(studentRepository.findById(id)).isEmpty();
        assertThat(userRepository.findById(account.getId())).isEmpty();
        assertThat(status(student.get("/api/students/me"))).isEqualTo(401);
        assertThat(status(student.get("/api/courses"))).isEqualTo(401);
    }

    @Test
    void studentCannotReadOrMutateAnotherRegisteredStudentsInformation() throws Exception {
        BrowserSession first = signedInStudent(), second = signedInStudent();
        Number firstId = JsonPath.read(body(first.get("/api/students/me")), "$.id");
        Number secondId = JsonPath.read(body(second.get("/api/students/me")), "$.id");
        assertThat(firstId.longValue()).isNotEqualTo(secondId.longValue());
        assertThat(status(first.get("/api/students/" + secondId))).isEqualTo(403);
        assertThat(status(first.put("/api/students/" + secondId, "{}"))).isEqualTo(403);
        assertThat(status(first.delete("/api/students/" + secondId))).isEqualTo(403);
        assertThat(status(first.post("/api/students", "{}"))).isEqualTo(403);
        assertThat(status(first.post("/api/users", "{}"))).isEqualTo(403);
        assertThat(JsonPath.<Number>read(body(first.get("/api/students/me?userId=" + secondId)), "$.id").longValue()).isEqualTo(firstId.longValue());
    }

    @Test
    void adminCourseCrudRemainsAvailable() throws Exception {
        BrowserSession admin = signedInAdmin();
        String code = "CRUD" + SEQUENCE.incrementAndGet();
        MvcResult created = admin.post("/api/courses", """
                {"courseCode":"%s","courseName":"Original","durationMonths":6}
                """.formatted(code));
        assertThat(status(created)).isEqualTo(201);
        Number id = JsonPath.read(body(created), "$.id");
        assertThat(status(admin.get("/api/courses/" + id))).isEqualTo(200);
        MvcResult updated = admin.put("/api/courses/" + id, """
                {"courseCode":"%s","courseName":"Updated","durationMonths":12}
                """.formatted(code));
        assertThat(status(updated)).isEqualTo(200);
        assertThat(JsonPath.<String>read(body(updated), "$.courseName")).isEqualTo("Updated");
        assertThat(status(admin.delete("/api/courses/" + id))).isEqualTo(204);
        assertThat(status(admin.get("/api/courses/" + id))).isEqualTo(404);
    }

    @Test
    void concurrentDuplicateRegistrationCreatesOnlyOneLinkedPair() throws Exception {
        String email = uniqueEmail("racedregistration");
        BrowserSession first = newBrowser(), second = newBrowser();
        first.get("/api/auth/csrf"); second.get("/api/auth/csrf");
        var barrier = new java.util.concurrent.CyclicBarrier(2);
        try (var executor = java.util.concurrent.Executors.newFixedThreadPool(2)) {
            var one = executor.submit(() -> {
                barrier.await(5, java.util.concurrent.TimeUnit.SECONDS);
                return first.post("/api/auth/register", """
                        {"firstName":"First","lastName":"Student","username":"race%d","email":"%s","password":"%s","phone":"+94 77 123 4567"}
                        """.formatted(SEQUENCE.incrementAndGet(), email, PASSWORD));
            });
            var two = executor.submit(() -> {
                barrier.await(5, java.util.concurrent.TimeUnit.SECONDS);
                return second.post("/api/auth/register", """
                        {"firstName":"Second","lastName":"Student","username":"race%d","email":"%s","password":"%s","phone":"+94 77 123 4567"}
                        """.formatted(SEQUENCE.incrementAndGet(), email.toUpperCase(), PASSWORD));
            });
            assertThat(java.util.List.of(status(one.get(10, java.util.concurrent.TimeUnit.SECONDS)),
                    status(two.get(10, java.util.concurrent.TimeUnit.SECONDS)))).containsExactlyInAnyOrder(201, 409);
        }
        var accounts = userRepository.findAllByEmailIgnoreCase(email);
        assertThat(accounts).hasSize(1);
        var students = studentRepository.findAllByEmailIgnoreCase(email);
        assertThat(students).hasSize(1);
        assertThat(studentRepository.findByUser_Id(accounts.getFirst().getId()).orElseThrow().getId())
                .isEqualTo(students.getFirst().getId());
    }

    @Test
    void deletedStudentCannotLoginRefreshOrUseExistingAccessJwt() throws Exception {
        BrowserSession student = signedInStudent();
        Long userId = JsonPath.<Number>read(body(student.get("/api/auth/me")), "$.id").longValue();
        String email = userRepository.findById(userId).orElseThrow().getEmail();
        Long studentId = studentRepository.findByUser_Id(userId).orElseThrow().getId();
        String access = student.cookie(ACCESS), refresh = student.cookie(REFRESH);
        login(newBrowser(), email, PASSWORD, true); // A second token family must also be deleted.
        assertThat(status(signedInAdmin().delete("/api/students/" + studentId))).isEqualTo(204);
        assertThat(userRepository.findById(userId)).isEmpty();
        assertThat(studentRepository.findById(studentId)).isEmpty();
        assertThat(refreshTokenRepository.findAll().stream().filter(t -> t.getUser().getId().equals(userId))).isEmpty();
        assertThat(status(login(newBrowser(), email, PASSWORD, false))).isEqualTo(401);
        BrowserSession stolen = newBrowser(); stolen.setCookie(ACCESS, access); stolen.setCookie(REFRESH, refresh);
        assertThat(status(stolen.get("/api/courses"))).isEqualTo(401);
        assertThat(status(stolen.get("/api/auth/me"))).isEqualTo(401);
        MvcResult rejected = stolen.post("/api/auth/refresh", null);
        assertThat(status(rejected)).isEqualTo(401);
        assertThat(JsonPath.<String>read(body(rejected), "$.code")).isEqualTo("SESSION_EXPIRED");
    }

    @Test
    void directUserDeletionRemovesLinkedStudentAndStandaloneStudentUsers() throws Exception {
        BrowserSession admin = signedInAdmin();
        String email = registerStudent(newBrowser(), uniqueEmail("directdelete"));
        Long userId = userRepository.findAllByEmailIgnoreCase(email).getFirst().getId();
        Long studentId = studentRepository.findByUser_Id(userId).orElseThrow().getId();
        login(newBrowser(), email, PASSWORD, true);
        assertThat(status(admin.delete("/api/users/" + userId))).isEqualTo(204);
        assertThat(studentRepository.findById(studentId)).isEmpty();
        assertThat(userRepository.findById(userId)).isEmpty();
        User standalone = new User(); standalone.setUsername("standalone" + SEQUENCE.incrementAndGet());
        standalone.setEmail(uniqueEmail("standaloneDelete")); standalone.setPassword(passwordEncoder.encode(PASSWORD));
        standalone.setRole(Role.STUDENT); userRepository.save(standalone);
        login(newBrowser(), standalone.getEmail(), PASSWORD, true);
        assertThat(status(admin.delete("/api/users/" + standalone.getId()))).isEqualTo(204);
        assertThat(userRepository.findById(standalone.getId())).isEmpty();
        assertThat(refreshTokenRepository.findAll().stream().filter(t -> t.getUser().getId().equals(standalone.getId()))).isEmpty();
    }

    @Test
    void legacyDeletionNeverFindsAnAccountByMatchingEmailAndAdminLinksAreRefused() throws Exception {
        BrowserSession admin = signedInAdmin();
        User unrelatedAdmin = createAdminFixture();
        var legacy = new com.studentmanagement.backend.entity.Student();
        legacy.setFirstName("Legacy"); legacy.setLastName("Unlinked"); legacy.setEmail(unrelatedAdmin.getEmail());
        Long id = studentRepository.save(legacy).getId();
        assertThat(status(admin.delete("/api/students/" + id))).isEqualTo(204);
        assertThat(userRepository.findById(unrelatedAdmin.getId())).isPresent();
        var invalid = new com.studentmanagement.backend.entity.Student();
        invalid.setFirstName("Unexpected"); invalid.setLastName("Admin link"); invalid.setEmail(uniqueEmail("invalidlink"));
        invalid.setUser(unrelatedAdmin); Long invalidId = studentRepository.save(invalid).getId();
        assertThat(status(admin.delete("/api/students/" + invalidId))).isEqualTo(403);
        assertThat(status(admin.delete("/api/users/" + unrelatedAdmin.getId()))).isEqualTo(403);
        assertThat(studentRepository.findById(invalidId)).isPresent();
        assertThat(userRepository.findById(unrelatedAdmin.getId())).isPresent();
    }

    @Test
    void linkedEmailsCannotBeChangedAndRejectedRequestsPreservePasswordHash() throws Exception {
        String email = registerStudent(newBrowser(), uniqueEmail("frozen"));
        User account = userRepository.findAllByEmailIgnoreCase(email).getFirst();
        String hash = account.getPassword();
        var student = studentRepository.findByUser_Id(account.getId()).orElseThrow();
        BrowserSession admin = signedInAdmin();
        MvcResult profile = admin.put("/api/students/" + student.getId(), """
                {"firstName":"Changed","lastName":"Name","email":"%s"}
                """.formatted(uniqueEmail("newemail")));
        assertThat(status(profile)).isEqualTo(400);
        assertThat(JsonPath.<String>read(body(profile), "$.errors.email")).contains("read-only");
        MvcResult user = admin.put("/api/users/" + account.getId(), """
                {"username":"changed","email":"%s","password":"%s","role":"STUDENT"}
                """.formatted(uniqueEmail("newlogin"), "different-valid-password"));
        assertThat(status(user)).isEqualTo(400);
        assertThat(JsonPath.<String>read(body(user), "$.errors.email")).contains("read-only");
        assertThat(userRepository.findById(account.getId()).orElseThrow().getEmail()).isEqualTo(email);
        assertThat(userRepository.findById(account.getId()).orElseThrow().getPassword()).isEqualTo(hash);
        assertThat(studentRepository.findById(student.getId()).orElseThrow().getFirstName()).isEqualTo("Test");
    }

    @Test
    void legacyMismatchSurvivesOrdinaryEditsAndUnlinkedEmailCanStillBeCorrected() throws Exception {
        String email = registerStudent(newBrowser(), uniqueEmail("mismatch"));
        User account = userRepository.findAllByEmailIgnoreCase(email).getFirst();
        String hash = account.getPassword();
        var student = studentRepository.findByUser_Id(account.getId()).orElseThrow();
        String contact = uniqueEmail("legacyContact"); student.setEmail(contact); studentRepository.save(student);
        BrowserSession admin = signedInAdmin();
        MvcResult update = admin.put("/api/students/" + student.getId(), """
                {"firstName":"Edited","lastName":"Student","email":"%s","phone":"0700000000","course":"Existing course"}
                """.formatted(contact.toUpperCase()));
        assertThat(status(update)).isEqualTo(200);
        assertThat(JsonPath.<Boolean>read(body(update), "$.linkedAccount")).isTrue();
        assertThat(studentRepository.findById(student.getId()).orElseThrow().getEmail()).isEqualTo(contact);
        assertThat(userRepository.findById(account.getId()).orElseThrow().getEmail()).isEqualTo(email);
        assertThat(userRepository.findById(account.getId()).orElseThrow().getPassword()).isEqualTo(hash);
        var legacy = new com.studentmanagement.backend.entity.Student();
        legacy.setFirstName("Legacy"); legacy.setLastName("Record"); legacy.setEmail(uniqueEmail("oldcontact"));
        Long legacyId = studentRepository.save(legacy).getId(); String corrected = uniqueEmail("corrected");
        MvcResult correctedRecord = admin.put("/api/students/" + legacyId, """
                {"firstName":"Legacy","lastName":"Record","email":"%s"}
                """.formatted(corrected));
        assertThat(status(correctedRecord)).isEqualTo(200);
        assertThat(JsonPath.<Boolean>read(body(correctedRecord), "$.linkedAccount")).isFalse();
        assertThat(studentRepository.findById(legacyId).orElseThrow().getEmail()).isEqualTo(corrected);
    }

    // ---- student self-service ----------------------------------------------------

    @Test
    void studentCanUpdateTheirOwnContactDetails() throws Exception {
        BrowserSession browser = newBrowser();
        String email = registerStudent(browser, uniqueEmail("selfedit"));
        assertThat(status(login(browser, email, PASSWORD, false))).isEqualTo(200);

        // Email and course in the body are ignored: only name and phone are editable.
        MvcResult result = browser.put("/api/students/me", """
                {"firstName":"  Renamed ","lastName":"Person","phone":" 077  555 1234 ",
                 "email":"someone-else@example.com","course":"Hacked"}
                """);

        assertThat(status(result)).isEqualTo(200);
        assertThat(JsonPath.<String>read(body(result), "$.firstName")).isEqualTo("Renamed");
        assertThat(JsonPath.<String>read(body(result), "$.lastName")).isEqualTo("Person");
        assertThat(JsonPath.<String>read(body(result), "$.phone")).isEqualTo("077 555 1234");
        assertThat(JsonPath.<String>read(body(result), "$.email")).isEqualTo(email);
        assertThat(JsonPath.<Object>read(body(result), "$.course")).isNull();

        MvcResult reloaded = browser.get("/api/students/me");
        assertThat(JsonPath.<String>read(body(reloaded), "$.firstName")).isEqualTo("Renamed");
        assertThat(JsonPath.<String>read(body(reloaded), "$.phone")).isEqualTo("077 555 1234");
    }

    @Test
    void contactDetailUpdatesAreValidated() throws Exception {
        BrowserSession browser = signedInStudent();

        MvcResult badPhone = browser.put("/api/students/me", """
                {"firstName":"Valid","lastName":"Name","phone":"12345"}
                """);
        MvcResult blankName = browser.put("/api/students/me", """
                {"firstName":"  ","lastName":"Name","phone":"+94 77 123 4567"}
                """);

        assertThat(status(badPhone)).isEqualTo(400);
        assertThat(JsonPath.<String>read(body(badPhone), "$.errors.phone")).isNotBlank();
        assertThat(status(blankName)).isEqualTo(400);
        assertThat(JsonPath.<String>read(body(blankName), "$.errors.firstName")).isNotBlank();
        assertThat(JsonPath.<String>read(body(browser.get("/api/students/me")), "$.firstName"))
                .isEqualTo("Test");
    }

    @Test
    void selfServiceEndpointsAreForSignedInStudentsOnly() throws Exception {
        BrowserSession admin = signedInAdmin();
        assertThat(status(admin.put("/api/students/me", """
                {"firstName":"A","lastName":"B","phone":"+94 77 123 4567"}
                """))).isEqualTo(403);
        assertThat(status(admin.post("/api/auth/password", """
                {"currentPassword":"%s","newPassword":"another-password-1"}
                """.formatted(PASSWORD)))).isEqualTo(403);

        BrowserSession anonymous = newBrowser();
        assertThat(status(anonymous.put("/api/students/me", """
                {"firstName":"A","lastName":"B","phone":"+94 77 123 4567"}
                """))).isEqualTo(401);
        assertThat(status(anonymous.post("/api/auth/password", """
                {"currentPassword":"%s","newPassword":"another-password-1"}
                """.formatted(PASSWORD)))).isEqualTo(401);
    }

    @Test
    void studentCanChangeTheirPasswordAndOtherSessionsAreSignedOut() throws Exception {
        BrowserSession tab = newBrowser();
        String email = registerStudent(tab, uniqueEmail("pwchange"));
        assertThat(status(login(tab, email, PASSWORD, true))).isEqualTo(200);
        BrowserSession otherDevice = newBrowser();
        assertThat(status(login(otherDevice, email, PASSWORD, false))).isEqualTo(200);

        String newPassword = "brand-new-password-1";
        MvcResult changed = tab.post("/api/auth/password", """
                {"currentPassword":"%s","newPassword":"%s"}
                """.formatted(PASSWORD, newPassword));

        assertThat(status(changed)).isEqualTo(204);
        // The caller gets a fresh session that keeps its "remember me" choice.
        assertThat(BrowserSession.setCookieHeader(changed, REFRESH)).contains("Max-Age=604800");
        assertThat(status(tab.get("/api/auth/me"))).isEqualTo(200);
        assertThat(status(tab.post("/api/auth/refresh", null))).isEqualTo(200);

        // Every other session's refresh token was revoked.
        assertThat(status(otherDevice.post("/api/auth/refresh", null))).isEqualTo(401);

        assertThat(status(login(newBrowser(), email, PASSWORD, false))).isEqualTo(401);
        assertThat(status(login(newBrowser(), email, newPassword, false))).isEqualTo(200);
    }

    @Test
    void passwordChangeRequiresTheCurrentPasswordAndAValidNewOne() throws Exception {
        BrowserSession browser = newBrowser();
        String email = registerStudent(browser, uniqueEmail("pwreject"));
        assertThat(status(login(browser, email, PASSWORD, false))).isEqualTo(200);

        MvcResult wrongCurrent = browser.post("/api/auth/password", """
                {"currentPassword":"not-my-password","newPassword":"another-password-1"}
                """);
        MvcResult tooShort = browser.post("/api/auth/password", """
                {"currentPassword":"%s","newPassword":"short"}
                """.formatted(PASSWORD));
        MvcResult unchanged = browser.post("/api/auth/password", """
                {"currentPassword":"%s","newPassword":"%s"}
                """.formatted(PASSWORD, PASSWORD));

        assertThat(status(wrongCurrent)).isEqualTo(400);
        assertThat(JsonPath.<String>read(body(wrongCurrent), "$.errors.currentPassword")).isNotBlank();
        assertThat(status(tooShort)).isEqualTo(400);
        assertThat(JsonPath.<String>read(body(tooShort), "$.errors.newPassword")).isNotBlank();
        assertThat(status(unchanged)).isEqualTo(400);
        assertThat(JsonPath.<String>read(body(unchanged), "$.errors.newPassword")).isNotBlank();

        // Nothing changed: the session is intact and the original password still works.
        assertThat(status(browser.get("/api/auth/me"))).isEqualTo(200);
        assertThat(status(login(newBrowser(), email, PASSWORD, false))).isEqualTo(200);
    }
}
