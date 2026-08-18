package com.mms.data.recon.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import javax.sql.DataSource;

@Configuration
public class ReconJdbcFactory {

    @Bean(destroyMethod = "close")
    @Primary
    public DataSource dataSource(ReconDatabaseProperties database) {
        HikariConfig config = new HikariConfig();
        config.setPoolName("mms-recon-results");
        config.setJdbcUrl(database.jdbcUrl());
        config.setUsername(database.getUsername());
        config.setPassword(database.getPassword());
        config.setDriverClassName("org.postgresql.Driver");
        config.setMaximumPoolSize(Math.max(1, database.getMaximumPoolSize()));
        return new HikariDataSource(config);
    }
}
