package com.cake.platform.admin;

import java.math.BigInteger;
import java.math.BigDecimal;
import java.lang.reflect.InvocationTargetException;
import java.security.SecureRandom;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Types;
import java.time.Clock;
import java.time.ZoneOffset;
import java.util.*;
import java.util.concurrent.*;
import com.cake.platform.dto.AdminRequests;
import com.cake.platform.mapper.AdminMapper;
import com.cake.platform.mapper.UserAuthMapper;
import com.cake.platform.security.AuthPrincipal;
import com.cake.platform.security.JwtService;
import com.cake.platform.service.admin.AdminService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.*;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.SqlParameterValue;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.*;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.PlatformTransactionManager;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

/** Real MySQL, real security chain and transactions; all fixture cleanup uses owned IDs. */
@SpringBootTest
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
@ActiveProfiles("mysql-test")
@EnabledIfEnvironmentVariable(named = "CAKE_MYSQL_TEST_ENABLED", matches = "true")
class AdminHttpMySqlTests {
    private static final List<String> TABLES = List.of("sys_user", "cake_merchant", "cake_category", "cake_product", "cake_order", "cake_order_item");
    private static final String PASSWORD = "Fixture!Pass42";
    private static final BCryptPasswordEncoder BCRYPT = new BCryptPasswordEncoder(10);
    private static final String HASH = BCRYPT.encode(PASSWORD);
    private static final String SECRET = randomSecret();
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired JwtService jwt;
    @Autowired AdminMapper mapper;
    @Autowired UserAuthMapper users;
    @Autowired PasswordEncoder passwords;
    @Autowired PlatformTransactionManager manager;
    private final Map<String, List<BigInteger>> owned = new LinkedHashMap<>();
    private final List<String> createdPhones = new ArrayList<>();
    private Map<String, Long> before;
    private Account admin;
    private String adminToken;

    @DynamicPropertySource static void isolated(DynamicPropertyRegistry properties) {
        String test = System.getenv("DB_TEST_NAME");
        if (test == null || !test.matches("[A-Za-z][A-Za-z0-9_]*_test")
                || test.equals(System.getenv().getOrDefault("DB_NAME", "cake_order_system")))
            throw new IllegalStateException("平台管理测试仅允许显式独立_test数据库");
        properties.add("cake.security.jwt-secret-base64", () -> SECRET);
    }

    @BeforeEach void setup() {
        assertThat(jdbc.queryForObject("SELECT DATABASE()", String.class)).isEqualTo(System.getenv("DB_TEST_NAME"));
        assertThat(jdbc.queryForObject("SELECT VERSION()", String.class)).startsWith("8.0.19");
        TABLES.forEach(table -> owned.put(table, new ArrayList<>()));
        before = counts();
        admin = account("ADMIN");
        adminToken = token(admin);
    }

    @AfterEach void cleanOwnedFixturesOnly() {
        try {
            for (String phone : createdPhones) {
                var ids = jdbc.query("SELECT id FROM sys_user WHERE username=?", (row, n) -> row.getBigDecimal(1).toBigIntegerExact(), phone);
                ids.forEach(id -> {
                    if (!owned.get("sys_user").contains(id)) owned.get("sys_user").add(id);
                    jdbc.query("SELECT id FROM cake_merchant WHERE user_id=?", (row, n) -> row.getBigDecimal(1).toBigIntegerExact(), decimal(id))
                            .forEach(shop -> { if (!owned.get("cake_merchant").contains(shop)) owned.get("cake_merchant").add(shop); });
                });
            }
            for (String table : List.of("cake_order_item", "cake_order", "cake_product", "cake_category", "cake_merchant", "sys_user"))
                for (BigInteger id : owned.get(table)) update("DELETE FROM " + table + " WHERE id=?", id);
        } finally {
            if (before != null) assertThat(counts()).as("all six tables preserved after owned-ID cleanup").isEqualTo(before);
        }
    }

