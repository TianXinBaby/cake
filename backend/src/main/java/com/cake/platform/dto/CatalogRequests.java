package com.cake.platform.dto;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import com.cake.platform.exception.BusinessException;
import com.cake.platform.exception.CommonErrorCode;
import com.cake.platform.exception.FieldValidationException;
import com.cake.platform.security.AuthErrorCode;
import com.cake.platform.security.AuthFailure;
import com.fasterxml.jackson.databind.JsonNode;

/**
 * Strict public inputs for API-02~05, 15~22 and 29~31. Database records are never bound as request DTOs;
 * merchantId and status are never accepted where the requirement derives them from the login context.
 */
public final class CatalogRequests {
    private CatalogRequests() { }

    private static final int MAX_STOCK = Integer.MAX_VALUE;
    private static final BigDecimal MAX_PRICE = new BigDecimal("99999999.99");
    private static final String PRICE_PATTERN = "(0|[1-9][0-9]{0,7})(\\.[0-9]{1,2})?";
    private static final Set<String> PROTECTED = Set.of("id", "userId", "merchantId", "merchantName",
            "categoryName", "username", "password", "role", "tokenVersion", "createTime", "updateTime",
            "deleted", "stock", "status");
    private static final Set<String> MERCHANT_QUERY = Set.of("page", "size", "categoryId", "keyword", "status");
    private static final Set<String> PUBLIC_QUERY = Set.of("merchantId", "page", "size", "categoryId", "keyword");

    public static ProductCreation productCreation(JsonNode body) {
        var v = new Validation(body, Set.of("categoryId", "name", "spec", "price", "image", "description", "stock"));
        BigInteger categoryId = v.identifier("categoryId");
        String name = v.text("name", 60, false);
        String spec = v.text("spec", 30, false);
        BigDecimal price = v.price("price");
        int stock = v.optionalInteger("stock", 0, MAX_STOCK, "库存必须为0到2147483647的整数", 0);
        String image = v.image("image");
        String description = v.text("description", 500, true);
        v.finish();
        return new ProductCreation(categoryId, name, spec, price, stock, image, emptyToNull(description));
    }

    public static ProductEdit productEdit(JsonNode body) {
        var v = new Validation(body, Set.of("categoryId", "name", "spec", "price", "image", "description"));
        BigInteger categoryId = v.identifier("categoryId");
        String name = v.text("name", 60, false);
        String spec = v.text("spec", 30, false);
        BigDecimal price = v.price("price");
        String image = v.image("image");
        String description = v.text("description", 500, true);
        v.finish();
        return new ProductEdit(categoryId, name, spec, price, image, emptyToNull(description));
    }

    public static StockAdjustment stockAdjustment(JsonNode body) {
        var v = new Validation(body, Set.of("delta", "expectedStock"));
        Integer delta = v.integer("delta", -MAX_STOCK, MAX_STOCK, "增减量必须为非零整数");
        if (delta != null && delta == 0) { v.add("delta", "增减量不能为0"); }
        Integer expectedStock = v.integer("expectedStock", 0, MAX_STOCK, "原库存必须为非负整数");
        if (delta != null && expectedStock != null) {
            long adjusted = (long) expectedStock + delta;
            if (adjusted < 0) { v.add("delta", "调整后库存不能为负数"); }
            else if (adjusted > MAX_STOCK) { v.add("delta", "调整后库存超出上限"); }
        }
        v.finish();
        return new StockAdjustment(delta, expectedStock);
    }

    public static int status(JsonNode body) {
        var v = new Validation(body, Set.of("status"));
        Integer status = v.integer("status", 0, 1, "状态必须为整数0或1");
        v.finish();
        return status;
    }

    public static CategoryCreation categoryCreation(JsonNode body) {
        var v = new Validation(body, Set.of("name", "sort"));
        String name = v.text("name", 30, false);
        int sort = v.optionalInteger("sort", 0, MAX_STOCK, "排序必须为非负整数", 0);
        v.finish();
        return new CategoryCreation(name, sort);
    }

