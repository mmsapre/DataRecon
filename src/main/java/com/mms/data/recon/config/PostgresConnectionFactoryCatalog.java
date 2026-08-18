package com.mms.data.recon.config;

import io.r2dbc.pool.ConnectionPool;
import io.r2dbc.pool.ConnectionPoolConfiguration;
import io.r2dbc.postgresql.PostgresqlConnectionConfiguration;
import io.r2dbc.postgresql.PostgresqlConnectionFactory;
import io.r2dbc.spi.ConnectionFactories;
import io.r2dbc.spi.ConnectionFactory;
import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Named PostgreSQL R2DBC pools used as recon source/target datasources.
 * Seeded from YAML and mutable via the datasources API.
 */
@Component
public class PostgresConnectionFactoryCatalog {

    private final ConcurrentHashMap<String, ConnectionPool> pools = new ConcurrentHashMap<>();

    public PostgresConnectionFactoryCatalog(PostgresDatasourcesProperties datasources) {
        for (PostgresDatasourceProperties properties : datasources.asList()) {
            register(properties);
        }
    }

    public boolean has(String name) {
        return name != null && pools.containsKey(name);
    }

    public ConnectionFactory require(String name) {
        ConnectionPool pool = pools.get(name);
        if (pool == null) {
            throw new ConfigurationException("Unknown PostgreSQL datasource: " + name);
        }
        return pool;
    }

    public Map<String, ConnectionFactory> asMap() {
        return Map.copyOf(pools);
    }

    public synchronized void register(PostgresDatasourceProperties properties) {
        if (properties == null || properties.getName() == null || properties.getName().isBlank()) {
            throw new ConfigurationException("PostgreSQL datasource name is required");
        }
        String name = properties.getName();
        ConnectionFactory factory = createFactory(properties);
        ConnectionPoolConfiguration poolConfig = ConnectionPoolConfiguration.builder(factory)
                .maxSize(Math.max(1, properties.getMaxSize()))
                .build();
        ConnectionPool connectionPool = new ConnectionPool(poolConfig);
        ConnectionPool previous = pools.put(name, connectionPool);
        if (previous != null) {
            previous.dispose();
        }
    }

    public synchronized void unregister(String name) {
        ConnectionPool removed = pools.remove(name);
        if (removed != null) {
            removed.dispose();
        }
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
