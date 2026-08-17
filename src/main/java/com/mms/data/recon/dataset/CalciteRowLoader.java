package com.mms.data.recon.dataset;

import com.mms.data.recon.config.CalciteBigQueryCatalog;
import com.mms.data.recon.config.CalciteFileCatalog;
import com.mms.data.recon.config.DatasourceCatalog;
import io.micronaut.context.exceptions.ConfigurationException;
import jakarta.inject.Singleton;
import reactor.core.publisher.Flux;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

@Singleton
public class CalciteRowLoader {

    private final CalciteBigQueryCatalog bigQuery;
    private final CalciteFileCatalog files;
    private final DatasourceCatalog datasources;

    public CalciteRowLoader(
            CalciteBigQueryCatalog bigQuery,
            CalciteFileCatalog files,
            DatasourceCatalog datasources) {
        this.bigQuery = bigQuery;
        this.files = files;
        this.datasources = datasources;
    }

    public Flux<DataLoadDefinition.RawRow> load(DataLoadDefinition definition, int batchSize) {
        DatasourceType type = definition.resolveType(datasources);
        String ref = definition.getDatasourceRef();
        try {
            Connection connection = connection(type, ref);
            return Flux.fromIterable(query(connection, definition.resolveQueryStatement(type)));
        } catch (RuntimeException e) {
            return Flux.error(e);
        }
    }

    private Connection connection(DatasourceType type, String ref) {
        return switch (type) {
            case bigquery -> {
                if (!bigQuery.has(ref)) {
                    throw new ConfigurationException("BigQuery datasource [" + ref + "] is not configured");
                }
                yield bigQuery.connection(ref);
            }
            case file -> {
                if (!files.has(ref)) {
                    throw new ConfigurationException("File datasource [" + ref + "] is not configured");
                }
                yield files.connection(ref);
            }
            default -> throw new ConfigurationException(
                    "Calcite loader does not support datasource type " + type + " [" + ref + "]"
            );
        };
    }

    static List<DataLoadDefinition.RawRow> query(Connection connection, String sql) {
        try (Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(sql)) {
            ResultSetMetaData metadata = resultSet.getMetaData();
            int columnCount = metadata.getColumnCount();
            List<DataLoadDefinition.RawRow> rows = new ArrayList<>();
            while (resultSet.next()) {
                List<String> columns = new ArrayList<>(columnCount);
                List<Object> values = new ArrayList<>(columnCount);
                for (int i = 1; i <= columnCount; i++) {
                    columns.add(metadata.getColumnLabel(i));
                    values.add(resultSet.getObject(i));
                }
                rows.add(new DataLoadDefinition.RawRow(columns, values));
            }
            return rows;
        } catch (SQLException e) {
            throw new IllegalStateException("Calcite query failed: " + sql, e);
        }
    }
}
