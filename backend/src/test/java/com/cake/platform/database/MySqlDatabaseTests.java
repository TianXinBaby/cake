package com.cake.platform.database;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.sql.DataSource;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.PreparedStatementSetter;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.transaction.AfterTransaction;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Opt-in only: preinitialized dedicated MySQL 8.0.19 schema, never the development database. */
@SpringBootTest
@ActiveProfiles("mysql-test")
@EnabledIfEnvironmentVariable(named = "CAKE_MYSQL_TEST_ENABLED", matches = "true")
@Transactional
class MySqlDatabaseTests {
    private static final List<String> TABLES = List.of("sys_user", "cake_merchant", "cake_category",
            "cake_product", "cake_order", "cake_order_item");
    private static final String FIXTURE_HASH = "$2b$12$" + "A".repeat(53);
    private JdbcTemplate jdbc;
    @Autowired private DataSource dataSource;
    @Autowired private SqlSessionFactory sqlSessionFactory;
    private Map<String, Long> initialCounts;
    private BigInteger userA;
    private BigInteger userB;
    private BigInteger customer;
    private BigInteger merchantA;
    private BigInteger merchantB;
    private BigInteger categoryA;
    private BigInteger categoryB;
    private BigInteger productA;
    private BigInteger orderA;
    private String phoneA;

    @DynamicPropertySource
    static void requireIsolatedDatabase(DynamicPropertyRegistry ignored) {
        String testName = System.getenv("DB_TEST_NAME");
        String devName = System.getenv().getOrDefault("DB_NAME", "cake_order_system");
        if (testName == null || !testName.matches("[A-Za-z][A-Za-z0-9_]*_test") || testName.equals(devName)) {
            throw new IllegalStateException("真实数据库测试要求独立的 DB_TEST_NAME，名称须以 _test 结尾，且不得等于 DB_NAME。");
        }
    }

    @BeforeEach
    void createRollbackOnlyFixtures() {
        jdbc = new UnsignedJdbcTemplate(dataSource);
        initialCounts = counts();
        phoneA = syntheticPhone();
        userA = user(phoneA, phoneA, "MERCHANT");
        userB = user(syntheticPhone(), syntheticPhone(), "MERCHANT");
        customer = user(syntheticPhone(), syntheticPhone(), "CUSTOMER");
        merchantA = merchant(userA);
        merchantB = merchant(userB);
        categoryA = category(merchantA, "事务分类");
        categoryB = category(merchantB, "事务分类");
        productA = product(merchantA, categoryA, new BigDecimal("12.34"), 10);
        orderA = order(customer, merchantA, 0, 2);
    }

    @AfterTransaction
    void rollbackLeavesAllSixTableRowCountsUnchanged() {
        assertThat(counts()).as("transaction fixtures must all roll back").isEqualTo(initialCounts);
    }

    @Test
    void springDatasourceAndMybatisPlusInitializeAgainstMysql8019() throws Exception {
        assertThat(sqlSessionFactory.getConfiguration()).isInstanceOf(MybatisConfiguration.class);
        try (Connection connection = dataSource.getConnection()) {
            assertThat(connection.isValid(2)).isTrue();
            assertThat(connection.getMetaData().getDatabaseProductName()).isEqualTo("MySQL");
            assertThat(connection.getMetaData().getDatabaseProductVersion()).startsWith("8.0.19");
        }
        assertThat(jdbc.queryForObject("SELECT @@session.time_zone", String.class)).isEqualTo("+08:00");
        assertThat(jdbc.queryForObject("SELECT @@session.sql_mode", String.class)).contains("STRICT_TRANS_TABLES");
    }

    @Test
    void exactlySixInnoDbTablesUseConfirmedCollation() {
        var rows = jdbc.queryForList("SELECT TABLE_NAME, ENGINE, TABLE_COLLATION FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE() AND TABLE_TYPE = 'BASE TABLE'");
        assertThat(rows).hasSize(6);
        assertThat(rows).extracting(row -> row.get("TABLE_NAME").toString()).containsExactlyInAnyOrderElementsOf(TABLES);
        rows.forEach(row -> {
            assertThat(row.get("ENGINE")).isEqualTo("InnoDB");
            assertThat(row.get("TABLE_COLLATION")).isEqualTo("utf8mb4_0900_as_cs");
        });
    }