    @Test void createReturnsExactMerchantProjectionAndAtomicAccountDefaults() throws Exception {
        String phone = creationPhone();
        JsonNode merchant = request(admin(post("/api/admin/merchants")), creation(phone, "  新商家  "), 201, 200).path("data");
        assertMerchant(merchant);
        assertThat(merchant.path("name").asText()).isEqualTo("新商家");
        assertThat(merchant.path("username").asText()).isEqualTo(phone);
        var user = row("sys_user", new BigInteger(merchant.path("userId").asText()));
        assertThat(user.get("role")).isEqualTo("MERCHANT");
        assertThat(user.get("status")).isEqualTo(1);
        assertThat(user.get("token_version")).isEqualTo(0);
        assertThat(user.get("login_fail_count")).isEqualTo(0);
        assertThat(user.get("locked_until")).isNull();
        assertThat(BCRYPT.matches(PASSWORD, user.get("password").toString())).isTrue();
        assertThat(merchant.path("phone").asText()).isNotEqualTo(phone);
        String merchantToken = login(phone, PASSWORD, 200, 200).path("data").path("token").asText();
        request(bearer(get("/api/users/me"), merchantToken), null, 200, 200);
    }

    @ParameterizedTest @ValueSource(strings = {"ADMIN", "MERCHANT", "CUSTOMER"})
    void duplicateAccountNeverConvertsExistingRoleOrCreatesShop(String role) throws Exception {
        Account existing = account(role);
        Map<String, Object> user = row("sys_user", existing.id());
        long shops = count("cake_merchant");
        request(admin(post("/api/admin/merchants")), creation(existing.phone(), "重名店"), 409, 40901);
        assertThat(row("sys_user", existing.id())).isEqualTo(user);
        assertThat(count("cake_merchant")).isEqualTo(shops);
    }

    @Test void distinctStoreNamesNeedNotBeUnique() throws Exception {
        request(admin(post("/api/admin/merchants")), creation(creationPhone(), "允许同名"), 201, 200);
        request(admin(post("/api/admin/merchants")), creation(creationPhone(), "允许同名"), 201, 200);
    }

    @Test void creationFaultAfterBothInsertsRollsBackAccountAndShop() {
        String phone = creationPhone();
        var input = AdminRequests.creation(json.valueToTree(creation(phone, "回滚店")));
        var service = faulting("createMerchant");
        assertThatThrownBy(() -> service.create(principal(admin), input)).isInstanceOf(IllegalStateException.class);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sys_user WHERE username=?", Long.class, phone)).isZero();
        assertThat(count("cake_merchant")).isEqualTo(before.get("cake_merchant"));
    }

    @Test void concurrentDuplicateCreationCommitsOnlyOneAccountAndOneBinding() throws Exception {
        Account otherAdmin = account("ADMIN");
        String otherToken = token(otherAdmin), phone = creationPhone();
        var payload = creation(phone, "并发店");
        List<Integer> codes = concurrently(List.of(
                () -> response(admin(post("/api/admin/merchants")), payload).path("code").asInt(),
                () -> response(bearer(post("/api/admin/merchants"), otherToken), payload).path("code").asInt()));
        assertThat(codes).containsExactlyInAnyOrder(200, 40901);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM cake_merchant m JOIN sys_user u ON u.id=m.user_id WHERE u.username=?", Long.class, phone)).isEqualTo(1);
    }

    @ParameterizedTest @ValueSource(strings = {"MERCHANT", "CUSTOMER"})
    void allSixEndpointsRejectOtherRolesBeforePayloadParsing(String role) throws Exception {
        String token = token(account(role));
        for (var builder : allEndpoints("1")) request(bearer(builder, token), Map.of(), 403, 40302);
    }

    @Test void allSixEndpointsRequireAuthentication() throws Exception {
        for (var builder : allEndpoints("1")) request(builder, Map.of(), 401, 40102);
    }

    @ParameterizedTest @ValueSource(strings = {"role", "userId", "status", "merchantId", "tokenVersion", "username"})
    void protectedCreationFieldsReturn403BeforeValidationWithoutWrites(String field) throws Exception {
        Map<String, Object> payload = new LinkedHashMap<>(creation(creationPhone(), "无写入"));
        payload.put(field, "x"); payload.put("unexpected", "x");
        var snapshot = counts();
        request(admin(post("/api/admin/merchants")), payload, 403, 40302);
        assertThat(counts()).isEqualTo(snapshot);
    }

