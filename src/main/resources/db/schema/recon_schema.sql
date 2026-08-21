-- Idempotent recon app-store schema (placeholder form for the service).
-- manage-schema=true  → ReconSchemaBootstrap applies this on start (no history table).
-- manage-schema=false → apply externally; see recon_schema.defaults.sql for default names.
-- Placeholders: ${runTable} ${recordTable} ${datasourceTable} ${domainTable} ${profileTable}

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
    error_message TEXT,
    domain_id VARCHAR(255),
    profile_id VARCHAR(255),
    domain_run_id BIGINT,
    active BOOLEAN NOT NULL DEFAULT false,
    recon_mode VARCHAR(32),
    source_query TEXT,
    target_query TEXT,
    condition_fields TEXT,
    run_scope VARCHAR(32),
    baseline_run_id BIGINT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by VARCHAR(128) NOT NULL DEFAULT 'data-recon'
);

CREATE INDEX IF NOT EXISTS idx_rec_run_dataset
    ON ${runTable}(dataset_id, id DESC);

CREATE INDEX IF NOT EXISTS idx_rec_run_domain
    ON ${runTable}(domain_id, id DESC);

CREATE INDEX IF NOT EXISTS idx_rec_run_profile
    ON ${runTable}(domain_id, profile_id, id DESC);

CREATE INDEX IF NOT EXISTS idx_rec_run_domain_run
    ON ${runTable}(domain_run_id);

CREATE INDEX IF NOT EXISTS idx_rec_run_active
    ON ${runTable}(domain_id, profile_id, active, id DESC);

CREATE INDEX IF NOT EXISTS idx_rec_run_baseline
    ON ${runTable}(baseline_run_id);

ALTER TABLE ${runTable} ADD COLUMN IF NOT EXISTS domain_id VARCHAR(255);
ALTER TABLE ${runTable} ADD COLUMN IF NOT EXISTS profile_id VARCHAR(255);
ALTER TABLE ${runTable} ADD COLUMN IF NOT EXISTS domain_run_id BIGINT;
ALTER TABLE ${runTable} ADD COLUMN IF NOT EXISTS active BOOLEAN NOT NULL DEFAULT false;
ALTER TABLE ${runTable} ADD COLUMN IF NOT EXISTS recon_mode VARCHAR(32);
ALTER TABLE ${runTable} ADD COLUMN IF NOT EXISTS source_query TEXT;
ALTER TABLE ${runTable} ADD COLUMN IF NOT EXISTS target_query TEXT;
ALTER TABLE ${runTable} ADD COLUMN IF NOT EXISTS condition_fields TEXT;
ALTER TABLE ${runTable} ADD COLUMN IF NOT EXISTS run_scope VARCHAR(32);
ALTER TABLE ${runTable} ADD COLUMN IF NOT EXISTS baseline_run_id BIGINT;
ALTER TABLE ${runTable} ADD COLUMN IF NOT EXISTS created_at TIMESTAMPTZ NOT NULL DEFAULT now();
ALTER TABLE ${runTable} ADD COLUMN IF NOT EXISTS created_by VARCHAR(128) NOT NULL DEFAULT 'data-recon';

CREATE TABLE IF NOT EXISTS ${recordTable} (
    id BIGSERIAL PRIMARY KEY,
    run_id BIGINT NOT NULL REFERENCES ${runTable}(id) ON DELETE CASCADE,
    migration_key VARCHAR(1024) NOT NULL,
    source_hash VARCHAR(128),
    target_hash VARCHAR(128),
    status VARCHAR(32) NOT NULL,
    field_diffs TEXT,
    source_payload TEXT,
    target_payload TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by VARCHAR(128) NOT NULL DEFAULT 'data-recon'
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_rec_record_run_key
    ON ${recordTable}(run_id, migration_key);

CREATE INDEX IF NOT EXISTS idx_rec_record_status
    ON ${recordTable}(run_id, status);

ALTER TABLE ${recordTable} ADD COLUMN IF NOT EXISTS field_diffs TEXT;
ALTER TABLE ${recordTable} ADD COLUMN IF NOT EXISTS source_payload TEXT;
ALTER TABLE ${recordTable} ADD COLUMN IF NOT EXISTS target_payload TEXT;
ALTER TABLE ${recordTable} ADD COLUMN IF NOT EXISTS created_at TIMESTAMPTZ NOT NULL DEFAULT now();
ALTER TABLE ${recordTable} ADD COLUMN IF NOT EXISTS created_by VARCHAR(128) NOT NULL DEFAULT 'data-recon';

CREATE TABLE IF NOT EXISTS ${datasourceTable} (
    id              BIGSERIAL PRIMARY KEY,
    name            VARCHAR(128) NOT NULL,
    type            VARCHAR(32) NOT NULL,
    tags_json       TEXT,
    config_json     TEXT NOT NULL,
    version         INT NOT NULL DEFAULT 1,
    active          BOOLEAN NOT NULL DEFAULT true,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by      VARCHAR(128) NOT NULL,
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_by      VARCHAR(128) NOT NULL
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_rec_datasource_active_name
    ON ${datasourceTable}(name)
    WHERE active = true;

CREATE INDEX IF NOT EXISTS idx_rec_datasource_name_version
    ON ${datasourceTable}(name, version DESC);

CREATE TABLE IF NOT EXISTS ${domainTable} (
    id              BIGSERIAL PRIMARY KEY,
    domain_id       VARCHAR(128) NOT NULL,
    tags_json       TEXT,
    config_json     TEXT NOT NULL,
    version         INT NOT NULL DEFAULT 1,
    active          BOOLEAN NOT NULL DEFAULT true,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by      VARCHAR(128) NOT NULL,
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_by      VARCHAR(128) NOT NULL
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_rec_domain_active_id
    ON ${domainTable}(domain_id)
    WHERE active = true;

CREATE INDEX IF NOT EXISTS idx_rec_domain_id_version
    ON ${domainTable}(domain_id, version DESC);

CREATE TABLE IF NOT EXISTS ${profileTable} (
    id              BIGSERIAL PRIMARY KEY,
    domain_id       VARCHAR(128) NOT NULL,
    profile_id      VARCHAR(128) NOT NULL,
    tags_json       TEXT,
    config_json     TEXT NOT NULL,
    version         INT NOT NULL DEFAULT 1,
    active          BOOLEAN NOT NULL DEFAULT true,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by      VARCHAR(128) NOT NULL,
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_by      VARCHAR(128) NOT NULL
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_rec_profile_active
    ON ${profileTable}(domain_id, profile_id)
    WHERE active = true;

CREATE INDEX IF NOT EXISTS idx_rec_profile_version
    ON ${profileTable}(domain_id, profile_id, version DESC);
