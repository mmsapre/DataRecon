If a dataset source or target omits both "query" and "queryFile":

PostgreSQL / BigQuery / CSV / XLSX (Calcite SQL) load:
    queries/<dataset-id>-source.sql
    queries/<dataset-id>-target.sql

MongoDB loads:
    queries/<dataset-id>-source.json
    queries/<dataset-id>-target.json
or '{}' when the JSON file is absent.

Optional YAML (or API) `query` on source/target is used first when set:
    PostgreSQL / BigQuery: SQL that returns "MigrationKey" plus comparable columns
    MongoDB: JSON filter document (`collection` and `fields` still required)

PostgreSQL queries MUST return one unique column aliased as "MigrationKey".
MongoDB definitions set `collection`, `migrationKey`, and `fields`.
`fields` order MUST match the PostgreSQL SELECT columns after MigrationKey.
All remaining values are normalized, hashed, and compared in that order.

Run results are always persisted to PostgreSQL tables rec_run and rec_record.
