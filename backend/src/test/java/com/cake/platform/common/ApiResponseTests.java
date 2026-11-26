package com.cake.platform.common;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class ApiResponseTests {
    @Autowired
    private ObjectMapper mapper;

    @Test
    void successUsesOnlyRequirementFieldsAndSupportsTypedData() throws Exception {
        var response = ApiResponse.success(Map.of("displayName", "基础环境"));
        var json = mapper.readTree(mapper.writeValueAsString(response));
        assertThat(json.properties()).extracting(Map.Entry::getKey)
                .containsExactlyInAnyOrder("code", "msg", "data");
        assertThat(json.get("code").intValue()).isEqualTo(200);
        assertThat(json.get("msg").textValue()).isEqualTo("success");
        assertThat(json.path("data").path("displayName").textValue()).isEqualTo("基础环境");
    }

    @Test
    void successfulNullDataRemainsPresent() throws Exception {
        var json = mapper.readTree(mapper.writeValueAsString(ApiResponse.success(null)));
        assertThat(json.has("data")).isTrue();
        assertThat(json.get("data").isNull()).isTrue();
    }

    @Test
    void failureUsesTheExactRequirementEnvelope() throws Exception {
        var response = ApiResponse.failure(40001, "参数错误", null);
        assertThat(mapper.readTree(mapper.writeValueAsString(response)))
                .isEqualTo(mapper.readTree("{\"code\":40001,\"msg\":\"参数错误\",\"data\":null}"));
    }

    @Test
    void localDateTimeSerializesWithShanghaiOffsetAndMilliseconds() throws Exception {
        var time = LocalDateTime.parse("2026-11-24T15:00:00");
        assertThat(mapper.writeValueAsString(time)).isEqualTo("\"2026-11-24T15:00:00.000+08:00\"");
    }

    @Test
    void offsetTimeConvertsTheSameInstantToShanghai() throws Exception {
        var time = OffsetDateTime.parse("2026-11-24T07:00:00.123Z");
        assertThat(mapper.writeValueAsString(time)).isEqualTo("\"2026-11-24T15:00:00.123+08:00\"");
    }

    @Test
    void eventNullRemainsJsonNull() throws Exception {
        record Event(LocalDateTime completeTime) { }
        assertThat(mapper.writeValueAsString(new Event(null))).isEqualTo("{\"completeTime\":null}");
    }
}
