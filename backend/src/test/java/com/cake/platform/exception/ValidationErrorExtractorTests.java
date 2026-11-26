package com.cake.platform.exception;

import org.junit.jupiter.api.Test;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ValidationErrorExtractorTests {
    @Test
    void extractsFieldLocationsAndMessagesWithoutRejectedSecrets() {
        var binding = new BeanPropertyBindingResult(new Input(null, null), "fixture");
        binding.addError(new FieldError("fixture", "password", "secret-value", false,
                null, null, "密码长度不符合要求"));
        binding.addError(new FieldError("fixture", "password", "secret-value", false,
                null, null, "密码不能为空"));
        binding.addError(new FieldError("fixture", "displayName", "名称不能为空"));
        var errors = ValidationErrorExtractor.extract(binding);
        assertThat(errors).containsOnlyKeys("password", "displayName");
        assertThat(errors.get("password")).containsExactly("密码长度不符合要求", "密码不能为空");
        assertThat(errors.toString()).doesNotContain("secret-value");
        assertThatThrownBy(() -> errors.put("extra", java.util.List.of("error")))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    record Input(String password, String displayName) { }
}
