package com.mms.data.recon.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import io.micronaut.context.annotation.Bean;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Primary;
import jakarta.inject.Named;
import jakarta.inject.Singleton;

@Factory
public class ReconJdbcFactory {

    @Singleton
    @Primary
    @Named("default")
    @Bean(preDestroy = "close")
    HikariDataSource reconResultDataSource(ReconDatabaseProperties database) {
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
