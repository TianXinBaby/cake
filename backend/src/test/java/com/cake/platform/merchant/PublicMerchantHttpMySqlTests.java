package com.cake.platform.merchant;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.security.SecureRandom;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Types;
import java.util.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.*;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.SqlParameterValue;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.*;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

/** Real MySQL for API-33/34; only owned fixtures are removed, all six table counts are protected. */
@SpringBootTest
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
@ActiveProfiles("mysql-test")
@EnabledIfEnvironmentVariable(named = "CAKE_MYSQL_TEST_ENABLED", matches = "true")
class PublicMerchantHttpMySqlTests {
    private static final List<String> TABLES = List.of("sys_user", "cake_merchant", "cake_category",
            "cake_product", "cake_order", "cake_order_item");
    private static final String HASH = new BCryptPasswordEncoder(10).encode("Fixture!Pass42");
    private static final String SECRET = randomSecret();
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    private final List<BigInteger> shops = new ArrayList<>();
    private final List<BigInteger> users = new ArrayList<>();
    private Map<String, Long> before;

    @DynamicPropertySource static void isolated(DynamicPropertyRegistry properties) {
        String test = System.getenv("DB_TEST_NAME");
        if (test == null || !test.matches("[A-Za-z][A-Za-z0-9_]*_test")
                || test.equals(System.getenv().getOrDefault("DB_NAME", "cake_order_system")))
            throw new IllegalStateException("公开商家测试仅允许独立_test数据库");
        properties.add("cake.security.jwt-secret-base64", () -> SECRET);
    }

    @BeforeEach void setup() {
        assertThat(jdbc.queryForObject("SELECT DATABASE()", String.class)).isEqualTo(System.getenv("DB_TEST_NAME"));
        assertThat(jdbc.queryForObject("SELECT VERSION()", String.class)).startsWith("8.0.");
        before = counts();
    }

    @AfterEach void cleanOwnedFixturesOnly() {
        try {
            for (BigInteger shop : shops) { update("DELETE FROM cake_merchant WHERE id=?", shop); }
            for (BigInteger user : users) { update("DELETE FROM sys_user WHERE id=?", user); }
        } finally {
            assertThat(counts()).as("all six tables preserved after owned-ID cleanup").isEqualTo(before);
        }
    }

    @Test void listAndDetailReturnOnlyEnabledShopsWithPublicFields() throws Exception {
        String unique = UUID.randomUUID().toString().substring(0, 8);
        Shop first = shop("MERCHANT", 1, unique + "A", 1);
        Shop second = shop("MERCHANT", 1, unique + "B", 1);
        Shop disabledAccount = shop("MERCHANT", 0, unique + "C", 1);
        Shop disabledShop = shop("MERCHANT", 1, unique + "D", 0);

        JsonNode page = request(get("/api/merchants").param("keyword", unique).param("size", "10"),
                null, 200, 200).path("data");
        assertThat(page.path("total").asLong()).isEqualTo(2);
        assertThat(page.path("records").size()).isEqualTo(2);
        assertThat(page.path("records").get(0).path("id").asText()).isEqualTo(second.id().toString());
        assertThat(page.path("records").get(1).path("id").asText()).isEqualTo(first.id().toString());
        assertPublic(page.path("records").get(0), second, unique + "B");

        JsonNode detail = request(get("/api/merchants/" + first.id()), null, 200, 200).path("data");
        assertPublic(detail, first, unique + "A");
        assertThat(detail.path("description").asText()).isEqualTo("公开商家简介");

        request(get("/api/merchants/" + disabledAccount.id()), null, 404, 40404);
        request(get("/api/merchants/" + disabledShop.id()), null, 404, 40404);
        request(get("/api/merchants/18446744073709551615"), null, 404, 40404);
        request(get("/api/merchants/0"), null, 400, 40001);
        request(get("/api/merchants/abc"), null, 400, 40001);
    }

    @Test void keywordIsLiteralAndPaginationIsStable() throws Exception {
        String unique = UUID.randomUUID().toString().substring(0, 8);
        Shop literal = shop("MERCHANT", 1, unique + "%_!A", 1);
        shop("MERCHANT", 1, unique + "OTHER", 1);

        JsonNode found = request(get("/api/merchants").param("keyword", unique + "%_!"), null, 200, 200)
                .path("data");
        assertThat(found.path("total").asLong()).isEqualTo(1);
        assertThat(found.path("records").get(0).path("id").asText()).isEqualTo(literal.id().toString());

        JsonNode firstPage = request(get("/api/merchants").param("keyword", unique)
                .param("size", "1").param("page", "1"), null, 200, 200).path("data");
        JsonNode secondPage = request(get("/api/merchants").param("keyword", unique)
                .param("size", "1").param("page", "2"), null, 200, 200).path("data");
        JsonNode beyond = request(get("/api/merchants").param("keyword", unique)
                .param("size", "1").param("page", "99"), null, 200, 200).path("data");
        assertThat(firstPage.path("records").get(0).path("id").asText())
                .isNotEqualTo(secondPage.path("records").get(0).path("id").asText());
        assertThat(firstPage.path("total").asLong()).isEqualTo(2);
        assertThat(beyond.path("records").isEmpty()).isTrue();
        assertThat(beyond.path("total").asLong()).isEqualTo(2);
    }

