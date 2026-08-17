# Combination configs

Each source/target pairing is a **profile** under a **domain**. Use one YAML or one
`.properties` file per combination. Files here overlay `mms.recon` on top of
`src/main/resources/application.yml` (auth, result store, named datasources).

Full setup (create the Data Recon database, Flyway tables, datasources, then a profile)
is in the project [README](../README.md#setup).

## What you create vs what Flyway creates

| Store | You create | Data Recon creates |
|---|---|---|
| Recon result PostgreSQL | Empty database `data_recon` (name is configurable) | Schema + `rec_run` + `rec_record` on startup |
| Source / target | Business schemas, tables, collections | Nothing — profile YAML/properties only name them |

Recon table names (YAML and `.properties`): `config/database.yml`, `config/database.properties`.

## Load one combination

YAML:

```bash
mvn mn:run -Dmicronaut.config.files=config/combinations/party-pg-mongo.yml
```

Properties:

```bash
mvn mn:run -Dmicronaut.config.files=config/combinations/party-pg-mongo.properties
```

Several files (comma-separated). Maps merge, so multiple profiles can be active:

```bash
mvn mn:run "-Dmicronaut.config.files=config/combinations/party-pg-pg.yml,config/combinations/party-pg-mongo.yml"
```

Jar:

```bash
java -Dmicronaut.config.files=config/combinations/party-pg-pg.yml -jar target/data-recon-1.0.0-SNAPSHOT.jar
```

## Files

| File | Domain | Profile | Source | Target | Key | Recon mode |
|---|---|---|---|---|---|---|
| `party-pg-pg` | party | pg-pg | PostgreSQL `landing.party` | PostgreSQL `master.party` | SINGLE `party_id` | MISMATCH_DETAILS |
| `party-pg-pg-same-schema` | party | pg-pg-same-schema | PostgreSQL `public.party_landing` | PostgreSQL `public.party_master` | SINGLE `party_id` | MISMATCH_DETAILS |
| `party-pg-pg-composite` | party | pg-pg-composite | PostgreSQL `landing.party` | PostgreSQL `master.party` | COMPOSITE `party_id\|country_code` | FIELD_DETAILS |
| `party-pg-mongo` | party | pg-mongo | PostgreSQL `landing` | MongoDB `mongo` | SINGLE `party_id` | MISMATCH_DETAILS |
| `party-pg-bigquery` | party | pg-bigquery | PostgreSQL `landing` | BigQuery `bq` | SINGLE `party_id` | MISMATCH_DETAILS |
| `party-mongo-pg` | party | mongo-pg | MongoDB `mongo` | PostgreSQL `master` | SINGLE `party_id` | MISMATCH_DETAILS |
| `party-bigquery-pg` | party | bigquery-pg | BigQuery `bq` | PostgreSQL `master` | SINGLE `party_id` | MISMATCH_DETAILS |
| `party-mongo-bigquery` | party | mongo-bigquery | MongoDB `mongo` | BigQuery `bq` | SINGLE `party_id` | MISMATCH_DETAILS |
| `party-mongo-mongo` | party | mongo-mongo | MongoDB `mongo` | MongoDB `mongo` | SINGLE `party_id` | MISMATCH_DETAILS |
| `party-bigquery-mongo` | party | bigquery-mongo | BigQuery `bq` | MongoDB `mongo` | SINGLE `party_id` | MISMATCH_DETAILS |
| `party-bigquery-bigquery` | party | bigquery-bigquery | BigQuery `bq` | BigQuery `bq` | SINGLE `party_id` | MISMATCH_DETAILS |
| `account-pg-pg` | account | pg-pg | PostgreSQL `landing.account` | PostgreSQL `master.account` | SINGLE `account_id` | COUNTS |

Shared named datasources (already in `application.yml`): `landing`, `master` (PostgreSQL), `mongo`, `bq`.
Copies also live in `config/datasources.yml` / `config/datasources.properties`.
Result store: `config/database.yml` / `config/database.properties`.
Optional LLM summaries: `config/llm.yml` / `config/llm.properties` (requires both `url` and `api-key`).

Trigger after startup:

```bash
curl -u admin:admin -X POST http://localhost:8080/api/domains/party/profiles/pg-mongo/runs
```
