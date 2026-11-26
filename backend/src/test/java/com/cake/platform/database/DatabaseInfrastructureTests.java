package com.cake.platform.database;

import java.net.ServerSocket;
import java.sql.SQLException;
import javax.sql.DataSource;

import com.cake.platform.config.DatabaseConnectionConfiguration;
import com.cake.platform.config.SqlEventLog;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(DatabaseInfrastructureTests.Fixtures.class)
@ExtendWith(OutputCaptureExtension.class)
class DatabaseInfrastructureTests {
    @Autowired private MockMvc mvc;

    @Test
    void vendorConnectionDetailsNeverEscape() throws Exception {
        DataSource target = mock(DataSource.class);
        when(target.getConnection()).thenThrow(new SQLException("password=TEST_PRIVATE_SECRET jdbc:mysql://private-host"));
        var safe = new DatabaseConnectionConfiguration.SafeDataSource(target);
        assertThatThrownBy(safe::getConnection).isInstanceOf(SQLException.class)
                .hasMessage(DatabaseConnectionConfiguration.SAFE_FAILURE).hasNoCause();
    }

    @Test
    void explicitCredentialConnectionAlsoRemovesVendorDetails() throws Exception {
        DataSource target = mock(DataSource.class);
        when(target.getConnection("test-user", "TEST_PRIVATE_SECRET"))
                .thenThrow(new SQLException("test-user TEST_PRIVATE_SECRET"));
        var safe = new DatabaseConnectionConfiguration.SafeDataSource(target);
        assertThatThrownBy(() -> safe.getConnection("test-user", "TEST_PRIVATE_SECRET"))
                .hasMessage(DatabaseConnectionConfiguration.SAFE_FAILURE).hasNoCause();
    }

    @Test
    void developmentSqlLogOmitsSqlParametersResultsAndExceptions(CapturedOutput output) {
        var logger = (ch.qos.logback.classic.Logger) org.slf4j.LoggerFactory.getLogger("com.cake.platform.sql.events");
        var originalLevel = logger.getLevel();
        try {
            logger.setLevel(ch.qos.logback.classic.Level.DEBUG);
            var log = new SqlEventLog("TEST_PRIVATE_NAMESPACE");
            log.debug("==>  Preparing: select 'TEST_PRIVATE_SECRET'");
            log.debug("==> Parameters: TEST_PRIVATE_SECRET(String)");
            log.debug("<== Total: TEST_PRIVATE_SECRET");
            log.trace("TEST_PRIVATE_SECRET");
            log.error("TEST_PRIVATE_SECRET", new SQLException("TEST_PRIVATE_SECRET"));
        } finally {
            logger.setLevel(originalLevel);
        }
        assertThat(output.getAll()).contains("sqlEvent=PREPARE", "sqlEvent=ERROR")
                .doesNotContain("TEST_PRIVATE_SECRET", "TEST_PRIVATE_NAMESPACE");
    }

    @Test
    void unreachableRealMysqlDriverUsesExistingSafePublicError(CapturedOutput output) throws Exception {
        mvc.perform(get("/__database_fixture/unreachable"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value(50001))
                .andExpect(jsonPath("$.msg").value("操作失败，请稍后重试"))
                .andExpect(jsonPath("$.data.requestId").isString());
        assertThat(output.getAll()).doesNotContain("TEST_PRIVATE_SECRET", "jdbc:mysql:");
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class Fixtures {
        @Bean OfflineController offlineController() { return new OfflineController(); }
    }

    @RestController
    static class OfflineController {
        @GetMapping("/__database_fixture/unreachable")
        void unreachable() throws Exception {
            int port;
            try (var socket = new ServerSocket(0, 1, java.net.InetAddress.getLoopbackAddress())) {
                port = socket.getLocalPort();
            }
            var config = new HikariConfig();
            config.setJdbcUrl("jdbc:mysql://127.0.0.1:" + port + "/unreachable?connectTimeout=250&socketTimeout=250");
            config.setUsername("test-user");
            config.setPassword("TEST_PRIVATE_SECRET");
            config.setMaximumPoolSize(1);
            config.setMinimumIdle(0);
            config.setConnectionTimeout(300);
            config.setInitializationFailTimeout(-1);
            try (var pool = new HikariDataSource(config)) {
                new DatabaseConnectionConfiguration.SafeDataSource(pool).getConnection().close();
            }
        }
    }
}
