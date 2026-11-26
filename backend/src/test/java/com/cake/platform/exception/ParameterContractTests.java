package com.cake.platform.exception;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.cake.platform.common.ApiResponse;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import jakarta.validation.Validator;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Min;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.validation.ObjectError;
import org.springframework.web.bind.annotation.*;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(ParameterContractTests.FixtureConfiguration.class)
class ParameterContractTests {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired Validator validator;

    @Test void singleFieldIsAnArrayOfStrings() throws Exception {
        mvc.perform(post("/__contract/input").contentType(MediaType.APPLICATION_JSON)
                .content("{\"phone\":\"bad\",\"nickname\":\"ok\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value(40001))
                .andExpect(jsonPath("$.msg").value("参数错误"))
                .andExpect(jsonPath("$.data.fieldErrors.phone[0]").value("手机号格式不正确"))
                .andExpect(jsonPath("$.data.fieldErrors.phone.length()").value(1));
    }

    @Test void collectsMultipleFieldsAndMultipleMessagesWithoutOverwriting() throws Exception {
        var response = mvc.perform(post("/__contract/input").contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(Map.of("phone", "bad", "nickname", " ".repeat(31)))))
                .andExpect(status().isBadRequest()).andReturn();
        var json = mapper.readTree(response.getResponse().getContentAsString());
        assertThat(json.properties()).extracting(Map.Entry::getKey).containsExactlyInAnyOrder("code", "msg", "data");
        var fields = json.path("data").path("fieldErrors");
        assertThat(fields.isObject()).isTrue();
        assertThat(fields.properties()).extracting(Map.Entry::getKey).containsExactlyInAnyOrder("phone", "nickname");
        assertThat(fields.path("nickname")).hasSize(2);
        fields.properties().forEach(entry -> {
            assertThat(entry.getKey()).matches("[a-z][A-Za-z0-9]*").isNotEqualTo("_global");
            assertThat(entry.getValue().isArray()).isTrue();
            entry.getValue().forEach(message -> assertThat(message.isTextual()).isTrue());
        });
    }

    @Test void nestedArrayValidationUsesJsonNamesInsteadOfJavaNames() throws Exception {
        mvc.perform(post("/__contract/nested").contentType(MediaType.APPLICATION_JSON)
                .content("{\"items\":[{\"displayName\":\"\"}]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.data.fieldErrors['items[0].displayName'][0]").value("名称不能为空"))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("internal_name"))));
    }

    @Test void nestedJsonTypeFailureUsesPublicNameAndNoParserDetails() throws Exception {
        mvc.perform(post("/__contract/nested").contentType(MediaType.APPLICATION_JSON)
                .content("{\"items\":[{\"displayName\":{\"secret\":true}}]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.data.fieldErrors['items[0].displayName'][0]").value("参数类型不正确"))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("secret"))));
    }

    @Test void methodConstraintUsesExplicitPublicQueryName() throws Exception {
        mvc.perform(get("/__contract/query").param("pageSize", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.data.fieldErrors.pageSize[0]").isString())
                .andExpect(jsonPath("$.data.fieldErrors.internal_count").doesNotExist());
    }

    @Test void queryTypeAndMissingParameterBothLocatePublicField() throws Exception {
        for (String uri : List.of("/__contract/query?pageSize=secret-value", "/__contract/query")) {
            mvc.perform(get(uri)).andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.data.fieldErrors.pageSize[0]").isString())
                    .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("secret-value"))));
        }
    }

    @Test void constraintViolationFromBeanResolvesAlias() throws Exception {
        mvc.perform(get("/__contract/constraint")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.data.fieldErrors.displayName[0]").value("名称不能为空"));
    }

    @Test void malformedBodyAndMissingBodyAndUnknownFieldRemainNull() throws Exception {
        for (String body : List.of("{secret", "", "{\"unexpected\":true}")) {
            mvc.perform(post("/__contract/input").contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value(40001))
                    .andExpect(jsonPath("$.data").isEmpty())
                    .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("_global"))));
        }
    }

    @Test void onlyObjectErrorHasNoInventedField() {
        var errors = new BeanPropertyBindingResult(new Alias("ok"), "internalBean");
        errors.addError(new ObjectError("internalBean", "SELECT secret"));
        assertThat(ValidationErrorExtractor.extract(errors)).isEmpty();
    }

    @Test void globalErrorAlongsideFieldKeepsOnlyRealField() {
        var errors = new BeanPropertyBindingResult(new Alias(""), "internalBean");
        errors.addError(new ObjectError("internalBean", "SELECT secret"));
        errors.addError(new FieldError("internalBean", "internal_name", "名称不能为空"));
        assertThat(ValidationErrorExtractor.extract(errors)).containsOnlyKeys("displayName");
    }

    @Test void bindingAndCustomTemplateCannotLeakRejectedValues() {
        var errors = new BeanPropertyBindingResult(new Alias(""), "fixture");
        errors.addError(new FieldError("fixture", "internal_name", "private-password", true,
                null, null, "Failed to convert private-password java.lang.String SQL"));
        errors.addError(new FieldError("fixture", "internal_name", "private-password", false,
                null, null, "validatedValue=private-password"));
        assertThat(ValidationErrorExtractor.extract(errors).get("displayName"))
                .containsExactly("参数类型不正确", "参数错误");
    }

    @Test void unmappedAndNonCamelCasePathsAreNotExposed() {
        var errors = new BeanPropertyBindingResult(new Alias(""), "fixture");
        errors.addError(new FieldError("fixture", "unknown_field", "secret"));
        errors.addError(new FieldError("fixture", "_global", "secret"));
        assertThat(ValidationErrorExtractor.extract(errors)).isEmpty();
    }

    @Test void fieldExceptionAndPayloadAreDeeplyImmutableAndDeduplicateMessages() {
        var messages = new ArrayList<>(List.of("名称不能为空", "名称不能为空"));
        Map<String, List<String>> source = new LinkedHashMap<>();
        source.put("displayName", messages);
        var exception = new FieldValidationException(source);
        messages.add("later"); source.clear();
        assertThat(exception.errorCode()).isEqualTo(CommonErrorCode.INVALID_PARAMETER);
        assertThat(exception.fieldErrors().get("displayName")).containsExactly("名称不能为空");
        assertThatThrownBy(() -> exception.fieldErrors().put("x", List.of("x")))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> exception.fieldErrors().get("displayName").add("x"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test void emptyAndUnlocatableFieldExceptionsSerializeAsNull() throws Exception {
        mvc.perform(get("/__contract/empty")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.data").isEmpty());
    }

    @Test void successEnvelopeStillContainsExactlyThreeFields() throws Exception {
        var json = mapper.readTree(mapper.writeValueAsString(ApiResponse.success(null)));
        assertThat(json.properties()).extracting(Map.Entry::getKey).containsExactlyInAnyOrder("code", "msg", "data");
        assertThat(json.path("code").asInt()).isEqualTo(200);
        assertThat(json.path("data").isNull()).isTrue();
    }

    @Test void defaultConstraintMessagesRemainDistinctWithoutExposingTemplates() throws Exception {
        mvc.perform(post("/__contract/defaults").contentType(MediaType.APPLICATION_JSON)
                .content("{\"nickname\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.data.fieldErrors.nickname", org.hamcrest.Matchers.containsInAnyOrder(
                        "参数不能为空", "参数长度不符合要求")));
    }

    @Test void methodValidationCollectsNestedBeanAndQueryErrorsTogether() throws Exception {
        mvc.perform(post("/__contract/mixed").param("pageSize", "0")
                .contentType(MediaType.APPLICATION_JSON).content("{\"displayName\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.data.fieldErrors.displayName[0]").value("名称不能为空"))
                .andExpect(jsonPath("$.data.fieldErrors.pageSize[0]").isString());
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class FixtureConfiguration {
        @Bean FixtureController contractFixture(Validator validator) { return new FixtureController(validator); }
    }

    @RestController
    static class FixtureController {
        private final Validator validator;
        FixtureController(Validator validator) { this.validator = validator; }
        @PostMapping("/__contract/input") void input(@Valid @RequestBody Input input) { }
        @PostMapping("/__contract/nested") void nested(@Valid @RequestBody Nested input) { }
        @GetMapping("/__contract/query") void query(@RequestParam(name = "pageSize") @Min(1) int internal_count) { }
        @PostMapping("/__contract/defaults") void defaults(@Valid @RequestBody DefaultInput input) { }
        @PostMapping("/__contract/mixed") void mixed(@Valid @RequestBody Alias input,
                @RequestParam(name = "pageSize") @Min(1) int internal_count) { }
        @GetMapping("/__contract/constraint") void constraint() {
            throw new ConstraintViolationException(validator.validate(new Alias("")));
        }
        @GetMapping("/__contract/empty") void empty() {
            throw new FieldValidationException(Map.of("_global", List.of("参数错误")));
        }
    }

    record Input(@Pattern(regexp="1[3-9][0-9]{9}", message="手机号格式不正确") String phone,
                 @NotBlank(message="昵称不能为空") @Size(max=30, message="昵称长度不能超过30个字符") String nickname) { }
    record Alias(@JsonProperty("displayName") @NotBlank(message="名称不能为空") String internal_name) { }
    record Nested(@Valid List<Alias> items) { }
    record DefaultInput(@NotBlank @Size(min=2) String nickname) { }
}
