package com.mms.data.recon.dataset;

import com.mms.data.recon.config.CalciteBigQueryCatalog;
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

    private final CalciteBigQueryCatalog catalog;

    public CalciteRowLoader(CalciteBigQueryCatalog catalog) {
        this.catalog = catalog;
    }

    public Flux<DataLoadDefinition.RawRow> load(DataLoadDefinition definition, int batchSize) {
        if (!catalog.has(definition.getDatasourceRef())) {
            return Flux.error(new ConfigurationException(
                    "BigQuery datasource [" + definition.getDatasourceRef() + "] is not configured"
            ));
        }
        try {
            return Flux.fromIterable(query(
                    catalog.connection(definition.getDatasourceRef()),
                    definition.resolveQueryStatement(DatasourceType.bigquery)
            ));
        } catch (RuntimeException e) {
            return Flux.error(e);
        }
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
