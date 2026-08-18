package com.mms.data.recon.dataset;

import com.mms.data.recon.config.CalciteBigQueryCatalog;
import com.mms.data.recon.config.CalciteFileCatalog;
import com.mms.data.recon.config.ConfigurationException;
import com.mms.data.recon.config.DatasourceCatalog;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

@Component
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
            String sql = definition.resolveQueryStatement(type);
            return Flux.fromIterable(query(connection, sql, PreparedQueries.params(definition)));
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
        return query(connection, sql, List.of());
    }

    static List<DataLoadDefinition.RawRow> query(Connection connection, String sql, List<Object> params) {
        List<Object> values = params == null ? List.of() : params;
        int placeholders = PreparedQueries.countSqlPlaceholders(sql);
        PreparedQueries.requireParamCount("Calcite/BigQuery query", placeholders, values);

        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            for (int i = 0; i < values.size(); i++) {
                statement.setObject(i + 1, values.get(i));
            }
            try (ResultSet resultSet = statement.executeQuery()) {
                ResultSetMetaData metadata = resultSet.getMetaData();
                int columnCount = metadata.getColumnCount();
                List<DataLoadDefinition.RawRow> rows = new ArrayList<>();
                while (resultSet.next()) {
                    List<String> columns = new ArrayList<>(columnCount);
                    List<Object> rowValues = new ArrayList<>(columnCount);
                    for (int i = 1; i <= columnCount; i++) {
                        columns.add(metadata.getColumnLabel(i));
                        rowValues.add(resultSet.getObject(i));
                    }
                    rows.add(new DataLoadDefinition.RawRow(columns, rowValues));
                }
                return rows;
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Calcite query failed: " + sql, e);
        }
    }
}