    @Test void validationCollectsRealFieldsAndUnknownInputNeverGetsInventedLocation() throws Exception {
        var invalid = request(admin(post("/api/admin/merchants")), Map.of("accountPhone", "x", "nickname", "",
                "password", "a b", "name", "", "phone", "", "address", ""), 400, 40001).path("data").path("fieldErrors");
        assertThat(invalid.size()).isEqualTo(6);
        assertThat(invalid.path("password").size()).isEqualTo(2);
        var unknown = new LinkedHashMap<>(creation(creationPhone(), "无写入")); unknown.put("unexpected", "x");
        assertThat(request(admin(post("/api/admin/merchants")), unknown, 400, 40001).path("data").isNull()).isTrue();
        request(admin(post("/api/admin/merchants")).contentType(MediaType.APPLICATION_JSON).content("{"), null, 400, 40001);
        request(admin(post("/api/admin/merchants")), List.of(), 400, 40001);
        request(admin(post("/api/admin/merchants")), null, 400, 40001);
    }

    @Test void merchantSearchIsLiteralBoundAndIncludesDisabledWithStablePagination() throws Exception {
        String unique = UUID.randomUUID().toString().substring(0, 8);
        String name = unique + "%_\\!'<script>";
        BigInteger one = shop(account("MERCHANT"), name), two = shop(account("MERCHANT"), unique + "OTHER");
        update("UPDATE cake_merchant SET status=0 WHERE id=?", one);
        JsonNode literal = request(admin(get("/api/admin/merchants").param("keyword", name)), null, 200, 200).path("data");
        assertThat(literal.path("total").asLong()).isEqualTo(1);
        assertThat(literal.path("records").get(0).path("id").asText()).isEqualTo(one.toString());
        var first = request(admin(get("/api/admin/merchants").param("keyword", unique).param("size", "1")), null, 200, 200).path("data");
        assertThat(first.path("records").get(0).path("id").asText()).isEqualTo(two.toString());
        assertThat(first.path("total").asLong()).isEqualTo(2);
        var beyond = request(admin(get("/api/admin/merchants").param("keyword", unique).param("page", Long.toString(Long.MAX_VALUE)).param("size", "100")), null, 200, 200).path("data");
        assertThat(beyond.path("records").isEmpty()).isTrue();
        assertThat(beyond.path("total").asLong()).isEqualTo(2);
        var disabled = request(admin(get("/api/admin/merchants").param("keyword", unique).param("status", "0")), null, 200, 200).path("data");
        assertThat(disabled.path("total").asLong()).isEqualTo(1);
    }

    @Test void userSearchFixesRoleAndMatchesOnlyNicknameOrPhone() throws Exception {
        String unique = UUID.randomUUID().toString().substring(0, 8);
        Account customer = account("CUSTOMER"), merchant = account("MERCHANT");
        update("UPDATE sys_user SET nickname=? WHERE id IN (?,?)", unique + "%_\\!", customer.id(), merchant.id());
        JsonNode result = request(admin(get("/api/admin/users").param("keyword", unique + "%_\\!")), null, 200, 200).path("data");
        assertThat(result.path("total").asLong()).isEqualTo(1);
        assertUser(result.path("records").get(0));
        assertThat(result.path("records").get(0).path("id").asText()).isEqualTo(customer.id().toString());
        assertThat(request(admin(get("/api/admin/users").param("keyword", customer.phone())), null, 200, 200).path("data").path("total").asLong()).isEqualTo(1);
        request(admin(get("/api/admin/users").param("role", "CUSTOMER")), null, 403, 40302);
    }

