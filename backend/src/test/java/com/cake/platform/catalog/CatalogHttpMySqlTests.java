package com.cake.platform.catalog;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.lang.reflect.InvocationTargetException;
import java.security.SecureRandom;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Types;
import java.util.*;
import java.util.concurrent.*;
import com.cake.platform.mapper.CatalogMapper;
import com.cake.platform.security.AuthPrincipal;
import com.cake.platform.security.JwtService;
import com.cake.platform.service.catalog.MerchantCatalogService;
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
import org.springframework.test.context.*;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.PlatformTransactionManager;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

/**
 * Real MySQL, real security chain and transactions for API-02~05, 15~22, 29~31.
 * Fixtures are owned by this class and removed by exact shop/account ids, including their products,
 * categories and order references; rows outside those ids are never touched.
 */
@SpringBootTest
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
@ActiveProfiles("mysql-test")
@EnabledIfEnvironmentVariable(named = "CAKE_MYSQL_TEST_ENABLED", matches = "true")
class CatalogHttpMySqlTests {
    private static final List<String> TABLES = List.of("sys_user", "cake_merchant", "cake_category",
            "cake_product", "cake_order", "cake_order_item");
    private static final String PASSWORD = "Fixture!Pass42";
    private static final String HASH = new BCryptPasswordEncoder(10).encode(PASSWORD);
    private static final String SECRET = randomSecret();
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired JwtService jwt;
    @Autowired CatalogMapper mapper;
    @Autowired PlatformTransactionManager manager;
    private final List<BigInteger> ownedShops = new ArrayList<>();
    private final List<BigInteger> ownedUsers = new ArrayList<>();
    private Map<String, Long> before;

    @DynamicPropertySource static void isolated(DynamicPropertyRegistry properties) {
        String test = System.getenv("DB_TEST_NAME");
        if (test == null || !test.matches("[A-Za-z][A-Za-z0-9_]*_test")
                || test.equals(System.getenv().getOrDefault("DB_NAME", "cake_order_system")))
            throw new IllegalStateException("目录测试仅允许显式独立_test数据库");
        properties.add("cake.security.jwt-secret-base64", () -> SECRET);
    }

    @BeforeEach void setup() {
        assertThat(jdbc.queryForObject("SELECT DATABASE()", String.class)).isEqualTo(System.getenv("DB_TEST_NAME"));
        assertThat(jdbc.queryForObject("SELECT VERSION()", String.class)).startsWith("8.0");
        before = counts();
    }

    @AfterEach void cleanOwnedFixturesOnly() {
        try {
            for (BigInteger shop : ownedShops) {
                update("DELETE FROM cake_order_item WHERE order_id IN"
                        + " (SELECT id FROM cake_order WHERE merchant_id=?)", shop);
                update("DELETE FROM cake_order WHERE merchant_id=?", shop);
                update("DELETE FROM cake_product WHERE merchant_id=?", shop);
                update("DELETE FROM cake_category WHERE merchant_id=?", shop);
            }
            for (BigInteger shop : ownedShops) { update("DELETE FROM cake_merchant WHERE id=?", shop); }
            for (BigInteger user : ownedUsers) { update("DELETE FROM sys_user WHERE id=?", user); }
        } finally {
            if (before != null) {
                assertThat(counts()).as("all six tables preserved after owned-ID cleanup").isEqualTo(before);
            }
        }
    }

    // ---------- API-02 / 16: public shop catalogue ----------

    @Test void publicCatalogueOnlyShowsEnabledShopCategoryAndListedProducts() throws Exception {
        Merchant a = merchant("目录A店");
        Merchant b = merchant("目录B店");
        Category enabled = category(a.shop(), "生日蛋糕", 1, 1);
        Category second = category(a.shop(), "节日蛋糕", 2, 1);
        Category disabled = category(a.shop(), "旧分类", 3, 0);
        BigInteger listed = product(a.shop(), enabled, "草莓蛋糕", "6寸", "128.00", 3, 1, "/images/cake-01.svg");
        BigInteger soldOut = product(a.shop(), enabled, "售罄蛋糕", "8寸", "199.00", 0, 1, null);
        product(a.shop(), enabled, "下架蛋糕", "标准份", "99.00", 5, 0, null);
        product(a.shop(), disabled, "禁用分类商品", "标准份", "50.00", 5, 1, null);
        Category bCategory = category(b.shop(), "B店分类", 0, 1);
        product(b.shop(), bCategory, "B店蛋糕", "标准份", "66.00", 1, 1, null);
        Merchant stopped = merchant("目录停用店");
        Category stoppedCategory = category(stopped.shop(), "停用店分类", 0, 1);
        product(stopped.shop(), stoppedCategory, "停用店商品", "标准份", "10.00", 1, 1, null);
        update("UPDATE cake_merchant SET status=0 WHERE id=?", stopped.shop());

        JsonNode page = request(get("/api/products").param("merchantId", a.shop().toString())
                .param("size", "10"), null, 200, 200).path("data");
        assertThat(page.path("total").asLong()).isEqualTo(2);
        assertThat(page.path("page").asLong()).isEqualTo(1);
        assertThat(page.path("size").asInt()).isEqualTo(10);
        assertThat(page.path("records").size()).isEqualTo(2);
        assertThat(page.path("records").get(0).path("id").asText()).isEqualTo(soldOut.toString());
        assertThat(page.path("records").get(1).path("id").asText()).isEqualTo(listed.toString());
        assertPublicProduct(page.path("records").get(1), listed, a.shop(), enabled);

        JsonNode filtered = request(get("/api/products").param("merchantId", a.shop().toString())
                .param("categoryId", second.id().toString()), null, 200, 200).path("data");
        assertThat(filtered.path("records").isEmpty()).isTrue();
        assertThat(filtered.path("total").asLong()).isZero();
        // A category of another shop is never a valid filter for the selected shop.
        JsonNode foreign = request(get("/api/products").param("merchantId", a.shop().toString())
                .param("categoryId", bCategory.id().toString()), null, 200, 200).path("data");
        assertThat(foreign.path("records").isEmpty()).isTrue();

        JsonNode beyond = request(get("/api/products").param("merchantId", a.shop().toString())
                .param("page", "999").param("size", "100"), null, 200, 200).path("data");
        assertThat(beyond.path("records").isEmpty()).isTrue();
        assertThat(beyond.path("total").asLong()).isEqualTo(2);

        JsonNode categories = request(get("/api/categories").param("merchantId", a.shop().toString()),
                null, 200, 200).path("data");
        assertThat(categories.size()).isEqualTo(2);
        assertThat(categories.get(0).path("id").asText()).isEqualTo(enabled.id().toString());
        assertThat(categories.get(1).path("id").asText()).isEqualTo(second.id().toString());
        assertThat(categories.get(0).path("status").asInt()).isEqualTo(1);
        assertThat(fieldNames(categories.get(0))).containsExactlyInAnyOrder("id", "merchantId", "name", "sort", "status");

        request(get("/api/products").param("merchantId", stopped.shop().toString()), null, 404, 40404);
        request(get("/api/categories").param("merchantId", stopped.shop().toString()), null, 404, 40404);
        request(get("/api/products").param("merchantId", "0"), null, 400, 40001);
        var missing = request(get("/api/products"), null, 400, 40001);
        assertThat(missing.path("data").path("fieldErrors").path("merchantId").size()).isPositive();
    }

