package com.mms.data.recon.dataset;

import com.mms.data.recon.recrun.RecRecordRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DuckDbExceptReconcilerTest {

    @TempDir
    Path temp;

    @Test
    void exceptAllFindsSourceOnlyTargetOnlyAndMismatched() {
        DuckDbExceptReconciler reconciler = new DuckDbExceptReconciler(temp);
        DatasetConfiguration dataset = InMemoryRecStores.dataset("duck", List.of(), List.of());
        ReconSettings settings = new ReconSettings();
        settings.setMode(ReconMode.MISMATCH_DETAILS);

        DuckDbExceptReconciler.Result result = reconciler.compare(
                dataset,
                List.of(
                        InMemoryRecStores.row("1", "A"),
                        InMemoryRecStores.row("2", "B"),
                        InMemoryRecStores.row("3", "C")
                ),
                List.of(
                        InMemoryRecStores.row("1", "A"),
                        InMemoryRecStores.row("2", "X"),
                        InMemoryRecStores.row("4", "D")
                ),
                settings,
                RunScope.FULL
        );

        assertEquals(3, result.sourceCount());
        assertEquals(3, result.targetCount());
        assertEquals(1, result.matched());
        assertEquals(1, result.mismatched());
        assertEquals(1, result.sourceOnly());
        assertEquals(1, result.targetOnly());
        assertEquals(3, result.details().size());
        assertTrue(result.details().stream().anyMatch(r ->
                r.migrationKey().equals("2") && r.status() == RecRecordRepository.RecStatus.MISMATCHED));
        assertTrue(result.details().stream().anyMatch(r ->
                r.migrationKey().equals("3") && r.status() == RecRecordRepository.RecStatus.SOURCE_ONLY));
        assertTrue(result.details().stream().anyMatch(r ->
                r.migrationKey().equals("4") && r.status() == RecRecordRepository.RecStatus.TARGET_ONLY));
    }

    @Test
    void detailCompareUsesLenientHashingSoNumericTypesMatch() {
        DuckDbExceptReconciler reconciler = new DuckDbExceptReconciler(temp);
        DatasetConfiguration dataset = InMemoryRecStores.dataset("duck-lenient", List.of(), List.of());
        dataset.setHashingStrategy(HashingStrategy.TypeLenient);
        dataset.getSource().setFields(List.of("amount"));
        dataset.getTarget().setFields(List.of("amount"));
        ReconSettings settings = new ReconSettings();
        settings.setMode(ReconMode.MISMATCH_DETAILS);

        DuckDbExceptReconciler.Result result = reconciler.compare(
                dataset,
                List.of(new DataLoadDefinition.RawRow(
                        List.of("MigrationKey", "amount"),
                        List.of("1", 1)
                )),
                List.of(new DataLoadDefinition.RawRow(
                        List.of("MigrationKey", "amount"),
                        List.of("1", 1.0)
                )),
                settings,
                RunScope.FULL
        );

        assertEquals(1, result.matched());
        assertEquals(0, result.mismatched());
        assertEquals(0, result.details().size());
    }

    @Test
    void detailCompareUsesStrictHashingSoNumericTypesDiffer() {
        DuckDbExceptReconciler reconciler = new DuckDbExceptReconciler(temp);
        DatasetConfiguration dataset = InMemoryRecStores.dataset("duck-strict", List.of(), List.of());
        dataset.setHashingStrategy(HashingStrategy.TypeStrict);
        dataset.getSource().setFields(List.of("amount"));
        dataset.getTarget().setFields(List.of("amount"));
        ReconSettings settings = new ReconSettings();
        settings.setMode(ReconMode.MISMATCH_DETAILS);

        DuckDbExceptReconciler.Result result = reconciler.compare(
                dataset,
                List.of(new DataLoadDefinition.RawRow(
                        List.of("MigrationKey", "amount"),
                        List.of("1", 1)
                )),
                List.of(new DataLoadDefinition.RawRow(
                        List.of("MigrationKey", "amount"),
                        List.of("1", 1.0)
                )),
                settings,
                RunScope.FULL
        );

        assertEquals(0, result.matched());
        assertEquals(1, result.mismatched());
    }
}
