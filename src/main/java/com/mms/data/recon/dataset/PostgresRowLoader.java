package com.mms.data.recon.dataset;

import com.mms.data.recon.config.ConfigurationException;
import com.mms.data.recon.config.PostgresConnectionFactoryCatalog;
import io.r2dbc.spi.ConnectionFactory;
import io.r2dbc.spi.Row;
import io.r2dbc.spi.RowMetadata;
import io.r2dbc.spi.Statement;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.List;

@Component
public class PostgresRowLoader {

    private final PostgresConnectionFactoryCatalog connectionFactories;

    public PostgresRowLoader(PostgresConnectionFactoryCatalog connectionFactories) {
        this.connectionFactories = connectionFactories;
    }

    public Flux<DataLoadDefinition.RawRow> load(DataLoadDefinition definition) {
        String datasourceRef = definition.getDatasourceRef();
        if (datasourceRef == null || datasourceRef.isBlank()) {
            return Flux.error(new ConfigurationException(
                    "datasource is required for dataset " + definition.getDatasetId()
            ));
        }

        ConnectionFactory factory;
        try {
            factory = connectionFactories.require(datasourceRef);
        } catch (ConfigurationException e) {
            return Flux.error(e);
        }

        String sql = definition.resolveQueryStatement(DatasourceType.postgres);
        List<Object> params = PreparedQueries.params(definition);
        int placeholders = PreparedQueries.countSqlPlaceholders(sql);
        try {
            PreparedQueries.requireParamCount("PostgreSQL query", placeholders, params);
        } catch (ConfigurationException e) {
            return Flux.error(e);
        }

        String boundSql = PreparedQueries.toPostgresPlaceholders(sql);

        return Flux.usingWhen(
                Mono.from(factory.create()),
                connection -> {
                    Statement statement = connection.createStatement(boundSql);
                    for (int i = 0; i < params.size(); i++) {
                        Object value = params.get(i);
                        if (value == null) {
                            statement.bindNull(i, Object.class);
                        } else {
                            statement.bind(i, value);
                        }
                    }
                    return Flux.from(statement.execute()).flatMap(result -> result.map(this::toRawRow));
                },
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
