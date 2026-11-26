package com.cake.platform;

import javax.sql.DataSource;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class CakeOrderPlatformApplicationTests {
    @Autowired
    private ApplicationContext context;

    @Test
    void contextLoadsWithoutDatabase() {
        assertThat(context).isNotNull();
        assertThat(context.getBeansOfType(DataSource.class)).isEmpty();
    }
}