    @Test
    void all74ColumnsMatchIndependentDocxDictionaryIncludingDefaults() throws Exception {
        var expected = new ArrayList<List<String>>();
        try (var input = new BufferedReader(new InputStreamReader(
                getClass().getResourceAsStream("/database/expected-columns.tsv"), StandardCharsets.UTF_8))) {
            input.lines().skip(1).forEach(line -> expected.add(List.of(line.split("\t", -1))));
        }
        assertThat(expected).hasSize(74);
        var actual = jdbc.queryForList("SELECT TABLE_NAME, COLUMN_NAME, COLUMN_TYPE, IS_NULLABLE, COLUMN_DEFAULT, EXTRA, DATETIME_PRECISION FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE()");
        assertThat(actual).hasSize(74);
        for (var field : expected) {
            var row = actual.stream().filter(value -> field.get(0).equals(value.get("TABLE_NAME"))
                    && field.get(1).equals(value.get("COLUMN_NAME"))).findFirst().orElseThrow();
            assertThat(row.get("COLUMN_TYPE").toString().replaceAll("(?i)(bigint|tinyint|int)\\(\\d+\\)", "$1"))
                    .as(field.get(0) + "." + field.get(1)).isEqualTo(field.get(2));
            assertThat(row.get("IS_NULLABLE")).isEqualTo(field.get(3));
            String defaultValue = row.get("COLUMN_DEFAULT") == null ? "<NULL>" : row.get("COLUMN_DEFAULT").toString();
            assertThat(defaultValue.toUpperCase()).isEqualTo(field.get(4).toUpperCase());
            assertThat(row.get("EXTRA").toString().replace("DEFAULT_GENERATED", "").trim()).isEqualTo(field.get(5));
            if (field.get(2).equals("datetime(3)")) {
                assertThat(((Number) row.get("DATETIME_PRECISION")).intValue()).isEqualTo(3);
            }
        }
    }

    @Test
    void primaryUniqueOrdinaryAndFkSupportIndexesMatchExactly() {
        var expected = new LinkedHashMap<String, String>();
        TABLES.forEach(table -> expected.put(table + ".PRIMARY", "0:id"));
        expected.put("sys_user.uk_user_username", "0:username");
        expected.put("sys_user.uk_user_phone", "0:phone");
        expected.put("cake_merchant.uk_merchant_user", "0:user_id");
        expected.put("cake_category.uk_category_merchant_name", "0:merchant_id,name");
        expected.put("cake_category.uk_category_id_merchant", "0:id,merchant_id");
        expected.put("cake_product.uk_product_id_merchant", "0:id,merchant_id");
        expected.put("cake_order.uk_order_no", "0:order_no");
        expected.put("cake_order.uk_order_user_request", "0:user_id,request_id");
        expected.put("cake_order_item.uk_item_order_product", "0:order_id,product_id");
        expected.put("cake_merchant.idx_merchant_status", "1:status,id");
        expected.put("cake_category.idx_category_merchant_status_sort", "1:merchant_id,status,sort,id");
        expected.put("cake_product.idx_product_merchant_category_status", "1:merchant_id,category_id,status,id");
        expected.put("cake_product.idx_product_merchant_status", "1:merchant_id,status,id");
        expected.put("cake_product.fk_product_category_merchant", "1:category_id,merchant_id");
        expected.put("cake_order.idx_order_user_status_create", "1:user_id,status,create_time,id");
        expected.put("cake_order.idx_order_user_create", "1:user_id,create_time,id");
        expected.put("cake_order.idx_order_merchant_status_create", "1:merchant_id,status,create_time,id");
        expected.put("cake_order.idx_order_merchant_create", "1:merchant_id,create_time,id");
        expected.put("cake_order.idx_order_merchant_status_complete", "1:merchant_id,status,complete_time,id");
        expected.put("cake_order.idx_order_create", "1:create_time,id");
        expected.put("cake_order.idx_order_status_create", "1:status,create_time,id");
        expected.put("cake_order.idx_order_status_complete", "1:status,complete_time,id");
        expected.put("cake_order.idx_order_status_expire", "1:status,expire_time,id");
        expected.put("cake_order_item.idx_item_product", "1:product_id");
        var actual = new LinkedHashMap<String, String>();
        jdbc.queryForList("SELECT TABLE_NAME, INDEX_NAME, NON_UNIQUE, GROUP_CONCAT(COLUMN_NAME ORDER BY SEQ_IN_INDEX SEPARATOR ',') AS COLS FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() GROUP BY TABLE_NAME, INDEX_NAME, NON_UNIQUE")
                .forEach(row -> actual.put(row.get("TABLE_NAME") + "." + row.get("INDEX_NAME"), row.get("NON_UNIQUE") + ":" + row.get("COLS")));
        assertThat(actual).containsExactlyInAnyOrderEntriesOf(expected);
    }

