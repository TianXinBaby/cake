package com.cake.platform.catalog;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.LinkedHashMap;
import java.util.Map;
import com.cake.platform.dto.CatalogRequests;
import com.cake.platform.exception.BusinessException;
import com.cake.platform.exception.FieldValidationException;
import com.cake.platform.security.AuthFailure;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.*;

/** Request contract for API-02~05, 15~22 and 29~31 without a database. */
class CatalogRequestsTests {
    private final ObjectMapper json = new ObjectMapper();

    private static Map<String, Object> product() {
        var body = new LinkedHashMap<String, Object>();
        body.put("categoryId", "12");
        body.put("name", "  草莓奶油蛋糕  ");
        body.put("spec", "  6寸  ");
        body.put("price", "128.50");
        body.put("image", "/images/cake-01.svg");
        body.put("description", "  当日现做  ");
        return body;
    }

    private JsonNode node(Object value) { return json.valueToTree(value); }

    @Test void creationTrimsTextAndKeepsStockExplicitOrZero() {
        var body = product();
        body.put("stock", 7);
        var input = CatalogRequests.productCreation(node(body));
        assertThat(input.categoryId()).isEqualTo(BigInteger.valueOf(12));
        assertThat(input.name()).isEqualTo("草莓奶油蛋糕");
        assertThat(input.spec()).isEqualTo("6寸");
        assertThat(input.price()).isEqualByComparingTo("128.50");
        assertThat(input.stock()).isEqualTo(7);
        assertThat(input.image()).isEqualTo("/images/cake-01.svg");
        assertThat(input.description()).isEqualTo("当日现做");

        var defaults = CatalogRequests.productCreation(node(product()));
        assertThat(defaults.stock()).isZero();
    }

    @Test void creationNormalisesBlankOptionalTextToNull() {
        var body = product();
        body.put("image", "   ");
        body.put("description", "");
        var input = CatalogRequests.productCreation(node(body));
        assertThat(input.image()).isNull();
        assertThat(input.description()).isNull();
    }

    @ParameterizedTest @ValueSource(strings = {"0", "0.00", "-1", "1.234", "010.00", "1e2", "100000000",
            "99999999.999", ".5", "5."})
    void creationRejectsInvalidMoney(String price) {
        var body = product();
        body.put("price", price);
        assertThatThrownBy(() -> CatalogRequests.productCreation(node(body)))
                .isInstanceOfSatisfying(FieldValidationException.class,
                        error -> assertThat(error.fieldErrors()).containsOnlyKeys("price"));
    }

    @Test void creationAcceptsTwoDecimalBoundaries() {
        var body = product();
        body.put("price", "99999999.99");
        assertThat(CatalogRequests.productCreation(node(body)).price()).isEqualByComparingTo("99999999.99");
        body.put("price", "0.01");
        assertThat(CatalogRequests.productCreation(node(body)).price()).isEqualByComparingTo("0.01");
        body.put("price", "100");
        assertThat(CatalogRequests.productCreation(node(body)).price()).isEqualByComparingTo("100");
    }

    @ParameterizedTest @ValueSource(strings = {"\"7\"", "7.5", "true", "-1", "2147483648", "null"})
    void creationRejectsNonIntegerStock(String value) throws Exception {
        var body = product();
        var merged = json.readTree(json.writeValueAsString(body));
        ((com.fasterxml.jackson.databind.node.ObjectNode) merged).set("stock", json.readTree(value));
        assertThatThrownBy(() -> CatalogRequests.productCreation(merged))
                .isInstanceOfSatisfying(FieldValidationException.class,
                        error -> assertThat(error.fieldErrors()).containsOnlyKeys("stock"));
    }

    @ParameterizedTest @ValueSource(strings = {"status", "merchantId", "merchantName", "categoryName", "id", "createTime"})
    void creationRejectsDerivedOrProtectedFields(String field) {
        var body = product();
        body.put(field, "x");
        assertThatThrownBy(() -> CatalogRequests.productCreation(node(body))).isInstanceOf(AuthFailure.class);
    }