    @Test void queryErrorsIncludeMultipleActualFieldsAndRejectUnexpectedBody() throws Exception {
        JsonNode result = request(admin(get("/api/admin/users").param("page", "0").param("size", "101").param("status", "2")), null, 400, 40001);
        assertThat(result.path("data").path("fieldErrors").size()).isEqualTo(3);
        request(admin(get("/api/admin/merchants").param("keyword", "x").param("keyword", "y")), null, 400, 40001);
        assertThat(request(admin(get("/api/admin/users").param("unexpected", "x")), null, 400, 40001).path("data").isNull()).isTrue();
        request(admin(get("/api/admin/users")), Map.of("unexpected", "x"), 400, 40001);
        request(admin(get("/api/admin/users")), Map.of("role", "CUSTOMER"), 403, 40302);
    }

    @Test void editChangesOnlyStoreFieldsPreservingBindingAccountAndCreationTime() throws Exception {
        Account merchant = account("MERCHANT"); BigInteger id = onlyShop(merchant);
        var userBefore = row("sys_user", merchant.id()); var shopBefore = row("cake_merchant", id);
        JsonNode updated = request(admin(put("/api/admin/merchants/" + id)), edit("  改名  "), 200, 200).path("data");
        assertMerchant(updated);
        assertThat(updated.path("name").asText()).isEqualTo("改名");
        assertThat(row("sys_user", merchant.id())).isEqualTo(userBefore);
        assertThat(row("cake_merchant", id).get("user_id")).isEqualTo(shopBefore.get("user_id"));
        assertThat(row("cake_merchant", id).get("create_time")).isEqualTo(shopBefore.get("create_time"));
        for (String field : List.of("userId", "accountPhone", "role", "status", "username", "password")) {
            var payload = new LinkedHashMap<>(edit("拒绝")); payload.put(field, "x");
            request(admin(put("/api/admin/merchants/" + id)), payload, 403, 40302);
        }
    }

    @Test void storeStatusIsIdempotentDoesNotDisableAccountAndDoesNotInvalidateMerchantToken() throws Exception {
        Account merchant = account("MERCHANT"); BigInteger id = onlyShop(merchant); String token = token(merchant);
        var userBefore = row("sys_user", merchant.id());
        request(admin(patch("/api/admin/merchants/" + id + "/status")), Map.of("status", 0), 200, 200);
        var stopped = row("cake_merchant", id);
        JsonNode repeated = request(admin(patch("/api/admin/merchants/" + id + "/status")), Map.of("status", 0), 200, 200).path("data");
        assertThat(repeated.path("status").asInt()).isZero();
        assertThat(row("cake_merchant", id)).isEqualTo(stopped);
        assertThat(row("sys_user", merchant.id())).isEqualTo(userBefore);
        request(bearer(get("/api/users/me"), token), null, 200, 200);
        login(merchant.phone(), PASSWORD, 200, 200);
    }

    @Test void customerDisableAndEnableInvalidateAllEarlierTokensAndSameValueDoesNotWrite() throws Exception {
        Account customer = account("CUSTOMER"); String old = token(customer);
        var claims = jwt.decode(old);
        String other = new JwtService(SECRET, Clock.fixed(claims.getIssuedAt().minusSeconds(1), ZoneOffset.UTC))
                .issue(principal(customer)).token();
        assertThat(other).isNotEqualTo(old);
        jwt.decode(other);
        request(admin(patch("/api/admin/users/" + customer.id() + "/status")), Map.of("status", 0), 200, 200);
        assertThat(version(customer)).isEqualTo(1);
        request(bearer(get("/api/users/me"), old), null, 403, 40301);
        login(customer.phone(), PASSWORD, 403, 40301);
        var disabled = row("sys_user", customer.id());
        request(admin(patch("/api/admin/users/" + customer.id() + "/status")), Map.of("status", 0), 200, 200);
        assertThat(row("sys_user", customer.id())).isEqualTo(disabled);
        assertUser(request(admin(patch("/api/admin/users/" + customer.id() + "/status")), Map.of("status", 1), 200, 200).path("data"));
        assertThat(version(customer)).isEqualTo(2);
        request(bearer(get("/api/users/me"), old), null, 401, 40102);
        request(bearer(get("/api/users/me"), other), null, 401, 40102);
        login(customer.phone(), PASSWORD, 200, 200);
    }