    public static CategoryEdit categoryEdit(JsonNode body) {
        var v = new Validation(body, Set.of("name", "sort"));
        String name = v.text("name", 30, false);
        Integer sort = v.integer("sort", 0, MAX_STOCK, "排序必须为非负整数");
        v.finish();
        return new CategoryEdit(name, sort);
    }

    public static PublicQuery publicList(Map<String, String[]> parameters) {
        var v = new Validation();
        expect(parameters, PUBLIC_QUERY, v);
        String merchantText = single(v, parameters, "merchantId");
        BigInteger merchantId = null;
        if (merchantText == null) { v.add("merchantId", "该字段不能为空"); }
        else { merchantId = parseIdentifier(v, "merchantId", merchantText); }
        long page = number(v, parameters, "page", 1, Long.MAX_VALUE, 1);
        int size = (int) number(v, parameters, "size", 1, 100, 10);
        BigInteger categoryId = optionalIdentifier(v, parameters, "categoryId");
        String pattern = pattern(v, parameters);
        v.finish();
        return new PublicQuery(merchantId, page, size, categoryId, pattern);
    }

    public static ProductQuery merchantList(Map<String, String[]> parameters) {
        var v = new Validation();
        expect(parameters, MERCHANT_QUERY, v);
        long page = number(v, parameters, "page", 1, Long.MAX_VALUE, 1);
        int size = (int) number(v, parameters, "size", 1, 100, 10);
        BigInteger categoryId = optionalIdentifier(v, parameters, "categoryId");
        Integer status = null;
        String statusText = single(v, parameters, "status");
        if (statusText != null) {
            if (!statusText.matches("[01]")) { v.add("status", "状态必须为整数0或1"); }
            else { status = Integer.valueOf(statusText); }
        }
        String pattern = pattern(v, parameters);
        v.finish();
        return new ProductQuery(page, size, categoryId, status, pattern);
    }

    /** API-16 query: only the selected merchantId is accepted. */
    public static BigInteger publicMerchant(Map<String, String[]> parameters) {
        var v = new Validation();
        expect(parameters, Set.of("merchantId"), v);
        String text = single(v, parameters, "merchantId");
        BigInteger merchantId = null;
        if (text == null) { v.add("merchantId", "该字段不能为空"); }
        else { merchantId = parseIdentifier(v, "merchantId", text); }
        v.finish();
        return merchantId;
    }

    /** API-19 query: only an optional 0/1 status is accepted, never a client merchantId. */
    public static Integer categoryStatusFilter(Map<String, String[]> parameters) {
        var v = new Validation();
        expect(parameters, Set.of("status"), v);
        String statusText = single(v, parameters, "status");
        Integer status = null;
        if (statusText != null) {
            if (!statusText.matches("[01]")) { v.add("status", "状态必须为整数0或1"); }
            else { status = Integer.valueOf(statusText); }
        }
        v.finish();
        return status;
    }

    public static BigInteger id(String value) {        if (value == null || !value.matches("[1-9][0-9]{0,19}")) { throw invalidId(); }
        BigInteger id = new BigInteger(value);
        if (id.bitLength() > 64) { throw invalidId(); }
        return id;
    }

    public static void noQuery(Map<String, String[]> parameters) {
        if (parameters.isEmpty()) { return; }
        parameters.keySet().forEach(name -> {
            if (PROTECTED.contains(name)) { throw new AuthFailure(AuthErrorCode.FORBIDDEN); }
        });
        throw new BusinessException(CommonErrorCode.INVALID_PARAMETER);
    }

    public static void noBody(JsonNode body) {
        if (body == null) { return; }
        new Validation(body, Set.of()).finish();
        throw new BusinessException(CommonErrorCode.INVALID_PARAMETER);
    }

