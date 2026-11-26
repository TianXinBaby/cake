package com.cake.platform.security;

import java.util.Set;
import com.cake.platform.dto.AuthRequests;
import com.cake.platform.exception.BusinessException;
import com.cake.platform.exception.FieldValidationException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

/** Pure validation regressions for the confirmed public field-error contract and existing input rules. */
class AuthRequestsTests {
    private final ObjectMapper json = new ObjectMapper();

    @Test
    void onlyOperationAllowlistedFieldsAreAccepted() throws Exception {
        var body = json.readTree("{\"phone\":\"13800138000\",\"nickname\":\"昵称\",\"password\":\"Secret!42\"}");
        AuthRequests.fields(body, Set.of("phone", "nickname", "password"));
        assertThat(AuthRequests.text(body, "nickname")).isEqualTo("昵称");
    }

    @ParameterizedTest
    @ValueSource(strings = {"role", "status", "merchantId", "tokenVersion", "id", "userId", "phone", "username", "password", "token_version", "login_fail_count", "locked_until"})
    void everySensitiveNonAllowlistedFieldReturnsForbidden(String field) {
        var body = json.createObjectNode().put("nickname", "有效昵称").put(field, "value");
        assertCode(40302, () -> AuthRequests.fields(body, Set.of("nickname")));
    }

    @Test
    void protectedFieldWinsOverOrdinaryUnknownFieldRegardlessOfJsonFieldOrder() {
        var unknownFirst = json.createObjectNode().put("unknown", 1).put("role", "ADMIN");
        var protectedFirst = json.createObjectNode().put("role", "ADMIN").put("unknown", 1);
        assertCode(40302, () -> AuthRequests.fields(unknownFirst, Set.of("nickname")));
        assertCode(40302, () -> AuthRequests.fields(protectedFirst, Set.of("nickname")));
    }

    @Test
    void ordinaryUnknownFieldReturnsExistingParameterError() {
        assertCode(40001, () -> AuthRequests.fields(json.createObjectNode().put("unexpected", 1), Set.of("nickname")));
    }

    @ParameterizedTest
    @ValueSource(strings = {"null", "[]", "42", "\"value\"", "true"})
    void rejectsAnyNonObjectBody(String value) throws Exception {
        var node = json.readTree(value);
        assertCode(40001, () -> AuthRequests.fields(node, Set.of("nickname")));
    }

    @Test
    void missingNullAndNonTextFieldsAreNotCoerced() throws Exception {
        for (var body : java.util.List.of(json.readTree("{}"), json.readTree("{\"nickname\":null}"), json.readTree("{\"nickname\":1}"), json.readTree("{\"nickname\":[]}"))) {
            assertCode(40001, () -> AuthRequests.text(body, "nickname"));
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"13800138000", "19999999999", "13000000000"})
    void acceptsExactlyMainlandMobilePattern(String phone) { assertThat(AuthRequests.phone(phone)).isEqualTo(phone); }

    @ParameterizedTest
    @ValueSource(strings = {"", "1380013800", "138001380000", "12800138000", "+8613800138000", " 13800138000", "13800138000\n", "１３８００１３８０００"})
    void rejectsNonMainlandMobilePattern(String phone) { assertCode(40001, () -> AuthRequests.phone(phone)); }

