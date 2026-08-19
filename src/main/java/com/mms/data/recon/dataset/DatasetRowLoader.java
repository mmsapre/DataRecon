package com.mms.data.recon.dataset;

import com.mms.data.recon.config.DatasourceCatalog;
import reactor.core.publisher.Flux;
import org.springframework.stereotype.Component;

@Component
public class DatasetRowLoader implements RowLoader {

    private final PostgresRowLoader postgresRowLoader;
    private final MongoRowLoader mongoRowLoader;
    private final CalciteRowLoader calciteRowLoader;
    private final DatasourceCatalog datasourceCatalog;

    public DatasetRowLoader(
            PostgresRowLoader postgresRowLoader,
            MongoRowLoader mongoRowLoader,
            CalciteRowLoader calciteRowLoader,
            DatasourceCatalog datasourceCatalog) {
        this.postgresRowLoader = postgresRowLoader;
        this.mongoRowLoader = mongoRowLoader;
        this.calciteRowLoader = calciteRowLoader;
        this.datasourceCatalog = datasourceCatalog;
    }

    public DatasourceType resolveType(DataLoadDefinition definition) {
        return definition.resolveType(datasourceCatalog);
    }

    @Override
    public Flux<DataLoadDefinition.RawRow> load(DataLoadDefinition definition, int batchSize) {
        definition.applyCatalogDefaults(datasourceCatalog);
        return switch (resolveType(definition)) {
            case postgres -> postgresRowLoader.load(definition);
            case mongo -> mongoRowLoader.load(definition, batchSize);
            case bigquery, file -> calciteRowLoader.load(definition, batchSize);
        };
    }
}
