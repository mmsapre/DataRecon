package com.mms.data.recon.config;

import com.mms.data.recon.dataset.CalciteConnections;
import jakarta.annotation.Nullable;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class CalciteFileCatalog {

    private final ConcurrentHashMap<String, FileDatasourceProperties> byName = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Connection> connections = new ConcurrentHashMap<>();

    @Autowired
    public CalciteFileCatalog(FileDatasourcesProperties properties) {
        this(properties.asList());
    }

    public CalciteFileCatalog(@Nullable List<FileDatasourceProperties> properties) {
        if (properties != null) {
            for (FileDatasourceProperties property : properties) {
                byName.put(property.getName(), property);
            }
        }
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

    public synchronized void register(FileDatasourceProperties properties) {
        if (properties == null || properties.getName() == null || properties.getName().isBlank()) {
            throw new ConfigurationException("File datasource name is required");
        }
        String name = properties.getName();
        byName.put(name, properties);
        Connection previous = connections.remove(name);
        if (previous != null) {
            try {
                previous.close();
            } catch (SQLException ignored) {
                // replace cached connection
            }
        }
    }

    public synchronized void unregister(String name) {
        byName.remove(name);
        Connection previous = connections.remove(name);
        if (previous != null) {
            try {
                previous.close();
            } catch (SQLException ignored) {
                // drop cached connection
            }
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
    }
}
