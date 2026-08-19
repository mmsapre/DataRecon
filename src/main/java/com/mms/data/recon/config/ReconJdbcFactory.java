package com.mms.data.recon.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import javax.sql.DataSource;

/**
 * Primary JDBC pool for Data Recon’s own store — built only from
 * {@link ReconDatabaseProperties} ({@code mms.recon.database} / env).
 * Does not read the business datasource catalog table.
 *
 * <p>Business source/target connections are loaded from {@code rec_datasource}
 * into {@link DatasourceCatalog} and type registries at bootstrap / via API.
 */
@Configuration
public class ReconJdbcFactory {

    @Bean(name = "reconDataSource", destroyMethod = "close")
    @Primary
    public DataSource reconDataSource(ReconDatabaseProperties database) {
        HikariConfig config = new HikariConfig();
        config.setPoolName("mms-recon-primary");
        config.setJdbcUrl(database.jdbcUrl());
        config.setUsername(database.getUsername());
        config.setPassword(database.getPassword());
        config.setDriverClassName("org.postgresql.Driver");
        config.setMaximumPoolSize(Math.max(1, database.getMaximumPoolSize()));
        return new HikariDataSource(config);
    }
}