    @Test void invalidParametersBodiesAndProtectedFieldsAreRejected() throws Exception {
        JsonNode errors = request(get("/api/merchants").param("page", "0").param("size", "101")
                .param("keyword", "k".repeat(61)), null, 400, 40001);
        assertThat(errors.path("data").path("fieldErrors").size()).isEqualTo(3);
        request(get("/api/merchants").param("keyword", "a").param("keyword", "b"), null, 400, 40001);
        request(get("/api/merchants").param("status", "1"), null, 403, 40302);
        request(get("/api/merchants").param("merchantId", "1"), null, 403, 40302);
        assertThat(request(get("/api/merchants").param("unexpected", "x"), null, 400, 40001)
                .path("data").isNull()).isTrue();
        request(get("/api/merchants/" + UUID.randomUUID()).contentType(MediaType.APPLICATION_JSON).content("{}"),
                null, 400, 40001);
        request(get("/api/merchants"),
                Map.of("status", 1), 403, 40302);
        request(get("/api/merchants"), Map.of("unexpected", "x"), 400, 40001);
        request(get("/api/merchants/1").param("x", "1"), null, 400, 40001);
    }

    private Shop shop(String role, int userStatus, String name, int shopStatus) {
        String phone = absentPhone();
        BigInteger userId = insert("sys_user", "INSERT INTO sys_user(username,password,nickname,phone,role,status)"
                + " VALUES(?,?,?,?,?,?)", phone, HASH, "公开商家测试", phone, role, userStatus);
        users.add(userId);
        BigInteger id = insert("cake_merchant", "INSERT INTO cake_merchant(user_id,name,phone,address,description,status)"
                + " VALUES(?,?,?,?,?,?)", userId, name, absentPhone(), "公开地址", "公开商家简介", shopStatus);
        shops.add(id);
        return new Shop(id);
    }

    private JsonNode request(MockHttpServletRequestBuilder builder, Object payload, int status, int code)
            throws Exception {
        if (payload != null) { builder.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(payload)); }
        var result = mvc.perform(builder).andReturn();
        assertThat(result.getResponse().getStatus()).as("HTTP").isEqualTo(status);
        JsonNode body = json.readTree(result.getResponse().getContentAsByteArray());
        assertThat(body.path("code").asInt()).as("business code").isEqualTo(code);
        assertThat(body.size()).isEqualTo(3);
        return body;
    }

    private BigInteger insert(String table, String sql, Object... values) {
        var key = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            for (int i = 0; i < values.length; i++) {
                if (values[i] instanceof BigInteger integer) {
                    statement.setBigDecimal(i + 1, new BigDecimal(integer));
                } else if (values[i] == null) {
                    statement.setNull(i + 1, Types.VARCHAR);
                } else {
                    statement.setObject(i + 1, values[i]);
                }
            }
            return statement;
        }, key);
        return new BigInteger(Objects.requireNonNull(key.getKey()).toString());
    }

    private int update(String sql, Object... values) {
        return jdbc.update(sql, Arrays.stream(values).map(PublicMerchantHttpMySqlTests::decimal).toArray());
    }

    private static Object decimal(Object value) {
        return value instanceof BigInteger integer ? new SqlParameterValue(Types.DECIMAL, new BigDecimal(integer)) : value;
    }

    private long count(String table) { return jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Long.class); }

    private Map<String, Long> counts() {
        var counts = new LinkedHashMap<String, Long>();
        TABLES.forEach(table -> counts.put(table, count(table)));
        return counts;
    }

    private String absentPhone() {
        String phone;
        do {
            phone = "139" + String.format("%08d",
                    Math.floorMod(UUID.randomUUID().getLeastSignificantBits(), 100_000_000L));
        } while (jdbc.queryForObject("SELECT COUNT(*) FROM sys_user WHERE username=? OR phone=?", Long.class,
                phone, phone) != 0);
        return phone;
    }

    private void assertPublic(JsonNode node, Shop shop, String name) {
        Set<String> fields = new HashSet<>();
        node.fieldNames().forEachRemaining(fields::add);
        assertThat(fields).containsExactlyInAnyOrder("id", "name", "phone", "address", "description", "status");
        assertThat(node.path("id").asText()).isEqualTo(shop.id().toString());
        assertThat(node.path("name").asText()).isEqualTo(name);
        assertThat(node.path("phone").isTextual()).isTrue();
        assertThat(node.path("status").asInt()).isEqualTo(1);
    }

    private static String randomSecret() {
        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        return Base64.getEncoder().encodeToString(bytes);
    }

    private record Shop(BigInteger id) { }
}
