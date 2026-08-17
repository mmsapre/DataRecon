package com.mms.data.recon.dataset;

import com.mms.data.recon.config.BigQueryDatasourceProperties;
import com.mms.data.recon.config.FileDatasourceProperties;
import org.apache.calcite.adapter.java.ReflectiveSchema;
import org.apache.calcite.adapter.jdbc.JdbcSchema;
import org.apache.calcite.jdbc.CalciteConnection;
import org.apache.calcite.schema.SchemaPlus;
import org.apache.calcite.sql.SqlDialect;
import org.apache.calcite.sql.dialect.BigQuerySqlDialect;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Properties;

public final class CalciteConnections {

    private CalciteConnections() {}

    public static Connection inMemory(String schemaName, Object beans) {
        try {
            Properties info = new Properties();
            info.setProperty("lex", "JAVA");
            Connection connection = DriverManager.getConnection("jdbc:calcite:", info);
            CalciteConnection calcite = connection.unwrap(CalciteConnection.class);
            calcite.getRootSchema().add(schemaName, new ReflectiveSchema(beans));
            calcite.setSchema(schemaName);
            return connection;
        } catch (SQLException e) {
            throw new IllegalStateException("Unable to create in-memory Calcite schema", e);
        }
    }

    public static Connection bigQuery(BigQueryDatasourceProperties properties, DataSource dataSource) {
        try {
            Properties info = new Properties();
            info.setProperty("lex", "BIG_QUERY");
            info.setProperty("fun", "standard");
            Connection connection = DriverManager.getConnection("jdbc:calcite:", info);
            CalciteConnection calcite = connection.unwrap(CalciteConnection.class);
            SchemaPlus root = calcite.getRootSchema();
            String alias = properties.resolveCalciteSchema();
            root.add(alias, JdbcSchema.create(
                    root,
                    alias,
                    dataSource,
                    metaData -> bigQueryDialect(),
                    properties.resolveCatalog(),
                    emptyToNull(properties.getDataset())
            ));
            calcite.setSchema(alias);
            return connection;
        } catch (SQLException e) {
            throw new IllegalStateException(
                    "Unable to open Calcite BigQuery schema [" + properties.getName() + "]",
                    e
            );
        }
    }

    public static Connection file(FileDatasourceProperties properties) {
        try {
            Properties info = new Properties();
            info.setProperty("lex", "ORACLE");
            info.setProperty("fun", "standard");
            info.setProperty("unquotedCasing", "UNCHANGED");
            info.setProperty("quotedCasing", "UNCHANGED");
            info.setProperty("caseSensitive", "true");
            Connection connection = DriverManager.getConnection("jdbc:calcite:", info);
            CalciteConnection calcite = connection.unwrap(CalciteConnection.class);
            String alias = properties.resolveCalciteSchema();
            calcite.getRootSchema().add(alias, new CalciteFileSchema(properties));
            calcite.setSchema(alias);
            return connection;
        } catch (SQLException e) {
            throw new IllegalStateException(
                    "Unable to open Calcite file schema [" + properties.getName() + "]",
                    e
            );
        }
    }

    static SqlDialect bigQueryDialect() {
        SqlDialect dialect = SqlDialect.DatabaseProduct.BIG_QUERY.getDialect();
        return dialect == null ? BigQuerySqlDialect.DEFAULT : dialect;
    }

    private static String emptyToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
