package com.cake.platform.dto;

import java.math.BigInteger;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import com.cake.platform.exception.BusinessException;
import com.cake.platform.exception.CommonErrorCode;
import com.cake.platform.exception.FieldValidationException;
import com.cake.platform.security.AuthErrorCode;
import com.cake.platform.security.AuthFailure;
import com.fasterxml.jackson.databind.JsonNode;

/** Strict public inputs for API-35 through API-40; database records are never request DTOs. */
public final class AdminRequests {
    private AdminRequests() { }
    private static final Set<String> PROTECTED = Set.of("id", "userId", "merchantId", "username", "accountPhone",
            "nickname", "password", "role", "status", "tokenVersion", "token_version", "loginFailCount",
            "login_fail_count", "lockedUntil", "locked_until", "createTime", "updateTime");
    private static final Set<String> QUERY = Set.of("page", "size", "keyword", "status");

    public static Creation creation(JsonNode body) {
        var v = new Validation(body, Set.of("accountPhone", "nickname", "password", "name", "phone", "address", "description"));
        String accountPhone = v.reuse("accountPhone", () -> AuthRequests.phone(v.string("accountPhone", false)));
        String nickname = v.reuse("nickname", () -> AuthRequests.nickname(v.string("nickname", false)));
        String password = v.reuse("password", () -> AuthRequests.password(v.string("password", false)));
        String name = v.text("name", 60, false);
        String phone = v.reuse("phone", () -> AuthRequests.phone(v.string("phone", false)));
        String address = v.text("address", 120, false);
        String description = v.text("description", 500, true);
        v.finish();
        return new Creation(accountPhone, nickname, password, name, phone, address, description);
    }

    public static Edit edit(JsonNode body) {
        var v = new Validation(body, Set.of("name", "phone", "address", "description"));
        String name = v.text("name", 60, false);
        String phone = v.reuse("phone", () -> AuthRequests.phone(v.string("phone", false)));
        String address = v.text("address", 120, false);
        String description = v.text("description", 500, true);
        v.finish();
        return new Edit(name, phone, address, description);
    }

    public static int status(JsonNode body) {
        var v = new Validation(body, Set.of("status"));
        JsonNode status = body.get("status");
        if (status == null || !status.isIntegralNumber() || !status.canConvertToInt()
                || (status.intValue() != 0 && status.intValue() != 1)) {
            v.add("status", "状态必须为整数0或1");
        }
        v.finish();
        return status.intValue();
    }

    public static BigInteger id(String value) {
        if (value == null || !value.matches("[1-9][0-9]{0,19}")) { throw invalidId(); }
        BigInteger id = new BigInteger(value);
        if (id.bitLength() > 64) { throw invalidId(); }
        return id;
    }

    private static FieldValidationException invalidId() {
        return FieldValidationException.forField("id", "ID必须为有效的正十进制字符串");
    }

    public static ListQuery list(Map<String, String[]> parameters) {
        var v = new Validation();
        parameters.keySet().forEach(name -> {
            if (!QUERY.contains(name) && PROTECTED.contains(name)) { throw new AuthFailure(AuthErrorCode.FORBIDDEN); }
        });
        parameters.keySet().forEach(name -> { if (!QUERY.contains(name)) v.unknown = true; });
        long page = number(v, parameters, "page", 1, Long.MAX_VALUE, 1);
        int size = (int) number(v, parameters, "size", 1, 100, 10);
        String statusText = single(v, parameters, "status");
        Integer status = null;
        if (statusText != null) {
            if (!statusText.matches("[01]")) v.add("status", "状态必须为整数0或1");
            else status = Integer.valueOf(statusText);
        }
        String keyword = single(v, parameters, "keyword");
        if (keyword != null) {
            keyword = trim(keyword);
            if (keyword.codePointCount(0, keyword.length()) > 60) v.add("keyword", "关键字长度不能超过60个字符");
        }
        v.finish();
        // A fixed escape character makes %, _ and backslash input literal under all SQL modes.
        String pattern = keyword == null || keyword.isEmpty() ? null : "%" + keyword.replace("!", "!!")
                .replace("%", "!%").replace("_", "!_") + "%";
        return new ListQuery(page, size, pattern, status);
    }

