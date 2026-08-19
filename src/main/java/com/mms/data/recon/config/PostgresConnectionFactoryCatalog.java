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
 * Named PostgreSQL R2DBC factories used as business source/target datasources.
 * Registered via API (or optional YAML seed). Pools are created lazily on
 * {@link #require(String)} when a recon run executes — not at app start.
 */
@Component
public class PostgresConnectionFactoryCatalog {

    private final ConcurrentHashMap<String, PostgresDatasourceProperties> byName = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, ConnectionPool> pools = new ConcurrentHashMap<>();

    public PostgresConnectionFactoryCatalog(PostgresDatasourcesProperties datasources) {
        for (PostgresDatasourceProperties properties : datasources.asList()) {
            register(properties);
        }
    }

    public boolean has(String name) {
        return name != null && byName.containsKey(name);
    }

    /** Resolve (and lazily pool) a named business Postgres datasource for execution. */
    public ConnectionFactory require(String name) {
        PostgresDatasourceProperties properties = byName.get(name);
        if (properties == null) {
            throw new ConfigurationException("Unknown PostgreSQL datasource: " + name);
        }
        return pools.computeIfAbsent(name, ignored -> createPool(properties));
    }

    public Map<String, ConnectionFactory> asMap() {
        Map<String, ConnectionFactory> out = new LinkedHashMap<>();
        for (String name : byName.keySet()) {
            out.put(name, require(name));
        }
        return Map.copyOf(out);
    }

    public synchronized void register(PostgresDatasourceProperties properties) {
        if (properties == null || properties.getName() == null || properties.getName().isBlank()) {
            throw new ConfigurationException("PostgreSQL datasource name is required");
        }
        String name = properties.getName();
        byName.put(name, properties);
        ConnectionPool previous = pools.remove(name);
        if (previous != null) {
            previous.dispose();
        }
    }

    public synchronized void unregister(String name) {
        byName.remove(name);
        ConnectionPool removed = pools.remove(name);
        if (removed != null) {
            removed.dispose();
        }
    }

    private static ConnectionPool createPool(PostgresDatasourceProperties properties) {
        ConnectionFactory factory = createFactory(properties);
        ConnectionPoolConfiguration poolConfig = ConnectionPoolConfiguration.builder(factory)
                .maxSize(Math.max(1, properties.getMaxSize()))
                .build();
        return new ConnectionPool(poolConfig);
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

    public static String schemaFromUrl(String url) {
        if (url == null || url.isBlank()) {
            return null;
        }
        try {
            String normalized = url;
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
        byName.clear();
    }
}