    @Test void publicCatalogueSearchIsLiteralAndShopBound() throws Exception {
        Merchant a = merchant("目录搜索店");
        Category category = category(a.shop(), "搜索分类", 0, 1);
        BigInteger literal = product(a.shop(), category, "特价%_!蛋糕", "标准份", "88.00", 2, 1, null);
        product(a.shop(), category, "普通蛋糕", "标准份", "88.00", 2, 1, null);

        JsonNode result = request(get("/api/products").param("merchantId", a.shop().toString())
                .param("keyword", "%_!"), null, 200, 200).path("data");
        assertThat(result.path("total").asLong()).isEqualTo(1);
        assertThat(result.path("records").get(0).path("id").asText()).isEqualTo(literal.toString());
        JsonNode noMatch = request(get("/api/products").param("merchantId", a.shop().toString())
                .param("keyword", "%"), null, 200, 200).path("data");
        assertThat(noMatch.path("total").asLong()).isEqualTo(1);
    }

    // ---------- API-15: public product detail ----------

    @Test void publicDetailHidesUnlistedDisabledCategoryAndUnavailableShop() throws Exception {
        Merchant a = merchant("目录详情店");
        Category enabled = category(a.shop(), "详情分类", 0, 1);
        Category disabled = category(a.shop(), "详情禁用分类", 1, 0);
        BigInteger visible = product(a.shop(), enabled, "详情蛋糕", "6寸", "128.00", 4, 1, "/images/cake-02.svg");
        BigInteger offShelf = product(a.shop(), enabled, "下架详情", "6寸", "10.00", 4, 0, null);
        BigInteger inDisabled = product(a.shop(), disabled, "禁用分类详情", "6寸", "10.00", 4, 1, null);
        Merchant stopped = merchant("目录详情停用店");
        Category stoppedCategory = category(stopped.shop(), "停用分类", 0, 1);
        BigInteger stoppedProduct = product(stopped.shop(), stoppedCategory, "停用商品", "标准份", "1.00", 1, 1, null);
        update("UPDATE cake_merchant SET status=0 WHERE id=?", stopped.shop());

        JsonNode detail = request(get("/api/products/" + visible), null, 200, 200).path("data");
        assertPublicProduct(detail, visible, a.shop(), enabled);
        assertThat(detail.path("description").asText()).isEqualTo("商品详情");
        assertThat(detail.path("stock").asInt()).isEqualTo(4);
        assertThat(detail.path("price").asText()).isEqualTo("128.00");
        assertThat(detail.has("createTime")).isFalse();
        assertThat(detail.has("updateTime")).isFalse();

        request(get("/api/products/" + offShelf), null, 404, 40401);
        request(get("/api/products/" + inDisabled), null, 404, 40401);
        request(get("/api/products/" + stoppedProduct), null, 404, 40404);
        request(get("/api/products/18446744073709551615"), null, 404, 40401);
        request(get("/api/products/0"), null, 400, 40001);
        request(get("/api/products/abc"), null, 400, 40001);
    }

    // ---------- API-17 / 18: merchant catalogue reads ----------

    @Test void merchantCatalogueIsOwnStoreOnlyAndIncludesOffShelfRows() throws Exception {
        Merchant a = merchant("目录经营A店");
        Merchant b = merchant("目录经营B店");
        Category aCategory = category(a.shop(), "本店分类", 0, 1);
        BigInteger ownListed = product(a.shop(), aCategory, "本店上架", "标准份", "10.00", 1, 1, null);
        BigInteger ownOffShelf = product(a.shop(), aCategory, "本店下架", "标准份", "20.00", 2, 0, null);
        Category bCategory = category(b.shop(), "他店分类", 0, 1);
        BigInteger foreign = product(b.shop(), bCategory, "他店商品", "标准份", "30.00", 3, 1, null);

        JsonNode page = request(merchant(a, get("/api/merchant/products")), null, 200, 200).path("data");
        assertThat(page.path("total").asLong()).isEqualTo(2);
        assertThat(page.path("records").size()).isEqualTo(2);
        assertThat(page.path("records").get(0).path("id").asText()).isEqualTo(ownOffShelf.toString());
        assertMerchantProduct(page.path("records").get(0), a.shop(), aCategory);
        assertThat(page.path("records").get(0).path("stock").asInt()).isEqualTo(2);
        assertThat(page.path("records").get(0).path("status").asInt()).isZero();

        JsonNode offShelfOnly = request(merchant(a, get("/api/merchant/products").param("status", "0")),
                null, 200, 200).path("data");
        assertThat(offShelfOnly.path("total").asLong()).isEqualTo(1);
        JsonNode otherCategory = request(merchant(a, get("/api/merchant/products")
                .param("categoryId", bCategory.id().toString())), null, 200, 200).path("data");
        assertThat(otherCategory.path("records").isEmpty()).isTrue();
        JsonNode ownCategory = request(merchant(a, get("/api/merchant/products")
                .param("categoryId", aCategory.id().toString())), null, 200, 200).path("data");
        assertThat(ownCategory.path("total").asLong()).isEqualTo(2);

        JsonNode detail = request(merchant(a, get("/api/merchant/products/" + ownListed)), null, 200, 200).path("data");
        assertMerchantProduct(detail, a.shop(), aCategory);
        assertThat(detail.path("createTime").asText()).matches(".*\\.\\d{3}\\+08:00");
        assertThat(detail.path("updateTime").asText()).matches(".*\\.\\d{3}\\+08:00");

        request(merchant(a, get("/api/merchant/products/" + foreign)), null, 404, 40401);
        request(merchant(a, get("/api/merchant/products").param("merchantId", b.shop().toString())),
                null, 403, 40302);
        request(get("/api/merchant/products"), null, 401, 40102);
    }