    @Test void creationCollectsMultipleActualFields() {
        var body = new LinkedHashMap<String, Object>();
        body.put("categoryId", 12);
        body.put("name", "  ");
        body.put("spec", "s".repeat(31));
        body.put("price", "1.234");
        body.put("image", "ftp://host/a.png");
        body.put("description", "d".repeat(501));
        body.put("stock", -1);
        assertThatThrownBy(() -> CatalogRequests.productCreation(node(body)))
                .isInstanceOfSatisfying(FieldValidationException.class, error -> assertThat(error.fieldErrors())
                        .containsOnlyKeys("categoryId", "name", "spec", "price", "image", "description", "stock"));
    }

    @Test void editNeverAcceptsStockOrStatus() {
        var body = product();
        body.put("stock", 3);
        assertThatThrownBy(() -> CatalogRequests.productEdit(node(body))).isInstanceOf(AuthFailure.class);
        body.remove("stock");
        body.put("status", 1);
        assertThatThrownBy(() -> CatalogRequests.productEdit(node(body))).isInstanceOf(AuthFailure.class);
    }

    @Test void editReturnsOnlyTheBasicFields() {
        var input = CatalogRequests.productEdit(node(product()));
        assertThat(input.categoryId()).isEqualTo(BigInteger.valueOf(12));
        assertThat(input.name()).isEqualTo("草莓奶油蛋糕");
        assertThat(input.price()).isEqualByComparingTo("128.50");
    }

    @Test void stockAdjustmentRejectsZeroAndOutOfRangeResults() throws Exception {
        assertThat(CatalogRequests.stockAdjustment(json.readTree("{\"delta\":5,\"expectedStock\":3}")))
                .isEqualTo(new CatalogRequests.StockAdjustment(5, 3));
        assertThat(CatalogRequests.stockAdjustment(json.readTree("{\"delta\":-3,\"expectedStock\":3}")))
                .isEqualTo(new CatalogRequests.StockAdjustment(-3, 3));

        for (String body : new String[]{"{\"delta\":0,\"expectedStock\":3}",
                "{\"delta\":-4,\"expectedStock\":3}",
                "{\"delta\":2147483647,\"expectedStock\":1}",
                "{\"delta\":1}", "{\"delta\":1,\"expectedStock\":-1}",
                "{\"delta\":\"1\",\"expectedStock\":1}", "{\"delta\":1.5,\"expectedStock\":1}"}) {
            assertThatThrownBy(() -> CatalogRequests.stockAdjustment(json.readTree(body)))
                    .as(body).isInstanceOf(FieldValidationException.class);
        }
    }

    @ParameterizedTest @ValueSource(strings = {"\"1\"", "true", "1.0", "2", "null", "0.5"})
    void statusRequiresJsonIntegerZeroOrOne(String value) throws Exception {
        assertThatThrownBy(() -> CatalogRequests.status(json.readTree("{\"status\":" + value + "}")))
                .isInstanceOfSatisfying(FieldValidationException.class,
                        error -> assertThat(error.fieldErrors()).containsOnlyKeys("status"));
        assertThatThrownBy(() -> CatalogRequests.status(json.readTree("{}")))
                .isInstanceOfSatisfying(FieldValidationException.class,
                        error -> assertThat(error.fieldErrors()).containsOnlyKeys("status"));
    }

    @Test void categoryCreationDefaultsSortAndTrimsName() throws Exception {
        var input = CatalogRequests.categoryCreation(json.readTree("{\"name\":\"  生日蛋糕  \"}"));
        assertThat(input.name()).isEqualTo("生日蛋糕");
        assertThat(input.sort()).isZero();
        var sorted = CatalogRequests.categoryCreation(json.readTree("{\"name\":\"生日蛋糕\",\"sort\":5}"));
        assertThat(sorted.sort()).isEqualTo(5);
    }

