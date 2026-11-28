package com.cake.platform.dto;

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

/** Public merchant list/detail parameters (API-33/34); never accepts a client status or identity field. */
public final class PublicMerchantRequests {
    private PublicMerchantRequests() { }

    private static final Set<String> QUERY = Set.of("page", "size", "keyword");
    private static final Set<String> PROTECTED = Set.of("id", "userId", "merchantId", "username", "password",
            "role", "status", "tokenVersion", "createTime", "updateTime");

    public static ListQuery list(Map<String, String[]> parameters) {
        parameters.keySet().forEach(name -> {
            if (!QUERY.contains(name) && PROTECTED.contains(name)) { throw new AuthFailure(AuthErrorCode.FORBIDDEN); }
        });
        boolean unknown = parameters.keySet().stream().anyMatch(name -> !QUERY.contains(name));
        Map<String, List<String>> errors = new LinkedHashMap<>();
        long page = number(parameters, "page", 1, Long.MAX_VALUE, 1, errors);
        int size = (int) number(parameters, "size", 1, 100, 10, errors);
        String keyword = single(parameters, "keyword", errors);
        if (keyword != null) {
            keyword = trim(keyword);
            if (keyword.codePointCount(0, keyword.length()) > 60) {
                errors.putIfAbsent("keyword", List.of("关键字长度不能超过60个字符"));
            }
        }
        if (!errors.isEmpty()) { throw new FieldValidationException(errors); }
        if (unknown) { throw new BusinessException(CommonErrorCode.INVALID_PARAMETER); }
        String pattern = keyword == null || keyword.isEmpty() ? null
                : "%" + keyword.replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%";
        return new ListQuery(page, size, pattern);
    }
    public static BigInteger id(String value) {
        if (value == null || !value.matches("[1-9][0-9]{0,19}")) { throw invalidId(); }
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
        body.fieldNames().forEachRemaining(name -> {
            if (PROTECTED.contains(name)) { throw new AuthFailure(AuthErrorCode.FORBIDDEN); }
        });
        throw new BusinessException(CommonErrorCode.INVALID_PARAMETER);
    }

    private static long number(Map<String, String[]> parameters, String name, long min, long max, long fallback,
                               Map<String, List<String>> errors) {
        String value = single(parameters, name, errors);
        if (value == null) { return fallback; }
        String message = name.equals("size") ? "每页条数必须为1到100的整数" : "页码必须为正整数";
        if (!value.matches("[0-9]+")) { errors.putIfAbsent(name, List.of(message)); return fallback; }
        try {
            long parsed = Long.parseLong(value);
            if (parsed < min || parsed > max) { errors.putIfAbsent(name, List.of(message)); return fallback; }
            return parsed;
        } catch (NumberFormatException ignored) {
            errors.putIfAbsent(name, List.of(message));
            return fallback;
        }
    }

    private static String single(Map<String, String[]> parameters, String name, Map<String, List<String>> errors) {
        String[] values = parameters.get(name);
        if (values == null) { return null; }
        if (values.length != 1 || values[0] == null) {
            errors.putIfAbsent(name, List.of("参数只允许提供一次"));
            return null;
        }
        return values[0];
    }
    private static FieldValidationException invalidId() {
        return FieldValidationException.forField("id", "ID必须为有效的正十进制字符串");
    }

    private static String trim(String value) {
        int start = 0, end = value.length();
        while (start < end && space(value.codePointAt(start))) { start += Character.charCount(value.codePointAt(start)); }
        while (end > start && space(value.codePointBefore(end))) { end -= Character.charCount(value.codePointBefore(end)); }
        return value.substring(start, end);
    }

    private static boolean space(int c) { return Character.isWhitespace(c) || Character.isSpaceChar(c); }

    public record ListQuery(long page, int size, String pattern) {
        public Long offset() {
            BigInteger offset = BigInteger.valueOf(page).subtract(BigInteger.ONE).multiply(BigInteger.valueOf(size));
            return offset.bitLength() > 63 ? null : offset.longValueExact();
        }
    }
}
