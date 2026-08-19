package com.mms.data.recon.config;

import io.r2dbc.pool.ConnectionPool;
import io.r2dbc.pool.ConnectionPoolConfiguration;
import io.r2dbc.postgresql.PostgresqlConnectionConfiguration;
import io.r2dbc.postgresql.PostgresqlConnectionFactory;
import io.r2dbc.spi.ConnectionFactories;
import io.r2dbc.spi.ConnectionFactory;
import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Component;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Named PostgreSQL R2DBC pools used as recon source/target datasources.
 * Seeded from YAML and mutable via the datasources API.
 * Schema may be set explicitly or carried on the connection URI
 * ({@code schema} / {@code currentSchema} / {@code search_path} query params).
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
        String schema = properties.resolveSchema();
        String url = properties.resolveUrl();
        if (url != null && !url.isBlank()) {
            return ConnectionFactories.get(withSchemaQueryParam(url, schema));
        }
        PostgresqlConnectionConfiguration.Builder builder = PostgresqlConnectionConfiguration.builder()
                .host(properties.getHost())
                .port(properties.getPort())
                .database(properties.getDatabase())
                .username(properties.getUsername())
                .password(properties.getPassword() == null ? "" : properties.getPassword());
        if (schema != null && !schema.isBlank()) {
            builder.schema(schema.trim());
        }
        return new PostgresqlConnectionFactory(builder.build());
    }

    /**
     * Ensure an R2DBC/JDBC-style URL carries schema when provided separately and not already present.
     */
    static String withSchemaQueryParam(String url, String schema) {
        if (url == null || url.isBlank() || schema == null || schema.isBlank()) {
            return url;
        }
        if (schemaFromUrl(url) != null) {
            return url;
        }
        String sep = url.contains("?") ? "&" : "?";
        return url + sep + "schema=" + schema.trim();
    }

    /**
     * Read schema from URI query: {@code schema}, {@code currentSchema}, or {@code search_path}.
     */
    public static String schemaFromUrl(String url) {
        if (url == null || url.isBlank()) {
            return null;
        }
        try {
            String normalized = url;
            // URI parser expects a scheme; r2dbc:postgresql://... is fine
            int q = normalized.indexOf('?');
            if (q < 0) {
                return null;
            }
            Map<String, String> params = new LinkedHashMap<>();
            for (String part : normalized.substring(q + 1).split("&")) {
                int eq = part.indexOf('=');
                if (eq <= 0) {
                    continue;
                }
                String key = URLDecoder.decode(part.substring(0, eq), StandardCharsets.UTF_8)
                        .toLowerCase(Locale.ROOT);
                String value = URLDecoder.decode(part.substring(eq + 1), StandardCharsets.UTF_8);
                params.put(key, value);
            }
            String schema = firstNonBlank(
                    params.get("schema"),
                    params.get("currentschema"),
                    params.get("search_path"),
                    params.get("searchpath")
            );
            if (schema == null) {
                return null;
            }
            // search_path may be "landing,public" — use the first entry for catalog default
            int comma = schema.indexOf(',');
            return comma < 0 ? schema.trim() : schema.substring(0, comma).trim();
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    @PreDestroy
    public void close() {
        pools.values().forEach(ConnectionPool::dispose);
        pools.clear();
    }
}
