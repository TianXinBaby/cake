package com.cake.platform.admin;

import java.math.BigInteger;
import java.util.Map;
import com.cake.platform.dto.AdminRequests;
import com.cake.platform.exception.FieldValidationException;
import com.cake.platform.security.AuthFailure;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.*;

class AdminRequestsTests {
    private final ObjectMapper json = new ObjectMapper();

    @Test void creationTrimsBusinessTextAndKeepsBothPhoneFieldsDistinct() {
        var input = AdminRequests.creation(json.valueToTree(Map.of("accountPhone", "13900000001",
                "nickname", "  商家  ", "password", "Fixture!42", "name", "  同名店铺  ",
                "phone", "13800000002", "address", "  测试地址  ", "description", "  简介  ")));
        assertThat(input.accountPhone()).isEqualTo("13900000001");
        assertThat(input.phone()).isEqualTo("13800000002");
        assertThat(input.nickname()).isEqualTo("商家");
        assertThat(input.name()).isEqualTo("同名店铺");
        assertThat(input.address()).isEqualTo("测试地址");
        assertThat(input.description()).isEqualTo("简介");
    }

    @Test void creationCollectsActualFieldsAndMultiplePasswordMessages() {
        assertThatThrownBy(() -> AdminRequests.creation(json.valueToTree(Map.of("accountPhone", "x",
                "nickname", " ", "password", "a b", "name", "", "phone", 123, "address", ""))))
                .isInstanceOfSatisfying(FieldValidationException.class, error -> {
                    assertThat(error.fieldErrors()).containsOnlyKeys("accountPhone", "nickname", "password", "name", "phone", "address");
                    assertThat(error.fieldErrors().get("password")).hasSize(2);
                });
    }

    @ParameterizedTest @ValueSource(strings = {"role", "status", "userId", "merchantId", "tokenVersion", "username"})
    void protectedFieldsTakePriorityOverMissingAndUnknownInput(String field) {
        assertThatThrownBy(() -> AdminRequests.creation(json.valueToTree(Map.of(field, "x", "unexpected", "x"))))
                .isInstanceOf(AuthFailure.class);
    }

    @Test void editingCannotChangeAccountPhone() {
        assertThatThrownBy(() -> AdminRequests.edit(json.valueToTree(Map.of("accountPhone", "13900000001"))))
                .isInstanceOf(AuthFailure.class);
    }

    @ParameterizedTest @ValueSource(strings = {"0", "-1", "01", "1.0", "18446744073709551616", "abc"})
    void unsignedIdsRejectInvalidAndOverflowingValues(String value) {
        assertThatThrownBy(() -> AdminRequests.id(value)).isInstanceOfSatisfying(FieldValidationException.class,
                error -> assertThat(error.fieldErrors()).containsOnlyKeys("id"));
    }

    @Test void unsignedIdPreservesFullMysqlRange() {
        assertThat(AdminRequests.id("18446744073709551615")).isEqualTo(new BigInteger("18446744073709551615"));
    }

    @ParameterizedTest @ValueSource(strings = {"\"1\"", "true", "1.0", "2", "null", "-1"})
    void statusRequiresJsonIntegerZeroOrOne(String value) throws Exception {
        assertThatThrownBy(() -> AdminRequests.status(json.readTree("{\"status\":" + value + "}")))
                .isInstanceOfSatisfying(FieldValidationException.class, error ->
                        assertThat(error.fieldErrors()).containsOnlyKeys("status"));
    }

    @Test void paginationDefaultsAndLiteralWildcardEscaping() {
        var defaults = AdminRequests.list(Map.of());
        assertThat(defaults.page()).isEqualTo(1);
        assertThat(defaults.size()).isEqualTo(10);
        assertThat(AdminRequests.list(Map.of("keyword", new String[]{" %_\\ "})).pattern()).isEqualTo("%!%!_\\%");
    }

    @Test void pageOffsetOverflowReturnsNoSqlOffsetInsteadOfWrapping() {
        var input = AdminRequests.list(Map.of("page", new String[]{Long.toString(Long.MAX_VALUE)}, "size", new String[]{"100"}));
        assertThat(input.offset()).isNull();
    }

    @Test void queryCollectsActualFieldsAndRejectsDuplicates() {
        assertThatThrownBy(() -> AdminRequests.list(Map.of("page", new String[]{"0"},
                "size", new String[]{"101"}, "status", new String[]{"1", "0"}, "keyword", new String[]{"x".repeat(61)})))
                .isInstanceOfSatisfying(FieldValidationException.class, error ->
                        assertThat(error.fieldErrors()).containsOnlyKeys("page", "size", "status", "keyword"));
    }

    @Test void unknownInputHasNoInventedFieldLocation() {
        assertThatThrownBy(() -> AdminRequests.list(Map.of("unexpected", new String[]{"x"})))
                .isInstanceOf(com.cake.platform.exception.BusinessException.class)
                .isNotInstanceOf(FieldValidationException.class);
    }

    @Test void roleQueryIsForbiddenEvenWithOrdinaryUnknownFields() {
        assertThatThrownBy(() -> AdminRequests.list(Map.of("role", new String[]{"CUSTOMER"}, "unexpected", new String[]{"x"})))
                .isInstanceOf(AuthFailure.class);
    }
}
