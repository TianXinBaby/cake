package com.cake.platform.dto;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import com.cake.platform.exception.BusinessException;
import com.cake.platform.exception.CommonErrorCode;
import com.cake.platform.exception.FieldValidationException;
import com.cake.platform.exception.ParameterErrorData;
import com.cake.platform.security.AuthErrorCode;
import com.cake.platform.security.AuthFailure;
import com.fasterxml.jackson.databind.JsonNode;

/** Per-operation input allowlists; protected fields take priority over ordinary parameter errors. */
public final class AuthRequests {
    private AuthRequests() { }
    private static final Set<String> PROTECTED = Set.of("id", "userId", "username", "phone", "role",
            "status", "merchantId", "tokenVersion", "token_version", "loginFailCount", "login_fail_count",
            "lockedUntil", "locked_until", "createTime", "updateTime", "password");
    private static final Set<String> REGISTER_PROTECTED = Set.of("role", "status", "merchantId", "tokenVersion");

    public static Registration registration(JsonNode body) {
        var validation = new Validation(body, Set.of("phone", "nickname", "password"), REGISTER_PROTECTED);
        String phone = validation.phone("phone");
        String nickname = validation.nickname("nickname");
        String password = validation.password("password");
        validation.finish();
        return new Registration(phone, nickname, password);
    }

    public static Login login(JsonNode body) {
        var validation = new Validation(body, Set.of("username", "password"), PROTECTED);
        String username = validation.phone("username");
        String password = validation.existingPassword("password");
        validation.finish();
        return new Login(username, password);
    }

    public static String nickname(JsonNode body) {
        var validation = new Validation(body, Set.of("nickname"), PROTECTED);
        String nickname = validation.nickname("nickname");
        validation.finish();
        return nickname;
    }

    public static PasswordChange passwordChange(JsonNode body) {
        var validation = new Validation(body, Set.of("oldPassword", "newPassword"), PROTECTED);
        String oldPassword = validation.existingPassword("oldPassword");
        String newPassword = validation.password("newPassword");
        validation.finish();
        return new PasswordChange(oldPassword, newPassword);
    }

    public static void fields(JsonNode body, Set<String> allowed) { fields(body, allowed, PROTECTED); }

    public static void fields(JsonNode body, Set<String> allowed, Set<String> protectedFields) {
        new Validation(body, allowed, protectedFields).finish();
    }

    // Retain the existing helpers for existing callers, with the same validation rules.
    public static String text(JsonNode body, String name) {
        var validation = new Validation(body);
        String value = validation.text(name);
        validation.finish();
        return value;
    }

    public static String phone(String value) {
        var validation = single("phone", value);
        String phone = validation.phone("phone");
        validation.finish();
        return phone;
    }

    public static String nickname(String value) {
        var validation = single("nickname", value);
        String nickname = validation.nickname("nickname");
        validation.finish();
        return nickname;
    }

    public static String password(String value) {
        var validation = single("password", value);
        String password = validation.password("password");
        validation.finish();
        return password;
    }

    private static Validation single(String name, String value) {
        var body = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();
        body.put(name, value);
        return new Validation(body, Set.of(name), Set.of());
    }

    private static boolean space(int codePoint) {
        return Character.isWhitespace(codePoint) || Character.isSpaceChar(codePoint);
    }

    public static void invalid() { throw new BusinessException(CommonErrorCode.INVALID_PARAMETER); }

    public record Registration(String phone, String nickname, String password) { }
    public record Login(String username, String password) { }
    public record PasswordChange(String oldPassword, String newPassword) { }

    /** Collect only fixed messages and public input names; never retain rejected values. */
    private static final class Validation {
        private final JsonNode body;
        private final Map<String, List<String>> errors = new LinkedHashMap<>();
        private boolean unlocatable;

        private Validation(JsonNode body) {
            if (body == null || !body.isObject()) { invalid(); }
            this.body = body;
        }

        private Validation(JsonNode body, Set<String> allowed, Set<String> protectedFields) {
            this(body);
            var names = body.fieldNames();
            while (names.hasNext()) {
                String name = names.next();
                if (!allowed.contains(name) && protectedFields.contains(name)) {
                    throw new AuthFailure(AuthErrorCode.FORBIDDEN);
                }
            }
            names = body.fieldNames();
            while (names.hasNext()) {
                String name = names.next();
                if (!allowed.contains(name)) {
                    // Unknown names are not public API fields, so do not fabricate a form location.
                    unlocatable = true;
                }
            }
        }

        private String text(String name) {
            var value = body.get(name);
            if (value == null || value.isNull()) {
                add(name, label(name) + "不能为空");
                return null;
            }
            if (!value.isTextual()) {
                add(name, label(name) + "必须为字符串");
                return null;
            }
            return value.textValue();
        }

        private String phone(String name) {
            String value = text(name);
            if (value != null && !value.matches("^1[3-9][0-9]{9}$")) {
                add(name, name.equals("username") ? "账号格式不正确" : "手机号格式不正确");
            }
            return value;
        }

        private String nickname(String name) {
            String value = text(name);
            if (value == null) { return null; }
            int start = 0;
            int end = value.length();
            while (start < end && space(value.codePointAt(start))) {
                start += Character.charCount(value.codePointAt(start));
            }
            while (end > start && space(value.codePointBefore(end))) {
                end -= Character.charCount(value.codePointBefore(end));
            }
            String trimmed = value.substring(start, end);
            int count = trimmed.codePointCount(0, trimmed.length());
            if (count < 1) { add(name, "昵称不能为空"); }
            if (count > 30) { add(name, "昵称长度不能超过30个字符"); }
            if (trimmed.codePoints().anyMatch(Character::isISOControl)) { add(name, "昵称不能包含控制字符"); }
            return trimmed;
        }

        private String password(String name) {
            String value = text(name);
            if (value == null) { return null; }
            if (value.isEmpty()) {
                add(name, label(name) + "不能为空");
                return value;
            }
            if (value.length() < 6 || value.length() > 20) { add(name, label(name) + "长度必须为6到20个字符"); }
            if (value.codePoints().anyMatch(AuthRequests::space)) { add(name, label(name) + "不能包含空白字符"); }
            if (value.codePoints().anyMatch(c -> Character.isISOControl(c)
                    || Character.getType(c) == Character.FORMAT || Character.getType(c) == Character.SURROGATE
                    || !Character.isDefined(c))) { add(name, label(name) + "不能包含不可见或无效字符"); }
            return value;
        }

        private String existingPassword(String name) {
            String value = text(name);
            if (value != null) {
                if (value.isEmpty()) { add(name, label(name) + "不能为空"); }
                if (value.length() > 100) { add(name, label(name) + "长度不能超过100个字符"); }
            }
            return value;
        }

        private void add(String name, String message) {
            if (ParameterErrorData.safeField(name)) { errors.computeIfAbsent(name, ignored -> new ArrayList<>()).add(message); }
            else { unlocatable = true; }
        }

        private void finish() {
            if (!errors.isEmpty()) { throw new FieldValidationException(errors); }
            if (unlocatable) { invalid(); }
        }

        private static String label(String name) {
            return switch (name) {
                case "phone" -> "手机号";
                case "username" -> "账号";
                case "nickname" -> "昵称";
                case "oldPassword" -> "原密码";
                case "newPassword" -> "新密码";
                default -> "密码";
            };
        }
    }
}
