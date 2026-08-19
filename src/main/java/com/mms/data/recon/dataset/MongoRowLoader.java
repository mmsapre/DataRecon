package com.mms.data.recon.dataset;

import com.mms.data.recon.config.ConfigurationException;
import com.mms.data.recon.config.MongoClientCatalog;
import com.mongodb.client.model.Projections;
import com.mongodb.reactivestreams.client.FindPublisher;
import com.mongodb.reactivestreams.client.MongoCollection;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;

@Component
public class MongoRowLoader {

    private final MongoClientCatalog catalog;

    public MongoRowLoader(MongoClientCatalog catalog) {
        this.catalog = catalog;
    }

    public Flux<DataLoadDefinition.RawRow> load(DataLoadDefinition definition, int batchSize) {
        if (!catalog.has(definition.getDatasourceRef())) {
            return Flux.error(new ConfigurationException(
                    "Mongo datasource [" + definition.getDatasourceRef() + "] is not configured"
            ));
        }
        if (definition.getCollection() == null || definition.getCollection().isBlank()) {
            return Flux.error(new ConfigurationException(
                    "Mongo dataset " + definition.getDatasetId() + " requires collection"
            ));
        }

        MongoCollection<Document> collection = catalog.database(definition.getDatasourceRef())
                .getCollection(definition.getCollection());

        Bson filter = parseFilter(
                definition.resolveQueryStatement(DatasourceType.mongo),
                PreparedQueries.params(definition)
        );
        List<String> projected = projectionFields(definition);
        FindPublisher<Document> find = collection.find(filter)
                .projection(Projections.include(projected))
                .batchSize(Math.max(1, batchSize));

        String ref = definition.getDatasourceRef();
        return Flux.from(find)
                .map(document -> MongoDocumentMapper.toRawRow(
                        document,
                        definition.getMigrationKey(),
                        definition.getFields()
                ))
                .onErrorMap(error -> {
                    String message = error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage();
                    if (message.toLowerCase().contains("connection refused")
                            || message.contains("MongoSocketOpenException")
                            || error.getClass().getSimpleName().contains("MongoSocket")) {
                        return new ConfigurationException(
                                "Mongo datasource [" + ref
                                        + "] could not connect (connection refused). "
                                        + "Check the datasource uri — it must reach a running MongoDB "
                                        + "(not an implied localhost default).",
                                error
                        );
                    }
                    return error;
                });
    }

    private static Bson parseFilter(String query, List<Object> params) {
        String json = PreparedQueries.bindMongoFilter(query, params);
        try {
            return Document.parse(json);
        } catch (Exception e) {
            throw new ConfigurationException("Mongo query must be a JSON filter document: " + json, e);
        }
    }

    private static List<String> projectionFields(DataLoadDefinition definition) {
        List<String> projected = new ArrayList<>();
        MigrationKeySpec key = definition.getMigrationKey();
        if (key != null) {
            projected.addAll(key.resolvedColumns());
        } else {
            projected.add(definition.migrationKeyField());
        }
        if (definition.getFields() != null) {
            projected.addAll(definition.getFields());
        }
        return projected;
    }
}
