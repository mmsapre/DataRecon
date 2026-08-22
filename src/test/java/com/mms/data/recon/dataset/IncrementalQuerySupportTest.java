package com.mms.data.recon.dataset;

import org.junit.jupiter.api.Test;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IncrementalQuerySupportTest {

    @Test
    void leavesQueryUnchangedWhenNoSinceUntilTokens() {
        DataLoadDefinition side = new DataLoadDefinition();
        side.setQuery("SELECT party_id AS \"MigrationKey\", region_code FROM party");
        DataLoadDefinition applied = IncrementalQuerySupport.applyWindow(
                side, DatasourceType.postgres, Instant.parse("2024-01-01T00:00:00Z"), Instant.parse("2024-01-02T00:00:00Z"));
        assertSame(side, applied);
    }

    @Test
    void expandsSinceUntilInDistinctSqlWithIdentifiers() {
        DataLoadDefinition side = new DataLoadDefinition();
        side.setQuery("""
                SELECT DISTINCT party_id AS "MigrationKey", region_code, party_name
                FROM party
                WHERE updated_at > :since AND updated_at <= :until AND status = ?
                """);
        side.setQueryParams(List.of("ACTIVE"));
        side.setIdentifiers(List.of("region_code"));
        side.setFields(List.of("party_name"));

        Instant since = Instant.parse("2024-06-01T10:00:00Z");
        Instant until = Instant.parse("2024-06-02T10:00:00Z");
        DataLoadDefinition applied = IncrementalQuerySupport.applyWindow(
                side, DatasourceType.postgres, since, until);

        assertTrue(applied.getQuery().contains("updated_at > ? AND updated_at <= ?"));
        assertFalse(applied.getQuery().contains(":since"));
        assertEquals(3, applied.getQueryParams().size());
        assertEquals(Timestamp.from(since), applied.getQueryParams().get(0));
        assertEquals(Timestamp.from(until), applied.getQueryParams().get(1));
        assertEquals("ACTIVE", applied.getQueryParams().get(2));
        assertEquals(List.of("region_code", "party_name"), applied.comparableFields());
    }

    @Test
    void expandsMongoSinceUntilStrings() {
        DataLoadDefinition side = new DataLoadDefinition();
        side.setCollection("party");
        side.setQuery("{ \"updated_at\": { \"$gt\": \":since\", \"$lte\": \":until\" } }");

        Instant since = Instant.parse("2024-06-01T10:00:00Z");
        Instant until = Instant.parse("2024-06-02T10:00:00Z");
        DataLoadDefinition applied = IncrementalQuerySupport.applyWindow(
                side, DatasourceType.mongo, since, until);

        assertEquals(
                "{ \"updated_at\": { \"$gt\": \"?\", \"$lte\": \"?\" } }",
                applied.getQuery()
        );
        assertEquals(List.of(since.toString(), until.toString()), applied.getQueryParams());
    }

    @Test
    void sameQuerySupportsFullEpochAndIncrementalSince() {
        DataLoadDefinition side = new DataLoadDefinition();
        side.setQuery("SELECT party_id AS \"MigrationKey\" FROM party WHERE updated_at > :since AND updated_at <= :until");

        Instant until = Instant.parse("2024-06-02T10:00:00Z");
        DataLoadDefinition full = IncrementalQuerySupport.applyWindow(
                side, DatasourceType.postgres, Instant.EPOCH, until);
        assertEquals(Timestamp.from(Instant.EPOCH), full.getQueryParams().get(0));
        assertEquals(Timestamp.from(until), full.getQueryParams().get(1));

        Instant previousRun = Instant.parse("2024-06-01T10:00:00Z");
        DataLoadDefinition incremental = IncrementalQuerySupport.applyWindow(
                side, DatasourceType.postgres, previousRun, until);
        assertEquals(Timestamp.from(previousRun), incremental.getQueryParams().get(0));
        assertEquals(Timestamp.from(until), incremental.getQueryParams().get(1));
    }

    @Test
    void singleAndMultipleIdentifiersRemainComparable() {
        DataLoadDefinition single = new DataLoadDefinition();
        single.setIdentifiers(List.of("region_code"));
        single.setFields(List.of("party_name"));
        assertEquals(List.of("region_code", "party_name"), single.comparableFields());

        DataLoadDefinition multi = new DataLoadDefinition();
        multi.setIdentifiers(List.of("region_code", "account_id"));
        multi.setFields(List.of("party_name"));
        assertEquals(List.of("region_code", "account_id", "party_name"), multi.comparableFields());
    }
}