    @Test void categoryCreationRejectsStatusAndNegativeSort() throws Exception {
        assertThatThrownBy(() -> CatalogRequests.categoryCreation(json.readTree("{\"name\":\"x\",\"status\":1}")))
                .isInstanceOf(AuthFailure.class);
        assertThatThrownBy(() -> CatalogRequests.categoryCreation(json.readTree("{\"name\":\"x\",\"sort\":-1}")))
                .isInstanceOfSatisfying(FieldValidationException.class,
                        error -> assertThat(error.fieldErrors()).containsOnlyKeys("sort"));
        assertThatThrownBy(() -> CatalogRequests.categoryCreation(json.readTree("{\"name\":\"x\",\"sort\":1.5}")))
                .isInstanceOfSatisfying(FieldValidationException.class,
                        error -> assertThat(error.fieldErrors()).containsOnlyKeys("sort"));
    }

    @Test void categoryEditRequiresSortAndName() throws Exception {
        assertThat(CatalogRequests.categoryEdit(json.readTree("{\"name\":\" 新品 \",\"sort\":2}")))
                .isEqualTo(new CatalogRequests.CategoryEdit("新品", 2));
        assertThatThrownBy(() -> CatalogRequests.categoryEdit(json.readTree("{\"name\":\"新品\"}")))
                .isInstanceOfSatisfying(FieldValidationException.class,
                        error -> assertThat(error.fieldErrors()).containsOnlyKeys("sort"));
        assertThatThrownBy(() -> CatalogRequests.categoryEdit(json.readTree("{\"name\":\"\",\"sort\":1}")))
                .isInstanceOfSatisfying(FieldValidationException.class,
                        error -> assertThat(error.fieldErrors()).containsOnlyKeys("name"));
    }

    @Test void publicQueryRequiresMerchantAndEscapesLiteralKeyword() {
        var defaults = CatalogRequests.publicList(Map.of("merchantId", new String[]{"12"}));
        assertThat(defaults.merchantId()).isEqualTo(BigInteger.valueOf(12));
        assertThat(defaults.page()).isEqualTo(1);
        assertThat(defaults.size()).isEqualTo(10);
        assertThat(defaults.pattern()).isNull();

        var literal = CatalogRequests.publicList(Map.of("merchantId", new String[]{"12"},
                "keyword", new String[]{" %_\\! "}));
        assertThat(literal.pattern()).isEqualTo("%!%!_\\!!%");

        assertThatThrownBy(() -> CatalogRequests.publicList(Map.of()))
                .isInstanceOfSatisfying(FieldValidationException.class,
                        error -> assertThat(error.fieldErrors()).containsOnlyKeys("merchantId"));
        assertThatThrownBy(() -> CatalogRequests.publicList(Map.of("merchantId", new String[]{"0"})))
                .isInstanceOfSatisfying(FieldValidationException.class,
                        error -> assertThat(error.fieldErrors()).containsOnlyKeys("merchantId"));
        assertThatThrownBy(() -> CatalogRequests.publicList(Map.of("merchantId", new String[]{"1"},
                "categoryId", new String[]{"abc"})))
                .isInstanceOfSatisfying(FieldValidationException.class,
                        error -> assertThat(error.fieldErrors()).containsOnlyKeys("categoryId"));
    }

    @Test void publicQueryForbidsShopStatusOverride() {
        assertThatThrownBy(() -> CatalogRequests.publicList(Map.of("merchantId", new String[]{"1"},
                "status", new String[]{"1"}))).isInstanceOf(AuthFailure.class);
        assertThatThrownBy(() -> CatalogRequests.publicList(Map.of("merchantId", new String[]{"1"},
                "unexpected", new String[]{"x"}))).isInstanceOf(BusinessException.class)
                .isNotInstanceOf(FieldValidationException.class);
    }