    @Test
    void allEightForeignKeysHaveExactOrderedColumnsAndRestrictRules() {
        Map<String, String> expected = Map.of(
                "cake_merchant.fk_merchant_user", "user_id->sys_user:id",
                "cake_category.fk_category_merchant", "merchant_id->cake_merchant:id",
                "cake_product.fk_product_merchant", "merchant_id->cake_merchant:id",
                "cake_product.fk_product_category_merchant", "category_id,merchant_id->cake_category:id,merchant_id",
                "cake_order.fk_order_merchant", "merchant_id->cake_merchant:id",
                "cake_order.fk_order_user", "user_id->sys_user:id",
                "cake_order_item.fk_item_order", "order_id->cake_order:id",
                "cake_order_item.fk_item_product", "product_id->cake_product:id");
        var actual = new LinkedHashMap<String, String>();
        jdbc.queryForList("SELECT k.TABLE_NAME,k.CONSTRAINT_NAME,k.REFERENCED_TABLE_NAME,GROUP_CONCAT(k.COLUMN_NAME ORDER BY k.ORDINAL_POSITION) AS COLS,GROUP_CONCAT(k.REFERENCED_COLUMN_NAME ORDER BY k.ORDINAL_POSITION) AS REF_COLS,r.DELETE_RULE,r.UPDATE_RULE FROM information_schema.KEY_COLUMN_USAGE k JOIN information_schema.REFERENTIAL_CONSTRAINTS r ON k.CONSTRAINT_SCHEMA=r.CONSTRAINT_SCHEMA AND k.CONSTRAINT_NAME=r.CONSTRAINT_NAME WHERE k.TABLE_SCHEMA=DATABASE() AND k.REFERENCED_TABLE_NAME IS NOT NULL GROUP BY k.TABLE_NAME,k.CONSTRAINT_NAME,k.REFERENCED_TABLE_NAME,r.DELETE_RULE,r.UPDATE_RULE")
                .forEach(row -> {
                    assertThat(row.get("DELETE_RULE")).isEqualTo("RESTRICT");
                    assertThat(row.get("UPDATE_RULE")).isEqualTo("RESTRICT");
                    actual.put(row.get("TABLE_NAME") + "." + row.get("CONSTRAINT_NAME"), row.get("COLS") + "->" + row.get("REFERENCED_TABLE_NAME") + ":" + row.get("REF_COLS"));
                });
        assertThat(actual).containsExactlyInAnyOrderEntriesOf(expected);
    }

    @Test
    void all29ChecksArePresentAndEnforced() {
        var expected = List.of("ck_user_role", "ck_user_status", "ck_user_token_version", "ck_user_login_fail_count", "ck_user_text", "ck_user_phone", "ck_merchant_status", "ck_merchant_text", "ck_merchant_phone", "ck_category_status", "ck_category_sort", "ck_category_name", "ck_product_price", "ck_product_stock", "ck_product_status", "ck_product_text", "ck_order_amount", "ck_order_status", "ck_order_type", "ck_order_text", "ck_order_phone", "ck_order_address", "ck_order_expire", "ck_order_state_events", "ck_order_event_times", "ck_item_price", "ck_item_quantity", "ck_item_subtotal", "ck_item_snapshot");
        var checks = jdbc.queryForList("SELECT CONSTRAINT_NAME,ENFORCED FROM information_schema.TABLE_CONSTRAINTS WHERE CONSTRAINT_SCHEMA=DATABASE() AND CONSTRAINT_TYPE='CHECK'");
        assertThat(checks).extracting(row -> row.get("CONSTRAINT_NAME")).containsExactlyInAnyOrderElementsOf(expected);
        checks.forEach(row -> assertThat(row.get("ENFORCED")).isEqualTo("YES"));
    }