    private static FieldValidationException invalidId() {
        return FieldValidationException.forField("id", "ID必须为有效的正十进制字符串");
    }

    private static void expect(Map<String, String[]> parameters, Set<String> allowed, Validation v) {
        parameters.keySet().forEach(name -> {
            if (!allowed.contains(name) && PROTECTED.contains(name)) { throw new AuthFailure(AuthErrorCode.FORBIDDEN); }
        });
        parameters.keySet().forEach(name -> { if (!allowed.contains(name)) { v.unknown = true; } });
    }

    private static BigInteger optionalIdentifier(Validation v, Map<String, String[]> parameters, String name) {
        String text = single(v, parameters, name);
        return text == null ? null : parseIdentifier(v, name, text);
    }

    private static BigInteger parseIdentifier(Validation v, String name, String text) {
        if (!text.matches("[1-9][0-9]{0,19}")) {
            v.add(name, "ID必须为有效的正十进制字符串");
            return null;
        }
        BigInteger value = new BigInteger(text);
        if (value.bitLength() > 64) {
            v.add(name, "ID超出允许范围");
            return null;
        }
        return value;
    }

    private static String pattern(Validation v, Map<String, String[]> parameters) {
        String keyword = single(v, parameters, "keyword");
        if (keyword == null) { return null; }
        keyword = trim(keyword);
        if (keyword.codePointCount(0, keyword.length()) > 60) {
            v.add("keyword", "关键字长度不能超过60个字符");
        }
        if (keyword.isEmpty()) { return null; }
        // A fixed escape character keeps %, _ and ! literal under every SQL mode.
        return "%" + keyword.replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%";
    }

    private static long number(Validation v, Map<String, String[]> parameters, String name,
                               long min, long max, long fallback) {
        String value = single(v, parameters, name);
        if (value == null) { return fallback; }
        try {
            if (!value.matches("[0-9]+")) { throw new NumberFormatException(); }
            long parsed = Long.parseLong(value);
            if (parsed < min || parsed > max) { throw new NumberFormatException(); }
            return parsed;
        } catch (NumberFormatException ignored) {
            v.add(name, name.equals("size") ? "每页条数必须为1到100的整数" : "页码必须为正整数");
            return fallback;
        }
    }

    private static String single(Validation v, Map<String, String[]> parameters, String name) {
        String[] values = parameters.get(name);
        if (values == null) { return null; }
        if (values.length != 1 || values[0] == null) {
            v.add(name, "参数只允许提供一次");
            return null;
        }
        return values[0];
    }

    private static String trim(String value) {
        int start = 0, end = value.length();
        while (start < end && space(value.codePointAt(start))) { start += Character.charCount(value.codePointAt(start)); }
        while (end > start && space(value.codePointBefore(end))) { end -= Character.charCount(value.codePointBefore(end)); }
        return value.substring(start, end);
    }

    private static boolean space(int c) { return Character.isWhitespace(c) || Character.isSpaceChar(c); }

    private static String emptyToNull(String value) { return value == null || value.isEmpty() ? null : value; }

    public record ProductCreation(BigInteger categoryId, String name, String spec, BigDecimal price, int stock,
                                  String image, String description) { }
    public record ProductEdit(BigInteger categoryId, String name, String spec, BigDecimal price, String image,
                              String description) { }
    public record StockAdjustment(int delta, int expectedStock) { }
    public record CategoryCreation(String name, int sort) { }
    public record CategoryEdit(String name, int sort) { }

    public record ProductQuery(long page, int size, BigInteger categoryId, Integer status, String pattern) {
        public Long offset() {
            BigInteger offset = BigInteger.valueOf(page).subtract(BigInteger.ONE).multiply(BigInteger.valueOf(size));
            return offset.bitLength() > 63 ? null : offset.longValueExact();
        }
    }

