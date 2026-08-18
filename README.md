# Data Recon

A Java 17 / Maven / Spring Boot service that reconciles source and target datasets:

- Java 17
- Spring Boot 3.4
- PostgreSQL source and optional PostgreSQL target via named R2DBC datasources
- MongoDB source or target via named Mongo datasources
- BigQuery source or target through Apache Calcite only (JDBC adapter + BigQuery dialect)
- CSV / XLSX source or target through Apache Calcite only (path + regex file name pattern)
- PostgreSQL persistence for run history and per-key hash results
- `MigrationKey` based reconciliation
- TypeStrict / TypeLenient hashing
- Flyway schema
- Basic authentication
- REST APIs and Swagger UI (`/swagger-ui.html`)
- Domain and profile scheduling
- Inline or file-based SQL / Mongo JSON filters
- Optional LLM summaries of run results (OpenAI-compatible API; requires URL and API key)

## Architecture

| Role | Store |
|---|---|
| Reconciliation results | PostgreSQL only (`mms.recon.database`; schema/table names configurable) |
| Source dataset | PostgreSQL, MongoDB, BigQuery (Calcite), or CSV/XLSX files (Calcite) |
| Target dataset | PostgreSQL, MongoDB, BigQuery (Calcite), or CSV/XLSX files (Calcite) |

A **domain** is a business area. Each domain has one or more **profiles**, and each
profile is one source/target combination. Trigger and results work at domain level
(all profiles) or profile level (one pairing).

The engine compares source and target independently. Any named datasource can be
used on either side:

| Domain | Profile | Source | Target |
|---|---|---|---|
| `party` | `pg-pg` | PostgreSQL `landing.party` | PostgreSQL `master.party` (other schema, same DB) |
| `party` | `pg-pg-same-schema` | PostgreSQL `public.party_landing` | PostgreSQL `public.party_master` (same schema) |
| `party` | `pg-pg-composite` | PostgreSQL `landing.party` | PostgreSQL `master.party` (composite key `party_id|country_code`) |
| `party` | `pg-mongo` | PostgreSQL `landing` | MongoDB `mongo` |
| `party` | `pg-bigquery` | PostgreSQL `landing` | BigQuery `bq` |
| `party` | `mongo-pg` | MongoDB `mongo` | PostgreSQL `master` |
| `party` | `bigquery-pg` | BigQuery `bq` | PostgreSQL `master` |
| `party` | `mongo-bigquery` | MongoDB `mongo` | BigQuery `bq` |
| `party` | `pg-csv` | PostgreSQL `landing` | CSV files via Calcite (`path` + `pattern`) |
| `party` | `pg-xlsx` | PostgreSQL `landing` | XLSX files via Calcite (`path` + `pattern`) |

Ready-to-load YAML and `.properties` for every pairing (including Mongo→Mongo,
BigQuery→Mongo, BigQuery→BigQuery, and domain `account`) are on the classpath under
[`src/main/resources/config/combinations/`](src/main/resources/config/combinations/).
See [`src/main/resources/config/README.md`](src/main/resources/config/README.md).

Spring Boot config (env-specific):

| File | Profile |
|---|---|
| [`application.yml`](src/main/resources/application.yml) | Shared: **recon DB** + auth / optional LLM |
| [`application-dev.yml`](src/main/resources/application-dev.yml) (and uat/sit/prod) | Empty catalogs; pass profile only |