    @Test
    void eachOf29CheckDefinitionsHasAnActualViolatingStatement() {
        check("ck_user_role", "UPDATE sys_user SET role='ROOT' WHERE id=?", customer);
        check("ck_user_status", "UPDATE sys_user SET status=2 WHERE id=?", customer);
        check("ck_user_token_version", "UPDATE sys_user SET token_version=-1 WHERE id=?", customer);
        check("ck_user_login_fail_count", "UPDATE sys_user SET login_fail_count=6 WHERE id=?", customer);
        check("ck_user_text", "UPDATE sys_user SET nickname='' WHERE id=?", customer);
        check("ck_user_phone", "UPDATE sys_user SET phone='INVALID' WHERE id=?", customer);
        check("ck_merchant_status", "UPDATE cake_merchant SET status=2 WHERE id=?", merchantA);
        check("ck_merchant_text", "UPDATE cake_merchant SET name='' WHERE id=?", merchantA);
        check("ck_merchant_phone", "UPDATE cake_merchant SET phone='INVALID' WHERE id=?", merchantA);
        check("ck_category_status", "UPDATE cake_category SET status=2 WHERE id=?", categoryA);
        check("ck_category_sort", "UPDATE cake_category SET sort=-1 WHERE id=?", categoryA);
        check("ck_category_name", "UPDATE cake_category SET name='' WHERE id=?", categoryA);
        check("ck_product_price", "UPDATE cake_product SET price=0 WHERE id=?", productA);
        check("ck_product_stock", "UPDATE cake_product SET stock=-1 WHERE id=?", productA);
        check("ck_product_status", "UPDATE cake_product SET status=2 WHERE id=?", productA);
        check("ck_product_text", "UPDATE cake_product SET spec='' WHERE id=?", productA);
        check("ck_order_amount", "UPDATE cake_order SET total_amount=0 WHERE id=?", orderA);
        // Invalid status also contradicts the state matrix. Both definitions are independently inspected above.
        fails(3819, () -> jdbc.update("UPDATE cake_order SET status=8 WHERE id=?", orderA));
        // Type 3 also contradicts the address branch, so the first reported CHECK may vary.
        fails(3819, () -> jdbc.update("UPDATE cake_order SET order_type=3 WHERE id=?", orderA));
        check("ck_order_text", "UPDATE cake_order SET receiver='' WHERE id=?", orderA);
        check("ck_order_phone", "UPDATE cake_order SET phone='INVALID' WHERE id=?", orderA);
        check("ck_order_address", "UPDATE cake_order SET address='自提不应有配送地址' WHERE id=?", orderA);
        check("ck_order_expire", "UPDATE cake_order SET expire_time=DATE_ADD(create_time,INTERVAL 29 MINUTE) WHERE id=?", orderA);
        check("ck_order_state_events", "UPDATE cake_order SET status=7 WHERE id=?", orderA);
        check("ck_order_event_times", "UPDATE cake_order SET status=7,pay_time=DATE_SUB(create_time,INTERVAL 1 SECOND) WHERE id=?", orderA);
        BigInteger itemId = item();
        // Zero price and matching zero subtotal violate both positivity definitions; report combination rejection.
        fails(3819, () -> jdbc.update("UPDATE cake_order_item SET price=0,subtotal=0 WHERE id=?", itemId));
        check("ck_item_quantity", "UPDATE cake_order_item SET quantity=100,subtotal=price*100 WHERE id=?", itemId);
        check("ck_item_subtotal", "UPDATE cake_order_item SET subtotal=1 WHERE id=?", itemId);
        check("ck_item_snapshot", "UPDATE cake_order_item SET product_spec='' WHERE id=?", itemId);
    }

