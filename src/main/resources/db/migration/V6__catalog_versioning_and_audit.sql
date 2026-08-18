-- Audit columns on recon result tables (app actor = spring.application.name / mms.recon.actor).
ALTER TABLE ${runTable}
    ADD COLUMN IF NOT EXISTS created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    ADD COLUMN IF NOT EXISTS created_by VARCHAR(128) NOT NULL DEFAULT 'data-recon';

ALTER TABLE ${recordTable}
    ADD COLUMN IF NOT EXISTS created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    ADD COLUMN IF NOT EXISTS created_by VARCHAR(128) NOT NULL DEFAULT 'data-recon';

-- Versioned catalog: updates insert a new row and mark the previous active row inactive.
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
