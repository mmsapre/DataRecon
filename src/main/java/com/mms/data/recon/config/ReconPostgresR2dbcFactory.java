package com.mms.data.recon.config;

import io.r2dbc.pool.ConnectionPool;
import io.r2dbc.pool.ConnectionPoolConfiguration;
import io.r2dbc.postgresql.PostgresqlConnectionConfiguration;
import io.r2dbc.postgresql.PostgresqlConnectionFactory;
import io.r2dbc.spi.ConnectionFactories;
import io.r2dbc.spi.ConnectionFactory;
import jakarta.annotation.PreDestroy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.LinkedHashMap;
import java.util.Map;

@Configuration
public class ReconPostgresR2dbcFactory {

    private final Map<String, ConnectionPool> pools = new LinkedHashMap<>();

    @Bean
    public Map<String, ConnectionFactory> postgresConnectionFactories(
            PostgresDatasourcesProperties datasources) {
        Map<String, ConnectionFactory> factories = new LinkedHashMap<>();
        for (PostgresDatasourceProperties properties : datasources.asList()) {
            ConnectionFactory factory = createFactory(properties);
            ConnectionPoolConfiguration pool = ConnectionPoolConfiguration.builder(factory)
                    .maxSize(Math.max(1, properties.getMaxSize()))
                    .build();
            ConnectionPool connectionPool = new ConnectionPool(pool);
            pools.put(properties.getName(), connectionPool);
            factories.put(properties.getName(), connectionPool);
        }
        return Map.copyOf(factories);
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

    @PreDestroy
    public void close() {
        pools.values().forEach(ConnectionPool::dispose);
        pools.clear();
    }
}
