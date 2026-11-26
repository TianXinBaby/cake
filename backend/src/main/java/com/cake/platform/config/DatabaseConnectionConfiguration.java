package com.cake.platform.config;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.SQLTransientConnectionException;
import javax.sql.DataSource;

import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.datasource.DelegatingDataSource;

/** Database infrastructure only; never initializes or migrates application tables. */
@Configuration(proxyBeanMethods = false)
@Profile({"dev", "prod", "mysql-test"})
@EnableConfigurationProperties(DataSourceProperties.class)
public class DatabaseConnectionConfiguration {
    public static final String SAFE_FAILURE = "数据库连接失败，请检查本机数据库服务、网络和环境变量配置。";

    @Bean(name = "databasePool", destroyMethod = "close")
    @ConfigurationProperties("spring.datasource.hikari")
    HikariDataSource databasePool(DataSourceProperties properties) {
        if (properties.getUsername() == null || properties.getUsername().isBlank()
                || properties.getPassword() == null || properties.getPassword().isBlank()) {
            throw new IllegalStateException("数据库凭据未配置，请在本机交互设置 DB_USERNAME 和 DB_PASSWORD。");
        }
        return properties.initializeDataSourceBuilder().type(HikariDataSource.class).build();
    }

    @Bean
    @Primary
    DataSource dataSource(@Qualifier("databasePool") HikariDataSource pool) {
        return new SafeDataSource(pool);
    }

    @Bean
    ApplicationRunner verifyDatabaseConnection(DataSource dataSource) {
        return args -> {
            try (Connection connection = dataSource.getConnection()) {
                if (!connection.isValid(2)) {
                    throw new SQLTransientConnectionException(SAFE_FAILURE);
                }
            } catch (SQLException exception) {
                throw new SQLTransientConnectionException(SAFE_FAILURE, "08001");
            }
        };
    }

    /** Discards vendor messages and causes because these may include local credentials or hosts. */
    public static final class SafeDataSource extends DelegatingDataSource {
        public SafeDataSource(DataSource target) {
            super(target);
        }

        @Override
        public Connection getConnection() throws SQLException {
            try {
                return super.getConnection();
            } catch (SQLException exception) {
                throw new SQLTransientConnectionException(SAFE_FAILURE, "08001");
            }
        }

        @Override
        public Connection getConnection(String username, String password) throws SQLException {
            try {
                return super.getConnection(username, password);
            } catch (SQLException exception) {
                throw new SQLTransientConnectionException(SAFE_FAILURE, "08001");
            }
        }
    }
}