    @ParameterizedTest @ValueSource(strings = {"ADMIN", "MERCHANT"})
    void nonCustomerTargetsAre404AndUnchanged(String role) throws Exception {
        Account target = account(role); var snapshot = row("sys_user", target.id());
        request(admin(patch("/api/admin/users/" + target.id() + "/status")), Map.of("status", 0), 404, 40405);
        assertThat(row("sys_user", target.id())).isEqualTo(snapshot);
    }

    @Test void maxUnsignedIdsAreBoundWithoutNarrowingAndMissingResourcesUseBusiness404() throws Exception {
        String max = "18446744073709551615";
        request(admin(put("/api/admin/merchants/" + max)), edit("不存在"), 404, 40404);
        request(admin(patch("/api/admin/merchants/" + max + "/status")), Map.of("status", 0), 404, 40404);
        request(admin(patch("/api/admin/users/" + max + "/status")), Map.of("status", 0), 404, 40405);
        request(admin(patch("/api/admin/users/18446744073709551616/status")), Map.of("status", 0), 400, 40001);
    }

    @ParameterizedTest @ValueSource(strings = {"\"0\"", "null", "true", "0.0", "-1", "2"})
    void bothStatusEndpointsRejectInvalidJsonTypesWithoutWrites(String value) throws Exception {
        Account merchant = account("MERCHANT"), customer = account("CUSTOMER");
        var shop = row("cake_merchant", onlyShop(merchant)); var user = row("sys_user", customer.id());
        for (String path : List.of("/api/admin/merchants/" + onlyShop(merchant) + "/status", "/api/admin/users/" + customer.id() + "/status"))
            request(admin(patch(path)).contentType(MediaType.APPLICATION_JSON).content("{\"status\":" + value + "}"), null, 400, 40001);
        assertThat(row("cake_merchant", onlyShop(merchant))).isEqualTo(shop);
        assertThat(row("sys_user", customer.id())).isEqualTo(user);
    }

    @Test void concurrentRepeatedDisableIncrementsVersionOnce() throws Exception {
        Account customer = account("CUSTOMER"), otherAdmin = account("ADMIN"); String other = token(otherAdmin);
        String path = "/api/admin/users/" + customer.id() + "/status";
        List<Integer> codes = concurrently(List.of(
                () -> response(admin(patch(path)), Map.of("status", 0)).path("code").asInt(),
                () -> response(bearer(patch(path), other), Map.of("status", 0)).path("code").asInt()));
        assertThat(codes).containsOnly(200);
        assertThat(version(customer)).isEqualTo(1);
    }

    @Test void concurrentLogoutAndDisableNeverLoseVersionIncrementOrRestoreEnabledState() throws Exception {
        Account customer = account("CUSTOMER"); String token = token(customer);
        List<Integer> codes = concurrently(List.of(
                () -> response(admin(patch("/api/admin/users/" + customer.id() + "/status")), Map.of("status", 0)).path("code").asInt(),
                () -> response(bearer(post("/api/auth/logout"), token), null).path("code").asInt()));
        assertThat(codes.get(0)).isEqualTo(200);
        assertThat(codes.get(1)).isIn(200, 40102, 40301);
        assertThat(row("sys_user", customer.id()).get("status")).isEqualTo(0);
        assertThat(version(customer)).isEqualTo(codes.get(1) == 200 ? 2 : 1);
    }

    @Test void faultAfterStatusSqlRollsBackStateVersionAndTime() {
        Account customer = account("CUSTOMER"); var snapshot = row("sys_user", customer.id());
        assertThatThrownBy(() -> faulting("customerStatus").customerStatus(principal(admin), customer.id(), 0))
                .isInstanceOf(IllegalStateException.class);
        assertThat(row("sys_user", customer.id())).isEqualTo(snapshot);
    }

