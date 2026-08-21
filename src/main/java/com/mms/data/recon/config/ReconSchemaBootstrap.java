package com.mms.data.recon.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Ensures recon app-store tables exist on startup using idempotent DDL.
 * No Flyway and no schema-history / version-tracking table.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class ReconSchemaBootstrap implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(ReconSchemaBootstrap.class);
    private static final String SCHEMA_RESOURCE = "db/schema/recon_schema.sql";

    private final DataSource dataSource;
    private final ReconDatabaseProperties database;

    public ReconSchemaBootstrap(DataSource dataSource, ReconDatabaseProperties database) {
        this.dataSource = dataSource;
        this.database = database;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!database.isManageSchema()) {
            if (database.isRecreateOnStart()) {
                log.warn(
                        "mms.recon.database.recreate-on-start ignored because manage-schema=false "
                                + "(expect tables from external DDL: classpath:db/schema/recon_schema.sql)"
                );
            } else {
                log.info(
                        "mms.recon.database.manage-schema=false — skipping DDL; "
                                + "recon tables must already exist (see db/schema/recon_schema.sql)"
                );
            }
            return;
        }

        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            ensureSchema(statement);
            // Remove leftover Flyway history if present — this app does not use it.
            statement.execute("DROP TABLE IF EXISTS flyway_schema_history");
            String schema = database.resolvedSchema();
            if (schema != null && !schema.isBlank() && !"public".equalsIgnoreCase(schema)) {
                statement.execute(
                        "DROP TABLE IF EXISTS " + quoteIdent(schema) + ".flyway_schema_history"
                );
            }
            if (database.isRecreateOnStart()) {
                log.warn(
                        "mms.recon.database.recreate-on-start=true — dropping recon tables in schema '{}'",
                        database.resolvedSchema()
                );
                dropRecTables(statement);
            }

            List<String> sql = loadStatements();
            for (String raw : sql) {
                statement.execute(raw);
            }
            log.info(
                    "Recon schema ensured in '{}' (tables run={}, record={}, catalog)",
                    database.resolvedSchema(),
                    database.getTables().getRun(),
                    database.getTables().getRecord()
            );
        } catch (SQLException | IOException e) {
            throw new IllegalStateException("Unable to ensure recon schema", e);
        }
    }

    private void ensureSchema(Statement statement) throws SQLException {
        String schema = database.resolvedSchema();
        if (schema != null && !schema.isBlank() && !"public".equalsIgnoreCase(schema)) {
            statement.execute("CREATE SCHEMA IF NOT EXISTS " + quoteIdent(schema));
        }
    }

    private void dropRecTables(Statement statement) throws SQLException {
        ReconDatabaseProperties.Tables tables = database.getTables();
        // Children first (FK from record → run).
        statement.execute("DROP TABLE IF EXISTS " + qualify(tables.getRecord()) + " CASCADE");
        statement.execute("DROP TABLE IF EXISTS " + qualify(tables.getProfile()) + " CASCADE");
        statement.execute("DROP TABLE IF EXISTS " + qualify(tables.getDomain()) + " CASCADE");
        statement.execute("DROP TABLE IF EXISTS " + qualify(tables.getDatasource()) + " CASCADE");
        statement.execute("DROP TABLE IF EXISTS " + qualify(tables.getRun()) + " CASCADE");
    }

    private List<String> loadStatements() throws IOException {
        String script = new ClassPathResource(SCHEMA_RESOURCE)
                .getContentAsString(StandardCharsets.UTF_8);
        Map<String, String> placeholders = Map.of(
                "runTable", qualify(database.getTables().getRun()),
                "recordTable", qualify(database.getTables().getRecord()),
                "datasourceTable", qualify(database.getTables().getDatasource()),
                "domainTable", qualify(database.getTables().getDomain()),
                "profileTable", qualify(database.getTables().getProfile())
        );
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            script = script.replace("${" + entry.getKey() + "}", entry.getValue());
        }

        List<String> statements = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String line : script.split("\n")) {
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("--")) {
                continue;
            }
            current.append(line).append('\n');
            if (trimmed.endsWith(";")) {
                String sql = current.toString().trim();
                if (sql.endsWith(";")) {
                    sql = sql.substring(0, sql.length() - 1).trim();
                }
                if (!sql.isEmpty()) {
                    statements.add(sql);
                }
                current.setLength(0);
            }
        }
        String trailing = current.toString().trim();
        if (!trailing.isEmpty()) {
            statements.add(trailing.endsWith(";") ? trailing.substring(0, trailing.length() - 1).trim() : trailing);
        }
        return statements;
    }

    private String qualify(String table) {
        String schema = database.resolvedSchema();
        if (schema == null || schema.isBlank() || "public".equalsIgnoreCase(schema)) {
            return quoteIdent(table);
        }
        return quoteIdent(schema) + "." + quoteIdent(table);
    }

    private static String quoteIdent(String ident) {
        return "\"" + SqlIdentifiers.require("ident", ident) + "\"";
    }
}