    @Test
    void allEightStatesAndBothFulfillmentBranchesAcceptValidEventShapes() {
        for (int type : List.of(1, 2)) {
            for (int state : List.of(0, 7, 1, 5, 3, 4, type == 1 ? 2 : 6)) {
                BigInteger id = order(customer, merchantA, 0, 2);
                var created = LocalDateTime.of(2026, 11, 25, 12, 0, 0, 123_000_000);
                var paid = state == 0 ? null : created.plusMinutes(1);
                var accepted = List.of(1, 5, 2, 6, 3, 4).contains(state) ? created.plusHours(1) : null;
                var started = List.of(5, 2, 6, 3).contains(state) ? created.plusHours(2) : null;
                var delivered = type == 1 && List.of(2, 3).contains(state) ? created.plusHours(3) : null;
                var ready = type == 2 && List.of(6, 3).contains(state) ? created.plusHours(3) : null;
                jdbc.update("UPDATE cake_order SET status=?,order_type=?,address=?,pay_time=?,accept_time=?,start_time=?,delivery_time=?,ready_time=?,complete_time=?,cancel_time=?,cancel_reason=? WHERE id=?", state, type, type == 1 ? "事务配送地址" : null, paid, accepted, started, delivered, ready, state == 3 ? created.plusHours(4) : null, state == 4 ? created.plusHours(2) : null, state == 4 ? "事务取消原因" : null, id);
                assertThat(jdbc.queryForObject("SELECT status FROM cake_order WHERE id=?", Integer.class, id)).isEqualTo(state);
                if (state == 4) {
                    assertThat(jdbc.queryForObject("SELECT pay_time FROM cake_order WHERE id=?", LocalDateTime.class, id)).isEqualTo(paid);
                    assertThat(jdbc.queryForObject("SELECT accept_time FROM cake_order WHERE id=?", LocalDateTime.class, id)).isEqualTo(accepted);
                }
            }
        }
    }

    @Test
    void unsignedBigintAboveSignedLongRangeRoundTripsUsingJdbcBigInteger() {
        var id = new java.math.BigInteger("18446744073709551614");
        // No insert: InnoDB AUTO_INCREMENT advancement would survive a transaction rollback.
        assertThat(jdbc.queryForObject("SELECT CAST(? AS UNSIGNED)", java.math.BigInteger.class, id)).isEqualTo(id);
        assertThat(jdbc.queryForObject("SELECT CAST(CAST(? AS UNSIGNED) AS CHAR)", String.class, id)).isEqualTo(id.toString());
    }

    @Test
    void confirmedCollationKeepsCaseAndAccentDistinctWithinOneMerchant() {
        BigInteger upper = category(merchantA, "Cake");
        BigInteger lower = category(merchantA, "cake");
        BigInteger plain = category(merchantA, "cafe");
        BigInteger accented = category(merchantA, "café");
        assertThat(List.of(upper, lower, plain, accented)).doesNotHaveDuplicates();
        fails(1062, () -> category(merchantA, "Cake"));
        fails(1062, () -> category(merchantA, "café"));
    }

    @Test
    void accountDefaultsAreAppliedByTheActualDatabase() {
        BigInteger id = insert("INSERT INTO sys_user(username,password,nickname,phone) VALUES(?,?,?,?)", syntheticPhone(), FIXTURE_HASH, "默认值测试", syntheticPhone());
        var row = jdbc.queryForMap("SELECT role,status,token_version,login_fail_count,locked_until,create_time,update_time FROM sys_user WHERE id=?", id);
        assertThat(row.get("role")).isEqualTo("CUSTOMER");
        assertThat(((Number) row.get("status")).intValue()).isEqualTo(1);
        assertThat(((Number) row.get("token_version")).intValue()).isZero();
        assertThat(((Number) row.get("login_fail_count")).intValue()).isZero();
        assertThat(row.get("locked_until")).isNull();
        assertThat(row.get("create_time")).isNotNull();
        assertThat(row.get("update_time")).isNotNull();
    }