    @Test void versionOverflowIsSafe500AndRollsBackStatus() throws Exception {
        Account customer = account("CUSTOMER");
        update("UPDATE sys_user SET token_version=2147483647 WHERE id=?", customer.id());
        var snapshot = row("sys_user", customer.id());
        JsonNode failure = request(admin(patch("/api/admin/users/" + customer.id() + "/status")), Map.of("status", 0), 500, 50001);
        assertThat(failure.path("data").path("requestId").asText()).isNotBlank();
        assertThat(failure.toString()).doesNotContain("SQL", "Exception", "token_version", "2147483647");
        assertThat(row("sys_user", customer.id())).isEqualTo(snapshot);
    }

    @Test void administrativeChangesPreserveHistoricalOrderItemsAndProductStock() throws Exception {
        Account merchant = account("MERCHANT"), customer = account("CUSTOMER"); BigInteger shop = onlyShop(merchant);
        BigInteger category = insert("cake_category", "INSERT INTO cake_category(merchant_id,name) VALUES(?,?)", shop, "历史夹具分类");
        BigInteger product = insert("cake_product", "INSERT INTO cake_product(merchant_id,category_id,name,spec,price,stock) VALUES(?,?,?,?,?,?)", shop, category, "历史商品", "标准份", "10.00", 7);
        BigInteger order = insert("cake_order", "INSERT INTO cake_order(merchant_id,merchant_name,order_no,user_id,request_id,request_hash,total_amount,status,order_type,receiver,phone,expire_time) VALUES(?,?,?,?,?,?,'10.00',0,2,?,?,DATE_ADD(CURRENT_TIMESTAMP(3),INTERVAL 30 MINUTE))",
                shop, "历史店铺快照", UUID.randomUUID().toString().replace("-", ""), customer.id(), UUID.randomUUID().toString(), "0".repeat(64), "联系人快照", customer.phone());
        BigInteger item = insert("cake_order_item", "INSERT INTO cake_order_item(order_id,product_id,product_name,product_spec,price,quantity,subtotal) VALUES(?,?,?,?,'10.00',1,'10.00')", order, product, "历史商品快照", "历史规格快照");
        var orderBefore = row("cake_order", order); var itemBefore = row("cake_order_item", item); var productBefore = row("cake_product", product);
        request(admin(put("/api/admin/merchants/" + shop)), edit("新店铺名"), 200, 200);
        request(admin(patch("/api/admin/merchants/" + shop + "/status")), Map.of("status", 0), 200, 200);
        request(admin(patch("/api/admin/users/" + customer.id() + "/status")), Map.of("status", 0), 200, 200);
        assertThat(row("cake_order", order)).isEqualTo(orderBefore);
        assertThat(row("cake_order_item", item)).isEqualTo(itemBefore);
        assertThat(row("cake_product", product)).isEqualTo(productBefore);
    }

    @Test void writeTransactionRechecksAdministratorCurrentRoleVersionAndStatus() {
        var input = AdminRequests.creation(json.valueToTree(creation(creationPhone(), "拒绝")));
        AdminService service = new AdminService(mapper, users, passwords, manager);
        update("UPDATE sys_user SET token_version=1 WHERE id=?", admin.id());
        assertThatThrownBy(() -> service.create(principal(admin), input)).isInstanceOfSatisfying(com.cake.platform.security.AuthFailure.class,
                error -> assertThat(error.errorCode().code()).isEqualTo(40102));
        update("UPDATE sys_user SET token_version=0,role='CUSTOMER' WHERE id=?", admin.id());
        assertThatThrownBy(() -> service.create(principal(admin), input)).isInstanceOfSatisfying(com.cake.platform.security.AuthFailure.class,
                error -> assertThat(error.errorCode().code()).isEqualTo(40302));
        update("UPDATE sys_user SET role='ADMIN',status=0,token_version=1 WHERE id=?", admin.id());
        assertThatThrownBy(() -> service.create(principal(admin), input)).isInstanceOfSatisfying(com.cake.platform.security.AuthFailure.class,
                error -> assertThat(error.errorCode().code()).isEqualTo(40301));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sys_user WHERE username=?", Long.class, input.accountPhone())).isZero();
    }

