package com.cake.platform.security;

import java.math.BigInteger;
import java.security.SecureRandom;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.cake.platform.mapper.UserAuthMapper;
import com.cake.platform.service.auth.AuthenticationService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.MockMvcPrint;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.core.Authentication;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * HTTP and real InnoDB regression tests. They run only against an explicitly selected,
 * preinitialized _test schema. No schema changes, development credentials, or dev rows.
 * Fixtures commit so the filter, HTTP transactions, and concurrent threads see real rows.
 * Cleanup deletes exact generated IDs, including when an assertion or request fails.
 */
@SpringBootTest
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
@ActiveProfiles("mysql-test")
@EnabledIfEnvironmentVariable(named = "CAKE_MYSQL_TEST_ENABLED", matches = "true")
@Import(AuthHttpMySqlTests.SecurityFixtureConfiguration.class)
class AuthHttpMySqlTests {
    private static final List<String> TABLES = List.of("sys_user", "cake_merchant", "cake_category",
            "cake_product", "cake_order", "cake_order_item");
    private static final String TEST_SECRET = randomSecret();
    private static final String PASSWORD = "Fixture!Pass42";
    private static final String NEW_PASSWORD = "Changed!Pass43";
    private static final BCryptPasswordEncoder BCRYPT = new BCryptPasswordEncoder(10);
    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper json;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private JwtService jwt;
    @Autowired private UserAuthMapper authMapper;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private PlatformTransactionManager transactionManager;
    private final List<BigInteger> ownUsers = new ArrayList<>();
    private final List<BigInteger> ownMerchants = new ArrayList<>();
    private final List<String> registrationPhones = new ArrayList<>();
    private Map<String, Long> beforeCounts;

    @DynamicPropertySource
    static void isolatedDatabaseAndEphemeralSigningKey(DynamicPropertyRegistry properties) {
        String testName = System.getenv("DB_TEST_NAME");
        String devName = System.getenv().getOrDefault("DB_NAME", "cake_order_system");
        if (testName == null || !testName.matches("[A-Za-z][A-Za-z0-9_]*_test") || testName.equals(devName)) {
            throw new IllegalStateException("认证集成测试只允许独立 DB_TEST_NAME，名称须以 _test 结尾且不同于 DB_NAME。");
        }
        properties.add("cake.security.jwt-secret-base64", () -> TEST_SECRET);
    }

    @BeforeEach
    void snapshotExistingRows() {
        assertThat(jdbc.queryForObject("SELECT DATABASE()", String.class)).isEqualTo(System.getenv("DB_TEST_NAME"));
        assertThat(jdbc.queryForObject("SELECT VERSION()", String.class)).startsWith("8.0.19");
        beforeCounts = counts();
    }

    @AfterEach
    void removeOnlyOwnedRowsAndVerifyPreservation() {
        try {
            // Recover a generated ID even if registration's HTTP assertion failed after commit.
            for (String phone : registrationPhones) {
                jdbc.query("SELECT id FROM sys_user WHERE phone=?", (row, number) -> row.getBigDecimal(1).toBigIntegerExact(), phone)
                        .forEach(id -> { if (!ownUsers.contains(id)) ownUsers.add(id); });
            }
            for (BigInteger id : ownMerchants) jdbc.update("DELETE FROM cake_merchant WHERE id=?", id);
            for (BigInteger id : ownUsers) jdbc.update("DELETE FROM sys_user WHERE id=?", id);
        } finally {
            if (beforeCounts != null) assertThat(counts()).as("all six table row counts after exact-ID cleanup")
                    .isEqualTo(beforeCounts);
        }
    }

    @Test
    void registrationCreatesOnlyEnabledCustomerUsingDatabaseDefaultsAndBcryptWithoutLogin() throws Exception {
        String phone = newRegistrationPhone();
        JsonNode response = request(post("/api/auth/register"), registration(phone, "  注册昵称  ", PASSWORD), 201, 200);
        JsonNode user = response.path("data");
        BigInteger id = new BigInteger(user.path("id").asText());
        assertThat(user.path("id").isTextual()).isTrue();
        assertThat(user.path("username").asText()).isEqualTo(phone);
        assertThat(user.path("phone").asText()).isEqualTo(phone);
        assertThat(user.path("nickname").asText()).isEqualTo("注册昵称");
        assertThat(user.path("role").asText()).isEqualTo("CUSTOMER");
        assertThat(user.path("status").asInt()).isEqualTo(1);
        assertThat(user.path("createTime").asText()).matches(".*\\.\\d{3}\\+08:00");
        assertPublicUser(user);
        assertThat(response.path("data").has("token")).isFalse();
        Map<String, Object> row = jdbc.queryForMap("SELECT * FROM sys_user WHERE id=?", id);
        assertThat(BCRYPT.matches(PASSWORD, row.get("password").toString())).isTrue();
        assertThat(row.get("password").toString().startsWith("$2")).isTrue();
        assertThat(((Number) row.get("token_version")).intValue()).isZero();
        assertThat(((Number) row.get("login_fail_count")).intValue()).isZero();
        assertThat(row.get("locked_until")).isNull();
        assertThat(row.get("create_time")).isNotNull();
        assertThat(row.get("update_time")).isNotNull();
        request(get("/api/users/me"), null, 401, 40102);
    }