    public static void noQuery(Map<String, String[]> parameters) {
        if (parameters.isEmpty()) return;
        parameters.keySet().forEach(name -> { if (PROTECTED.contains(name)) throw new AuthFailure(AuthErrorCode.FORBIDDEN); });
        throw new BusinessException(CommonErrorCode.INVALID_PARAMETER);
    }

    public static void noBody(JsonNode body) {
        if (body == null) return;
        new Validation(body, Set.of()).finish();
        throw new BusinessException(CommonErrorCode.INVALID_PARAMETER);
    }

    private static long number(Validation v, Map<String, String[]> parameters, String name, long min, long max, long fallback) {
        String value = single(v, parameters, name);
        if (value == null) return fallback;
        try {
            if (!value.matches("[0-9]+")) throw new NumberFormatException();
            long parsed = Long.parseLong(value);
            if (parsed < min || parsed > max) throw new NumberFormatException();
            return parsed;
        } catch (NumberFormatException ignored) {
            v.add(name, name.equals("size") ? "每页条数必须为1到100的整数" : "页码必须为正整数");
            return fallback;
        }
    }

    private static String single(Validation v, Map<String, String[]> parameters, String name) {
        String[] values = parameters.get(name);
        if (values == null) return null;
        if (values.length != 1 || values[0] == null) { v.add(name, "参数只允许提供一次"); return null; }
        return values[0];
    }

    private static String trim(String value) {
        int start = 0, end = value.length();
        while (start < end && space(value.codePointAt(start))) start += Character.charCount(value.codePointAt(start));
        while (end > start && space(value.codePointBefore(end))) end -= Character.charCount(value.codePointBefore(end));
        return value.substring(start, end);
    }
    private static boolean space(int c) { return Character.isWhitespace(c) || Character.isSpaceChar(c); }

    public record Creation(String accountPhone, String nickname, String password, String name, String phone,
                           String address, String description) { }
    public record Edit(String name, String phone, String address, String description) { }
    public record ListQuery(long page, int size, String pattern, Integer status) {
        public Long offset() {
            BigInteger offset = BigInteger.valueOf(page).subtract(BigInteger.ONE).multiply(BigInteger.valueOf(size));
            return offset.bitLength() > 63 ? null : offset.longValueExact();
        }
    }

    private static final class Validation {
        private JsonNode body;
        private final Map<String, List<String>> errors = new LinkedHashMap<>();
        private boolean unknown;
        private Validation() { }
        private Validation(JsonNode body, Set<String> allowed) {
            if (body == null || !body.isObject()) throw new BusinessException(CommonErrorCode.INVALID_PARAMETER);
            this.body = body;
            body.fieldNames().forEachRemaining(name -> {
                if (!allowed.contains(name) && PROTECTED.contains(name)) throw new AuthFailure(AuthErrorCode.FORBIDDEN);
            });
            body.fieldNames().forEachRemaining(name -> { if (!allowed.contains(name)) unknown = true; });
        }
        private String string(String name, boolean optional) {
            JsonNode node = body.get(name);
            if (node == null || node.isNull()) {
                if (!optional) add(name, "该字段不能为空");
                return null;
            }
            if (!node.isTextual()) { add(name, "该字段必须为字符串"); return null; }
            return node.textValue();
        }
        private String text(String name, int max, boolean optional) {
            String value = string(name, optional);
            if (value == null) return null;
            value = trim(value);
            int length = value.codePointCount(0, value.length());
            if (!optional && length == 0) add(name, "该字段不能为空");
            if (length > max) add(name, "长度不能超过" + max + "个字符");
            return value;
        }
        private String reuse(String field, Supplier<String> operation) {
            try { return operation.get(); }
            catch (FieldValidationException error) {
                // Missing/type errors already locate this field; keep one root cause per field.
                if (!errors.containsKey(field)) error.fieldErrors().values().forEach(messages -> errors.put(field, messages));
                return null;
            }
        }
        private void add(String field, String message) { errors.put(field, List.of(message)); }
        private void finish() {
            if (!errors.isEmpty()) throw new FieldValidationException(errors);
            if (unknown) throw new BusinessException(CommonErrorCode.INVALID_PARAMETER);
        }
    }
}