    public record PublicQuery(BigInteger merchantId, long page, int size, BigInteger categoryId, String pattern) {
        public Long offset() {
            BigInteger offset = BigInteger.valueOf(page).subtract(BigInteger.ONE).multiply(BigInteger.valueOf(size));
            return offset.bitLength() > 63 ? null : offset.longValueExact();
        }
    }

    private static final class Validation {
        private final JsonNode body;
        private final Map<String, List<String>> errors = new LinkedHashMap<>();
        private boolean unknown;

        private Validation() { body = null; }

        private Validation(JsonNode body, Set<String> allowed) {
            if (body == null || !body.isObject()) { throw new BusinessException(CommonErrorCode.INVALID_PARAMETER); }
            this.body = body;
            body.fieldNames().forEachRemaining(name -> {
                if (!allowed.contains(name) && PROTECTED.contains(name)) { throw new AuthFailure(AuthErrorCode.FORBIDDEN); }
            });
            body.fieldNames().forEachRemaining(name -> { if (!allowed.contains(name)) { unknown = true; } });
        }

        private String string(String name, boolean optional) {
            JsonNode node = body.get(name);
            if (node == null || node.isNull()) {
                if (!optional) { add(name, "该字段不能为空"); }
                return null;
            }
            if (!node.isTextual()) { add(name, "该字段必须为字符串"); return null; }
            return node.textValue();
        }

        private String text(String name, int max, boolean optional) {
            String value = string(name, optional);
            if (value == null) { return null; }
            value = trim(value);
            int length = value.codePointCount(0, value.length());
            if (!optional && length == 0) { add(name, "该字段不能为空"); }
            else if (length > max) { add(name, "长度不能超过" + max + "个字符"); }
            return value;
        }

        private BigInteger identifier(String name) {
            String value = string(name, false);
            if (value == null) { return null; }
            value = trim(value);
            if (!value.matches("[1-9][0-9]{0,19}")) {
                add(name, "ID必须为有效的正十进制字符串");
                return null;
            }
            BigInteger parsed = new BigInteger(value);
            if (parsed.bitLength() > 64) { add(name, "ID超出允许范围"); return null; }
            return parsed;
        }

        private BigDecimal price(String name) {
            String value = string(name, false);
            if (value == null) { return null; }
            value = trim(value);
            if (!value.matches(PRICE_PATTERN)) {
                add(name, "单价必须为大于0且最多两位小数的金额");
                return null;
            }
            BigDecimal price = new BigDecimal(value);
            if (price.signum() <= 0) { add(name, "单价必须大于0"); }
            else if (price.compareTo(MAX_PRICE) > 0) { add(name, "单价不能超过99999999.99"); }
            return price;
        }

        private String image(String name) {
            String value = string(name, true);
            if (value == null) { return null; }
            value = trim(value);
            if (value.isEmpty()) { return null; }
            if (value.codePointCount(0, value.length()) > 255) {
                add(name, "图片地址长度不能超过255个字符");
            } else if (!value.matches("^(/|https?://)\\S*$")) {
                add(name, "图片地址必须为/开头的站内路径或http(s)链接");
            }
            return value;
        }

        private Integer integer(String name, int min, int max, String message) {
            JsonNode node = body.get(name);
            if (node == null || node.isNull()) { add(name, "该字段不能为空"); return null; }
            if (!node.isIntegralNumber() || !node.canConvertToInt()) { add(name, message); return null; }
            int value = node.intValue();
            if (value < min || value > max) { add(name, message); return null; }
            return value;
        }

        private int optionalInteger(String name, int min, int max, String message, int fallback) {
            JsonNode node = body.get(name);
            if (node == null) { return fallback; }
            Integer value = integer(name, min, max, message);
            return value == null ? fallback : value;
        }

        private void add(String field, String message) { errors.put(field, List.of(message)); }

        private void finish() {
            if (!errors.isEmpty()) { throw new FieldValidationException(errors); }
            if (unknown) { throw new BusinessException(CommonErrorCode.INVALID_PARAMETER); }
        }
    }
}
