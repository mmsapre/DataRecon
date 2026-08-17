package com.mms.data.recon.dataset;

import org.apache.calcite.sql.SqlDialect;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CalciteRowLoaderTest {

    public static final class Party {
        public final String MigrationKey;
        public final String party_name;
        public final String country_code;
        public final String status;

        public Party(String migrationKey, String partyName, String countryCode, String status) {
            this.MigrationKey = migrationKey;
            this.party_name = partyName;
            this.country_code = countryCode;
            this.status = status;
        }
    }

    public static final class PartySchema {
        public final Party[] party = {
                new Party("P1", "Acme", "US", "ACTIVE"),
                new Party("P2", "Beta", "IN", "ACTIVE")
        };
    }

    @Test
    void bigQueryDialectIsCalciteBigQuery() {
        SqlDialect dialect = CalciteConnections.bigQueryDialect();
        assertNotNull(dialect);
        assertEquals(SqlDialect.DatabaseProduct.BIG_QUERY, dialect.getDatabaseProduct());
    }

    @Test
    void calciteQueryReturnsMigrationKeyRows() throws Exception {
        try (Connection connection = CalciteConnections.inMemory("hr", new PartySchema())) {
            List<DataLoadDefinition.RawRow> rows = CalciteRowLoader.query(
                    connection,
                    "SELECT MigrationKey, party_name, country_code, status FROM party"
            );
            assertEquals(2, rows.size());
            assertEquals("P1", rows.get(0).migrationKey());
            assertEquals(List.of("Acme", "US", "ACTIVE"), rows.get(0).comparableValues());
        }
    }

    @Test
    void calciteComparableValuesHashLikePostgresSide() throws Exception {
        try (Connection connection = CalciteConnections.inMemory("hr", new PartySchema())) {
            DataLoadDefinition.RawRow calcite = CalciteRowLoader.query(
                    connection,
                    "SELECT MigrationKey, party_name, country_code, status FROM party WHERE MigrationKey = 'P1'"
            ).get(0);

            String calciteHash = RowHasher.hash(calcite.comparableValues(), HashingStrategy.TypeLenient);
            String postgresHash = RowHasher.hash(List.of("Acme", "US", "ACTIVE"), HashingStrategy.TypeLenient);
            assertEquals(postgresHash, calciteHash);
            assertTrue(calciteHash.matches("[0-9a-f]{64}"));
        }
    }
}