    // ---------- API-03: create ----------

    @Test void creatingAProductAlwaysStartsOffShelfInTheLoginShop() throws Exception {
        Merchant a = merchant("目录新增A店");
        Merchant b = merchant("目录新增B店");
        Category enabled = category(a.shop(), "新增分类", 0, 1);
        Category disabled = category(a.shop(), "新增禁用分类", 1, 0);
        Category foreign = category(b.shop(), "新增他店分类", 0, 1);
        long productsBefore = countProducts(a.shop());

        JsonNode created = request(merchant(a, post("/api/merchant/products")),
                creation(enabled.id().toString(), "  新蛋糕  ", "6寸", "128.50", 4, "/images/cake-03.svg", "  新品  "),
                201, 200).path("data");
        BigInteger id = new BigInteger(created.path("id").asText());
        assertMerchantProduct(created, a.shop(), enabled);
        assertThat(created.path("name").asText()).isEqualTo("新蛋糕");
        assertThat(created.path("status").asInt()).isZero();
        assertThat(created.path("stock").asInt()).isEqualTo(4);
        assertThat(created.path("price").asText()).isEqualTo("128.50");
        assertThat(row("cake_product", id).get("status")).isEqualTo(0);

        JsonNode secondSpec = request(merchant(a, post("/api/merchant/products")),
                creation(enabled.id().toString(), "新蛋糕", "8寸", "168.00", 0, null, null), 201, 200).path("data");
        assertThat(secondSpec.path("id").asText()).isNotEqualTo(id.toString());
        assertThat(secondSpec.path("spec").asText()).isEqualTo("8寸");

        JsonNode inDisabled = request(merchant(a, post("/api/merchant/products")),
                creation(disabled.id().toString(), "禁用分类新品", "标准份", "10.00", 0, null, null), 201, 200)
                .path("data");
        assertThat(inDisabled.path("status").asInt()).isZero();

        long productsAfter = countProducts(a.shop());
        assertThat(productsAfter).isEqualTo(productsBefore + 3);

        request(merchant(a, post("/api/merchant/products")),
                creation(foreign.id().toString(), "跨店分类", "标准份", "10.00", 0, null, null), 404, 40403);
        var withStatus = creation(enabled.id().toString(), "带状态", "标准份", "10.00", 0, null, null);
        withStatus.put("status", 1);
        request(merchant(a, post("/api/merchant/products")), withStatus, 403, 40302);
        var withMerchant = creation(enabled.id().toString(), "带商家", "标准份", "10.00", 0, null, null);
        withMerchant.put("merchantId", a.shop().toString());
        request(merchant(a, post("/api/merchant/products")), withMerchant, 403, 40302);

        var badPrice = creation(enabled.id().toString(), "错价", "标准份", "10.001", 0, null, null);
        var priceError = request(merchant(a, post("/api/merchant/products")), badPrice, 400, 40001);
        assertThat(priceError.path("data").path("fieldErrors").path("price").size()).isPositive();
        var badStock = creation(enabled.id().toString(), "错库存", "标准份", "10.00", -1, null, null);
        var stockError = request(merchant(a, post("/api/merchant/products")), badStock, 400, 40001);
        assertThat(stockError.path("data").path("fieldErrors").path("stock").size()).isPositive();
        assertThat(countProducts(a.shop())).isEqualTo(productsAfter);
    }

    // ---------- API-04: edit ----------