**Datasources, domains, profiles, and tags** are created through the HTTP APIs after start
(see [APIs](#apis)). Sample combo YAML under
[`src/main/resources/config/combinations/`](src/main/resources/config/combinations/)
is reference-only.

Other pairings (Mongo→Mongo, BigQuery→Mongo, BigQuery→BigQuery) are the same: define
named datasources under `mms.recon.postgres.datasources`, `mms.recon.mongodb.datasources`,
`mms.recon.bigquery.datasources`, or `mms.recon.file.datasources`, then **attach** them to a profile:

```yaml
datasources:
  source: landing
  target: master
```

`type` is optional when the name exists in that catalog. Add more domains as siblings
under `mms.recon.domains` (for example `account` with its own profiles).

## Setup

You need Java 17, Maven, and a PostgreSQL instance for Data Recon’s **own** result store.
Source and target systems (PostgreSQL, MongoDB, BigQuery) are separate and must already
hold the business tables or collections you want to compare.

### 1. Create the Data Recon database

Create an empty PostgreSQL database. Flyway creates the recon tables on first start.
Do **not** create `rec_run` / `rec_record` yourself.

```sql
CREATE DATABASE data_recon;
```

If the recon user is not a superuser, grant connect and schema rights on that database.

### 2. Configure the recon store

Defaults live in [`src/main/resources/application.yml`](src/main/resources/application.yml)
(`mms.recon.database`). Optional overlay copies:
[`src/main/resources/config/database.yml`](src/main/resources/config/database.yml) /
[`.properties`](src/main/resources/config/database.properties).

```properties
mms.recon.database.host=localhost
mms.recon.database.port=5432
mms.recon.database.name=data_recon
mms.recon.database.username=postgres
mms.recon.database.password=postgres
mms.recon.database.schema=public
mms.recon.database.tables.run=rec_run
mms.recon.database.tables.record=rec_record
```

Same keys in YAML under `mms.recon.database`. Environment variables:

| Property | Env | Default |
|---|---|---|
| host | `DATA_RECON_DB_HOST` | `localhost` |
| port | `DATA_RECON_DB_PORT` | `5432` |
| name | `DATA_RECON_DB_NAME` | `data_recon` |
| username | `DATA_RECON_DB_USER` | `postgres` |
| password | `DATA_RECON_DB_PASSWORD` | `postgres` |
| schema | `DATA_RECON_DB_SCHEMA` | `public` |
| tables.run | `DATA_RECON_DB_RUN_TABLE` | `rec_run` |
| tables.record | `DATA_RECON_DB_RECORD_TABLE` | `rec_record` |

On startup Flyway:

- creates `mms.recon.database.schema` if it does not exist
- creates/updates **only** Data Recon’s tables: `rec_run` (run history) and `rec_record` (per-key hashes)

Those tables store counts, match status, hashes, and the optional source/target query plus
recon options used for that run — never source/target business values.

### 3. Prepare source and target (not created by Data Recon)

Profile config only **points at** existing tables. Example for PostgreSQL → Mongo:

```sql
CREATE SCHEMA IF NOT EXISTS landing;
CREATE SCHEMA IF NOT EXISTS master;

CREATE TABLE landing.party (
    party_id     VARCHAR(64) PRIMARY KEY,
    party_name   TEXT NOT NULL,
    country_code VARCHAR(8),
    status       VARCHAR(32)
);

CREATE TABLE master.party (
    party_id     VARCHAR(64) PRIMARY KEY,
    party_name   TEXT NOT NULL,
    country_code VARCHAR(8),
    status       VARCHAR(32)
);
```

```js
db.party.insertOne({
  party_id: "P1",
  party_name: "Acme",
  country_code: "US",
  status: "ACTIVE"
})
```

### 4. Register datasources, domains, and profiles (API)

Catalog entries (including **tags**) are **not** seeded from YAML. After the server is up:

1. `POST /api/datasources` — postgres / mongo / bigquery (+ optional `tags`)
2. `POST /api/domains` — domain (+ optional `tags`)
3. `POST /api/domains/{domainId}/profiles` — profile attaching named datasources (+ optional `tags`)

List with `?tag=...` on each resource. The UI Setup page uses the same APIs.

Optional reference samples (not auto-loaded):
[`src/main/resources/config/combinations/`](src/main/resources/config/combinations/).

### 5. Example API bodies

Datasource:

```json
{
  "name": "landing",
  "type": "postgres",
  "tags": ["party", "prod"],
  "host": "localhost",
  "port": 5432,
  "database": "data",
  "username": "postgres",
  "password": "postgres"
}
```

Domain + profile (after datasources exist): see [APIs](#apis) curl examples below.

Reference-only YAML/properties for pairings still live under
[`src/main/resources/config/combinations/`](src/main/resources/config/combinations/)
if you want copy-paste examples — they are **not** loaded at startup.

`migrationKey.type`: `SINGLE` | `COMPOSITE` | `DEFINED`  
`recon.mode`: `COUNTS` | `MISMATCH_DETAILS` | `FIELD_DETAILS`

When table/column mapping is not enough (joins, filters, expressions), set **`query`** on
source and/or target. It is optional; when present it wins over generated SQL from
`schema` / `table` / `fields`. Or set `queryFile` to a `.sql` / `.json` path.
Use **`queryParams`** with positional `?` placeholders — values are bound (prepared
statements for PostgreSQL / BigQuery; safe JSON binding for Mongo), not concatenated.

| Type | `query` | `queryParams` |
|---|---|---|
| PostgreSQL | SQL. Must alias the key as `"MigrationKey"`, then comparable columns in `fields` order | Bound to `?` via R2DBC |
| BigQuery | SQL. Alias as `MigrationKey` (Calcite). Same column contract | Bound to `?` via JDBC `PreparedStatement` |
| MongoDB | JSON filter. Still set `collection` and `fields`. `{}` is all documents | Replaces `"?"` / `?` placeholders |

```yaml
# classpath:config/combinations/party-query.yml
source:
  query: >
    SELECT party_id AS "MigrationKey", party_name, country_code, status
    FROM landing.party
    WHERE status = ?
  queryParams: [ACTIVE]
  fields: [party_name, country_code, status]
target:
  query: >
    SELECT party_id AS "MigrationKey", party_name, country_code, status
    FROM master.party
    WHERE status = ?
  queryParams: [ACTIVE]
  fields: [party_name, country_code, status]
```

```yaml
target:
  collection: party
  fields: [party_name, country_code, status]
  query: '{ "status": "?" }'
  queryParams: [ACTIVE]
```

```yaml
target:
  query: >
    SELECT party_id AS MigrationKey, party_name, country_code, status
    FROM party
    WHERE status = ?
  queryParams: [ACTIVE]
  fields: [party_name, country_code, status]
```

Each run still stores results in PostgreSQL `rec_run` / `rec_record`. Optional query text
and recon options are saved on that run (`source_query`, `target_query`, `recon_mode`,
`condition_fields`) so GET `/api/runs` shows what was compared. Record rows stay hashes
only, never business values.

Optional domain `schedule` triggers every profile on an interval. Optional profile
`schedule` triggers that pairing only.

### 6. Auth and defaults

```properties
mms.recon.auth.username=admin
mms.recon.auth.password=admin
mms.recon.defaults.hashingStrategy=TypeLenient
mms.recon.defaults.reconMode=MISMATCH_DETAILS
```

Env: `DATA_RECON_USER`, `DATA_RECON_PASSWORD` (defaults `admin` / `admin`).

The React console ([data-recon-ui](https://github.com/mmsapre/data-recon-ui)) uses a **separate backend URL and agent URL** per environment (`dev`, `uat`, `sit`, `prod`). CORS is enabled so that UI can call this API from another origin:

```bash
export DATA_RECON_CORS_ORIGIN=http://localhost:5173
```

Default origin is `http://localhost:5173`. OPTIONS requests are anonymous; `/api` still requires basic auth.

### 7. Start

```bash
export DATA_RECON_DB_HOST=localhost
export DATA_RECON_DB_PORT=5432
export DATA_RECON_DB_NAME=data_recon
export DATA_RECON_DB_USER=postgres
export DATA_RECON_DB_PASSWORD=postgres

export PG_HOST=localhost
export PG_PORT=5432
export PG_DB=data
export PG_USER=postgres
export PG_PASSWORD=postgres

export MONGO_URI=mongodb://localhost:27017
export MONGO_DB=data

export DATA_RECON_USER=admin
export DATA_RECON_PASSWORD=admin

```bash
# Pass profile only (dev is default via PROFILE / SPRING_PROFILES_ACTIVE)
mvn clean package
mvn spring-boot:run

mvn spring-boot:run -Dspring-boot.run.profiles=uat
# or: export SPRING_PROFILES_ACTIVE=prod
# or: export PROFILE=sit
```

Jar — profile only:

```bash
java -Dspring.profiles.active=prod -jar target/data-recon-1.0.0-SNAPSHOT.jar
```

Shared settings: `src/main/resources/application.yml`.  
Env catalog: `application-{dev|uat|sit|prod}.yml`.  
After start, Flyway has created `public.rec_run` and `public.rec_record` (or the names you set). Then trigger
a run (see APIs below). Each completed profile run stores counts, optional source/target
queries, recon mode, and condition fields on `rec_run`, and per-key hashes on `rec_record`.

## APIs

Setup order from the controllers: **datasources → domains → profiles** (profiles attach named
datasources). Optional **`tags`** on each level group related items; list with `?tag=party`.

```text
# 1) Datasources (source/target connections)
GET    /api/datasources
GET    /api/datasources?tag=prod
POST   /api/datasources
GET    /api/datasources/{name}
PUT    /api/datasources/{name}
DELETE /api/datasources/{name}

# 2) Domains
POST   /api/domains
GET    /api/domains
GET    /api/domains?tag=party
GET    /api/domains/{domainId}
PUT    /api/domains/{domainId}
DELETE /api/domains/{domainId}

# 3) Profiles within a domain
POST   /api/domains/{domainId}/profiles
GET    /api/domains/{domainId}/profiles
GET    /api/domains/{domainId}/profiles?tag=nightly
GET    /api/domains/{domainId}/profiles/{profileId}
PUT    /api/domains/{domainId}/profiles/{profileId}
DELETE /api/domains/{domainId}/profiles/{profileId}

PUT  /api/domains/{domainId}/profiles/{profileId}/datasources
PUT  /api/domains/{domainId}/profiles/{profileId}/source
PUT  /api/domains/{domainId}/profiles/{profileId}/target

PUT  /api/domains/{domainId}/recon
PUT  /api/domains/{domainId}/profiles/{profileId}/recon

POST /api/domains/{domainId}/runs
POST /api/domains/{domainId}/profiles/{profileId}/runs
  Body (optional):
  {
    "mode": "COUNTS|MISMATCH_DETAILS|FIELD_DETAILS",
    "conditionFields": ["col"],
    "forceFull": true
  }
  Default is incremental when a previous active profile run exists.
  forceFull=true forces a FULL extract/compare and new baseline.
  Detail modes stream source/target into DuckDB and compare with EXCEPT ALL;
  mismatch rows (with payloads) are stored and available via GET /api/runs/{runId}/records.

GET  /api/domains/{domainId}/runs?active=true
GET  /api/domains/{domainId}/runs/{domainRunId}
GET  /api/domains/{domainId}/profiles/{profileId}/runs?active=true

GET  /api/runs/{runId}/records
GET  /api/runs/{runId}/records?status=MISMATCHED

GET  /api/runs/{runId}/summary
POST /api/runs/{runId}/summary
GET  /api/domains/{domainId}/runs/{domainRunId}/summary
POST /api/domains/{domainId}/runs/{domainRunId}/summary
```

Swagger UI (no login) and OpenAPI YAML:

```text
http://localhost:8080/swagger-ui.html
http://localhost:8080/v3/api-docs
```

Use **Authorize** in Swagger UI with basic auth (`DATA_RECON_USER` / `DATA_RECON_PASSWORD`) before calling `/api` endpoints. Spec and UI are anonymous; APIs stay authenticated.

YAML still seeds datasources (`mms.recon.postgres|mongodb|bigquery|file.datasources`) and domains.
API create/update for datasources, domains, and profiles is in-memory; a restart reloads YAML only.

```bash
# 1) Register datasources (or use YAML-seeded names)
curl -u admin:admin -X POST http://localhost:8080/api/datasources \
  -H 'Content-Type: application/json' \
  -d '{"name":"landing","type":"postgres","tags":["prod","source"],"host":"localhost","database":"data"}'

curl -u admin:admin -X POST http://localhost:8080/api/datasources \
  -H 'Content-Type: application/json' \
  -d '{"name":"csv","type":"file","tags":["prod","target"],"path":"./data/files","pattern":"party.*[.]csv","format":"csv"}'

# 2) Domain
curl -u admin:admin -X POST http://localhost:8080/api/domains \
  -H 'Content-Type: application/json' \
  -d '{"id":"party","schedule":"1h","tags":["party"]}'

# 3) Profile attaching those datasources
curl -u admin:admin -X POST http://localhost:8080/api/domains/party/profiles \
  -H 'Content-Type: application/json' \
  -d '{
    "id":"pg-csv",
    "tags":["nightly"],
    "datasources":{"source":"landing","target":"csv"},
    "migrationKey":{"type":"SINGLE","columns":["party_id"]},
    "source":{"schema":"public","table":"party","fields":["party_name","status"]},
    "target":{"table":"party","fields":["party_name","status"]},
    "recon":{"mode":"FIELD_DETAILS","conditionFields":["party_name","status"]}
  }'

curl -u admin:admin -X PUT http://localhost:8080/api/domains/party/profiles/pg-csv/datasources \
  -H 'Content-Type: application/json' \
  -d '{"source":"landing","target":"csv"}'

curl -u admin:admin -X PUT http://localhost:8080/api/domains/party/profiles/pg-csv/source \
  -H 'Content-Type: application/json' \
  -d '{"schema":"public","table":"party_landing","fields":["party_name","status"]}'

# Optional: provide the query instead of schema/table (PostgreSQL / BigQuery SQL, Mongo JSON filter)
curl -u admin:admin -X PUT http://localhost:8080/api/domains/party/profiles/pg-pg/source \
  -H 'Content-Type: application/json' \
  -d '{"query":"SELECT party_id AS \"MigrationKey\", party_name, status FROM landing.party","fields":["party_name","status"]}'
```

Recon mode is set on the profile (or domain) and can be overridden when triggering a run:

| Mode | What is stored |
|---|---|
| `COUNTS` | Match / mismatch / source-only / target-only counts only |
| `MISMATCH_DETAILS` | Counts plus per-key rows for mismatches (optional `conditionFields` keep only those mismatches) |
| `FIELD_DETAILS` | Mismatch details plus which condition fields differed (hashes, not business values) |

```bash
curl -u admin:admin -X PUT http://localhost:8080/api/domains/party/profiles/pg-pg/recon \
  -H 'Content-Type: application/json' \
  -d '{"mode":"FIELD_DETAILS","conditionFields":["party_name","status"]}'

curl -u admin:admin -X POST http://localhost:8080/api/domains/party/profiles/pg-pg/runs \
  -H 'Content-Type: application/json' \
  -d '{"mode":"COUNTS"}'
```

A successful run is marked `active`. Previous runs for that profile stay stored but `active` is set to false. Failed runs do not replace the active run.

Trigger every party profile (domain level):

```bash
curl -u admin:admin -X POST \
  http://localhost:8080/api/domains/party/runs
```

Trigger one source/target profile:

```bash
curl -u admin:admin -X POST \
  http://localhost:8080/api/domains/party/profiles/pg-mongo/runs
```

PostgreSQL to PostgreSQL:

```bash
curl -u admin:admin -X POST \
  http://localhost:8080/api/domains/party/profiles/pg-pg/runs
```

PostgreSQL to BigQuery:

```bash
curl -u admin:admin -X POST \
  http://localhost:8080/api/domains/party/profiles/pg-bigquery/runs
```

List domain results (all profiles) or one profile:

```bash
curl -u admin:admin \
  http://localhost:8080/api/domains/party/runs

curl -u admin:admin \
  http://localhost:8080/api/domains/party/profiles/pg-pg/runs
```

## LLM summaries

Optional. When both an LLM **URL** and **API key** are provided, Data Recon can summarize a completed run through an OpenAI-compatible chat completions API (`/v1/chat/completions`). The prompt includes counts, statuses, and mismatch keys only — not source/target business values.

Configure both values (empty = disabled):

```yaml
mms:
  recon:
    llm:
      url: ${DATA_RECON_LLM_URL:}
      api-key: ${DATA_RECON_LLM_API_KEY:}
      model: ${DATA_RECON_LLM_MODEL:gpt-4o-mini}
```

```bash
export DATA_RECON_LLM_URL=https://api.openai.com/v1
export DATA_RECON_LLM_API_KEY=sk-...
export DATA_RECON_LLM_MODEL=gpt-4o-mini
```

Copies on classpath: [`src/main/resources/config/llm.yml`](src/main/resources/config/llm.yml)
and [`.properties`](src/main/resources/config/llm.properties) (same keys as `application.yml`).

Or pass URL and API key on the request (overrides or supplies config):

```bash
# Uses mms.recon.llm.url + api-key from config
curl -u admin:admin \
  http://localhost:8080/api/runs/42/summary

# Pass URL and API key on the request
curl -u admin:admin -X POST http://localhost:8080/api/runs/42/summary \
  -H 'Content-Type: application/json' \
  -d '{"url":"https://api.openai.com/v1","apiKey":"sk-...","model":"gpt-4o-mini"}'

curl -u admin:admin -X POST \
  http://localhost:8080/api/domains/party/runs/7/summary \
  -H 'Content-Type: application/json' \
  -d '{"url":"http://localhost:11434","apiKey":"ollama"}'
```

`GET` uses config only. `POST` accepts `url`, `apiKey` (also `api-key`), and optional `model`. Both URL and API key must be present after merge; otherwise the API returns 400.

## Reconciliation contract

PostgreSQL source/target tables can be in the **same schema** or **different schemas**.
Attach datasources on the profile. The profile `migrationKey.type` enum is:

| Type | Meaning |
|---|---|
| `SINGLE` | One column |
| `COMPOSITE` | Combination of two or more columns (joined with `separator`, default `\|`) |
| `DEFINED` | Caller-defined SQL expression (PostgreSQL/BigQuery) or Mongo field path |

```yaml
# Other schema, same database; SINGLE key
datasources:
  source: landing
  target: landing
migrationKey:
  type: SINGLE
  columns: [party_id]
source:
  schema: landing
  table: party
  fields: [party_name, country_code, status]
target:
  schema: master
  table: party
  fields: [party_name, country_code, status]
```

```yaml
# Same schema, different tables
datasources:
  source: landing
  target: landing
migrationKey:
  type: SINGLE
  columns: [party_id]
source:
  schema: public
  table: party_landing
  fields: [party_name]
target:
  schema: public
  table: party_master
  fields: [party_name]
```

```yaml
# COMPOSITE key
migrationKey:
  type: COMPOSITE
  columns: [party_id, country_code]
  separator: "|"
```

```yaml
# DEFINED key (SQL expression)
migrationKey:
  type: DEFINED
  expression: "concat(party_id, '-', country_code)"
```

Omit `schema` to use an unqualified table name (`FROM party`). To supply SQL or a Mongo
filter yourself, set `query` (or `queryFile`) on that side. Custom SQL must still return:

```sql
SELECT
    some_unique_business_key AS "MigrationKey",
    comparable_col_1,
    comparable_col_2,
    ...
```

MongoDB and extra domains:

```yaml
mms:
  recon:
    domains:
      party:
        # schedule: 1h          # optional: trigger every profile on an interval
        hashingStrategy: TypeLenient
        profiles:
          pg-mongo:
            # schedule: 15m     # optional: trigger this pairing only
            datasources:
              source: landing
              target: mongo
            migrationKey:
              type: SINGLE
              columns: [party_id]
            source:
              schema: landing
              table: party
              fields: [party_name, country_code, status]
            target:
              collection: party
              fields:
                - party_name
                - country_code
                - status
              query: '{}'
      account:
        profiles:
          pg-pg:
            datasources:
              source: landing
              target: landing
            migrationKey:
              type: SINGLE
              columns: [account_id]
            source:
              schema: landing
              table: account
              fields: [account_name, status]
            target:
              schema: master
              table: account
              fields: [account_name, status]
```

`fields` order must match the PostgreSQL SELECT columns after `MigrationKey`.
The engine compares source/target records by MigrationKey and stores only hashes
in PostgreSQL, not the business values.

## BigQuery / Calcite

BigQuery is accessed **only through Apache Calcite** (`JdbcSchema` + `BigQuerySqlDialect`).
There is no Google BigQuery client SDK. Calcite pushes SQL to a BigQuery JDBC driver
that you add on the runtime classpath as transport, for example Simba:

`com.simba.googlebigquery.jdbc.Driver`

```yaml
mms:
  recon:
    bigquery:
      datasources:
        bq:
          project-id: my-gcp-project
          dataset: master
          credentials-file: /path/to/sa.json   # optional; otherwise OAuthType=3 ADC
    domains:
      party:
        profiles:
          pg-bigquery:
            datasources:
              source: landing
              target: bq
            migrationKey:
              type: SINGLE
              columns: [party_id]
            source:
              type: postgres
              query: >
                SELECT party_id AS "MigrationKey", party_name, country_code, status
                FROM landing.party
            target:
              type: bigquery
              query: >
                SELECT party_id AS MigrationKey, party_name, country_code, status
                FROM party
```

```bash
curl -u admin:admin -X POST \
  http://localhost:8080/api/domains/party/profiles/pg-bigquery/runs
```

## CSV / XLSX (Calcite)

CSV and Excel files are queried **only through Apache Calcite**. Configure a named
datasource with a **path** (directory or single file) and a **name pattern** (Java regex).
All matching files are read as one table (`UNION ALL`). Apache POI is only the XLSX
transport, the same way a BigQuery JDBC driver is transport for BigQuery.

YAML (`application-dev.yml` or `classpath:config/datasources.yml`) and properties
(`classpath:config/datasources.properties`):

```yaml
mms:
  recon:
    file:
      datasources:
        csv:
          path: ${FILE_PATH:./data/files}
          pattern: ${FILE_PATTERN:party.*[.]csv}
          format: csv          # csv | xlsx (inferred from pattern if omitted)
          table: party         # Calcite table name used in the profile
          calcite-schema: files
        xlsx:
          path: ${XLSX_PATH:./data/files}
          pattern: ${XLSX_PATTERN:party.*[.]xlsx}
          format: xlsx
          table: party
          sheet: party         # optional; first sheet if omitted
```

```properties
mms.recon.file.datasources.csv.path=./data/files
mms.recon.file.datasources.csv.pattern=party.*[.]csv
mms.recon.file.datasources.csv.format=csv
mms.recon.file.datasources.csv.table=party
```

First row is the header (`party_id,party_name,...`). Profile `target.table` (or
`source.table`) must match `table` on the file datasource.

```yaml
datasources:
  source: landing
  target: csv
source:
  schema: landing
  table: party
  fields: [party_name, country_code, status]
target:
  table: party
  fields: [party_name, country_code, status]
```

```bash
export FILE_PATH=./data/files
export FILE_PATTERN=party.*[.]csv
curl -u admin:admin -X POST \
  http://localhost:8080/api/domains/party/profiles/pg-csv/runs
```