    @Test
    void trimsNicknameAndAcceptsOneAndThirtyCharacterBounds() {
        assertThat(AuthRequests.nickname("  昵称  ")).isEqualTo("昵称");
        assertThat(AuthRequests.nickname("\u00a0\u2007昵称\u202f")).isEqualTo("昵称");
        assertThat(AuthRequests.nickname("名")).isEqualTo("名");
        assertThat(AuthRequests.nickname("名".repeat(30))).hasSize(30);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "\t\n", "\u2003", "\u00a0\u2007\u202f", "1234567890123456789012345678901", "含\u0000控制符", "含\n换行"})
    void rejectsEmptyOverlongOrControlCharacterNickname(String nickname) { assertCode(40001, () -> AuthRequests.nickname(nickname)); }

    @ParameterizedTest
    @ValueSource(strings = {"123456", "12345678901234567890", "Ab!@#$%^&*()_+-=", "密码安全测试"})
    void acceptsSixToTwentyVisibleNonWhitespacePassword(String password) { assertThat(AuthRequests.password(password)).isEqualTo(password); }

    @ParameterizedTest
    @ValueSource(strings = {"", "12345", "123456789012345678901", "abc def", "abc\tdef", "abc\ndef", "abc\u00a0def", "abc\u2003def", "abc\u200bdef", "abc\u0000def"})
    void rejectsPasswordLengthWhitespaceControlAndInvisibleFormatCharacters(String password) {
        assertCode(40001, () -> AuthRequests.password(password));
    }

    @Test
    void registrationCollectsAllIndependentFieldsAndMultipleMessagesForOneField() {
        var body = json.createObjectNode().put("phone", "rejected-phone-secret")
                .put("nickname", "   ").put("password", "a b");
        var error = catchThrowableOfType(() -> AuthRequests.registration(body), FieldValidationException.class);
        assertThat(error.errorCode().code()).isEqualTo(40001);
        assertThat(error.fieldErrors()).containsOnlyKeys("phone", "nickname", "password");
        assertThat(error.fieldErrors().get("phone")).containsExactly("手机号格式不正确");
        assertThat(error.fieldErrors().get("nickname")).containsExactly("昵称不能为空");
        assertThat(error.fieldErrors().get("password"))
                .containsExactly("密码长度必须为6到20个字符", "密码不能包含空白字符");
        assertThat(error.fieldErrors().toString()).doesNotContain("rejected-phone-secret", "a b", "_global");
    }

    @Test
    void missingNullAndWrongTypeStopOnlyTheirOwnFieldChecks() {
        var body = json.createObjectNode().putNull("nickname").put("password", 123);
        var error = catchThrowableOfType(() -> AuthRequests.registration(body), FieldValidationException.class);
        assertThat(error.fieldErrors()).containsOnlyKeys("phone", "nickname", "password");
        assertThat(error.fieldErrors().get("phone")).containsExactly("手机号不能为空");
        assertThat(error.fieldErrors().get("nickname")).containsExactly("昵称不能为空");
        assertThat(error.fieldErrors().get("password")).containsExactly("密码必须为字符串");
    }

    @Test
    void nicknameCollectsIndependentLengthAndControlCharacterFailures() {
        var error = catchThrowableOfType(() -> AuthRequests.nickname("名".repeat(31) + "\u0000"),
                FieldValidationException.class);
        assertThat(error.fieldErrors().get("nickname"))
                .containsExactly("昵称长度不能超过30个字符", "昵称不能包含控制字符");
    }

    @Test
    void loginUsesItsPublicUsernameNameAndCollectsPasswordFailure() {
        var body = json.createObjectNode().put("username", "invalid").put("password", "");
        var error = catchThrowableOfType(() -> AuthRequests.login(body), FieldValidationException.class);
        assertThat(error.fieldErrors()).containsOnlyKeys("username", "password");
        assertThat(error.fieldErrors().get("username")).containsExactly("账号格式不正确");
        assertThat(error.fieldErrors()).doesNotContainKey("phone");
    }

    @Test
    void existingPasswordChecksKeepTheirOriginalOneToOneHundredCharacterPolicy() {
        String existing = " \u0000".repeat(50);
        var login = AuthRequests.login(json.createObjectNode().put("username", "13800138000").put("password", existing));
        assertThat(login.password()).isEqualTo(existing);
        var change = AuthRequests.passwordChange(json.createObjectNode().put("oldPassword", existing)
                .put("newPassword", "Secret!42"));
        assertThat(change.oldPassword()).isEqualTo(existing);
        var error = catchThrowableOfType(() -> AuthRequests.passwordChange(json.createObjectNode()
                .put("oldPassword", "x".repeat(101)).put("newPassword", "Secret!42")), FieldValidationException.class);
        assertThat(error.fieldErrors()).containsOnlyKeys("oldPassword");
    }

    @Test
    void passwordChangeCollectsOldAndNewPasswordFieldsWithCamelCaseNames() {
        var body = json.createObjectNode().put("oldPassword", "").put("newPassword", "a b");
        var error = catchThrowableOfType(() -> AuthRequests.passwordChange(body), FieldValidationException.class);
        assertThat(error.fieldErrors()).containsOnlyKeys("oldPassword", "newPassword");
        assertThat(error.fieldErrors().get("oldPassword")).containsExactly("原密码不能为空");
        assertThat(error.fieldErrors().get("newPassword"))
                .containsExactly("新密码长度必须为6到20个字符", "新密码不能包含空白字符");
    }

    @ParameterizedTest
    @ValueSource(strings = {"unexpected", "token_version", "nested.value", "items[0]", "__proto__", "prototype", "constructor", "new\nfield"})
    void registrationUnknownNamesNeverBecomeInventedPublicFields(String name) {
        var body = json.createObjectNode().put("phone", "13800138000").put("nickname", "有效昵称")
                .put("password", "Secret!42").put(name, "not reflected");
        var error = catchThrowableOfType(() -> AuthRequests.registration(body), BusinessException.class);
        assertThat(error.errorCode().code()).isEqualTo(40001);
        assertThat(error).isNotInstanceOf(FieldValidationException.class);
    }

    @Test
    void unknownFieldDoesNotHideOtherReliablePublicFieldFailures() {
        var body = json.createObjectNode().put("phone", "invalid").put("nickname", "有效昵称")
                .put("password", "Secret!42").put("unknown_name", "not reflected");
        var error = catchThrowableOfType(() -> AuthRequests.registration(body), FieldValidationException.class);
        assertThat(error.fieldErrors()).containsOnlyKeys("phone");
    }

    @ParameterizedTest
    @ValueSource(strings = {"role", "status", "merchantId", "tokenVersion"})
    void registrationProtectedFieldsStillWinOverEveryCollectedParameterProblem(String field) {
        var body = json.createObjectNode().put("phone", "invalid").put("nickname", "")
                .put("password", "bad").put("unexpected", "value").put(field, "not permitted");
        assertCode(40302, () -> AuthRequests.registration(body));
    }

    @Test
    void validOperationValuesArePreservedAndOnlyNicknameIsTrimmed() {
        var body = json.createObjectNode().put("phone", "13800138000").put("nickname", "\u00a0昵称\u202f")
                .put("password", "Secret!42");
        var input = AuthRequests.registration(body);
        assertThat(input.phone()).isEqualTo("13800138000");
        assertThat(input.nickname()).isEqualTo("昵称");
        assertThat(input.password()).isEqualTo("Secret!42");
    }

    private static void assertCode(int code, org.assertj.core.api.ThrowableAssert.ThrowingCallable action) {
        assertThatThrownBy(action).isInstanceOf(BusinessException.class)
                .satisfies(error -> assertThat(((BusinessException) error).errorCode().code()).isEqualTo(code));
    }
}
