CREATE TABLE IF NOT EXISTS ${runTable} (
    id BIGSERIAL PRIMARY KEY,
    dataset_id VARCHAR(255) NOT NULL,
    status VARCHAR(32) NOT NULL,
    started_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    completed_at TIMESTAMPTZ,
    source_count BIGINT NOT NULL DEFAULT 0,
    target_count BIGINT NOT NULL DEFAULT 0,
    matched_count BIGINT NOT NULL DEFAULT 0,
    mismatched_count BIGINT NOT NULL DEFAULT 0,
    source_only_count BIGINT NOT NULL DEFAULT 0,
    target_only_count BIGINT NOT NULL DEFAULT 0,
    error_message TEXT
);

CREATE INDEX IF NOT EXISTS idx_rec_run_dataset
    ON ${runTable}(dataset_id, id DESC);

CREATE TABLE IF NOT EXISTS ${recordTable} (
    id BIGSERIAL PRIMARY KEY,
    run_id BIGINT NOT NULL REFERENCES ${runTable}(id) ON DELETE CASCADE,
    migration_key VARCHAR(1024) NOT NULL,
    source_hash VARCHAR(128),
    target_hash VARCHAR(128),
    status VARCHAR(32) NOT NULL
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_rec_record_run_key
    ON ${recordTable}(run_id, migration_key);

CREATE INDEX IF NOT EXISTS idx_rec_record_status
    ON ${recordTable}(run_id, status);
