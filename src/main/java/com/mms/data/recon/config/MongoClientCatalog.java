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

@Component
public class MongoClientCatalog {

    private final Map<String, MongoDatasourceProperties> byName;
    private final ConcurrentHashMap<String, MongoClient> clients = new ConcurrentHashMap<>();

    @Autowired
    public MongoClientCatalog(MongoDatasourcesProperties properties) {
        this(properties.asList());
    }

    public MongoClientCatalog(@Nullable List<MongoDatasourceProperties> properties) {
        Map<String, MongoDatasourceProperties> map = new LinkedHashMap<>();
        if (properties != null) {
            for (MongoDatasourceProperties property : properties) {
                map.put(property.getName(), property);
            }
        }
        this.byName = Map.copyOf(map);
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
