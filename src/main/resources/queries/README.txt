If a dataset source or target omits both "query" and "queryFile":

PostgreSQL loads:
    queries/<dataset-id>-source.sql
    queries/<dataset-id>-target.sql

MongoDB loads:
    queries/<dataset-id>-source.json
    queries/<dataset-id>-target.json
or '{}' when the JSON file is absent.

PostgreSQL queries MUST return one unique column aliased as "MigrationKey".
MongoDB definitions set `collection`, `migrationKey`, and `fields`.
`fields` order MUST match the PostgreSQL SELECT columns after MigrationKey.
All remaining values are normalized, hashed, and compared in that order.

Run results are always persisted to PostgreSQL tables rec_run and rec_record.
