package com.mms.data.recon.config;

import io.r2dbc.pool.ConnectionPool;
import io.r2dbc.pool.ConnectionPoolConfiguration;
import io.r2dbc.postgresql.PostgresqlConnectionConfiguration;
import io.r2dbc.postgresql.PostgresqlConnectionFactory;
import io.r2dbc.spi.ConnectionFactories;
import io.r2dbc.spi.ConnectionFactory;
import io.r2dbc.spi.ConnectionFactoryOptions;
import jakarta.annotation.Nullable;
import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Component;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Named PostgreSQL R2DBC factories for business source/target datasources.
 * Empty at start; filled from catalog table / API. Pools created lazily on {@link #require(String)}.
 */
@Component
public class PostgresConnectionFactoryCatalog {

    private final ConcurrentHashMap<String, PostgresDatasourceProperties> byName = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, ConnectionPool> pools = new ConcurrentHashMap<>();

    public PostgresConnectionFactoryCatalog() {}

    /** Test helper. */
    public PostgresConnectionFactoryCatalog(@Nullable List<PostgresDatasourceProperties> properties) {
        if (properties != null) {
            for (PostgresDatasourceProperties property : properties) {
                register(property);
            }
        }
    }

    public boolean has(String name) {
        return name != null && byName.containsKey(name);
    }

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
            return ConnectionFactories.get(optionsFromUrl(properties, url, schema));
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
     * Build R2DBC options from a connection URI plus optional username/password fields.
     * Accepts {@code r2dbc:}, {@code jdbc:postgresql:}, and bare {@code postgresql://} forms.
     */
    static ConnectionFactoryOptions optionsFromUrl(
            PostgresDatasourceProperties properties,
            String url,
            String schema
    ) {
        String r2dbcUrl = toR2dbcUrl(withSchemaQueryParam(url, schema));
        ConnectionFactoryOptions parsed = ConnectionFactoryOptions.parse(r2dbcUrl);
        ConnectionFactoryOptions.Builder builder = ConnectionFactoryOptions.builder().from(parsed);
        if (!parsed.hasOption(ConnectionFactoryOptions.USER)
                && properties.getUsername() != null
                && !properties.getUsername().isBlank()) {
            builder.option(ConnectionFactoryOptions.USER, properties.getUsername());
        }
        if (!parsed.hasOption(ConnectionFactoryOptions.PASSWORD) && properties.getPassword() != null) {
            builder.option(ConnectionFactoryOptions.PASSWORD, properties.getPassword());
        }
        return builder.build();
    }

    /** Normalize JDBC / postgres URLs to an {@code r2dbc:} scheme ConnectionFactories accepts. */
    static String toR2dbcUrl(String url) {
        if (url == null || url.isBlank()) {
            return url;
        }
        String trimmed = url.trim();
        if (trimmed.regionMatches(true, 0, "r2dbc:", 0, 6)) {
            return trimmed;
        }
        if (trimmed.regionMatches(true, 0, "jdbc:postgresql:", 0, 16)) {
            return "r2dbc:postgresql:" + trimmed.substring(16);
        }
        if (trimmed.regionMatches(true, 0, "jdbc:postgres:", 0, 14)) {
            return "r2dbc:postgresql:" + trimmed.substring(14);
        }
        if (trimmed.regionMatches(true, 0, "postgresql://", 0, 13)) {
            return "r2dbc:" + trimmed;
        }
        if (trimmed.regionMatches(true, 0, "postgres://", 0, 11)) {
            return "r2dbc:postgresql://" + trimmed.substring(11);
        }
        return trimmed;
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