    @Test void merchantQueryNeverAcceptsClientMerchantId() {
        var defaults = CatalogRequests.merchantList(Map.of());
        assertThat(defaults.page()).isEqualTo(1);
        assertThat(defaults.size()).isEqualTo(10);
        assertThat(defaults.status()).isNull();
        assertThat(CatalogRequests.merchantList(Map.of("status", new String[]{"0"})).status()).isZero();
        assertThatThrownBy(() -> CatalogRequests.merchantList(Map.of("status", new String[]{"1", "0"})))
                .isInstanceOfSatisfying(FieldValidationException.class,
                        error -> assertThat(error.fieldErrors()).containsOnlyKeys("status"));
        assertThatThrownBy(() -> CatalogRequests.merchantList(Map.of("status", new String[]{"2"})))
                .isInstanceOfSatisfying(FieldValidationException.class,
                        error -> assertThat(error.fieldErrors()).containsOnlyKeys("status"));
        assertThatThrownBy(() -> CatalogRequests.merchantList(Map.of("merchantId", new String[]{"9"})))
                .isInstanceOf(AuthFailure.class);
    }

    @Test void queryRejectsInvalidPaginationAndLongKeyword() {
        assertThatThrownBy(() -> CatalogRequests.merchantList(Map.of("page", new String[]{"0"},
                "size", new String[]{"101"}, "keyword", new String[]{"k".repeat(61)})))
                .isInstanceOfSatisfying(FieldValidationException.class,
                        error -> assertThat(error.fieldErrors()).containsOnlyKeys("page", "size", "keyword"));
    }

    @Test void pageOffsetOverflowReturnsNoSqlOffset() {
        var input = CatalogRequests.merchantList(Map.of("page", new String[]{Long.toString(Long.MAX_VALUE)},
                "size", new String[]{"100"}));
        assertThat(input.offset()).isNull();
        var publicQuery = CatalogRequests.publicList(Map.of("merchantId", new String[]{"1"},
                "page", new String[]{Long.toString(Long.MAX_VALUE)}, "size", new String[]{"100"}));
        assertThat(publicQuery.offset()).isNull();
    }

    @Test void categoryStatusFilterIsOptionalAndStrict() {
        assertThat(CatalogRequests.categoryStatusFilter(Map.of())).isNull();
        assertThat(CatalogRequests.categoryStatusFilter(Map.of("status", new String[]{"0"}))).isZero();
        assertThatThrownBy(() -> CatalogRequests.categoryStatusFilter(Map.of("status", new String[]{"2"})))
                .isInstanceOfSatisfying(FieldValidationException.class,
                        error -> assertThat(error.fieldErrors()).containsOnlyKeys("status"));
        assertThatThrownBy(() -> CatalogRequests.categoryStatusFilter(Map.of("merchantId", new String[]{"1"})))
                .isInstanceOf(AuthFailure.class);
    }

    @ParameterizedTest @ValueSource(strings = {"0", "-1", "01", "1.0", "18446744073709551616", "abc", " "})
    void pathIdsRejectInvalidAndOverflowingValues(String value) {
        assertThatThrownBy(() -> CatalogRequests.id(value)).isInstanceOfSatisfying(FieldValidationException.class,
                error -> assertThat(error.fieldErrors()).containsOnlyKeys("id"));
    }

    @Test void pathIdPreservesFullUnsignedRange() {
        assertThat(CatalogRequests.id("18446744073709551615"))
                .isEqualTo(new BigInteger("18446744073709551615"));
    }

    @Test void bodyAndQueryGuardsKeepProtectedFieldsFirst() throws Exception {
        assertThatThrownBy(() -> CatalogRequests.noBody(json.readTree("{\"merchantId\":\"1\"}")))
                .isInstanceOf(AuthFailure.class);
        assertThatThrownBy(() -> CatalogRequests.noBody(json.readTree("{}")))
                .isInstanceOf(BusinessException.class).isNotInstanceOf(FieldValidationException.class);
        assertThatThrownBy(() -> CatalogRequests.noQuery(Map.of("merchantId", new String[]{"1"})))
                .isInstanceOf(AuthFailure.class);
        assertThatThrownBy(() -> CatalogRequests.noQuery(Map.of("x", new String[]{"1"})))
                .isInstanceOf(BusinessException.class).isNotInstanceOf(FieldValidationException.class);
    }

    @Test void priceIsExactDecimalNotFloatingPoint() {
        var body = product();
        body.put("price", "0.10");
        assertThat(CatalogRequests.productCreation(node(body)).price())
                .isEqualByComparingTo(new BigDecimal("0.10"));
    }
}
