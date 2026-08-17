package com.mms.data.recon.config;

import com.mms.data.recon.dataset.CalciteConnections;
import io.micronaut.context.exceptions.ConfigurationException;
import jakarta.annotation.Nullable;
import jakarta.annotation.PreDestroy;
import jakarta.inject.Singleton;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Singleton
public class CalciteFileCatalog {

    private final Map<String, FileDatasourceProperties> byName;
    private final ConcurrentHashMap<String, Connection> connections = new ConcurrentHashMap<>();

    public CalciteFileCatalog(@Nullable List<FileDatasourceProperties> properties) {
        Map<String, FileDatasourceProperties> map = new LinkedHashMap<>();
        if (properties != null) {
            for (FileDatasourceProperties property : properties) {
                map.put(property.getName(), property);
            }
        }
        this.byName = Map.copyOf(map);
    }

    public boolean has(String datasourceRef) {
        return datasourceRef != null && byName.containsKey(datasourceRef);
    }

    public Connection connection(String datasourceRef) {
        FileDatasourceProperties properties = byName.get(datasourceRef);
        if (properties == null) {
            throw new ConfigurationException("Unknown file datasource: " + datasourceRef);
        }
        return connections.computeIfAbsent(datasourceRef, ignored -> CalciteConnections.file(properties));
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
    }
}