    @Test void editingKeepsStockStatusAndShopAndValidatesCategory() throws Exception {
        Merchant a = merchant("目录编辑A店");
        Merchant b = merchant("目录编辑B店");
        Category first = category(a.shop(), "编辑分类一", 0, 1);
        Category second = category(a.shop(), "编辑分类二", 1, 1);
        Category disabled = category(a.shop(), "编辑禁用分类", 2, 0);
        Category foreign = category(b.shop(), "编辑他店分类", 0, 1);
        BigInteger id = product(a.shop(), first, "编辑前", "6寸", "10.00", 9, 1, null);
        var beforeRow = row("cake_product", id);

        JsonNode updated = request(merchant(a, put("/api/merchant/products/" + id)),
                edit(second.id().toString(), "  编辑后  ", "8寸", "128.50", "/images/cake-04.svg", "  新描述  "),
                200, 200).path("data");
        assertMerchantProduct(updated, a.shop(), second);
        assertThat(updated.path("name").asText()).isEqualTo("编辑后");
        assertThat(updated.path("spec").asText()).isEqualTo("8寸");
        assertThat(updated.path("price").asText()).isEqualTo("128.50");
        assertThat(updated.path("stock").asInt()).isEqualTo(9);
        assertThat(updated.path("status").asInt()).isEqualTo(1);
        var afterRow = row("cake_product", id);
        assertThat(afterRow.get("stock")).isEqualTo(beforeRow.get("stock"));
        assertThat(afterRow.get("status")).isEqualTo(beforeRow.get("status"));
        assertThat(afterRow.get("merchant_id")).isEqualTo(beforeRow.get("merchant_id"));
        assertThat(afterRow.get("create_time")).isEqualTo(beforeRow.get("create_time"));

        var withStock = edit(first.id().toString(), "改库存", "6寸", "10.00", null, null);
        withStock.put("stock", 3);
        request(merchant(a, put("/api/merchant/products/" + id)), withStock, 403, 40302);
        var withStatus = edit(first.id().toString(), "改状态", "6寸", "10.00", null, null);
        withStatus.put("status", 0);
        request(merchant(a, put("/api/merchant/products/" + id)), withStatus, 403, 40302);

        request(merchant(a, put("/api/merchant/products/" + product(b.shop(), foreign, "他店商品", "标准份", "1.00", 1, 0, null))),
                edit(first.id().toString(), "越权", "标准份", "10.00", null, null), 404, 40401);
        request(merchant(a, put("/api/merchant/products/" + id)),
                edit(foreign.id().toString(), "跨店分类", "6寸", "10.00", null, null), 404, 40403);
        request(merchant(a, put("/api/merchant/products/" + id)),
                edit(disabled.id().toString(), "上架进禁用", "6寸", "10.00", null, null), 409, 40913);

        var invalid = edit(first.id().toString(), "错价", "6寸", "1.234", null, null);
        request(merchant(a, put("/api/merchant/products/" + id)), invalid, 400, 40001);
        assertThat(row("cake_product", id).get("name")).isEqualTo("编辑后");
        assertThat(row("cake_product", id).get("price")).isNotNull();
    }

    // ---------- API-05 / 22: delete protections ----------

