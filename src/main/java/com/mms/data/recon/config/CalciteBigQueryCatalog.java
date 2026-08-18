package com.mms.data.recon.config;

import com.mms.data.recon.dataset.CalciteConnections;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import jakarta.annotation.Nullable;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class CalciteBigQueryCatalog {

    private final Map<String, BigQueryDatasourceProperties> byName;
    private final ConcurrentHashMap<String, HikariDataSource> pools = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Connection> connections = new ConcurrentHashMap<>();

    @Autowired
    public CalciteBigQueryCatalog(BigQueryDatasourcesProperties properties) {
        this(properties.asList());
    }

    public CalciteBigQueryCatalog(@Nullable List<BigQueryDatasourceProperties> properties) {
        Map<String, BigQueryDatasourceProperties> map = new LinkedHashMap<>();
        if (properties != null) {
            for (BigQueryDatasourceProperties property : properties) {
                map.put(property.getName(), property);
            }
        }
        this.byName = Map.copyOf(map);
    }

    public boolean has(String datasourceRef) {
        return datasourceRef != null && byName.containsKey(datasourceRef);
    }

    public Connection connection(String datasourceRef) {
        BigQueryDatasourceProperties properties = byName.get(datasourceRef);
        if (properties == null) {
            throw new ConfigurationException("Unknown BigQuery datasource: " + datasourceRef);
        }
        return connections.computeIfAbsent(datasourceRef, ignored -> open(properties));
    }

    private Connection open(BigQueryDatasourceProperties properties) {
        loadDriver(properties.getDriverClassName());
        HikariDataSource pool = pools.computeIfAbsent(properties.getName(), ignored -> jdbcPool(properties));
        return CalciteConnections.bigQuery(properties, pool);
    }

    private static HikariDataSource jdbcPool(BigQueryDatasourceProperties properties) {
        HikariConfig config = new HikariConfig();
        config.setPoolName("mms-recon-bq-" + properties.getName());
        config.setJdbcUrl(properties.resolveJdbcUrl());
        config.setDriverClassName(properties.getDriverClassName());
        if (properties.getUsername() != null && !properties.getUsername().isBlank()) {
            config.setUsername(properties.getUsername());
            config.setPassword(properties.getPassword());
        }
        config.setMaximumPoolSize(Math.max(1, properties.getMaxSize()));
        return new HikariDataSource(config);
    }

    private static void loadDriver(String driverClassName) {
        if (driverClassName == null || driverClassName.isBlank()) {
            return;
        }
        try {
            Class.forName(driverClassName);
        } catch (ClassNotFoundException e) {
            throw new ConfigurationException(
                    "BigQuery JDBC driver [" + driverClassName + "] is not on the classpath. "
                            + "Queries still go through Apache Calcite; add a BigQuery JDBC driver "
                            + "only as the transport (no Google BigQuery client library is used).",
                    e
            );
        }
    }

    @PreDestroy
    public void close() {
        connections.values().forEach(connection -> {
            try {
                connection.close();
            } catch (SQLException ignored) {
                // closing a cached Calcite connection during shutdown
            }
        });
        connections.clear();
        pools.values().forEach(HikariDataSource::close);
        pools.clear();
    }
}
