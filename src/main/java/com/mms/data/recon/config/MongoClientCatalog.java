package com.mms.data.recon.config;

import com.mongodb.ConnectionString;
import com.mongodb.MongoClientSettings;
import com.mongodb.MongoCredential;
import com.mongodb.reactivestreams.client.MongoClient;
import com.mongodb.reactivestreams.client.MongoClients;
import com.mongodb.reactivestreams.client.MongoDatabase;
import jakarta.annotation.Nullable;
import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Named Mongo clients for business source/target datasources.
 * Empty at start; filled from catalog table / API. Clients created lazily on {@link #database(String)}.
 */
@Component
public class MongoClientCatalog {

    private final ConcurrentHashMap<String, MongoDatasourceProperties> byName = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, MongoClient> clients = new ConcurrentHashMap<>();

    public MongoClientCatalog() {}

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
        if (properties.getUri() == null || properties.getUri().isBlank()) {
            throw new ConfigurationException(
                    "Mongo datasource '" + properties.getName()
                            + "' requires uri (e.g. mongodb://host:27017/db) — localhost is not assumed"
            );
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
        String uri = requireUri(properties);
        try {
            MongoClientSettings.Builder builder = MongoClientSettings.builder()
                    .applyConnectionString(new ConnectionString(uri));

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
        } catch (RuntimeException e) {
            throw new ConfigurationException(
                    "Invalid Mongo uri for datasource '" + properties.getName() + "': " + uri,
                    e
            );
        }
    }

    private static String requireUri(MongoDatasourceProperties properties) {
        if (properties.getUri() == null || properties.getUri().isBlank()) {
            throw new ConfigurationException(
                    "Mongo datasource '" + properties.getName() + "' has no uri configured"
            );
        }
        return properties.getUri().trim();
    }

    private static String resolveDatabase(MongoDatasourceProperties properties) {
        if (properties.getDatabase() != null && !properties.getDatabase().isBlank()) {
            return properties.getDatabase();
        }
        String fromUri = new ConnectionString(requireUri(properties)).getDatabase();
        return fromUri == null || fromUri.isBlank() ? "data" : fromUri;
    }

    @PreDestroy
    public void close() {
        clients.values().forEach(MongoClient::close);
        clients.clear();
    }
}