    private AdminService faulting(String methodName) {
        AdminMapper fault = (AdminMapper) java.lang.reflect.Proxy.newProxyInstance(AdminMapper.class.getClassLoader(),
                new Class<?>[]{AdminMapper.class}, (proxy, method, args) -> {
                    try {
                        Object result = method.invoke(mapper, args);
                        if (method.getName().equals(methodName)) throw new IllegalStateException("Injected phase-4 rollback failure");
                        return result;
                    } catch (InvocationTargetException error) { throw error.getCause(); }
                });
        return new AdminService(fault, users, passwords, manager);
    }
    private Account account(String role) {
        String phone = absentPhone();
        BigInteger id = insert("sys_user", "INSERT INTO sys_user(username,password,nickname,phone,role) VALUES(?,?,?,?,?)", phone, HASH, "平台测试", phone, role);
        Account account = new Account(id, phone);
        if (role.equals("MERCHANT")) shop(account, "平台测试商家");
        return account;
    }
    private BigInteger shop(Account merchant, String name) {
        BigInteger id = onlyShopOrNull(merchant);
        if (id != null) { update("UPDATE cake_merchant SET name=? WHERE id=?", name, id); return id; }
        return insert("cake_merchant", "INSERT INTO cake_merchant(user_id,name,phone,address) VALUES(?,?,?,?)", merchant.id(), name, absentPhone(), "测试地址");
    }
    private BigInteger onlyShopOrNull(Account merchant) {
        var ids = jdbc.query("SELECT id FROM cake_merchant WHERE user_id=?", (row, n) -> row.getBigDecimal(1).toBigIntegerExact(), decimal(merchant.id()));
        return ids.isEmpty() ? null : ids.get(0);
    }
    private BigInteger onlyShop(Account merchant) { return Objects.requireNonNull(onlyShopOrNull(merchant)); }
    private BigInteger insert(String table, String sql, Object... values) {
        var key = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            for (int i = 0; i < values.length; i++) {
                if (values[i] instanceof BigInteger integer) statement.setBigDecimal(i + 1, new BigDecimal(integer));
                else statement.setObject(i + 1, values[i]);
            }
            return statement;
        }, key);
        BigInteger id = new BigInteger(Objects.requireNonNull(key.getKey()).toString()); owned.get(table).add(id); return id;
    }
    private JsonNode request(MockHttpServletRequestBuilder builder, Object payload, int status, int code) throws Exception {
        var result = perform(builder, payload);
        assertThat(result.getResponse().getStatus()).as("HTTP").isEqualTo(status);
        JsonNode body = json.readTree(result.getResponse().getContentAsByteArray());
        assertThat(body.path("code").asInt()).as("business code").isEqualTo(code);
        assertThat(body.size()).isEqualTo(3);
        assertThat(body.has("msg") && body.has("data")).isTrue();
        if (code == 40001 && !body.path("data").isNull()) {
            var fields = body.path("data").path("fieldErrors");
            assertThat(body.path("data").size()).isEqualTo(1);
            assertThat(fields.isObject() && !fields.isEmpty()).isTrue();
            fields.fields().forEachRemaining(entry -> {
                assertThat(entry.getKey()).matches("[a-z][A-Za-z0-9]*").isNotEqualTo("_global");
                assertThat(entry.getValue().isArray() && !entry.getValue().isEmpty()).isTrue();
                entry.getValue().forEach(message -> assertThat(message.isTextual() && !message.asText().isBlank()).isTrue());
            });
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
    private JsonNode login(String phone, String password, int status, int code) throws Exception {
        return request(post("/api/auth/login"), Map.of("username", phone, "password", password), status, code);
    }
    private String token(Account account) { return jwt.issue(principal(account)).token(); }
    private AuthPrincipal principal(Account account) {
        var row = row("sys_user", account.id());
        return new AuthPrincipal(account.id(), row.get("role").toString(), 0, onlyShopOrNull(account));
    }
    private MockHttpServletRequestBuilder admin(MockHttpServletRequestBuilder builder) { return bearer(builder, adminToken); }
    private MockHttpServletRequestBuilder bearer(MockHttpServletRequestBuilder builder, String token) { return builder.header("Authorization", "Bearer " + token); }
    private List<MockHttpServletRequestBuilder> allEndpoints(String id) {
        return List.of(get("/api/admin/merchants"), post("/api/admin/merchants"), put("/api/admin/merchants/" + id),
                patch("/api/admin/merchants/" + id + "/status"), get("/api/admin/users"), patch("/api/admin/users/" + id + "/status"));
    }
    private Map<String, Object> creation(String phone, String name) {
        var payload = new LinkedHashMap<String, Object>();
        payload.put("accountPhone", phone); payload.put("nickname", "商家昵称"); payload.put("password", PASSWORD);
        payload.put("name", name); payload.put("phone", "13800000002"); payload.put("address", "店铺地址"); payload.put("description", null);
        return payload;
    }
    private Map<String, Object> edit(String name) { return Map.of("name", name, "phone", "13800000003", "address", "新地址", "description", "简介"); }
    private String creationPhone() { String phone = absentPhone(); createdPhones.add(phone); return phone; }
    private String absentPhone() {
        String phone;
        do { phone = "139" + String.format("%08d", Math.floorMod(UUID.randomUUID().getLeastSignificantBits(), 100_000_000L)); }
        while (jdbc.queryForObject("SELECT COUNT(*) FROM sys_user WHERE username=? OR phone=?", Long.class, phone, phone) != 0);
        return phone;
    }
    private Map<String, Object> row(String table, BigInteger id) { return jdbc.queryForMap("SELECT * FROM " + table + " WHERE id=?", decimal(id)); }
    private static Object decimal(Object value) {
        return value instanceof BigInteger integer ? new SqlParameterValue(Types.DECIMAL, new BigDecimal(integer)) : value;
    }
    private int update(String sql, Object... values) { return jdbc.update(sql, Arrays.stream(values).map(AdminHttpMySqlTests::decimal).toArray()); }
    private int version(Account account) { return ((Number) row("sys_user", account.id()).get("token_version")).intValue(); }
    private long count(String table) { return jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Long.class); }
    private Map<String, Long> counts() { var counts = new LinkedHashMap<String, Long>(); TABLES.forEach(table -> counts.put(table, count(table))); return counts; }
    private void assertMerchant(JsonNode node) {
        assertThat(fieldNames(node)).containsExactlyInAnyOrder("id", "name", "phone", "address", "description", "status", "userId", "username", "createTime", "updateTime");
        assertThat(node.path("id").isTextual() && node.path("userId").isTextual()).isTrue();
        assertThat(node.path("createTime").asText()).matches(".*\\.\\d{3}\\+08:00");
    }
    private void assertUser(JsonNode node) {
        assertThat(fieldNames(node)).containsExactlyInAnyOrder("id", "username", "nickname", "phone", "role", "status", "createTime");
        assertThat(node.path("id").isTextual()).isTrue();
    }
    private Set<String> fieldNames(JsonNode node) { Set<String> names = new HashSet<>(); node.fieldNames().forEachRemaining(names::add); return names; }
    private static String randomSecret() { byte[] bytes = new byte[32]; new SecureRandom().nextBytes(bytes); return Base64.getEncoder().encodeToString(bytes); }
    private record Account(BigInteger id, String phone) { }
    private static <T> List<T> concurrently(List<Callable<T>> calls) throws Exception {
        var pool = Executors.newFixedThreadPool(calls.size()); var ready = new CountDownLatch(calls.size()); var start = new CountDownLatch(1);
        try {
            var futures = calls.stream().map(call -> pool.submit(() -> { ready.countDown(); if (!start.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("并发启动超时"); return call.call(); })).toList();
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue(); start.countDown();
            List<T> result = new ArrayList<>(); for (var future : futures) result.add(future.get(30, TimeUnit.SECONDS)); return result;
        } finally { start.countDown(); pool.shutdownNow(); assertThat(pool.awaitTermination(10, TimeUnit.SECONDS)).isTrue(); }
    }
}