    @Test void duplicateUsernameFails() { fails(1062, () -> user(phoneA, syntheticPhone(), "CUSTOMER")); }
    @Test void duplicatePhoneFails() { fails(1062, () -> user(syntheticPhone(), phoneA, "CUSTOMER")); }
    @Test void repeatedMerchantAccountBindingFails() { fails(1062, () -> merchant(userA)); }
    @Test void sameMerchantDuplicateCategoryFails() { fails(1062, () -> category(merchantA, "事务分类")); }
    @Test void differentMerchantsMayShareCategoryName() { assertThat(categoryB).isNotEqualTo(categoryA); }
    @Test void productCannotReferenceAnotherMerchantsCategory() { fails(1452, () -> product(merchantA, categoryB, BigDecimal.ONE, 1)); }
    @Test void absentMerchantReferenceFails() { fails(1452, () -> category(BigInteger.valueOf(Long.MAX_VALUE), "不存在商家")); }
    @Test void absentUserReferenceFails() { fails(1452, () -> merchant(BigInteger.valueOf(Long.MAX_VALUE))); }
    @Test void zeroAndNegativeProductPricesFail() { fails(3819, () -> product(merchantA, categoryA, BigDecimal.ZERO, 0)); fails(3819, () -> product(merchantA, categoryA, BigDecimal.ONE.negate(), 0)); }
    @Test void negativeStockFails() { fails(3819, () -> product(merchantA, categoryA, BigDecimal.ONE, -1)); }
    @Test void invalidRoleFails() { fails(3819, () -> user(syntheticPhone(), syntheticPhone(), "ROOT")); }
    @Test void invalidOrderStatusFails() { fails(3819, () -> order(customer, merchantA, 8, 2)); }
    @Test void invalidOrderTypeFails() { fails(3819, () -> order(customer, merchantA, 0, 3)); }
    @Test void duplicateProductInSameOrderFails() { item(); fails(1062, this::item); }
    @Test void referencedRowsCannotBeDeletedOrPrimaryKeysUpdated() { fails(1451, () -> jdbc.update("DELETE FROM cake_category WHERE id=?", categoryA)); fails(1451, () -> jdbc.update("UPDATE cake_category SET id=? WHERE id=?", BigInteger.valueOf(Long.MAX_VALUE), categoryA)); }

    @Test
    void decimalTenTwoKeepsExactCentsAndRejectsOverflow() {
        jdbc.update("UPDATE cake_product SET price=? WHERE id=?", new BigDecimal("99999999.99"), productA);
        assertThat(jdbc.queryForObject("SELECT price FROM cake_product WHERE id=?", BigDecimal.class, productA))
                .isEqualByComparingTo("99999999.99");
        fails(1264, () -> jdbc.update("UPDATE cake_product SET price=? WHERE id=?", new BigDecimal("100000000.00"), productA));
        jdbc.update("UPDATE cake_product SET price=? WHERE id=?", new BigDecimal("1.239"), productA);
        assertThat(jdbc.queryForObject("SELECT price FROM cake_product WHERE id=?", BigDecimal.class, productA))
                .isEqualByComparingTo("1.24");
    }

    @Test
    void datetimeThreeKeepsMilliseconds() {
        var instant = LocalDateTime.of(2026, 11, 25, 12, 34, 56, 123_000_000);
        jdbc.update("UPDATE sys_user SET locked_until=? WHERE id=?", instant, customer);
        assertThat(jdbc.queryForObject("SELECT locked_until FROM sys_user WHERE id=?", LocalDateTime.class, customer)).isEqualTo(instant);
    }

