package com.cake.platform.merchant;

import java.math.BigInteger;
import java.util.Map;
import com.cake.platform.dto.PublicMerchantRequests;
import com.cake.platform.exception.BusinessException;
import com.cake.platform.exception.FieldValidationException;
import com.cake.platform.security.AuthFailure;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.*;

/** API-33/34 parameter contract without a database. */
class PublicMerchantRequestsTests {
    private final ObjectMapper json = new ObjectMapper();

    @Test void listDefaultsAndLiteralKeywordEscaping() {
        var defaults = PublicMerchantRequests.list(Map.of());
        assertThat(defaults.page()).isEqualTo(1);
        assertThat(defaults.size()).isEqualTo(10);
        assertThat(defaults.pattern()).isNull();

        var literal = PublicMerchantRequests.list(Map.of("keyword", new String[]{" %_\\! "}));
        assertThat(literal.pattern()).isEqualTo("%!%!_\\!!%");
    }

    @Test void listRejectsInvalidPaginationAndDuplicates() {
        assertThatThrownBy(() -> PublicMerchantRequests.list(Map.of("page", new String[]{"0"},
                "size", new String[]{"101"}, "keyword", new String[]{"k".repeat(61)})))
                .isInstanceOfSatisfying(FieldValidationException.class,
                        error -> assertThat(error.fieldErrors()).containsOnlyKeys("page", "size", "keyword"));
        assertThatThrownBy(() -> PublicMerchantRequests.list(Map.of("keyword", new String[]{"a", "b"})))
                .isInstanceOfSatisfying(FieldValidationException.class,
                        error -> assertThat(error.fieldErrors()).containsOnlyKeys("keyword"));
    }

    @Test void listForbidsStatusAndClientIdentity() {
        assertThatThrownBy(() -> PublicMerchantRequests.list(Map.of("status", new String[]{"1"})))
                .isInstanceOf(AuthFailure.class);
        assertThatThrownBy(() -> PublicMerchantRequests.list(Map.of("merchantId", new String[]{"1"})))
                .isInstanceOf(AuthFailure.class);
        assertThatThrownBy(() -> PublicMerchantRequests.list(Map.of("unexpected", new String[]{"x"})))
                .isInstanceOf(BusinessException.class).isNotInstanceOf(FieldValidationException.class);
    }

    @Test void offsetOverflowReturnsNoSqlOffset() {
        var query = PublicMerchantRequests.list(Map.of("page", new String[]{Long.toString(Long.MAX_VALUE)},
                "size", new String[]{"100"}));
        assertThat(query.offset()).isNull();
    }

    @ParameterizedTest @ValueSource(strings = {"0", "-1", "01", "1.0", "18446744073709551616", "abc", " "})
    void pathIdRejectsInvalidAndOverflowingValues(String value) {
        assertThatThrownBy(() -> PublicMerchantRequests.id(value))
                .isInstanceOfSatisfying(FieldValidationException.class,
                        error -> assertThat(error.fieldErrors()).containsOnlyKeys("id"));
    }

    @Test void pathIdPreservesUnsignedMax() {
        assertThat(PublicMerchantRequests.id("18446744073709551615"))
                .isEqualTo(new BigInteger("18446744073709551615"));
    }

    @Test void bodyAndQueryGuardsKeepProtectedFieldsFirst() throws Exception {
        assertThatThrownBy(() -> PublicMerchantRequests.noBody(json.readTree("{\"status\":1}")))
                .isInstanceOf(AuthFailure.class);
        assertThatThrownBy(() -> PublicMerchantRequests.noBody(json.readTree("{}")))
                .isInstanceOf(BusinessException.class).isNotInstanceOf(FieldValidationException.class);
        assertThatThrownBy(() -> PublicMerchantRequests.noQuery(Map.of("merchantId", new String[]{"1"})))
                .isInstanceOf(AuthFailure.class);
        assertThatThrownBy(() -> PublicMerchantRequests.noQuery(Map.of("x", new String[]{"1"})))
                .isInstanceOf(BusinessException.class).isNotInstanceOf(FieldValidationException.class);
    }
}