    @Test void deletionRequiresNoStoreReferencesAndNoHistory() throws Exception {
        Merchant a = merchant("目录删除A店");
        Merchant b = merchant("目录删除B店");
        Category category = category(a.shop(), "删除分类", 0, 1);
        BigInteger deletable = product(a.shop(), category, "可删商品", "标准份", "10.00", 1, 0, null);

        JsonNode deleted = request(merchant(a, delete("/api/merchant/products/" + deletable)), null, 200, 200)
                .path("data");
        assertThat(deleted.path("id").asText()).isEqualTo(deletable.toString());
        assertThat(deleted.path("deleted").asBoolean()).isTrue();
        request(merchant(a, delete("/api/merchant/products/" + deletable)), null, 404, 40401);

        BigInteger referenced = product(a.shop(), category, "有历史商品", "标准份", "10.00", 1, 0, null);
        Account customer = account("CUSTOMER");
        BigInteger order = order(a.shop(), customer, referenced);
        request(merchant(a, delete("/api/merchant/products/" + referenced)), null, 409, 40910);
        assertThat(row("cake_product", referenced)).isNotNull();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM cake_order_item WHERE order_id=?", Long.class,
                decimal(order))).isEqualTo(1);

        request(merchant(a, delete("/api/merchant/categories/" + category.id())), null, 409, 40909);
        Category empty = category(a.shop(), "空分类", 1, 1);
        JsonNode categoryDeleted = request(merchant(a, delete("/api/merchant/categories/" + empty.id())),
                null, 200, 200).path("data");
        assertThat(categoryDeleted.path("deleted").asBoolean()).isTrue();
        request(merchant(a, delete("/api/merchant/categories/" + empty.id())), null, 404, 40403);
        Category foreign = category(b.shop(), "他店空分类", 0, 1);
        request(merchant(a, delete("/api/merchant/categories/" + foreign.id())), null, 404, 40403);
        request(merchant(a, delete("/api/merchant/products/18446744073709551615")), null, 404, 40401);
    }

    // ---------- API-19~22 / 31: category maintenance ----------

    @Test void categoryMaintenanceEnforcesStoreScopeAndUniqueName() throws Exception {
        Merchant a = merchant("目录分类A店");
        Merchant b = merchant("目录分类B店");

        JsonNode created = request(merchant(a, post("/api/merchant/categories")),
                Map.of("name", "  新品分类  "), 201, 200).path("data");
        BigInteger id = new BigInteger(created.path("id").asText());
        assertMerchantCategory(created, a.shop());
        assertThat(created.path("name").asText()).isEqualTo("新品分类");
        assertThat(created.path("sort").asInt()).isZero();
        assertThat(created.path("status").asInt()).isEqualTo(1);

        request(merchant(a, post("/api/merchant/categories")), Map.of("name", "新品分类"), 409, 40901);
        JsonNode sameNameOtherStore = request(merchant(b, post("/api/merchant/categories")),
                Map.of("name", "新品分类"), 201, 200).path("data");
        assertThat(sameNameOtherStore.path("merchantId").asText()).isEqualTo(b.shop().toString());
        BigInteger foreignCategory = new BigInteger(sameNameOtherStore.path("id").asText());
        request(merchant(a, post("/api/merchant/categories")), Map.of("name", "带状态", "status", 0), 403, 40302);

        JsonNode renamed = request(merchant(a, put("/api/merchant/categories/" + id)),
                Map.of("name", "改名分类", "sort", 7), 200, 200).path("data");
        assertThat(renamed.path("name").asText()).isEqualTo("改名分类");
        assertThat(renamed.path("sort").asInt()).isEqualTo(7);
        request(merchant(a, put("/api/merchant/categories/" + id)),
                Map.of("name", "改名分类", "sort", -1), 400, 40001);
        request(merchant(a, put("/api/merchant/categories/" + id)),
                Map.of("name", "改名分类"), 400, 40001);
        BigInteger other = new BigInteger(request(merchant(a, post("/api/merchant/categories")),
                Map.of("name", "另一个分类"), 201, 200).path("data").path("id").asText());
        request(merchant(a, put("/api/merchant/categories/" + id)),
                Map.of("name", "另一个分类", "sort", 1), 409, 40901);
        request(merchant(a, put("/api/merchant/categories/" + foreignCategory)),
                Map.of("name", "越权改名", "sort", 1), 404, 40403);

        JsonNode all = request(merchant(a, get("/api/merchant/categories")), null, 200, 200).path("data");
        assertThat(all.size()).isEqualTo(2);
        JsonNode filtered = request(merchant(a, get("/api/merchant/categories").param("status", "1")),
                null, 200, 200).path("data");
        assertThat(filtered.size()).isEqualTo(2);
        request(merchant(a, get("/api/merchant/categories").param("status", "2")), null, 400, 40001);
        request(merchant(a, get("/api/merchant/categories").param("merchantId", b.shop().toString())),
                null, 403, 40302);

        // A category with a listed product cannot be disabled; disabling is idempotent once allowed.
        BigInteger listed = product(a.shop(), new Category(id), "禁用保护商品", "标准份", "10.00", 1, 1, null);
        request(merchant(a, patch("/api/merchant/categories/" + id + "/status")),
                Map.of("status", 0), 409, 40909);
        request(merchant(a, patch("/api/merchant/products/" + listed + "/status")),
                Map.of("status", 0), 200, 200);
        JsonNode disabled = request(merchant(a, patch("/api/merchant/categories/" + id + "/status")),
                Map.of("status", 0), 200, 200).path("data");
        assertThat(disabled.path("status").asInt()).isZero();
        var disabledRow = row("cake_category", id);
        JsonNode repeated = request(merchant(a, patch("/api/merchant/categories/" + id + "/status")),
                Map.of("status", 0), 200, 200).path("data");
        assertThat(repeated.path("status").asInt()).isZero();
        assertThat(row("cake_category", id)).isEqualTo(disabledRow);
        request(merchant(a, patch("/api/merchant/categories/" + id + "/status")),
                Map.of("status", 2), 400, 40001);
        request(merchant(a, patch("/api/merchant/categories/18446744073709551615/status")),
                Map.of("status", 1), 404, 40403);
        request(merchant(a, patch("/api/merchant/categories/" + id + "/status")),
                Map.of("status", 1), 200, 200);
    }

    // ---------- API-29: stock ----------

    @Test void stockChangeUsesExpectedValueAndRejectsStaleOrNegativeResults() throws Exception {
        Merchant a = merchant("目录库存A店");
        Merchant b = merchant("目录库存B店");
        Category category = category(a.shop(), "库存分类", 0, 1);
        BigInteger id = product(a.shop(), category, "库存商品", "标准份", "10.00", 10, 0, null);

        JsonNode changed = request(merchant(a, patch("/api/merchant/products/" + id + "/stock")),
                Map.of("delta", 5, "expectedStock", 10), 200, 200).path("data");
        assertThat(changed.path("id").asText()).isEqualTo(id.toString());
        assertThat(changed.path("stock").asInt()).isEqualTo(15);
        assertThat(changed.path("updateTime").asText()).matches(".*\\.\\d{3}\\+08:00");
        assertThat(((Number) row("cake_product", id).get("stock")).intValue()).isEqualTo(15);

        var negative = request(merchant(a, patch("/api/merchant/products/" + id + "/stock")),
                Map.of("delta", -20, "expectedStock", 15), 400, 40001);
        assertThat(negative.path("data").path("fieldErrors").path("delta").size()).isPositive();
        assertThat(((Number) row("cake_product", id).get("stock")).intValue()).isEqualTo(15);

        var stale = request(merchant(a, patch("/api/merchant/products/" + id + "/stock")),
                Map.of("delta", 1, "expectedStock", 14), 409, 40911);
        assertThat(stale.path("msg").asText()).isEqualTo("库存已变化，请刷新后重试");
        assertThat(((Number) row("cake_product", id).get("stock")).intValue()).isEqualTo(15);

        request(merchant(a, patch("/api/merchant/products/" + id + "/stock")),
                Map.of("delta", 0, "expectedStock", 15), 400, 40001);
        request(merchant(a, patch("/api/merchant/products/" + id + "/stock")),
                Map.of("delta", 1, "expectedStock", 15), 200, 200);
        request(merchant(a, patch("/api/merchant/products/" + id + "/stock")),
                Map.of("delta", 1), 400, 40001);
        request(merchant(a, patch("/api/merchant/products/18446744073709551615/stock")),
                Map.of("delta", 1, "expectedStock", 0), 404, 40401);
        BigInteger foreign = product(b.shop(), category(b.shop(), "他店库存分类", 0, 1), "他店库存", "标准份", "1.00", 1, 0, null);
        request(merchant(a, patch("/api/merchant/products/" + foreign + "/stock")),
                Map.of("delta", 1, "expectedStock", 1), 404, 40401);
    }

    @Test void concurrentStockChangesWithTheSameExpectedValueOnlyOneApplies() throws Exception {
        Merchant a = merchant("目录并发库存店");
        Category category = category(a.shop(), "并发库存分类", 0, 1);
        BigInteger id = product(a.shop(), category, "并发库存商品", "标准份", "10.00", 10, 0, null);
        String path = "/api/merchant/products/" + id + "/stock";
        List<Integer> codes = concurrently(List.of(
                () -> response(merchant(a, patch(path)), Map.of("delta", 5, "expectedStock", 10)).path("code").asInt(),
                () -> response(merchant(a, patch(path)), Map.of("delta", 5, "expectedStock", 10)).path("code").asInt()));
        assertThat(codes).containsExactlyInAnyOrder(200, 40911);
        assertThat(((Number) row("cake_product", id).get("stock")).intValue()).isEqualTo(15);
    }

    // ---------- API-30 / 31: listing and category state ----------

    @Test void listingRequiresAnEnabledCategoryAndIsIdempotent() throws Exception {
        Merchant a = merchant("目录上下架店");
        Category category = category(a.shop(), "上下架分类", 0, 1);
        BigInteger id = product(a.shop(), category, "上下架商品", "标准份", "10.00", 0, 0, null);

        JsonNode listed = request(merchant(a, patch("/api/merchant/products/" + id + "/status")),
                Map.of("status", 1), 200, 200).path("data");
        assertThat(listed.path("status").asInt()).isEqualTo(1);
        var listedRow = row("cake_product", id);
        JsonNode repeated = request(merchant(a, patch("/api/merchant/products/" + id + "/status")),
                Map.of("status", 1), 200, 200).path("data");
        assertThat(repeated.path("status").asInt()).isEqualTo(1);
        assertThat(row("cake_product", id)).isEqualTo(listedRow);

        request(merchant(a, patch("/api/merchant/products/" + id + "/status")),
                Map.of("status", 0), 200, 200);
        request(merchant(a, patch("/api/merchant/categories/" + category.id() + "/status")),
                Map.of("status", 0), 200, 200);
        request(merchant(a, patch("/api/merchant/products/" + id + "/status")),
                Map.of("status", 1), 409, 40913);
        assertThat(((Number) row("cake_product", id).get("status")).intValue()).isZero();
        request(merchant(a, patch("/api/merchant/products/" + id + "/status")),
                Map.of("status", 2), 400, 40001);
        request(merchant(a, patch("/api/merchant/products/18446744073709551615/status")),
                Map.of("status", 1), 404, 40401);
    }

    @Test void concurrentCategoryDisableAndListingNeverBreakTheInvariant() throws Exception {
        Merchant a = merchant("目录并发状态店");
        Category category = category(a.shop(), "并发状态分类", 0, 1);
        BigInteger id = product(a.shop(), category, "并发状态商品", "标准份", "10.00", 1, 0, null);
        List<JsonNode> results = concurrently(List.of(
                () -> response(merchant(a, patch("/api/merchant/categories/" + category.id() + "/status")),
                        Map.of("status", 0)),
                () -> response(merchant(a, patch("/api/merchant/products/" + id + "/status")),
                        Map.of("status", 1))));
        int categoryStatus = ((Number) row("cake_category", category.id()).get("status")).intValue();
        int productStatus = ((Number) row("cake_product", id).get("status")).intValue();
        assertThat(results.get(0).path("code").asInt()).isIn(200, 40909);
        assertThat(results.get(1).path("code").asInt()).isIn(200, 40913);
        assertThat(categoryStatus == 1 || productStatus == 0)
                .as("a listed product never belongs to a disabled category").isTrue();
    }

    // ---------- roles, transactions ----------

    @ParameterizedTest @ValueSource(strings = {"ADMIN", "CUSTOMER"})
    void allTwelveMerchantEndpointsRejectOtherRolesBeforePayloadParsing(String role) throws Exception {
        Merchant a = merchant("目录权限店");
        Category category = category(a.shop(), "权限分类", 0, 1);
        BigInteger product = product(a.shop(), category, "权限商品", "标准份", "10.00", 1, 0, null);
        String token = token(account(role));
        for (var builder : merchantEndpoints(product, category.id())) {
            request(bearer(builder, token), Map.of(), 403, 40302);
        }
    }

    @Test void allTwelveMerchantEndpointsRequireAuthentication() throws Exception {
        Merchant a = merchant("目录匿名店");
        Category category = category(a.shop(), "匿名分类", 0, 1);
        BigInteger product = product(a.shop(), category, "匿名商品", "标准份", "10.00", 1, 0, null);
        for (var builder : merchantEndpoints(product, category.id())) request(builder, Map.of(), 401, 40102);
    }

    @Test void publicEndpointsStayAnonymousAndStillValidateTheirQuery() throws Exception {
        request(get("/api/products"), null, 400, 40001);
        request(get("/api/products/18446744073709551615"), null, 404, 40401);
        request(get("/api/categories"), null, 400, 40001);
    }

    @Test void writeTransactionRechecksCurrentMerchantRoleVersionAndStatus() {
        Merchant a = merchant("目录复核店");
        Category category = category(a.shop(), "复核分类", 0, 1);
        var service = new MerchantCatalogService(mapper, manager);
        var input = new com.cake.platform.dto.CatalogRequests.ProductCreation(category.id(), "复核商品", "标准份",
                new BigDecimal("10.00"), 0, null, null);
        var principal = new AuthPrincipal(a.userId(), "MERCHANT", 0, a.shop());

        update("UPDATE sys_user SET token_version=1 WHERE id=?", a.userId());
        assertThatThrownBy(() -> service.create(principal, input))
                .isInstanceOfSatisfying(com.cake.platform.security.AuthFailure.class,
                        error -> assertThat(error.errorCode().code()).isEqualTo(40102));
        update("UPDATE sys_user SET token_version=0,role='CUSTOMER' WHERE id=?", a.userId());
        assertThatThrownBy(() -> service.create(principal, input))
                .isInstanceOfSatisfying(com.cake.platform.security.AuthFailure.class,
                        error -> assertThat(error.errorCode().code()).isEqualTo(40302));
        update("UPDATE sys_user SET role='MERCHANT',status=0,token_version=1 WHERE id=?", a.userId());
        assertThatThrownBy(() -> service.create(principal, input))
                .isInstanceOfSatisfying(com.cake.platform.security.AuthFailure.class,
                        error -> assertThat(error.errorCode().code()).isEqualTo(40301));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM cake_product WHERE merchant_id=?", Long.class,
                decimal(a.shop()))).isZero();
    }

    @Test void failedInsertOrStockUpdateRollsBackTheWholeTransaction() throws Exception {
        Merchant a = merchant("目录回滚店");
        Category category = category(a.shop(), "回滚分类", 0, 1);
        BigInteger product = product(a.shop(), category, "回滚商品", "标准份", "10.00", 10, 0, null);
        long productsBefore = countProducts(a.shop());

        var createInput = new com.cake.platform.dto.CatalogRequests.ProductCreation(category.id(), "回滚新品",
                "标准份", new BigDecimal("10.00"), 0, null, null);
        assertThatThrownBy(() -> faulting("insertProduct").create(principal(a), createInput))
                .isInstanceOf(IllegalStateException.class);
        assertThat(countProducts(a.shop())).isEqualTo(productsBefore);

        var stockInput = new com.cake.platform.dto.CatalogRequests.StockAdjustment(5, 10);
        assertThatThrownBy(() -> faulting("updateStock").stock(principal(a), product, stockInput))
                .isInstanceOf(IllegalStateException.class);
        assertThat(((Number) row("cake_product", product).get("stock")).intValue()).isEqualTo(10);
    }

    @Test void maximumUnsignedIdsUseBusinessNotFoundAndOversizedIdsUseFieldErrors() throws Exception {
        Merchant a = merchant("目录大ID店");
        Category category = category(a.shop(), "大ID分类", 0, 1);
        String max = "18446744073709551615";
        request(merchant(a, get("/api/merchant/products/" + max)), null, 404, 40401);
        request(merchant(a, put("/api/merchant/products/" + max)),
                edit(category.id().toString(), "越界", "标准份", "10.00", null, null), 404, 40401);
        request(merchant(a, patch("/api/merchant/products/" + max + "/status")),
                Map.of("status", 0), 404, 40401);
        request(merchant(a, put("/api/merchant/categories/" + max)),
                Map.of("name", "越界", "sort", 0), 404, 40403);
        request(merchant(a, get("/api/merchant/products/18446744073709551616")), null, 400, 40001);
    }

    // ---------- helpers ----------

    private Merchant merchant(String name) {
        String phone = absentPhone();
        BigInteger userId = insert("sys_user", "INSERT INTO sys_user(username,password,nickname,phone,role)"
                + " VALUES(?,?,?,?, 'MERCHANT')", phone, HASH, "目录测试商家", phone);
        ownedUsers.add(userId);
        BigInteger shop = insert("cake_merchant", "INSERT INTO cake_merchant(user_id,name,phone,address)"
                + " VALUES(?,?,?,?)", userId, name, absentPhone(), "目录测试地址");
        ownedShops.add(shop);
        return new Merchant(userId, shop);
    }

    private Account account(String role) {
        String phone = absentPhone();
        BigInteger id = insert("sys_user", "INSERT INTO sys_user(username,password,nickname,phone,role)"
                + " VALUES(?,?,?,?,?)", phone, HASH, "目录测试账号", phone, role);
        ownedUsers.add(id);
        return new Account(id, phone);
    }

    private Category category(BigInteger shop, String name, int sort, int status) {
        BigInteger id = insert("cake_category", "INSERT INTO cake_category(merchant_id,name,sort,status)"
                + " VALUES(?,?,?,?)", shop, name, sort, status);
        return new Category(id);
    }

    private BigInteger product(BigInteger shop, Category category, String name, String spec, String price,
                               int stock, int status, String image) {
        return insert("cake_product", "INSERT INTO cake_product(merchant_id,category_id,name,spec,price,stock,"
                + "image,description,status) VALUES(?,?,?,?,?,?,?,'商品详情',?)",
                shop, category.id(), name, spec, new BigDecimal(price), stock, image, status);
    }

    private BigInteger order(BigInteger shop, Account customer, BigInteger product) {
        BigInteger order = insert("cake_order", "INSERT INTO cake_order(merchant_id,merchant_name,order_no,user_id,"
                + "request_id,request_hash,total_amount,status,order_type,receiver,phone,expire_time)"
                + " VALUES(?,?,?,?,?,?,'10.00',0,2,?,?,DATE_ADD(CURRENT_TIMESTAMP(3),INTERVAL 30 MINUTE))",
                shop, "目录快照店", UUID.randomUUID().toString().replace("-", ""), customer.id(),
                UUID.randomUUID().toString(), "0".repeat(64), "目录联系人", customer.phone());
        insert("cake_order_item", "INSERT INTO cake_order_item(order_id,product_id,product_name,product_spec,"
                + "price,quantity,subtotal) VALUES(?,?,?,?,'10.00',1,'10.00')",
                order, product, "目录商品快照", "标准份快照");
        return order;
    }

    private Map<String, Object> creation(String categoryId, String name, String spec, String price, int stock,
                                         String image, String description) {
        var body = new LinkedHashMap<String, Object>();
        body.put("categoryId", categoryId);
        body.put("name", name);
        body.put("spec", spec);
        body.put("price", price);
        body.put("stock", stock);
        body.put("image", image);
        body.put("description", description);
        return body;
    }

    private Map<String, Object> edit(String categoryId, String name, String spec, String price, String image,
                                     String description) {
        var body = new LinkedHashMap<String, Object>();
        body.put("categoryId", categoryId);
        body.put("name", name);
        body.put("spec", spec);
        body.put("price", price);
        body.put("image", image);
        body.put("description", description);
        return body;
    }

    private List<MockHttpServletRequestBuilder> merchantEndpoints(BigInteger product, BigInteger category) {
        return List.of(
                get("/api/merchant/products"),
                post("/api/merchant/products"),
                get("/api/merchant/products/" + product),
                put("/api/merchant/products/" + product),
                delete("/api/merchant/products/" + product),
                patch("/api/merchant/products/" + product + "/stock"),
                patch("/api/merchant/products/" + product + "/status"),
                get("/api/merchant/categories"),
                post("/api/merchant/categories"),
                put("/api/merchant/categories/" + category),
                delete("/api/merchant/categories/" + category),
                patch("/api/merchant/categories/" + category + "/status"));
    }

    private MerchantCatalogService faulting(String methodName) {
        CatalogMapper fault = (CatalogMapper) java.lang.reflect.Proxy.newProxyInstance(
                CatalogMapper.class.getClassLoader(), new Class<?>[]{CatalogMapper.class},
                (proxy, method, args) -> {
                    try {
                        Object result = method.invoke(mapper, args);
                        if (method.getName().equals(methodName)) {
                            throw new IllegalStateException("Injected phase-5 rollback failure");
                        }
                        return result;
                    } catch (InvocationTargetException error) {
                        throw error.getCause();
                    }
                });
        return new MerchantCatalogService(fault, manager);
    }

    private JsonNode request(MockHttpServletRequestBuilder builder, Object payload, int status, int code)
            throws Exception {
        var result = perform(builder, payload);
        assertThat(result.getResponse().getStatus()).as("HTTP").isEqualTo(status);
        JsonNode body = json.readTree(result.getResponse().getContentAsByteArray());
        assertThat(body.path("code").asInt()).as("business code").isEqualTo(code);
        assertThat(body.size()).isEqualTo(3);
        if (code == 40001 && !body.path("data").isNull()) {
            var fields = body.path("data").path("fieldErrors");
            assertThat(body.path("data").size()).isEqualTo(1);
            assertThat(fields.isObject() && !fields.isEmpty()).isTrue();
            fields.fields().forEachRemaining(entry -> {
                assertThat(entry.getKey()).matches("[a-z][A-Za-z0-9]*");
                assertThat(entry.getValue().isArray() && !entry.getValue().isEmpty()).isTrue();
            });
        }
        return body;
    }

    private JsonNode response(MockHttpServletRequestBuilder builder, Object payload) throws Exception {
        return json.readTree(perform(builder, payload).getResponse().getContentAsByteArray());
    }

    private org.springframework.test.web.servlet.MvcResult perform(MockHttpServletRequestBuilder builder,
                                                                  Object payload) throws Exception {
        if (payload != null) { builder.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(payload)); }
        return mvc.perform(builder).andReturn();
    }

    private String token(Account account) { return jwt.issue(principal(account)).token(); }

    private AuthPrincipal principal(Merchant merchant) {
        return new AuthPrincipal(merchant.userId(), "MERCHANT", 0, merchant.shop());
    }

    private AuthPrincipal principal(Account account) {
        String role = jdbc.queryForObject("SELECT role FROM sys_user WHERE id=?", String.class, decimal(account.id()));
        return new AuthPrincipal(account.id(), role, 0, null);
    }

    private MockHttpServletRequestBuilder merchant(Merchant merchant, MockHttpServletRequestBuilder builder) {
        return bearer(builder, jwt.issue(principal(merchant)).token());
    }

    private MockHttpServletRequestBuilder bearer(MockHttpServletRequestBuilder builder, String token) {
        return builder.header("Authorization", "Bearer " + token);
    }

    private BigInteger insert(String table, String sql, Object... values) {
        var key = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            for (int i = 0; i < values.length; i++) {
                if (values[i] instanceof BigInteger integer) {
                    statement.setBigDecimal(i + 1, new BigDecimal(integer));
                } else if (values[i] instanceof BigDecimal decimal) {
                    statement.setBigDecimal(i + 1, decimal);
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

    private Map<String, Object> row(String table, BigInteger id) {
        return jdbc.queryForMap("SELECT * FROM " + table + " WHERE id=?", decimal(id));
    }

    private int update(String sql, Object... values) {
        return jdbc.update(sql, Arrays.stream(values).map(CatalogHttpMySqlTests::decimal).toArray());
    }

    private static Object decimal(Object value) {
        return value instanceof BigInteger integer ? new SqlParameterValue(Types.DECIMAL, new BigDecimal(integer)) : value;
    }

    private long countProducts(BigInteger shop) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM cake_product WHERE merchant_id=?", Long.class, decimal(shop));
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

    private void assertPublicProduct(JsonNode node, BigInteger id, BigInteger shop, Category category) {
        assertThat(fieldNames(node)).containsExactlyInAnyOrder("id", "merchantId", "merchantName", "categoryId",
                "categoryName", "name", "spec", "price", "stock", "image", "description", "status");
        assertThat(node.path("id").asText()).isEqualTo(id.toString());
        assertThat(node.path("merchantId").asText()).isEqualTo(shop.toString());
        assertThat(node.path("categoryId").asText()).isEqualTo(category.id().toString());
        assertThat(node.path("price").isTextual()).isTrue();
        assertThat(node.path("price").asText()).matches("[0-9]+\\.[0-9]{2}");
        assertThat(node.path("status").asInt()).isEqualTo(1);
    }

    private void assertMerchantProduct(JsonNode node, BigInteger shop, Category category) {
        assertThat(fieldNames(node)).containsExactlyInAnyOrder("id", "merchantId", "merchantName", "categoryId",
                "categoryName", "name", "spec", "price", "stock", "image", "description", "status",
                "createTime", "updateTime");
        assertThat(node.path("merchantId").asText()).isEqualTo(shop.toString());
        assertThat(node.path("categoryId").asText()).isEqualTo(category.id().toString());
        assertThat(node.path("createTime").asText()).matches(".*\\.\\d{3}\\+08:00");
        assertThat(node.path("price").asText()).matches("[0-9]+\\.[0-9]{2}");
    }

    private void assertMerchantCategory(JsonNode node, BigInteger shop) {
        assertThat(fieldNames(node)).containsExactlyInAnyOrder("id", "merchantId", "name", "sort", "status",
                "createTime", "updateTime");
        assertThat(node.path("merchantId").asText()).isEqualTo(shop.toString());
        assertThat(node.path("createTime").asText()).matches(".*\\.\\d{3}\\+08:00");
    }

    private static Set<String> fieldNames(JsonNode node) {
        Set<String> names = new HashSet<>();
        node.fieldNames().forEachRemaining(names::add);
        return names;
    }

    private static String randomSecret() {
        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        return Base64.getEncoder().encodeToString(bytes);
    }

    private record Merchant(BigInteger userId, BigInteger shop) { }
    private record Account(BigInteger id, String phone) { }
    private record Category(BigInteger id) { }

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
            List<T> result = new ArrayList<>();
            for (var future : futures) result.add(future.get(30, TimeUnit.SECONDS));
            return result;
        } finally {
            start.countDown();
            pool.shutdownNow();
            assertThat(pool.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        }
    }
}