    private Map<String, Long> counts() {
        var result = new LinkedHashMap<String, Long>();
        TABLES.forEach(table -> result.put(table, jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Long.class)));
        return result;
    }

    private BigInteger user(String username, String phone, String role) {
        return insert("INSERT INTO sys_user(username,password,nickname,phone,role) VALUES(?,?,?,?,?)", username, FIXTURE_HASH, "回滚测试", phone, role);
    }

    private BigInteger merchant(BigInteger userId) {
        return insert("INSERT INTO cake_merchant(user_id,name,phone,address) VALUES(?,?,?,?)", userId, "回滚测试商家", syntheticPhone(), "仅事务测试地址");
    }

    private BigInteger category(BigInteger merchantId, String name) {
        return insert("INSERT INTO cake_category(merchant_id,name) VALUES(?,?)", merchantId, name);
    }

    private BigInteger product(BigInteger merchantId, BigInteger categoryId, BigDecimal price, int stock) {
        return insert("INSERT INTO cake_product(merchant_id,category_id,name,spec,price,stock) VALUES(?,?,?,?,?,?)", merchantId, categoryId, "回滚测试蛋糕", "标准份", price, stock);
    }

    private BigInteger order(BigInteger userId, BigInteger merchantId, int status, int type) {
        var now = LocalDateTime.of(2026, 11, 25, 12, 0, 0, 123_000_000);
        return insert("INSERT INTO cake_order(merchant_id,merchant_name,order_no,user_id,request_id,request_hash,total_amount,status,order_type,receiver,phone,create_time,expire_time) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?)", merchantId, "历史店铺快照", UUID.randomUUID().toString().replace("-", ""), userId, UUID.randomUUID().toString(), "a".repeat(64), new BigDecimal("12.34"), status, type, "测试联系人", syntheticPhone(), now, now.plusMinutes(30));
    }

    private BigInteger item() {
        return insert("INSERT INTO cake_order_item(order_id,product_id,product_name,product_spec,price,quantity,subtotal) VALUES(?,?,?,?,?,?,?)", orderA, productA, "历史蛋糕快照", "历史规格", new BigDecimal("12.34"), 1, new BigDecimal("12.34"));
    }

    private BigInteger insert(String sql, Object... parameters) {
        var holder = new org.springframework.jdbc.support.GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            for (int i = 0; i < parameters.length; i++) {
                if (parameters[i] instanceof BigInteger id) statement.setBigDecimal(i + 1, new BigDecimal(id));
                else statement.setObject(i + 1, parameters[i]);
            }
            return statement;
        }, holder);
        return new BigInteger(holder.getKey().toString());
    }

    /** Connector/J setObject(BigInteger) selects signed BIGINT; decimal binding preserves UNSIGNED IDs. */
    private static class UnsignedJdbcTemplate extends JdbcTemplate {
        UnsignedJdbcTemplate(DataSource source) { super(source); }

        @Override
        protected PreparedStatementSetter newArgPreparedStatementSetter(Object[] arguments) {
            if (arguments == null) return super.newArgPreparedStatementSetter(null);
            Object[] exact = arguments.clone();
            for (int i = 0; i < exact.length; i++) {
                if (exact[i] instanceof BigInteger id) exact[i] = new BigDecimal(id);
            }
            return super.newArgPreparedStatementSetter(exact);
        }
    }

    private static String syntheticPhone() {
        return "139" + String.format("%08d", Math.floorMod(UUID.randomUUID().getLeastSignificantBits(), 100_000_000L));
    }

    private static void fails(int mysqlCode, org.assertj.core.api.ThrowableAssert.ThrowingCallable operation) {
        assertThatThrownBy(operation).satisfies(error -> {
            Throwable cause = error;
            while (!(cause instanceof java.sql.SQLException) && cause.getCause() != null) cause = cause.getCause();
            assertThat(cause).isInstanceOf(java.sql.SQLException.class);
            assertThat(((java.sql.SQLException) cause).getErrorCode()).as("actual MySQL constraint error").isEqualTo(mysqlCode);
        });
    }

    private void check(String constraint, String sql, Object... parameters) {
        assertThatThrownBy(() -> jdbc.update(sql, parameters)).satisfies(error -> {
            Throwable cause = error;
            while (!(cause instanceof java.sql.SQLException) && cause.getCause() != null) cause = cause.getCause();
            assertThat(cause).isInstanceOf(java.sql.SQLException.class);
            assertThat(((java.sql.SQLException) cause).getErrorCode()).isEqualTo(3819);
            assertThat(cause.getMessage()).contains(constraint);
        });
    }
}
