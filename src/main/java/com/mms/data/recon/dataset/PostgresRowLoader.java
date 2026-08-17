package com.mms.data.recon.dataset;

import io.micronaut.context.BeanLocator;
import io.micronaut.context.exceptions.ConfigurationException;
import io.micronaut.data.r2dbc.operations.R2dbcOperations;
import io.micronaut.inject.qualifiers.Qualifiers;
import io.r2dbc.spi.Row;
import io.r2dbc.spi.RowMetadata;
import jakarta.inject.Singleton;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;

@Singleton
public class PostgresRowLoader {

    private final BeanLocator beanLocator;

    public PostgresRowLoader(BeanLocator beanLocator) {
        this.beanLocator = beanLocator;
    }

    public Flux<DataLoadDefinition.RawRow> load(DataLoadDefinition definition) {
        String datasourceRef = definition.getDatasourceRef();
        if (datasourceRef == null || datasourceRef.isBlank()) {
            return Flux.error(new ConfigurationException(
                    "datasource is required for dataset " + definition.getDatasetId()
            ));
        }

        R2dbcOperations operations = beanLocator.getBean(
                R2dbcOperations.class,
                Qualifiers.byName(datasourceRef)
        );

        String sql = definition.resolveQueryStatement(DatasourceType.postgres);

        return Flux.usingWhen(
                operations.connectionFactory().create(),
                connection -> Flux.from(connection.createStatement(sql).execute())
                        .flatMap(result -> result.map(this::toRawRow)),
                connection -> connection.close()
        );
    }

    private DataLoadDefinition.RawRow toRawRow(Row row, RowMetadata metadata) {
        List<String> names = new ArrayList<>();
        List<Object> values = new ArrayList<>();

        metadata.getColumnMetadatas().forEach(column -> {
            String name = column.getName();
            names.add(name);
            values.add(row.get(name));
        });

        return new DataLoadDefinition.RawRow(names, values);
    }
}
