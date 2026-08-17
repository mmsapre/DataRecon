package com.mms.data.recon.config;

import io.micronaut.context.annotation.Bean;
import io.micronaut.context.annotation.EachBean;
import io.micronaut.context.annotation.Factory;
import io.r2dbc.pool.ConnectionPool;
import io.r2dbc.pool.ConnectionPoolConfiguration;
import io.r2dbc.postgresql.PostgresqlConnectionConfiguration;
import io.r2dbc.postgresql.PostgresqlConnectionFactory;
import io.r2dbc.spi.ConnectionFactories;
import io.r2dbc.spi.ConnectionFactory;

@Factory
public class ReconPostgresR2dbcFactory {

    @EachBean(PostgresDatasourceProperties.class)
    @Bean(preDestroy = "close")
    ConnectionPool postgresConnectionFactory(PostgresDatasourceProperties properties) {
        ConnectionFactory factory = createFactory(properties);
        ConnectionPoolConfiguration pool = ConnectionPoolConfiguration.builder(factory)
                .maxSize(Math.max(1, properties.getMaxSize()))
                .build();
        return new ConnectionPool(pool);
    }

    private static ConnectionFactory createFactory(PostgresDatasourceProperties properties) {
        if (properties.getUrl() != null && !properties.getUrl().isBlank()) {
            return ConnectionFactories.get(properties.getUrl());
        }
        return new PostgresqlConnectionFactory(
                PostgresqlConnectionConfiguration.builder()
                        .host(properties.getHost())
                        .port(properties.getPort())
                        .database(properties.getDatabase())
                        .username(properties.getUsername())
                        .password(properties.getPassword() == null ? "" : properties.getPassword())
                        .build()
        );
    }
}
