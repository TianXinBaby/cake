package com.cake.platform.config;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.TimeZone;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.module.SimpleModule;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class JacksonConfiguration {
    private static final ZoneId SHANGHAI = ZoneId.of("Asia/Shanghai");
    private static final DateTimeFormatter MILLISECOND_TIME =
            DateTimeFormatter.ofPattern("uuuu-MM-dd'T'HH:mm:ss.SSSXXX");

    @Bean
    Jackson2ObjectMapperBuilderCustomizer localTimeSerialization() {
        SimpleModule module = new SimpleModule();
        module.addSerializer(LocalDateTime.class, new JsonSerializer<>() {
            @Override
            public void serialize(LocalDateTime value, JsonGenerator generator, SerializerProvider serializers)
                    throws java.io.IOException {
                generator.writeString(value.atZone(SHANGHAI).format(MILLISECOND_TIME));
            }
        });
        module.addSerializer(OffsetDateTime.class, new JsonSerializer<>() {
            @Override
            public void serialize(OffsetDateTime value, JsonGenerator generator, SerializerProvider serializers)
                    throws java.io.IOException {
                generator.writeString(value.atZoneSameInstant(SHANGHAI).format(MILLISECOND_TIME));
            }
        });
        return builder -> builder.timeZone(TimeZone.getTimeZone(SHANGHAI)).modulesToInstall(module);
    }
}
