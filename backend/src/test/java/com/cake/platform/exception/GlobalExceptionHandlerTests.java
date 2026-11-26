package com.cake.platform.exception;

import java.util.Set;
import java.util.UUID;

import com.cake.platform.common.ApiResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(GlobalExceptionHandlerTests.FixtureConfiguration.class)
@ExtendWith(OutputCaptureExtension.class)
class GlobalExceptionHandlerTests {
    @Autowired
    private MockMvc mvc;
    @Autowired
    private ObjectMapper mapper;

    @Test
    void successUsesHttp200AndBusiness200() throws Exception {
        mvc.perform(get("/__fixture/success"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.msg").value("success"))
                .andExpect(jsonPath("$.data").value("ready"));
    }

    @Test
    void envelopeCanBeUsedWithHttp201WithoutChangingBusinessCode() throws Exception {
        mvc.perform(get("/__fixture/created"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.code").value(200));
    }

    @Test
    void invalidBeanReturnsRequirement400AndNoRejectedValues() throws Exception {
        var result = mvc.perform(post("/__fixture/validated").contentType(MediaType.APPLICATION_JSON)
                .content("{\"displayName\":\"\",\"password\":\"sensitive-password\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value(40001))
                .andExpect(jsonPath("$.msg").value("参数错误"))
                .andExpect(jsonPath("$.data.fieldErrors.displayName[0]").value("名称不能为空")).andReturn();
        assertEnvelopeOnly(result);
        assertThat(result.getResponse().getContentAsString()).doesNotContain("sensitive-password");
    }

    @Test
    void malformedJsonIs400WithoutParserDetails() throws Exception {
        var result = mvc.perform(post("/__fixture/validated").contentType(MediaType.APPLICATION_JSON)
                .content("{\"displayName\": internal-json-secret"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value(40001))
                .andExpect(jsonPath("$.msg").value("参数错误")).andReturn();
        assertThat(result.getResponse().getContentAsString())
                .doesNotContain("internal-json-secret", "JsonParseException", "line", "column");
    }

    @Test
    void missingRequestBodyIs400() throws Exception {
        mvc.perform(post("/__fixture/validated").contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value(40001));
    }

    @Test
    void invalidJsonValueTypeIs400() throws Exception {
        mvc.perform(post("/__fixture/validated").contentType(MediaType.APPLICATION_JSON)
                .content("{\"displayName\":{},\"password\":\"secret\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value(40001));
    }

    @Test
    void unknownOrdinaryJsonFieldIsRejected() throws Exception {
        mvc.perform(post("/__fixture/validated").contentType(MediaType.APPLICATION_JSON)
                .content("{\"displayName\":\"ok\",\"password\":\"secret\",\"unexpected\":true}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value(40001));
    }

    @Test
    void methodInputValidationIs400() throws Exception {
        mvc.perform(get("/__fixture/method-validation").param("count", "0"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value(40001));
    }

    @Test
    void missingQueryParameterUsesTheRequirement400Envelope() throws Exception {
        mvc.perform(get("/__fixture/method-validation"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value(40001))
                .andExpect(jsonPath("$.msg").value("参数错误"));
    }

    @Test
    void queryTypeMismatchDoesNotRevealItsRejectedValue() throws Exception {
        var result = mvc.perform(get("/__fixture/method-validation").param("count", "private-query-secret"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value(40001)).andReturn();
        assertThat(result.getResponse().getContentAsString()).doesNotContain("private-query-secret");
    }

    @Test
    void serverReturnValidationIs500() throws Exception {
        mvc.perform(get("/__fixture/invalid-return"))
                .andExpect(status().isInternalServerError()).andExpect(jsonPath("$.code").value(50001));
    }

    @Test
    void constraintViolationsAre400() throws Exception {
        mvc.perform(get("/__fixture/constraint"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value(40001));
    }

    @Test
    void baseBusinessExceptionUsesItsRequirementMapping() throws Exception {
        mvc.perform(get("/__fixture/business"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value(40001))
                .andExpect(jsonPath("$.msg").value("参数错误"));
    }

    @Test
    void systemBusinessExceptionIncludesTraceId() throws Exception {
        mvc.perform(get("/__fixture/business-system"))
                .andExpect(status().isInternalServerError()).andExpect(jsonPath("$.code").value(50001))
                .andExpect(jsonPath("$.data.requestId").isString());
    }

    @Test
    void unknownFailureIs500WithoutInternalDetailsAndTraceIdIsUnique() throws Exception {
        var first = unknownFailure();
        var second = unknownFailure();
        assertEnvelopeOnly(first);
        String firstId = mapper.readTree(first.getResponse().getContentAsString()).path("data")
                .path("requestId").asText();
        String secondId = mapper.readTree(second.getResponse().getContentAsString()).path("data")
                .path("requestId").asText();
        assertThat(UUID.fromString(firstId).toString()).isEqualTo(firstId);
        assertThat(firstId).isNotEqualTo(secondId);
        assertThat(first.getResponse().getContentAsString())
                .doesNotContain("SELECT", "password", "RuntimeException", "stackTrace", "token-secret");
    }

    @Test
    void traceLogsExcludeRequestBodyHeadersQueryAndPathValues(CapturedOutput output) throws Exception {
        var result = mvc.perform(post("/__fixture/fail/private-path-secret")
                .contentType(MediaType.APPLICATION_JSON).content("{\"password\":\"private-body-secret\"}")
                .queryParam("token", "private-query-secret")
                .header("Authorization", "Bearer private-jwt-secret")
                .header("X-Request-ID", "private-caller-request-secret"))
                .andExpect(status().isInternalServerError()).andReturn();
        String id = mapper.readTree(result.getResponse().getContentAsString()).path("data")
                .path("requestId").asText();
        assertThat(output.getAll()).contains("requestId=" + id, "route=/__fixture/fail/{value}")
                .doesNotContain("private-body-secret", "private-query-secret", "private-jwt-secret",
                        "private-caller-request-secret", "private-path-secret", "SELECT password", "token-secret");
    }

    @Test
    void unimplementedRequirementPathRemains404() throws Exception {
        mvc.perform(get("/api/products").param("merchantId", "1")).andExpect(status().isNotFound());
    }

    private MvcResult unknownFailure() throws Exception {
        return mvc.perform(get("/__fixture/unknown"))
                .andExpect(status().isInternalServerError()).andExpect(jsonPath("$.code").value(50001))
                .andExpect(jsonPath("$.msg").value("操作失败，请稍后重试")).andReturn();
    }

    private void assertEnvelopeOnly(MvcResult result) throws Exception {
        var json = mapper.readTree(result.getResponse().getContentAsString());
        assertThat(json.properties()).extracting(java.util.Map.Entry::getKey)
                .containsExactlyInAnyOrder("code", "msg", "data");
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class FixtureConfiguration {
        @Bean
        FixtureController fixtureController() {
            return new FixtureController();
        }
    }

    /** Test fixtures are excluded from the production artifact. */
    @RestController
    static class FixtureController {
        @GetMapping("/__fixture/success")
        ApiResponse<String> success() {
            return ApiResponse.success("ready");
        }

        @GetMapping("/__fixture/created")
        ResponseEntity<ApiResponse<Object>> created() {
            return ResponseEntity.status(201).body(ApiResponse.success(null));
        }

        @PostMapping("/__fixture/validated")
        ApiResponse<Object> validated(@Valid @RequestBody Input input) {
            return ApiResponse.success(null);
        }

        @GetMapping("/__fixture/method-validation")
        ApiResponse<Object> methodValidation(@RequestParam @Min(1) int count) {
            return ApiResponse.success(null);
        }

        @GetMapping("/__fixture/invalid-return")
        @Min(1)
        int invalidReturn() {
            return 0;
        }

        @GetMapping("/__fixture/constraint")
        void constraint() {
            throw new ConstraintViolationException(Set.of());
        }

        @GetMapping("/__fixture/business")
        void business() {
            throw new BusinessException(CommonErrorCode.INVALID_PARAMETER);
        }

        @GetMapping("/__fixture/business-system")
        void businessSystem() {
            throw new BusinessException(CommonErrorCode.INTERNAL_ERROR);
        }

        @GetMapping("/__fixture/unknown")
        void unknown() {
            throw new IllegalStateException("SELECT password FROM secret; JWT=token-secret");
        }

        @PostMapping("/__fixture/fail/{value}")
        void failingPost(@PathVariable String value, @RequestBody Input input) {
            throw new IllegalStateException("SELECT password; JWT=token-secret");
        }
    }

    record Input(@NotBlank(message = "名称不能为空") String displayName, String password) { }
}
