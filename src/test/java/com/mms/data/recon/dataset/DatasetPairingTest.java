package com.mms.data.recon.dataset;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DatasetPairingTest {

    @ParameterizedTest(name = "{0} -> {1}")
    @CsvSource({
            "postgres, postgres",
            "postgres, mongo",
            "postgres, bigquery",
            "mongo, postgres",
            "mongo, mongo",
            "mongo, bigquery",
            "bigquery, postgres",
            "bigquery, mongo",
            "bigquery, bigquery",
            "postgres, file",
            "file, postgres",
            "file, file",
            "file, mongo",
            "mongo, file",
            "file, bigquery",
            "bigquery, file"
    })
    void reconcilesAnyConfiguredSourceTargetPair(DatasourceType sourceType, DatasourceType targetType) {
        DataLoadDefinition source = side("source", sourceType);
        DataLoadDefinition target = side("target", targetType);

        DatasetConfiguration dataset = new DatasetConfiguration();
        dataset.setId(sourceType + "-to-" + targetType);
        dataset.setSource(source);
        dataset.setTarget(target);
        dataset.setBatchSize(50);
        dataset.setHashingStrategy(HashingStrategy.TypeLenient);
        dataset.initialize();

        InMemoryRecStores.ScriptedRowLoader loader = new InMemoryRecStores.ScriptedRowLoader()
                .put("source", List.of(InMemoryRecStores.row("P1", "Acme", "US", "ACTIVE")))
                .put("target", List.of(InMemoryRecStores.row("P1", "Acme", "US", "ACTIVE")));
        InMemoryRecStores.MemoryRecRunRepository runs = new InMemoryRecStores.MemoryRecRunRepository();
        InMemoryRecStores.MemoryRecRecordRepository records = new InMemoryRecStores.MemoryRecRecordRepository();

        DatasetRecService service = new DatasetRecService(loader, runs, records, new DuckDbExceptReconciler());
        long runId = InMemoryRecStores.awaitReconcile(service, dataset);

        assertEquals(1L, runId);
        assertEquals(0, records.inserted.size());
        assertEquals(1, runs.lastSummary.matched());
        assertEquals(sourceType.name(), source.describeType());
        assertEquals(targetType.name(), target.describeType());
    }

    private static DataLoadDefinition side(String datasourceRef, DatasourceType type) {
        DataLoadDefinition definition = new DataLoadDefinition();
        definition.setDatasourceRef(datasourceRef);
        definition.setType(type);
        if (type == DatasourceType.mongo) {
            definition.setCollection("party");
            definition.setMigrationKey("party_id");
            definition.setFields(List.of("party_name", "country_code", "status"));
        }
        if (type != DatasourceType.mongo) {
            definition.setQuery("SELECT party_id AS \"MigrationKey\", party_name, country_code, status FROM party");
        }
        return definition;
    }
}
