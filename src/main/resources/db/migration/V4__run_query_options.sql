ALTER TABLE ${runTable}
    ADD COLUMN IF NOT EXISTS source_query TEXT;

ALTER TABLE ${runTable}
    ADD COLUMN IF NOT EXISTS target_query TEXT;

ALTER TABLE ${runTable}
    ADD COLUMN IF NOT EXISTS condition_fields TEXT;
