package com.mms.data.recon.config;

import com.mongodb.ConnectionString;
import com.mongodb.MongoClientSettings;
import com.mongodb.MongoCredential;
import com.mongodb.reactivestreams.client.MongoClient;
import com.mongodb.reactivestreams.client.MongoClients;
import com.mongodb.reactivestreams.client.MongoDatabase;
import jakarta.annotation.Nullable;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Named Mongo clients used as business source/target datasources.
 * Registered via API. Clients are created lazily on {@link #database(String)} when a run executes.
 */
@Component
public class MongoClientCatalog {

    private final ConcurrentHashMap<String, MongoDatasourceProperties> byName = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, MongoClient> clients = new ConcurrentHashMap<>();

    @Autowired
    public MongoClientCatalog(MongoDatasourcesProperties properties) {
        this(properties.asList());
    }

    public MongoClientCatalog(@Nullable List<MongoDatasourceProperties> properties) {
        if (properties != null) {
            for (MongoDatasourceProperties property : properties) {
                byName.put(property.getName(), property);
            }
        }
    }

    public boolean has(String datasourceRef) {
        return datasourceRef != null && byName.containsKey(datasourceRef);
    }

    public MongoDatabase database(String datasourceRef) {
        MongoDatasourceProperties properties = byName.get(datasourceRef);
        if (properties == null) {
            throw new IllegalArgumentException("Unknown Mongo datasource: " + datasourceRef);
        }
        MongoClient client = clients.computeIfAbsent(datasourceRef, ignored -> create(properties));
        return client.getDatabase(resolveDatabase(properties));
    }

    public synchronized void register(MongoDatasourceProperties properties) {
        if (properties == null || properties.getName() == null || properties.getName().isBlank()) {
            throw new ConfigurationException("Mongo datasource name is required");
        }
        String name = properties.getName();
        byName.put(name, properties);
        MongoClient previous = clients.remove(name);
        if (previous != null) {
            previous.close();
        }
    }

    public synchronized void unregister(String name) {
        byName.remove(name);
        MongoClient previous = clients.remove(name);
        if (previous != null) {
            previous.close();
        }
    }

    private static MongoClient create(MongoDatasourceProperties properties) {
        MongoClientSettings.Builder builder = MongoClientSettings.builder()
                .applyConnectionString(new ConnectionString(properties.getUri()));

        if (properties.getUsername() != null && !properties.getUsername().isBlank()) {
            char[] password = properties.getPassword() == null
                    ? new char[0]
                    : properties.getPassword().toCharArray();
            String authDb = properties.getAuthDatabase() == null || properties.getAuthDatabase().isBlank()
                    ? "admin"
                    : properties.getAuthDatabase();
            builder.credential(MongoCredential.createCredential(
                    properties.getUsername(),
                    authDb,
                    password
            ));
        }

        return MongoClients.create(builder.build());
    }

    private static String resolveDatabase(MongoDatasourceProperties properties) {
        if (properties.getDatabase() != null && !properties.getDatabase().isBlank()) {
            return properties.getDatabase();
        }
        String fromUri = new ConnectionString(properties.getUri()).getDatabase();
        return fromUri == null || fromUri.isBlank() ? "data" : fromUri;
    }

    @PreDestroy
    public void close() {
        clients.values().forEach(MongoClient::close);
        clients.clear();
    }
}