    @Test
    void duplicateRegistrationReturns40901AndDoesNotChangeExistingAccount() throws Exception {
        Account account = account("CUSTOMER");
        request(post("/api/auth/register"), registration(account.phone(), "冲突昵称", NEW_PASSWORD), 409, 40901);
        assertThat(user(account.id()).get("nickname")).isEqualTo("认证测试");
        assertThat(BCRYPT.matches(PASSWORD, user(account.id()).get("password").toString())).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"role", "status", "merchantId", "tokenVersion"})
    void registrationRejectsEveryProtectedField(String field) throws Exception {
        Map<String, Object> payload = registration(newRegistrationPhone(), "注册昵称", PASSWORD);
        payload.put(field, field.equals("role") ? "ADMIN" : 1);
        JsonNode result = request(post("/api/auth/register"), payload, 403, 40302);
        assertThat(result.path("data").isNull()).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"123", "12800138000", "1380013800a", " 13800138000", "13800138000 "})
    void registrationRejectsInvalidMainlandPhone(String phone) throws Exception {
        request(post("/api/auth/register"), registration(phone, "昵称", PASSWORD), 400, 40001);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   ", "1234567890123456789012345678901"})
    void registrationRejectsBlankOrOverlongTrimmedNickname(String nickname) throws Exception {
        request(post("/api/auth/register"), registration(newRegistrationPhone(), nickname, PASSWORD), 400, 40001);
    }

    @ParameterizedTest
    @ValueSource(strings = {"12345", "123456789012345678901", "abc def", "abc\tdef", "abc\ndef", "abc\u00a0def", "abc\u2003def"})
    void registrationRejectsInvalidPassword(String password) throws Exception {
        request(post("/api/auth/register"), registration(newRegistrationPhone(), "昵称", password), 400, 40001);
    }

    @Test
    void registrationRejectsUnknownFieldsWithoutInventingPublicFieldNames() throws Exception {
        Map<String, Object> payload = registration(newRegistrationPhone(), "昵称", PASSWORD);
        payload.put("unexpected", "value");
        JsonNode result = request(post("/api/auth/register"), payload, 400, 40001);
        assertThat(result.path("data").isNull()).isTrue();
    }

    @Test
    void registrationReturnsAllPublicFieldErrorsAndMultiplePasswordMessagesWithoutWrites() throws Exception {
        Map<String, Long> before = counts();
        JsonNode result = request(post("/api/auth/register"), registration("rejected-phone-secret", "   ", "a b"), 400, 40001);
        JsonNode errors = result.path("data").path("fieldErrors");
        assertThat(errors.size()).isEqualTo(3);
        assertThat(errors.path("phone").get(0).asText()).isEqualTo("手机号格式不正确");
        assertThat(errors.path("nickname").get(0).asText()).isEqualTo("昵称不能为空");
        assertThat(errors.path("password").size()).isEqualTo(2);
        assertThat(errors.path("password").get(0).asText()).isEqualTo("密码长度必须为6到20个字符");
        assertThat(errors.path("password").get(1).asText()).isEqualTo("密码不能包含空白字符");
        assertThat(result.toString()).doesNotContain("rejected-phone-secret", "a b");
        assertThat(counts()).isEqualTo(before);
    }

    @Test
    void loginParameterFailuresUseUsernameAndDoNotChangeAccountFailures() throws Exception {
        Account account = account("CUSTOMER");
        JsonNode result = request(post("/api/auth/login"), Map.of("username", "invalid", "password", ""), 400, 40001);
        JsonNode errors = result.path("data").path("fieldErrors");
        assertThat(errors.size()).isEqualTo(2);
        assertThat(errors.has("username")).isTrue();
        assertThat(errors.has("password")).isTrue();
        assertThat(errors.has("phone")).isFalse();
        assertThat(failCount(account)).isZero();
    }

    @ParameterizedTest
    @ValueSource(strings = {"[]", "null", "{"})
    void unlocatableRegistrationBodiesKeepNullData(String payload) throws Exception {
        JsonNode result = request(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(payload),
                null, 400, 40001);
        assertThat(result.path("data").isNull()).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"username", "id", "userId", "token_version", "unexpected"})
    void registrationTreatsOtherNonContractFieldsAsParameterErrors(String field) throws Exception {
        Map<String, Object> payload = registration(newRegistrationPhone(), "昵称", PASSWORD);
        payload.put(field, "1");
        request(post("/api/auth/register"), payload, 400, 40001);
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "[]", "null", "{", "{\"phone\":1,\"nickname\":\"昵称\",\"password\":\"Secret!42\"}"})
    void registrationRejectsMissingFieldsInvalidJsonAndWrongTypes(String payload) throws Exception {
        request(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(payload), null, 400, 40001);
    }

    @Test
    void loginSignsTwoHourJwtAndReturnsOnlyDocumentedPublicUser() throws Exception {
        Account account = account("CUSTOMER");
        JsonNode response = login(account, PASSWORD, 200, 200).path("data");
        assertThat(response.path("tokenType").asText()).isEqualTo("Bearer");
        assertThat(response.path("expiresAt").asText()).matches(".*\\.\\d{3}\\+08:00");
        var decoded = jwt.decode(response.path("token").asText());
        assertThat(decoded.getSubject()).isEqualTo(account.id().toString());
        assertThat(Duration.between(decoded.getIssuedAt(), decoded.getExpiresAt())).isEqualTo(Duration.ofHours(2));
        assertThat(OffsetDateTime.parse(response.path("expiresAt").asText()).toInstant()).isEqualTo(decoded.getExpiresAt());
        assertThat(decoded.getClaimAsString("role")).isEqualTo("CUSTOMER");
        assertThat(((Number) decoded.getClaim("ver")).intValue()).isZero();
        assertPublicUser(response.path("user"));
        request(bearer(get("/api/users/me"), response.path("token").asText()), null, 200, 200);
    }

    @Test
    void nonexistentAccountAndWrongPasswordHaveExactlySamePublicFailure() throws Exception {
        Account account = account("CUSTOMER");
        JsonNode wrong = login(account, "Wrong!Pass42", 401, 40101);
        JsonNode absent = request(post("/api/auth/login"), Map.of("username", absentPhone(), "password", "Wrong!Pass42"), 401, 40101);
        assertThat(wrong).isEqualTo(absent);
    }

    @Test
    void disabledAccountCannotLoginAndDoesNotReceiveJwt() throws Exception {
        Account account = account("CUSTOMER");
        jdbc.update("UPDATE sys_user SET status=0 WHERE id=?", account.id());
        assertThat(login(account, PASSWORD, 403, 40301).path("data").has("token")).isFalse();
    }

    @Test
    void loginRejectsClientRoleSelection() throws Exception {
        Account account = account("CUSTOMER");
        request(post("/api/auth/login"), Map.of("username", account.phone(), "password", PASSWORD, "role", "ADMIN"), 403, 40302);
        assertThat(failCount(account)).isZero();
    }

    @Test
    void firstFourFailuresPersistAndFifthLocksForExactlyTenDatabaseMinutes() throws Exception {
        Account account = account("CUSTOMER");
        for (int failure = 1; failure <= 4; failure++) {
            login(account, "Wrong!Pass42", 401, 40101);
            assertThat(failCount(account)).isEqualTo(failure);
            assertThat(user(account.id()).get("locked_until")).isNull();
        }
        Instant before = Instant.now();
        JsonNode response = login(account, "Wrong!Pass42", 423, 42301);
        Instant after = Instant.now();
        assertThat(failCount(account)).isEqualTo(5);
        String lockedUntil = response.path("data").path("lockedUntil").asText();
        assertThat(lockedUntil).matches(".*\\.\\d{3}\\+08:00");
        Instant locked = OffsetDateTime.parse(lockedUntil).toInstant();
        assertThat(locked).isBetween(before.plus(Duration.ofMinutes(10)).minusSeconds(1), after.plus(Duration.ofMinutes(10)).plusSeconds(1));
        LocalDateTime stored = jdbc.queryForObject("SELECT locked_until FROM sys_user WHERE id=?", LocalDateTime.class, account.id());
        assertThat(OffsetDateTime.parse(lockedUntil).toLocalDateTime()).isEqualTo(stored);
        login(account, PASSWORD, 423, 42301);
        assertThat(failCount(account)).isEqualTo(5);
    }

    @Test
    void expiredLockStartsNewFailureRoundWithoutWaitingTenMinutes() throws Exception {
        Account account = account("CUSTOMER");
        jdbc.update("UPDATE sys_user SET login_fail_count=5,locked_until=DATE_SUB(CURRENT_TIMESTAMP(3),INTERVAL 1 SECOND) WHERE id=?", account.id());
        login(account, "Wrong!Pass42", 401, 40101);
        assertThat(failCount(account)).isEqualTo(1);
        assertThat(user(account.id()).get("locked_until")).isNull();
    }

    @Test
    void successfulLoginClearsFailedCountAndExpiredLock() throws Exception {
        Account account = account("CUSTOMER");
        jdbc.update("UPDATE sys_user SET login_fail_count=5,locked_until=DATE_SUB(CURRENT_TIMESTAMP(3),INTERVAL 1 SECOND) WHERE id=?", account.id());
        login(account, PASSWORD, 200, 200);
        assertThat(failCount(account)).isZero();
        assertThat(user(account.id()).get("locked_until")).isNull();
        jdbc.update("UPDATE sys_user SET login_fail_count=3 WHERE id=?", account.id());
        login(account, PASSWORD, 200, 200);
        assertThat(failCount(account)).isZero();
    }

    @Test
    void concurrentFailuresCannotLoseIncrementsOrExceedLockThreshold() throws Exception {
        Account account = account("CUSTOMER");
        List<Integer> codes = concurrently(8, () -> rawLoginCode(account, "Wrong!Pass42"));
        assertThat(codes).filteredOn(code -> code == 40101).hasSize(4);
        assertThat(codes).filteredOn(code -> code == 42301).hasSize(4);
        assertThat(failCount(account)).isEqualTo(5);
        assertThat(user(account.id()).get("locked_until")).isNotNull();
    }

    @Test
    void concurrentFailuresForDifferentAccountsRemainIndependent() throws Exception {
        Account first = account("CUSTOMER");
        Account second = account("CUSTOMER");
        List<Callable<Integer>> calls = new ArrayList<>();
        for (int i = 0; i < 3; i++) calls.add(() -> rawLoginCode(first, "Wrong!Pass42"));
        for (int i = 0; i < 2; i++) calls.add(() -> rawLoginCode(second, "Wrong!Pass42"));
        assertThat(concurrently(calls)).containsOnly(40101);
        assertThat(failCount(first)).isEqualTo(3);
        assertThat(failCount(second)).isEqualTo(2);
    }

    @Test
    void concurrentDuplicateRegistrationCreatesOnlyOneCustomer() throws Exception {
        String phone = newRegistrationPhone();
        List<Integer> codes = concurrently(2, () -> response(post("/api/auth/register"), registration(phone, "并发注册", PASSWORD)).path("code").asInt());
        assertThat(codes).containsExactlyInAnyOrder(200, 40901);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sys_user WHERE phone=?", Long.class, phone)).isEqualTo(1L);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "Bearer", "Basic abc", "Bearer not-a-jwt", "Bearer a.b.c", "Bearer token extra"})
    void missingOrMalformedAuthorizationIsRejected(String header) throws Exception {
        var request = get("/api/users/me");
        if (!header.isEmpty()) request.header("Authorization", header);
        request(request, null, 401, 40102);
    }

    @Test
    void signatureTamperingIsRejected() throws Exception {
        Account account = account("CUSTOMER");
        String token = token(account);
        int signatureStart = token.lastIndexOf('.') + 1;
        String forged = token.substring(0, signatureStart) + (token.charAt(signatureStart) == 'A' ? "B" : "A") + token.substring(signatureStart + 1);
        request(bearer(get("/api/users/me"), forged), null, 401, 40102);
    }

    @Test
    void expiredJwtIsRejected() throws Exception {
        Account account = account("CUSTOMER");
        JwtService pastIssuer = new JwtService(TEST_SECRET, Clock.offset(Clock.systemUTC(), Duration.ofHours(-3)));
        String expired = pastIssuer.issue(new AuthPrincipal(account.id(), "CUSTOMER", 0, null)).token();
        request(bearer(get("/api/users/me"), expired), null, 401, 40102);
    }

    @Test
    void tokenVersionMismatchIsRejectedOnVeryNextRequest() throws Exception {
        Account account = account("CUSTOMER");
        String token = token(account);
        jdbc.update("UPDATE sys_user SET token_version=token_version+1 WHERE id=?", account.id());
        request(bearer(get("/api/users/me"), token), null, 401, 40102);
    }

    @Test
    void deletedUserCannotUsePreviouslyIssuedJwt() throws Exception {
        Account account = account("CUSTOMER");
        String token = token(account);
        jdbc.update("DELETE FROM sys_user WHERE id=?", account.id());
        request(bearer(get("/api/users/me"), token), null, 401, 40102);
    }

    @Test
    void disablingUserImmediatelyBlocksPreviouslyIssuedJwt() throws Exception {
        Account account = account("CUSTOMER");
        String token = token(account);
        jdbc.update("UPDATE sys_user SET status=0 WHERE id=?", account.id());
        request(bearer(get("/api/users/me"), token), null, 403, 40301);
    }

    @Test
    void signedRoleEscalationDoesNotOverrideCurrentDatabaseRole() throws Exception {
        Account account = account("CUSTOMER");
        String falselyPrivileged = jwt.issue(new AuthPrincipal(account.id(), "ADMIN", 0, null)).token();
        JsonNode profile = request(bearer(get("/api/users/me"), falselyPrivileged), null, 200, 200).path("data");
        assertThat(profile.path("role").asText()).isEqualTo("CUSTOMER");
        request(bearer(get("/api/admin/__phase3_role_fixture"), falselyPrivileged), null, 403, 40302);
    }

    @ParameterizedTest
    @ValueSource(strings = {"ADMIN", "MERCHANT", "CUSTOMER"})
    void allThreeRolesReadOnlyTheirOwnPublicProfile(String role) throws Exception {
        Account account = account(role);
        Account other = account("CUSTOMER");
        JsonNode login = login(account, PASSWORD, 200, 200).path("data");
        String token = login.path("token").asText();
        JsonNode profile = request(bearer(get("/api/users/me"), token), null, 200, 200).path("data");
        assertThat(profile.path("id").asText()).isEqualTo(account.id().toString());
        assertThat(profile.path("id").asText()).isNotEqualTo(other.id().toString());
        assertThat(profile.path("role").asText()).isEqualTo(role);
        assertPublicUser(profile);
        assertPublicUser(login.path("user"));
        JsonNode context = request(bearer(get("/api/__phase3_security_context"), token), null, 200, 200).path("data");
        assertThat(context.path("userId").asText()).isEqualTo(account.id().toString());
        assertThat(context.path("role").asText()).isEqualTo(role);
        assertThat(context.path("tokenVersion").asInt()).isZero();
        assertThat(context.path("authority").asText()).isEqualTo("ROLE_" + role);
        if (role.equals("MERCHANT")) {
            String merchantId = jdbc.queryForObject("SELECT CAST(id AS CHAR) FROM cake_merchant WHERE user_id=?", String.class, account.id());
            assertThat(profile.path("merchantId").isTextual()).isTrue();
            assertThat(profile.path("merchantId").asText()).isEqualTo(merchantId);
            assertThat(login.path("user").path("merchantId").asText()).isEqualTo(merchantId);
            assertThat(context.path("merchantId").asText()).isEqualTo(merchantId);
        } else {
            assertThat(profile.has("merchantId")).isFalse();
        }
    }

    @Test
    void merchantWithoutBindingCannotReceiveNormalLoginToken() throws Exception {
        Account account = account("MERCHANT", false);
        JsonNode result = login(account, PASSWORD, 500, 50001);
        assertThat(result.path("data").has("token")).isFalse();
        assertThat(result.path("data").has("requestId")).isTrue();
    }

    @Test
    void invalidNonMerchantShopBindingCannotReceiveNormalToken() throws Exception {
        Account account = account("CUSTOMER");
        createMerchant(account.id());
        login(account, PASSWORD, 500, 50001);
    }

    @Test
    void removingMerchantBindingImmediatelyInvalidatesMerchantToken() throws Exception {
        Account account = account("MERCHANT");
        String token = token(account);
        jdbc.update("DELETE FROM cake_merchant WHERE user_id=?", account.id());
        request(bearer(get("/api/users/me"), token), null, 500, 50001);
    }

    @ParameterizedTest
    @ValueSource(strings = {"userId", "id", "phone", "role", "merchantId", "unexpected"})
    void profileRejectsQueryParametersInsteadOfReadingAnotherUser(String field) throws Exception {
        Account account = account("CUSTOMER");
        String token = token(account);
        // Query parameters are unsupported by API-12 regardless of the apparent requested target.
        int code = field.equals("unexpected") ? 40001 : 40302;
        request(bearer(get("/api/users/me").param(field, "1"), token), null, code == 40001 ? 400 : 403, code);
    }

    @Test
    void protectedQueryFieldTakesPriorityOverOrdinaryUnknownFieldInEitherOrder() throws Exception {
        Account account = account("CUSTOMER");
        String token = token(account);
        request(bearer(get("/api/users/me").param("unexpected", "1").param("role", "ADMIN"), token), null, 403, 40302);
        request(bearer(get("/api/users/me").param("role", "ADMIN").param("unexpected", "1"), token), null, 403, 40302);
    }

    @Test
    void profileRejectsRequestBodyIncludingProtectedTargetFields() throws Exception {
        Account account = account("CUSTOMER");
        String token = token(account);
        request(bearer(get("/api/users/me"), token), Map.of("nickname", "不允许读取请求体"), 400, 40001);
        request(bearer(get("/api/users/me"), token), Map.of("userId", "1"), 403, 40302);
    }

    @ParameterizedTest
    @ValueSource(strings = {"ADMIN", "MERCHANT", "CUSTOMER"})
    void realSecurityFilterChainAndMethodSecurityEnforceCurrentRole(String role) throws Exception {
        Account account = account(role);
        String token = token(account);
        for (String required : List.of("ADMIN", "MERCHANT", "CUSTOMER")) {
            String route = switch (required) {
                case "ADMIN" -> "/api/admin/__phase3_role_fixture";
                case "MERCHANT" -> "/api/merchant/__phase3_role_fixture";
                default -> "/api/orders/__phase3_role_fixture";
            };
            request(bearer(get(route), token), null, role.equals(required) ? 200 : 403, role.equals(required) ? 200 : 40302);
        }
        request(bearer(get("/api/__phase3_admin_method_fixture"), token), null, role.equals("ADMIN") ? 200 : 403, role.equals("ADMIN") ? 200 : 40302);
    }

    @ParameterizedTest
    @ValueSource(strings = {"ADMIN", "MERCHANT", "CUSTOMER"})
    void nicknameUpdateChangesOnlyAuthenticatedUsersNickname(String role) throws Exception {
        Account account = account(role);
        Account other = account("CUSTOMER");
        Map<String, Object> before = user(account.id());
        JsonNode updated = request(bearer(patch("/api/users/me"), token(account)), Map.of("nickname", "  新昵称  "), 200, 200).path("data");
        assertThat(updated.path("nickname").asText()).isEqualTo("新昵称");
        assertThat(user(other.id()).get("nickname")).isEqualTo("认证测试");
        Map<String, Object> after = user(account.id());
        for (String field : Set.of("username", "phone", "password", "role", "status", "token_version", "login_fail_count", "locked_until", "create_time")) {
            assertThat(after.get(field)).as("unrelated field " + field).isEqualTo(before.get(field));
        }
        assertPublicUser(updated);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   ", "1234567890123456789012345678901"})
    void nicknameUpdateRejectsBlankOrOverlongValues(String nickname) throws Exception {
        Account account = account("CUSTOMER");
        request(bearer(patch("/api/users/me"), token(account)), Map.of("nickname", nickname), 400, 40001);
        assertThat(user(account.id()).get("nickname")).isEqualTo("认证测试");
    }

    @ParameterizedTest
    @ValueSource(strings = {"username", "phone", "role", "status", "merchantId", "userId", "id", "tokenVersion"})
    void nicknameUpdateRejectsEveryProtectedFieldAndDoesNotWrite(String field) throws Exception {
        Account account = account("CUSTOMER");
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("nickname", "禁止写入");
        payload.put(field, field.equals("role") ? "ADMIN" : "1");
        JsonNode result = request(bearer(patch("/api/users/me"), token(account)), payload, 403, 40302);
        assertThat(result.path("data").isNull()).isTrue();
        assertThat(user(account.id()).get("nickname")).isEqualTo("认证测试");
    }

    @Test
    void logoutAtomicallyInvalidatesAllPreviouslyIssuedTokens() throws Exception {
        Account account = account("CUSTOMER");
        String first = token(account);
        String second = distinctEarlierToken(account, first);
        request(bearer(post("/api/auth/logout"), first), null, 200, 200);
        assertThat(version(account)).isEqualTo(1);
        request(bearer(get("/api/users/me"), first), null, 401, 40102);
        request(bearer(get("/api/users/me"), second), null, 401, 40102);
        request(bearer(post("/api/auth/logout"), first), null, 401, 40102);
        assertThat(version(account)).isEqualTo(1);
        request(bearer(get("/api/users/me"), token(account)), null, 200, 200);
    }

    @Test
    void logoutReturnsNullWithoutDeletingAccountOrIssuingReplacementToken() throws Exception {
        Account account = account("CUSTOMER");
        JsonNode result = request(bearer(post("/api/auth/logout"), token(account)), null, 200, 200);
        assertThat(result.path("data").isNull()).isTrue();
        assertThat(user(account.id()).get("username")).isEqualTo(account.phone());
    }

    @ParameterizedTest
    @ValueSource(strings = {"ADMIN", "MERCHANT", "CUSTOMER"})
    void passwordChangeUsesBcryptAndInvalidatesAllOldTokensWithoutReplacement(String role) throws Exception {
        Account account = account(role);
        String first = token(account);
        String second = distinctEarlierToken(account, first);
        String oldHash = user(account.id()).get("password").toString();
        JsonNode result = request(bearer(patch("/api/users/me/password"), first), Map.of("oldPassword", PASSWORD, "newPassword", NEW_PASSWORD), 200, 200);
        assertThat(result.path("data").isNull()).isTrue();
        String newHash = user(account.id()).get("password").toString();
        assertThat(newHash.equals(oldHash)).isFalse();
        assertThat(BCRYPT.matches(NEW_PASSWORD, newHash)).isTrue();
        assertThat(BCRYPT.matches(PASSWORD, newHash)).isFalse();
        assertThat(version(account)).isEqualTo(1);
        request(bearer(get("/api/users/me"), first), null, 401, 40102);
        request(bearer(get("/api/users/me"), second), null, 401, 40102);
        login(account, PASSWORD, 401, 40101);
        login(account, NEW_PASSWORD, 200, 200);
    }

    @Test
    void wrongOldPasswordDoesNotChangeHashVersionOrExistingSession() throws Exception {
        Account account = account("CUSTOMER");
        String token = token(account);
        String hash = user(account.id()).get("password").toString();
        request(bearer(patch("/api/users/me/password"), token), Map.of("oldPassword", "Wrong!Pass42", "newPassword", NEW_PASSWORD), 401, 40101);
        assertThat(user(account.id()).get("password").toString().equals(hash)).isTrue();
        assertThat(version(account)).isZero();
        request(bearer(get("/api/users/me"), token), null, 200, 200);
    }

    @Test
    void sameNewPasswordIsRejectedWithoutWrites() throws Exception {
        Account account = account("CUSTOMER");
        JsonNode result = request(bearer(patch("/api/users/me/password"), token(account)),
                Map.of("oldPassword", PASSWORD, "newPassword", PASSWORD), 400, 40001);
        assertThat(result.path("data").path("fieldErrors").size()).isEqualTo(1);
        assertThat(result.path("data").path("fieldErrors").path("newPassword").get(0).asText())
                .isEqualTo("新密码不能与原密码相同");
        assertThat(version(account)).isZero();
    }

    @Test
    void passwordChangeReturnsBothActualFieldsWithoutChangingHashOrVersion() throws Exception {
        Account account = account("CUSTOMER");
        String token = token(account);
        Map<String, Object> before = user(account.id());
        JsonNode result = request(bearer(patch("/api/users/me/password"), token),
                Map.of("oldPassword", "", "newPassword", "a b"), 400, 40001);
        JsonNode errors = result.path("data").path("fieldErrors");
        assertThat(errors.size()).isEqualTo(2);
        assertThat(errors.path("oldPassword").get(0).asText()).isEqualTo("原密码不能为空");
        assertThat(errors.path("newPassword").size()).isEqualTo(2);
        Map<String, Object> after = user(account.id());
        assertThat(after.get("password").equals(before.get("password"))).isTrue();
        assertThat(after.get("token_version")).isEqualTo(before.get("token_version"));
        assertThat(after.get("update_time")).isEqualTo(before.get("update_time"));
    }

    @Test
    void passwordChangeRequiresBothFieldsAndRejectsProtectedFields() throws Exception {
        Account account = account("CUSTOMER");
        String token = token(account);
        request(bearer(patch("/api/users/me/password"), token), Map.of("newPassword", NEW_PASSWORD), 400, 40001);
        request(bearer(patch("/api/users/me/password"), token), Map.of("oldPassword", PASSWORD), 400, 40001);
        request(bearer(patch("/api/users/me/password"), token), Map.of("oldPassword", "", "newPassword", NEW_PASSWORD), 400, 40001);
        request(bearer(patch("/api/users/me/password"), token), Map.of("oldPassword", PASSWORD, "newPassword", NEW_PASSWORD, "userId", "1"), 403, 40302);
        assertThat(version(account)).isZero();
    }

    @ParameterizedTest
    @ValueSource(strings = {"12345", "123456789012345678901", "abc def", "abc\tdef", "abc\u00a0def", "abc\u2003def"})
    void invalidNewPasswordIsRejectedWithoutChangingHashOrVersion(String password) throws Exception {
        Account account = account("CUSTOMER");
        String hash = user(account.id()).get("password").toString();
        request(bearer(patch("/api/users/me/password"), token(account)), Map.of("oldPassword", PASSWORD, "newPassword", password), 400, 40001);
        assertThat(user(account.id()).get("password").toString().equals(hash)).isTrue();
        assertThat(version(account)).isZero();
    }

    @Test
    void concurrentPasswordChangesAllowOnlyOneOldPasswordTransition() throws Exception {
        Account account = account("CUSTOMER");
        String token = token(account);
        List<Callable<Integer>> calls = List.of(
                () -> response(bearer(patch("/api/users/me/password"), token), Map.of("oldPassword", PASSWORD, "newPassword", NEW_PASSWORD)).path("code").asInt(),
                () -> response(bearer(patch("/api/users/me/password"), token), Map.of("oldPassword", PASSWORD, "newPassword", "Other!Pass44")).path("code").asInt());
        List<Integer> results = concurrently(calls);
        assertThat(results).filteredOn(code -> code == 200).hasSize(1);
        assertThat(results).allMatch(code -> code == 200 || code == 40102 || code == 40101);
        assertThat(version(account)).isEqualTo(1);
        String hash = user(account.id()).get("password").toString();
        assertThat(BCRYPT.matches(NEW_PASSWORD, hash) ^ BCRYPT.matches("Other!Pass44", hash)).isTrue();
    }

    @Test
    void faultAfterRealPasswordSqlRollsBackPasswordVersionAndUpdateTimeTogether() throws Exception {
        Account account = account("CUSTOMER");
        String token = token(account);
        Map<String, Object> before = user(account.id());
        // Decorate only this service instance. Execute the real mapper SQL inside the real
        // MySQL transaction, then inject a Java failure before TransactionTemplate commits.
        UserAuthMapper failingMapper = (UserAuthMapper) java.lang.reflect.Proxy.newProxyInstance(
                UserAuthMapper.class.getClassLoader(), new Class<?>[]{UserAuthMapper.class}, (proxy, method, args) -> {
                    try {
                        Object result = method.invoke(authMapper, args);
                        if (method.getName().equals("password")) throw new IllegalStateException("Injected phase-3 rollback failure");
                        return result;
                    } catch (java.lang.reflect.InvocationTargetException failure) {
                        throw failure.getCause();
                    }
                });
        AuthenticationService failing = new AuthenticationService(failingMapper, passwordEncoder, jwt, transactionManager);
        assertThatThrownBy(() -> failing.password(new AuthPrincipal(account.id(), "CUSTOMER", 0, null), PASSWORD, NEW_PASSWORD))
                .isInstanceOf(IllegalStateException.class).hasMessage("Injected phase-3 rollback failure");
        Map<String, Object> after = user(account.id());
        assertThat(after.get("password").equals(before.get("password"))).isTrue();
        assertThat(after.get("token_version")).isEqualTo(before.get("token_version"));
        assertThat(after.get("update_time")).isEqualTo(before.get("update_time"));
        request(bearer(get("/api/users/me"), token), null, 200, 200);
        login(account, PASSWORD, 200, 200);
    }

    private JsonNode request(MockHttpServletRequestBuilder builder, Object payload, int status, int code) throws Exception {
        var result = perform(builder, payload);
        assertThat(result.getResponse().getStatus()).as("HTTP status").isEqualTo(status);
        JsonNode body = json.readTree(result.getResponse().getContentAsByteArray());
        assertThat(body.path("code").asInt()).as("public business code").isEqualTo(code);
        assertThat(body.has("msg")).isTrue();
        assertThat(body.has("data")).isTrue();
        assertThat(body.size()).as("unchanged code/msg/data envelope").isEqualTo(3);
        if (code == 40001 && !body.path("data").isNull()) {
            JsonNode errors = body.path("data").path("fieldErrors");
            assertThat(body.path("data").size()).isEqualTo(1);
            assertThat(errors.isObject()).isTrue();
            assertThat(errors.isEmpty()).isFalse();
            assertThat(errors.has("_global")).isFalse();
            errors.fields().forEachRemaining(entry -> {
                assertThat(entry.getKey()).matches("[a-z][A-Za-z0-9]*");
                assertThat(entry.getValue().isArray()).isTrue();
                assertThat(entry.getValue().isEmpty()).isFalse();
                entry.getValue().forEach(message -> {
                    assertThat(message.isTextual()).isTrue();
                    assertThat(message.asText()).isNotBlank();
                });
            });
        } else {
            assertThat(body.path("data").has("fieldErrors")).isFalse();
        }
        return body;
    }

    private JsonNode response(MockHttpServletRequestBuilder builder, Object payload) throws Exception {
        return json.readTree(perform(builder, payload).getResponse().getContentAsByteArray());
    }

    private org.springframework.test.web.servlet.MvcResult perform(MockHttpServletRequestBuilder builder, Object payload) throws Exception {
        if (payload != null) builder.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(payload));
        return mvc.perform(builder).andReturn();
    }

    private JsonNode login(Account account, String password, int status, int code) throws Exception {
        return request(post("/api/auth/login"), Map.of("username", account.phone(), "password", password), status, code);
    }

    private int rawLoginCode(Account account, String password) throws Exception {
        return response(post("/api/auth/login"), Map.of("username", account.phone(), "password", password)).path("code").asInt();
    }

    private String token(Account account) throws Exception {
        return login(account, PASSWORD, 200, 200).path("data").path("token").asText();
    }

    private String distinctEarlierToken(Account account, String first) {
        var claims = jwt.decode(first);
        // NumericDate is second precision, so two immediate logins may issue identical JWTs.
        // Model a prior still-valid login with a different issued-at while preserving identity/version.
        var earlierIssuer = new JwtService(TEST_SECRET,
                Clock.fixed(claims.getIssuedAt().minusSeconds(1), java.time.ZoneOffset.UTC));
        String second = earlierIssuer.issue(new AuthPrincipal(account.id(), claims.getClaimAsString("role"),
                ((Number) claims.getClaim("ver")).intValue(), null)).token();
        assertThat(second.equals(first)).as("two independently issued existing tokens must differ").isFalse();
        jwt.decode(second); // Verify the second token's signature and current validity before invalidation.
        return second;
    }

    private MockHttpServletRequestBuilder bearer(MockHttpServletRequestBuilder builder, String token) {
        return builder.header("Authorization", "Bearer " + token);
    }

    private Account account(String role) { return account(role, true); }

    private Account account(String role, boolean bindMerchant) {
        String phone = absentPhone();
        BigInteger id = insert("INSERT INTO sys_user(username,password,nickname,phone,role) VALUES(?,?,?,?,?)", phone, BCRYPT.encode(PASSWORD), "认证测试", phone, role);
        ownUsers.add(id);
        if (role.equals("MERCHANT") && bindMerchant) createMerchant(id);
        return new Account(id, phone);
    }

    private void createMerchant(BigInteger userId) {
        ownMerchants.add(insert("INSERT INTO cake_merchant(user_id,name,phone,address) VALUES(?,?,?,?)", userId, "认证测试商家", absentPhone(), "专用测试地址"));
    }

    private BigInteger insert(String sql, Object... values) {
        var holder = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            for (int i = 0; i < values.length; i++) statement.setObject(i + 1, values[i]);
            return statement;
        }, holder);
        return new BigInteger(holder.getKey().toString());
    }

    private Map<String, Object> user(BigInteger id) { return jdbc.queryForMap("SELECT * FROM sys_user WHERE id=?", id); }
    private int failCount(Account account) { return ((Number) user(account.id()).get("login_fail_count")).intValue(); }
    private int version(Account account) { return ((Number) user(account.id()).get("token_version")).intValue(); }

    private String newRegistrationPhone() {
        String phone = absentPhone();
        registrationPhones.add(phone);
        return phone;
    }

    private String absentPhone() {
        String phone;
        do { phone = "139" + String.format("%08d", Math.floorMod(UUID.randomUUID().getLeastSignificantBits(), 100_000_000L)); }
        while (jdbc.queryForObject("SELECT COUNT(*) FROM sys_user WHERE phone=? OR username=?", Long.class, phone, phone) != 0);
        return phone;
    }

    private static Map<String, Object> registration(String phone, String nickname, String password) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("phone", phone);
        result.put("nickname", nickname);
        result.put("password", password);
        return result;
    }

    private void assertPublicUser(JsonNode user) {
        Set<String> fields = new java.util.HashSet<>();
        user.fieldNames().forEachRemaining(fields::add);
        assertThat(fields).contains("id", "username", "nickname", "phone", "role", "status", "createTime");
        assertThat(fields).doesNotContain("password", "tokenVersion", "token_version", "loginFailCount", "login_fail_count", "lockedUntil", "locked_until");
        assertThat(user.path("id").isTextual()).isTrue();
    }

    private Map<String, Long> counts() {
        Map<String, Long> result = new LinkedHashMap<>();
        for (String table : TABLES) result.put(table, jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Long.class));
        return result;
    }

    private static <T> List<T> concurrently(int count, Callable<T> operation) throws Exception {
        List<Callable<T>> calls = new ArrayList<>();
        for (int i = 0; i < count; i++) calls.add(operation);
        return concurrently(calls);
    }

    private static <T> List<T> concurrently(List<Callable<T>> calls) throws Exception {
        var pool = Executors.newFixedThreadPool(calls.size());
        var ready = new CountDownLatch(calls.size());
        var start = new CountDownLatch(1);
        try {
            var futures = calls.stream().map(call -> pool.submit(() -> {
                ready.countDown();
                if (!start.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("并发启动超时");
                return call.call();
            })).toList();
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            List<T> results = new ArrayList<>();
            for (var future : futures) results.add(future.get(30, TimeUnit.SECONDS));
            return results;
        } finally {
            start.countDown();
            pool.shutdownNow();
            assertThat(pool.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        }
    }

    private static String randomSecret() {
        byte[] secret = new byte[32];
        new SecureRandom().nextBytes(secret);
        return Base64.getEncoder().encodeToString(secret);
    }

    private record Account(BigInteger id, String phone) { }

    /** Available exclusively in this test context, never part of the production API. */
    @TestConfiguration(proxyBeanMethods = false)
    static class SecurityFixtureConfiguration {
        @Bean SecurityFixtureController securityFixtureController() { return new SecurityFixtureController(); }
    }

    @RestController
    static class SecurityFixtureController {
        @GetMapping({"/api/__phase3_security_context", "/api/admin/__phase3_role_fixture",
                "/api/merchant/__phase3_role_fixture", "/api/orders/__phase3_role_fixture"})
        com.cake.platform.common.ApiResponse<Map<String, Object>> context(Authentication authentication) {
            AuthPrincipal principal = (AuthPrincipal) authentication.getPrincipal();
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("userId", principal.userId().toString());
            data.put("role", principal.role());
            data.put("tokenVersion", principal.tokenVersion());
            data.put("merchantId", principal.merchantId() == null ? null : principal.merchantId().toString());
            data.put("authority", authentication.getAuthorities().iterator().next().getAuthority());
            return com.cake.platform.common.ApiResponse.success(data);
        }

        @GetMapping("/api/__phase3_admin_method_fixture")
        @PreAuthorize("hasRole('ADMIN')")
        public com.cake.platform.common.ApiResponse<Void> onlyAdmin() {
            return com.cake.platform.common.ApiResponse.success(null);
        }
    }
}
