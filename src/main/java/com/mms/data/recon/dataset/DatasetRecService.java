package com.mms.data.recon.dataset;

import com.mms.data.recon.recrun.FieldDiffs;
import com.mms.data.recon.recrun.RecRecordRepository;
import com.mms.data.recon.recrun.RecRunRepository;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class DatasetRecService {

    private final RowLoader rowLoader;
    private final RecRunRepository runRepository;
    private final RecRecordRepository recordRepository;
    private final DuckDbExceptReconciler duckDbExceptReconciler;

    public DatasetRecService(
            RowLoader rowLoader,
            RecRunRepository runRepository,
            RecRecordRepository recordRepository) {
        this(rowLoader, runRepository, recordRepository, new DuckDbExceptReconciler());
    }

    public DatasetRecService(
            RowLoader rowLoader,
            RecRunRepository runRepository,
            RecRecordRepository recordRepository,
            DuckDbExceptReconciler duckDbExceptReconciler) {
        this.rowLoader = rowLoader;
        this.runRepository = runRepository;
        this.recordRepository = recordRepository;
        this.duckDbExceptReconciler = duckDbExceptReconciler;
    }

    public Mono<Long> reconcile(DatasetConfiguration dataset) {
        return reconcile(dataset, null, dataset.resolvedRecon(), false);
    }

    public Mono<Long> reconcile(DatasetConfiguration dataset, Long domainRunId) {
        return reconcile(dataset, domainRunId, dataset.resolvedRecon(), false);
    }

    public Mono<Long> reconcile(DatasetConfiguration dataset, Long domainRunId, ReconSettings recon) {
        return reconcile(dataset, domainRunId, recon, false);
    }

    public Mono<Long> reconcile(
            DatasetConfiguration dataset,
            Long domainRunId,
            ReconSettings recon,
            boolean forceFull) {
        ReconSettings settings = recon == null ? dataset.resolvedRecon() : recon;
        settings.normalize();

        RunScope scope = RunScope.FULL;
        Long baselineRunId = null;
        if (!forceFull) {
            RecRunRepository.RunView active = runRepository.findActive(dataset.getDomainId(), dataset.getProfileId());
            if (active != null && "COMPLETED".equals(active.status())) {
                scope = RunScope.INCREMENTAL;
                baselineRunId = active.id();
            }
        }

        long runId = runRepository.create(
                dataset,
                domainRunId,
                settings.resolvedMode(),
                storedQuery(dataset.getSource()),
                storedQuery(dataset.getTarget()),
                settings.resolvedConditionFields(),
                scope,
                baselineRunId
        );

        if (settings.resolvedMode() == ReconMode.COUNTS) {
            return hashReconcile(runId, dataset, settings)
                    .doOnError(error -> runRepository.fail(runId, error))
                    .thenReturn(runId);
        }

        RunScope effectiveScope = scope;
        Long baseline = baselineRunId;
        return detailReconcile(runId, dataset, settings, effectiveScope, baseline)
                .doOnError(error -> runRepository.fail(runId, error))
                .thenReturn(runId);
    }

    private Mono<Void> hashReconcile(long runId, DatasetConfiguration dataset, ReconSettings settings) {
        Mono<Map<String, LoadedRow>> sourceMono =
                loadHashed(dataset.getSource(), dataset.getHashingStrategy(), dataset.getBatchSize());
        Mono<Map<String, LoadedRow>> targetMono =
                loadHashed(dataset.getTarget(), dataset.getHashingStrategy(), dataset.getBatchSize());
        return Mono.zip(sourceMono, targetMono)
                .flatMap(tuple -> persistHashComparison(runId, dataset, settings, tuple.getT1(), tuple.getT2()));
    }

    private Mono<Void> detailReconcile(
            long runId,
            DatasetConfiguration dataset,
            ReconSettings settings,
            RunScope scope,
            Long baselineRunId) {
        int batchSize = dataset.getBatchSize() == null ? 1000 : Math.max(1, dataset.getBatchSize());
        Mono<List<DataLoadDefinition.RawRow>> sourceMono =
                rowLoader.load(dataset.getSource(), batchSize).collectList();
        Mono<List<DataLoadDefinition.RawRow>> targetMono =
                rowLoader.load(dataset.getTarget(), batchSize).collectList();

        return Mono.zip(sourceMono, targetMono)
                .flatMap(tuple -> Mono.fromCallable(() -> duckDbExceptReconciler.compare(
                                dataset,
                                tuple.getT1(),
                                tuple.getT2(),
                                settings,
                                scope
                        ))
                        .flatMap(result -> persistDuckDbResult(runId, dataset, scope, baselineRunId, result)));
    }

    private Mono<Void> persistDuckDbResult(
            long runId,
            DatasetConfiguration dataset,
            RunScope scope,
            Long baselineRunId,
            DuckDbExceptReconciler.Result result) {
        List<RecRecordRepository.RecRecord> toStore = result.details();
        if (scope == RunScope.INCREMENTAL && baselineRunId != null) {
            Map<String, RecRecordRepository.RecRecord> baseline = recordRepository.findByRun(baselineRunId, null)
                    .stream()
                    .collect(Collectors.toMap(
                            RecRecordRepository.RecRecord::migrationKey,
                            Function.identity(),
                            (left, right) -> left,
                            LinkedHashMap::new
                    ));
            toStore = result.details().stream()
                    .filter(record -> changedSinceBaseline(baseline.get(record.migrationKey()), record))
                    .toList();
        }

        int batchSize = Math.max(1, dataset.getBatchSize() == null ? 1000 : dataset.getBatchSize());
        Flux<Void> persist = toStore.isEmpty()
                ? Flux.empty()
                : Flux.fromIterable(toStore)
                        .buffer(batchSize)
                        .doOnNext(batch -> recordRepository.insertBatch(runId, batch))
                        .then()
                        .flux();

        return persist.then().doOnSuccess(ignored -> runRepository.complete(runId, result.summary()));
    }

    private static boolean changedSinceBaseline(
            RecRecordRepository.RecRecord previous,
            RecRecordRepository.RecRecord current) {
        if (previous == null) {
            return true;
        }
        return previous.status() != current.status()
                || !Objects.equals(previous.sourceHash(), current.sourceHash())
                || !Objects.equals(previous.targetHash(), current.targetHash())
                || !Objects.equals(previous.sourcePayload(), current.sourcePayload())
                || !Objects.equals(previous.targetPayload(), current.targetPayload());
    }

    private static String storedQuery(DataLoadDefinition side) {
        return side == null ? null : side.storedQueryStatement();
    }

    private Mono<Map<String, LoadedRow>> loadHashed(
            DataLoadDefinition definition,
            HashingStrategy strategy,
            Integer batchSize) {

        int size = batchSize == null ? 1000 : Math.max(1, batchSize);
        return rowLoader.load(definition, size)
                .map(row -> Map.entry(row.migrationKey(), LoadedRow.from(row, definition, strategy)))
                .collectMap(Map.Entry::getKey, Map.Entry::getValue);
    }

    private Mono<Void> persistHashComparison(
            long runId,
            DatasetConfiguration dataset,
            ReconSettings recon,
            Map<String, LoadedRow> source,
            Map<String, LoadedRow> target) {

        Set<String> keys = new TreeSetAdapter(source.keySet(), target.keySet());

        List<RecRecordRepository.RecRecord> records = new ArrayList<>();
        AtomicLong matched = new AtomicLong();
        AtomicLong mismatched = new AtomicLong();
        AtomicLong sourceOnly = new AtomicLong();
        AtomicLong targetOnly = new AtomicLong();

        ReconMode mode = recon.resolvedMode();
        List<String> conditions = recon.resolvedConditionFields();

        for (String key : keys) {
            LoadedRow src = source.get(key);
            LoadedRow tgt = target.get(key);
            String sh = src == null ? null : src.rowHash();
            String th = tgt == null ? null : tgt.rowHash();

            RecRecordRepository.RecStatus status;
            if (src == null) {
                status = RecRecordRepository.RecStatus.TARGET_ONLY;
                targetOnly.incrementAndGet();
            } else if (tgt == null) {
                status = RecRecordRepository.RecStatus.SOURCE_ONLY;
                sourceOnly.incrementAndGet();
            } else if (Objects.equals(sh, th)) {
                status = RecRecordRepository.RecStatus.MATCHED;
                matched.incrementAndGet();
            } else {
                status = RecRecordRepository.RecStatus.MISMATCHED;
                mismatched.incrementAndGet();
            }

            if (!shouldPersist(mode, status, src, tgt, conditions)) {
                continue;
            }

            String fieldDiffs = null;
            if (mode == ReconMode.FIELD_DETAILS) {
                fieldDiffs = FieldDiffJson.of(src, tgt, conditions);
            }
            records.add(new RecRecordRepository.RecRecord(key, sh, th, status, fieldDiffs));
        }

        int batchSize = Math.max(1, dataset.getBatchSize() == null ? 1000 : dataset.getBatchSize());
        Flux<Void> persist = records.isEmpty()
                ? Flux.empty()
                : Flux.fromIterable(records)
                        .buffer(batchSize)
                        .doOnNext(batch -> recordRepository.insertBatch(runId, batch))
                        .then()
                        .flux();

        return persist
                .then()
                .doOnSuccess(ignored -> runRepository.complete(
                        runId,
                        new RecRunRepository.RunSummary(
                                source.size(),
                                target.size(),
                                matched.get(),
                                mismatched.get(),
                                sourceOnly.get(),
                                targetOnly.get()
                        )
                ));
    }

    static boolean shouldPersist(
            ReconMode mode,
            RecRecordRepository.RecStatus status,
            LoadedRow source,
            LoadedRow target,
            List<String> conditions) {
        if (mode == ReconMode.COUNTS) {
            return false;
        }
        if (status == RecRecordRepository.RecStatus.MATCHED) {
            return false;
        }
        if (mode == ReconMode.MISMATCH_DETAILS
                && status == RecRecordRepository.RecStatus.MISMATCHED
                && conditions != null
                && !conditions.isEmpty()
                && !conditionFieldMismatch(source, target, conditions)) {
            return false;
        }
        return true;
    }

    static boolean conditionFieldMismatch(LoadedRow source, LoadedRow target, List<String> conditions) {
        for (String field : conditions) {
            String left = source == null ? null : source.fieldHashes().get(field);
            String right = target == null ? null : target.fieldHashes().get(field);
            if (!Objects.equals(left, right)) {
                return true;
            }
        }
        return false;
    }

    record LoadedRow(String rowHash, LinkedHashMap<String, String> fieldHashes) {
        static LoadedRow from(
                DataLoadDefinition.RawRow row,
                DataLoadDefinition definition,
                HashingStrategy strategy) {
            List<Object> values = row.comparableValues();
            String rowHash = RowHasher.hash(values, strategy);
            LinkedHashMap<String, String> fields = new LinkedHashMap<>();
            List<String> names = definition.getFields();
            if (names == null || names.isEmpty()) {
                names = new ArrayList<>();
                for (String column : row.columns()) {
                    if (!DataLoadDefinition.MIGRATION_KEY_COLUMN_NAME.equalsIgnoreCase(column)) {
                        names.add(column);
                    }
                }
            }
            for (int i = 0; i < names.size() && i < values.size(); i++) {
                fields.put(names.get(i), RowHasher.hash(List.of(values.get(i)), strategy));
            }
            return new LoadedRow(rowHash, fields);
        }
    }

    static final class FieldDiffJson {
        private FieldDiffJson() {}

        static String of(LoadedRow source, LoadedRow target, List<String> conditions) {
            LinkedHashMap<String, String> diffs = new LinkedHashMap<>();
            List<String> fields = conditions == null || conditions.isEmpty()
                    ? fieldNames(source, target)
                    : conditions;
            for (String field : fields) {
                String left = source == null ? null : source.fieldHashes().get(field);
                String right = target == null ? null : target.fieldHashes().get(field);
                String status;
                if (left == null && right == null) {
                    status = RecRecordRepository.RecStatus.MATCHED.name();
                } else if (left == null) {
                    status = RecRecordRepository.RecStatus.TARGET_ONLY.name();
                } else if (right == null) {
                    status = RecRecordRepository.RecStatus.SOURCE_ONLY.name();
                } else if (Objects.equals(left, right)) {
                    status = RecRecordRepository.RecStatus.MATCHED.name();
                } else {
                    status = RecRecordRepository.RecStatus.MISMATCHED.name();
                }
                diffs.put(field, status);
            }
            return FieldDiffs.toJson(diffs);
        }

        private static List<String> fieldNames(LoadedRow source, LoadedRow target) {
            LinkedHashSet<String> names = new LinkedHashSet<>();
            if (source != null) {
                names.addAll(source.fieldHashes().keySet());
            }
            if (target != null) {
                names.addAll(target.fieldHashes().keySet());
            }
            return new ArrayList<>(names);
        }
    }

    private static final class TreeSetAdapter extends java.util.TreeSet<String> {
        private TreeSetAdapter(Set<String> left, Set<String> right) {
            super(left);
            addAll(right);
        }
    }
}
