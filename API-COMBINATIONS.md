# API combinations — mock request / response

Mock HTTP examples for **every source → target pairing** Data Recon supports.
All catalog and run data is stored in Data Recon’s **own PostgreSQL** (`mms.recon.database`).
Named datasources below are only connections used while comparing business data.

| | |
|---|---|
| Base URL | `http://localhost:8080` |
| Auth | HTTP Basic `admin` / `admin` (or `DATA_RECON_USER` / `DATA_RECON_PASSWORD`) |
| Content-Type | `application/json` |

Runs are **API-triggered only** (`POST .../runs`). There is no in-app schedule/cron field on domain or profile.

Audit fields on datasource / domain / profile responses:

| Field | Meaning |
|---|---|
| `createdAt` / `createdBy` | First version (actor defaults to `data-recon`) |
| `updatedAt` / `updatedBy` | Current version |
| `active` | `true` for the live row |
| `version` | Increments on each update (previous row becomes inactive in DB) |

---

## 0. Shared datasources (register once)

Datasources are **reusable**. Prefer a connection **URI that already includes schema**
(`schema` / `currentSchema` / `search_path`). You may also set `schema` as a separate field;
it is applied on the pool and stored for profile inheritance.

Profiles attach by **name**. Multiple profiles can share the same datasource.
When the URI sets the connection search path, profile SQL can use unqualified table names
(or omit `schema` on table mapping).

### 0.1 PostgreSQL — `landing` (URI with schema)

**Request**

```http
POST /api/datasources
```

```json
{
  "name": "landing",
  "type": "postgres",
  "tags": ["source", "pg"],
  "uri": "r2dbc:postgresql://localhost:5432/data?schema=landing",
  "username": "postgres",
  "password": "postgres"
}
```

`url` is accepted as an alias of `uri` for postgres.

**Response** `201 Created`

```json
{
  "name": "landing",
  "type": "postgres",
  "schema": "landing",
  "tags": ["source", "pg"],
  "createdAt": "2026-08-19T01:00:00Z",
  "createdBy": "data-recon",
  "updatedAt": "2026-08-19T01:00:00Z",
  "updatedBy": "data-recon",
  "active": true,
  "version": 1
}
```

### 0.2 PostgreSQL — `master` (URI with schema)

**Request**

```json
{
  "name": "master",
  "type": "postgres",
  "tags": ["target", "pg"],
  "uri": "r2dbc:postgresql://localhost:5432/data?schema=master",
  "username": "postgres",
  "password": "postgres"
}
```

**Response** `201 Created` — `"name": "master"`, `"schema": "master"`, `"tags": ["target", "pg"]`.

Equivalent host/port form (schema applied on the R2DBC pool):

```json
{
  "name": "master",
  "type": "postgres",
  "host": "localhost",
  "port": 5432,
  "database": "data",
  "schema": "master",
  "username": "postgres",
  "password": "postgres"
}
```

### 0.3 MongoDB — `mongo`

**Request**

```json
{
  "name": "mongo",
  "type": "mongo",
  "tags": ["mongo"],
  "uri": "mongodb://localhost:27017",
  "database": "recon",
  "authDatabase": "admin"
}
```

**Response** `201 Created`

```json
{
  "name": "mongo",
  "type": "mongo",
  "tags": ["mongo"],
  "createdAt": "2026-08-19T01:00:00Z",
  "createdBy": "data-recon",
  "updatedAt": "2026-08-19T01:00:00Z",
  "updatedBy": "data-recon",
  "active": true,
  "version": 1
}
```

### 0.4 BigQuery — `bq`

**Request**

```json
{
  "name": "bq",
  "type": "bigquery",
  "tags": ["bq"],
  "projectId": "my-gcp-project",
  "dataset": "recon",
  "schema": "recon",
  "credentialsFile": "/secrets/bq-sa.json",
  "oauthType": 3,
  "calciteSchema": "bq"
}
```

**Response** `201 Created`

```json
{
  "name": "bq",
  "type": "bigquery",
  "schema": "recon",
  "tags": ["bq"],
  "createdAt": "2026-08-19T01:00:00Z",
  "createdBy": "data-recon",
  "updatedAt": "2026-08-19T01:00:00Z",
  "updatedBy": "data-recon",
  "active": true,
  "version": 1
}
```

### 0.5 File CSV — `csv`

**Request**

```json
{
  "name": "csv",
  "type": "file",
  "tags": ["file", "csv"],
  "path": "./data/files",
  "pattern": "party.*[.]csv",
  "format": "csv",
  "header": true,
  "delimiter": ","
}
```

**Response** `201 Created`

```json
{
  "name": "csv",
  "type": "file",
  "tags": ["file", "csv"],
  "createdAt": "2026-08-19T01:00:00Z",
  "createdBy": "data-recon",
  "updatedAt": "2026-08-19T01:00:00Z",
  "updatedBy": "data-recon",
  "active": true,
  "version": 1
}
```

### 0.6 File XLSX — `xlsx`

**Request**

```json
{
  "name": "xlsx",
  "type": "file",
  "tags": ["file", "xlsx"],
  "path": "./data/files",
  "pattern": "party.*[.]xlsx",
  "format": "xlsx",
  "sheet": "Sheet1",
  "header": true
}
```

**Response** `201 Created` — same shape, `"name": "xlsx"`, `"tags": ["file", "xlsx"]`.

---

## 1. Domain

**Request**

```http
POST /api/domains
```

```json
{
  "id": "party",
  "hashingStrategy": "TypeLenient",
  "tags": ["party"]
}
```

**Response** `201 Created`

```json
{
  "id": "party",
  "hashingStrategy": "TypeLenient",
  "tags": ["party"],
  "profiles": [],
  "createdAt": "2026-08-19T01:01:00Z",
  "createdBy": "data-recon",
  "updatedAt": "2026-08-19T01:01:00Z",
  "updatedBy": "data-recon",
  "active": true,
  "version": 1
}
```

---

## 1b. End-to-end setup samples (datasource → domain → profile → run)

Both count and detail profiles use the **same detail query shape**
(`MigrationKey` + comparable `fields`). Engine path differs:

| `recon.mode` | Engine | Stored |
|---|---|---|
| `COUNTS` | In-memory **hash** of field values (no DuckDB) | Counts only |
| `MISMATCH_DETAILS` | Rows → **DuckDB** `EXCEPT ALL` | Counts + mismatch / source-only / target-only rows |
| `FIELD_DETAILS` | Rows → **DuckDB** `EXCEPT ALL` | Same + which `conditionFields` differed (+ payloads) |

SQL can be **plain** or **conditional** (`active`, `status`, open-ended
`end_date = '2099-12-31 00:00:00'`, etc.).

---

### Step A — Datasources (URI includes schema)

```http
POST /api/datasources
Authorization: Basic YWRtaW46YWRtaW4=
```

**Request — source**

```json
{
  "name": "landing",
  "type": "postgres",
  "tags": ["source"],
  "uri": "r2dbc:postgresql://localhost:5432/data?schema=landing",
  "username": "postgres",
  "password": "postgres"
}
```

**Response** `201`

```json
{
  "name": "landing",
  "type": "postgres",
  "schema": "landing",
  "tags": ["source"],
  "createdAt": "2026-08-19T01:00:00Z",
  "createdBy": "data-recon",
  "updatedAt": "2026-08-19T01:00:00Z",
  "updatedBy": "data-recon",
  "active": true,
  "version": 1
}
```

**Request — target**

```json
{
  "name": "master",
  "type": "postgres",
  "tags": ["target"],
  "uri": "r2dbc:postgresql://localhost:5432/data?schema=master",
  "username": "postgres",
  "password": "postgres"
}
```

**Response** `201` — `"name": "master"`, `"schema": "master"`, `"active": true`, `"version": 1`.

---

### Step B — Domain (datasources attached + hashing strategy)

Datasources attach at **domain** (defaults for all profiles) and/or **profile** (override).
Set `hashingStrategy` on the domain; profiles inherit unless they set their own.

| `hashingStrategy` | When to use |
|---|---|
| `TypeLenient` | Cross-system compare (PG ↔ Mongo ↔ BQ): numbers/bools/timestamps normalized |
| `TypeStrict` | Same Java types expected on both sides; type is part of the hash |

#### Domain with datasources + `TypeLenient` (default-friendly)

```http
POST /api/domains
```

**Request**

```json
{
  "id": "party",
  "hashingStrategy": "TypeLenient",
  "tags": ["party"],
  "datasources": { "source": "landing", "target": "master" }
}
```

**Response** `201`

```json
{
  "id": "party",
  "hashingStrategy": "TypeLenient",
  "tags": ["party"],
  "sourceDatasource": "landing",
  "targetDatasource": "master",
  "profiles": [],
  "createdAt": "2026-08-19T01:01:00Z",
  "createdBy": "data-recon",
  "updatedAt": "2026-08-19T01:01:00Z",
  "updatedBy": "data-recon",
  "active": true,
  "version": 1
}
```

#### Domain with `TypeStrict`

```json
{
  "id": "party-strict",
  "hashingStrategy": "TypeStrict",
  "tags": ["party", "strict"],
  "datasources": { "source": "landing", "target": "master" }
}
```

**Response** `201` — `"hashingStrategy": "TypeStrict"`, `"sourceDatasource": "landing"`, `"targetDatasource": "master"`.

#### Attach datasources after domain create

```http
PUT /api/domains/party/datasources
```

```json
{ "source": "landing", "target": "master" }
```

**Response** `200` — domain with `"sourceDatasource": "landing"`, `"targetDatasource": "master"`, `"version": 2`.

#### Profile inherits domain hashing + datasources

Omit `datasources` / `hashingStrategy` on the profile to inherit from the domain.

```json
{
  "id": "pg-pg-inherited",
  "migrationKey": { "type": "SINGLE", "columns": ["party_id"] },
  "source": { "table": "party", "fields": ["party_name", "status"] },
  "target": { "table": "party", "fields": ["party_name", "status"] },
  "recon": { "mode": "COUNTS" }
}
```

**Response** — `"hashingStrategy": "TypeLenient"` (from domain), `"sourceDatasource": "landing"`, `"targetDatasource": "master"`.

#### Profile overrides hashing to `TypeStrict`

```json
{
  "id": "pg-pg-strict",
  "hashingStrategy": "TypeStrict",
  "datasources": { "source": "landing", "target": "master" },
  "migrationKey": { "type": "SINGLE", "columns": ["party_id"] },
  "source": { "table": "party", "fields": ["party_name", "status"] },
  "target": { "table": "party", "fields": ["party_name", "status"] },
  "recon": { "mode": "FIELD_DETAILS", "conditionFields": ["party_name", "status"] }
}
```

**Response** — `"hashingStrategy": "TypeStrict"` (profile override).

Profiles omit `datasources` to inherit, or set their own / call
`PUT /api/domains/party/profiles/{profileId}/datasources`.

---

### Step C1 — Profile `COUNTS` with **plain SQL**

```http
POST /api/domains/party/profiles
```

**Request**

```json
{
  "id": "pg-pg-counts",
  "tags": ["counts"],
  "datasources": { "source": "landing", "target": "master" },
  "migrationKey": { "type": "SINGLE", "columns": ["party_id"] },
  "source": {
    "query": "SELECT party_id AS \"MigrationKey\", party_name, status FROM party",
    "fields": ["party_name", "status"]
  },
  "target": {
    "query": "SELECT party_id AS \"MigrationKey\", party_name, status FROM party",
    "fields": ["party_name", "status"]
  },
  "recon": { "mode": "COUNTS" }
}
```

**Response** `201`

```json
{
  "domainId": "party",
  "profileId": "pg-pg-counts",
  "id": "party.pg-pg-counts",
  "sourceDatasource": "landing",
  "sourceType": "postgres",
  "targetDatasource": "master",
  "targetType": "postgres",
  "migrationKeyType": "SINGLE",
  "migrationKeyColumns": ["party_id"],
  "hashingStrategy": "TypeLenient",
  "reconMode": "COUNTS",
  "conditionFields": [],
  "tags": ["counts"],
  "createdAt": "2026-08-19T01:02:00Z",
  "createdBy": "data-recon",
  "updatedAt": "2026-08-19T01:02:00Z",
  "updatedBy": "data-recon",
  "active": true,
  "version": 1
}
```

**Trigger**

```http
POST /api/domains/party/profiles/pg-pg-counts/runs
```

```json
{}
```

**Response** `202`

```json
{ "domainId": "party", "profileId": "pg-pg-counts", "runId": 101 }
```

**Get run** `GET /api/domains/party/profiles/pg-pg-counts/runs/101`

```json
{
  "id": 101,
  "datasetId": "party.pg-pg-counts",
  "domainId": "party",
  "profileId": "pg-pg-counts",
  "status": "COMPLETED",
  "sourceCount": 1000,
  "targetCount": 998,
  "matchedCount": 990,
  "mismatchedCount": 5,
  "sourceOnlyCount": 5,
  "targetOnlyCount": 3,
  "active": true,
  "reconMode": "COUNTS",
  "sourceQuery": "SELECT party_id AS \"MigrationKey\", party_name, status FROM party",
  "targetQuery": "SELECT party_id AS \"MigrationKey\", party_name, status FROM party",
  "conditionFields": [],
  "runScope": "FULL",
  "baselineRunId": null
}
```

`GET .../runs/101/records` → `[]` (counts mode stores no per-key rows).

---

### Step C2 — Profile `FIELD_DETAILS` with **plain SQL**

**Request**

```json
{
  "id": "pg-pg-details",
  "tags": ["details"],
  "datasources": { "source": "landing", "target": "master" },
  "migrationKey": { "type": "SINGLE", "columns": ["party_id"] },
  "source": {
    "query": "SELECT party_id AS \"MigrationKey\", party_name, status FROM party",
    "fields": ["party_name", "status"]
  },
  "target": {
    "query": "SELECT party_id AS \"MigrationKey\", party_name, status FROM party",
    "fields": ["party_name", "status"]
  },
  "recon": {
    "mode": "FIELD_DETAILS",
    "conditionFields": ["party_name", "status"]
  }
}
```

**Response** `201` — `"reconMode": "FIELD_DETAILS"`, `"conditionFields": ["party_name", "status"]`, `"version": 1`.

**Trigger** → `202` `{ "domainId": "party", "profileId": "pg-pg-details", "runId": 102 }`

**Records** `GET /api/domains/party/profiles/pg-pg-details/runs/102/records?status=MISMATCHED`

```json
[
  {
    "migrationKey": "P-100",
    "sourceHash": "a1b2…",
    "targetHash": "c3d4…",
    "status": "MISMATCHED",
    "fieldDiffs": "{\"party_name\":\"MATCHED\",\"status\":\"MISMATCHED\"}",
    "sourcePayload": "[\"Acme\",\"ACTIVE\"]",
    "targetPayload": "[\"Acme\",\"CLOSED\"]"
  }
]
```

---

### Step C3 — Profile with **conditional SQL** (active / status / open end-date)

Use the same query shape for count or details; only `recon.mode` changes.
Example: active rows, non-null status, open-ended membership
(`end_date = '2099-12-31 00:00:00'`).

**COUNTS + conditional SQL**

```json
{
  "id": "pg-pg-counts-active",
  "tags": ["counts", "conditional"],
  "datasources": { "source": "landing", "target": "master" },
  "migrationKey": { "type": "SINGLE", "columns": ["party_id"] },
  "source": {
    "query": "SELECT party_id AS \"MigrationKey\", party_name, status FROM party WHERE active = true AND status IS NOT NULL AND end_date = TIMESTAMP '2099-12-31 00:00:00'",
    "fields": ["party_name", "status"]
  },
  "target": {
    "query": "SELECT party_id AS \"MigrationKey\", party_name, status FROM party WHERE active = true AND status IS NOT NULL AND end_date = TIMESTAMP '2099-12-31 00:00:00'",
    "fields": ["party_name", "status"]
  },
  "recon": { "mode": "COUNTS" }
}
```

**Response** `201`

```json
{
  "domainId": "party",
  "profileId": "pg-pg-counts-active",
  "id": "party.pg-pg-counts-active",
  "sourceDatasource": "landing",
  "sourceType": "postgres",
  "targetDatasource": "master",
  "targetType": "postgres",
  "migrationKeyType": "SINGLE",
  "migrationKeyColumns": ["party_id"],
  "hashingStrategy": "TypeLenient",
  "reconMode": "COUNTS",
  "conditionFields": [],
  "tags": ["counts", "conditional"],
  "createdAt": "2026-08-19T01:03:00Z",
  "createdBy": "data-recon",
  "updatedAt": "2026-08-19T01:03:00Z",
  "updatedBy": "data-recon",
  "active": true,
  "version": 1
}
```

**FIELD_DETAILS + conditional SQL** (same filters; DuckDB path)

```json
{
  "id": "pg-pg-details-active",
  "tags": ["details", "conditional"],
  "datasources": { "source": "landing", "target": "master" },
  "migrationKey": { "type": "SINGLE", "columns": ["party_id"] },
  "source": {
    "query": "SELECT party_id AS \"MigrationKey\", party_name, status, country_code FROM party WHERE active = true AND upper(status) IN ('ACTIVE','PENDING') AND (end_date IS NULL OR end_date = TIMESTAMP '2099-12-31 00:00:00')",
    "fields": ["party_name", "status", "country_code"]
  },
  "target": {
    "query": "SELECT party_id AS \"MigrationKey\", party_name, status, country_code FROM party WHERE active = true AND upper(status) IN ('ACTIVE','PENDING') AND (end_date IS NULL OR end_date = TIMESTAMP '2099-12-31 00:00:00')",
    "fields": ["party_name", "status", "country_code"]
  },
  "recon": {
    "mode": "FIELD_DETAILS",
    "conditionFields": ["party_name", "status", "country_code"]
  }
}
```

**Response** `201` — `"reconMode": "FIELD_DETAILS"`, `"profileId": "pg-pg-details-active"`.

**Run response** (after `POST .../pg-pg-details-active/runs`)

```json
{ "domainId": "party", "profileId": "pg-pg-details-active", "runId": 103 }
```

**Completed run** includes the conditional SQL in `sourceQuery` / `targetQuery`:

```json
{
  "id": 103,
  "profileId": "pg-pg-details-active",
  "status": "COMPLETED",
  "matchedCount": 800,
  "mismatchedCount": 12,
  "sourceOnlyCount": 4,
  "targetOnlyCount": 2,
  "reconMode": "FIELD_DETAILS",
  "sourceQuery": "SELECT party_id AS \"MigrationKey\", party_name, status, country_code FROM party WHERE active = true AND upper(status) IN ('ACTIVE','PENDING') AND (end_date IS NULL OR end_date = TIMESTAMP '2099-12-31 00:00:00')",
  "targetQuery": "SELECT party_id AS \"MigrationKey\", party_name, status, country_code FROM party WHERE active = true AND upper(status) IN ('ACTIVE','PENDING') AND (end_date IS NULL OR end_date = TIMESTAMP '2099-12-31 00:00:00')",
  "conditionFields": ["party_name", "status", "country_code"],
  "active": true
}
```

**Parameterized conditional** (optional `queryParams` for `?` placeholders):

```json
{
  "id": "pg-pg-details-params",
  "datasources": { "source": "landing", "target": "master" },
  "migrationKey": { "type": "SINGLE", "columns": ["party_id"] },
  "source": {
    "query": "SELECT party_id AS \"MigrationKey\", party_name, status FROM party WHERE active = ? AND end_date = ?",
    "queryParams": [true, "2099-12-31 00:00:00"],
    "fields": ["party_name", "status"]
  },
  "target": {
    "query": "SELECT party_id AS \"MigrationKey\", party_name, status FROM party WHERE active = ? AND end_date = ?",
    "queryParams": [true, "2099-12-31 00:00:00"],
    "fields": ["party_name", "status"]
  },
  "recon": { "mode": "MISMATCH_DETAILS", "conditionFields": ["status"] }
}
```

---

### Step C4 — Table mapping (no SQL; schema from datasource URI)

```json
{
  "id": "pg-pg-tables",
  "datasources": { "source": "landing", "target": "master" },
  "migrationKey": { "type": "SINGLE", "columns": ["party_id"] },
  "source": { "table": "party", "fields": ["party_name", "status"] },
  "target": { "table": "party", "fields": ["party_name", "status"] },
  "recon": { "mode": "COUNTS" }
}
```

Engine generates `SELECT party_id AS "MigrationKey", party_name, status FROM party`
using the datasource search_path / schema.

---

### Step D — List domain / profiles

```http
GET /api/domains/party
```

```json
{
  "id": "party",
  "hashingStrategy": "TypeLenient",
  "tags": ["party"],
  "profiles": [
    { "profileId": "pg-pg-counts", "reconMode": "COUNTS", "active": true, "version": 1 },
    { "profileId": "pg-pg-details", "reconMode": "FIELD_DETAILS", "active": true, "version": 1 },
    { "profileId": "pg-pg-counts-active", "reconMode": "COUNTS", "active": true, "version": 1 },
    { "profileId": "pg-pg-details-active", "reconMode": "FIELD_DETAILS", "active": true, "version": 1 }
  ],
  "active": true,
  "version": 1
}
```

(`profiles` entries are full `ProfileApiModel` objects; shortened above.)

---

### Override mode on run (optional)

```http
POST /api/domains/party/profiles/pg-pg-details-active/runs
```

```json
{ "mode": "COUNTS", "forceFull": true }
```

Uses the profile’s conditional SQL, but runs hash/counts path for this execution.

---

## 2. Profile combinations (source → target)

Create with:

```http
POST /api/domains/party/profiles
```

Trigger a run with:

```http
POST /api/domains/party/profiles/{profileId}/runs
```

Optional run body:

```json
{ "mode": "MISMATCH_DETAILS", "forceFull": true }
```

**Accepted run response** (all combinations):

```json
{
  "domainId": "party",
  "profileId": "<profileId>",
  "runId": 101
}
```

Below: create **request** + **response** per combination. Register the datasources in §0 first.

### Matrix

| Profile id | Source | Target |
|---|---|---|
| [`pg-pg`](#21-postgres--postgres-pg-pg) | postgres `landing` | postgres `master` |
| [`pg-pg-same-schema`](#22-postgres--postgres-same-schema) | postgres `landing` | postgres `landing` |
| [`pg-pg-composite`](#23-postgres--postgres-composite-key) | postgres `landing` | postgres `master` |
| [`pg-mongo`](#24-postgres--mongo) | postgres | mongo |
| [`mongo-pg`](#25-mongo--postgres) | mongo | postgres |
| [`mongo-mongo`](#26-mongo--mongo) | mongo | mongo |
| [`pg-bigquery`](#27-postgres--bigquery) | postgres | bigquery |
| [`bigquery-pg`](#28-bigquery--postgres) | bigquery | postgres |
| [`mongo-bigquery`](#29-mongo--bigquery) | mongo | bigquery |
| [`bigquery-mongo`](#210-bigquery--mongo) | bigquery | mongo |
| [`bigquery-bigquery`](#211-bigquery--bigquery) | bigquery | bigquery |
| [`pg-csv`](#212-postgres--csv-file) | postgres | file csv |
| [`pg-xlsx`](#213-postgres--xlsx-file) | postgres | file xlsx |
| [`mongo-csv`](#214-mongo--csv-file) | mongo | file csv |
| [`bigquery-csv`](#215-bigquery--csv-file) | bigquery | file csv |
| [`csv-pg`](#216-csv-file--postgres) | file csv | postgres |
| [`csv-mongo`](#217-csv-file--mongo) | file csv | mongo |
| [`csv-bigquery`](#218-csv-file--bigquery) | file csv | bigquery |
| [`csv-csv`](#219-csv-file--csv-file) | file csv | file csv |
| [`xlsx-pg`](#220-xlsx-file--postgres) | file xlsx | postgres |

---

### 2.1 Postgres → Postgres (`pg-pg`)

Schema comes from the shared datasources (`landing` / `master`); the profile only sets table + fields.

**Request**

```json
{
  "id": "pg-pg",
  "tags": ["pg-pg"],
  "datasources": { "source": "landing", "target": "master" },
  "migrationKey": { "type": "SINGLE", "columns": ["party_id"] },
  "source": {
    "table": "party",
    "fields": ["party_name", "status"]
  },
  "target": {
    "table": "party",
    "fields": ["party_name", "status"]
  },
  "recon": {
    "mode": "FIELD_DETAILS",
    "conditionFields": ["party_name", "status"]
  }
}
```

**Response** `201 Created`

```json
{
  "domainId": "party",
  "profileId": "pg-pg",
  "id": "party.pg-pg",
  "sourceDatasource": "landing",
  "sourceType": "postgres",
  "targetDatasource": "master",
  "targetType": "postgres",
  "migrationKeyType": "SINGLE",
  "migrationKeyColumns": ["party_id"],
  "hashingStrategy": "TypeLenient",
  "reconMode": "FIELD_DETAILS",
  "conditionFields": ["party_name", "status"],
  "tags": ["pg-pg"],
  "createdAt": "2026-08-19T01:02:00Z",
  "createdBy": "data-recon",
  "updatedAt": "2026-08-19T01:02:00Z",
  "updatedBy": "data-recon",
  "active": true,
  "version": 1
}
```

---

### 2.2 Postgres → Postgres same schema

**Request**

```json
{
  "id": "pg-pg-same-schema",
  "datasources": { "source": "landing", "target": "landing" },
  "migrationKey": { "type": "SINGLE", "columns": ["party_id"] },
  "source": {
    "schema": "public",
    "table": "party_landing",
    "fields": ["party_name", "status"]
  },
  "target": {
    "schema": "public",
    "table": "party_master",
    "fields": ["party_name", "status"]
  },
  "recon": { "mode": "MISMATCH_DETAILS" }
}
```

**Response** `201` — `sourceType` / `targetType` both `"postgres"`, same datasource name on both sides:

```json
{
  "domainId": "party",
  "profileId": "pg-pg-same-schema",
  "id": "party.pg-pg-same-schema",
  "sourceDatasource": "landing",
  "sourceType": "postgres",
  "targetDatasource": "landing",
  "targetType": "postgres",
  "migrationKeyType": "SINGLE",
  "migrationKeyColumns": ["party_id"],
  "hashingStrategy": "TypeLenient",
  "reconMode": "MISMATCH_DETAILS",
  "conditionFields": [],
  "tags": [],
  "createdAt": "2026-08-19T01:02:00Z",
  "createdBy": "data-recon",
  "updatedAt": "2026-08-19T01:02:00Z",
  "updatedBy": "data-recon",
  "active": true,
  "version": 1
}
```

---

### 2.3 Postgres → Postgres composite key

**Request**

```json
{
  "id": "pg-pg-composite",
  "datasources": { "source": "landing", "target": "master" },
  "migrationKey": {
    "type": "COMPOSITE",
    "columns": ["party_id", "country_code"],
    "separator": "|"
  },
  "source": {
    "schema": "landing",
    "table": "party",
    "fields": ["party_name", "status"]
  },
  "target": {
    "schema": "master",
    "table": "party",
    "fields": ["party_name", "status"]
  },
  "recon": { "mode": "COUNTS" }
}
```

**Response** `201`

```json
{
  "domainId": "party",
  "profileId": "pg-pg-composite",
  "id": "party.pg-pg-composite",
  "sourceDatasource": "landing",
  "sourceType": "postgres",
  "targetDatasource": "master",
  "targetType": "postgres",
  "migrationKeyType": "COMPOSITE",
  "migrationKeyColumns": ["party_id", "country_code"],
  "hashingStrategy": "TypeLenient",
  "reconMode": "COUNTS",
  "conditionFields": [],
  "tags": [],
  "createdAt": "2026-08-19T01:02:00Z",
  "createdBy": "data-recon",
  "updatedAt": "2026-08-19T01:02:00Z",
  "updatedBy": "data-recon",
  "active": true,
  "version": 1
}
```

---

### 2.4 Postgres → Mongo

**Request**

```json
{
  "id": "pg-mongo",
  "datasources": { "source": "landing", "target": "mongo" },
  "migrationKey": { "type": "SINGLE", "columns": ["party_id"] },
  "source": {
    "schema": "landing",
    "table": "party",
    "fields": ["party_name", "status"]
  },
  "target": {
    "collection": "party",
    "fields": ["party_name", "status"]
  },
  "recon": { "mode": "FIELD_DETAILS", "conditionFields": ["party_name", "status"] }
}
```

**Response** `201`

```json
{
  "domainId": "party",
  "profileId": "pg-mongo",
  "id": "party.pg-mongo",
  "sourceDatasource": "landing",
  "sourceType": "postgres",
  "targetDatasource": "mongo",
  "targetType": "mongo",
  "migrationKeyType": "SINGLE",
  "migrationKeyColumns": ["party_id"],
  "hashingStrategy": "TypeLenient",
  "reconMode": "FIELD_DETAILS",
  "conditionFields": ["party_name", "status"],
  "tags": [],
  "createdAt": "2026-08-19T01:02:00Z",
  "createdBy": "data-recon",
  "updatedAt": "2026-08-19T01:02:00Z",
  "updatedBy": "data-recon",
  "active": true,
  "version": 1
}
```

---

### 2.5 Mongo → Postgres

**Request**

```json
{
  "id": "mongo-pg",
  "datasources": { "source": "mongo", "target": "master" },
  "migrationKey": { "type": "SINGLE", "columns": ["party_id"] },
  "source": {
    "collection": "party",
    "fields": ["party_name", "status"]
  },
  "target": {
    "schema": "master",
    "table": "party",
    "fields": ["party_name", "status"]
  },
  "recon": { "mode": "MISMATCH_DETAILS" }
}
```

**Response** `201`

```json
{
  "domainId": "party",
  "profileId": "mongo-pg",
  "id": "party.mongo-pg",
  "sourceDatasource": "mongo",
  "sourceType": "mongo",
  "targetDatasource": "master",
  "targetType": "postgres",
  "migrationKeyType": "SINGLE",
  "migrationKeyColumns": ["party_id"],
  "hashingStrategy": "TypeLenient",
  "reconMode": "MISMATCH_DETAILS",
  "conditionFields": [],
  "tags": [],
  "createdAt": "2026-08-19T01:02:00Z",
  "createdBy": "data-recon",
  "updatedAt": "2026-08-19T01:02:00Z",
  "updatedBy": "data-recon",
  "active": true,
  "version": 1
}
```

---

### 2.6 Mongo → Mongo

Register a second mongo datasource (e.g. `mongo-tgt`) the same way as §0.3, then:

**Request**

```json
{
  "id": "mongo-mongo",
  "datasources": { "source": "mongo", "target": "mongo-tgt" },
  "migrationKey": { "type": "SINGLE", "columns": ["party_id"] },
  "source": { "collection": "party_src", "fields": ["party_name", "status"] },
  "target": { "collection": "party_tgt", "fields": ["party_name", "status"] },
  "recon": { "mode": "COUNTS" }
}
```

**Response** `201`

```json
{
  "domainId": "party",
  "profileId": "mongo-mongo",
  "id": "party.mongo-mongo",
  "sourceDatasource": "mongo",
  "sourceType": "mongo",
  "targetDatasource": "mongo-tgt",
  "targetType": "mongo",
  "migrationKeyType": "SINGLE",
  "migrationKeyColumns": ["party_id"],
  "hashingStrategy": "TypeLenient",
  "reconMode": "COUNTS",
  "conditionFields": [],
  "tags": [],
  "createdAt": "2026-08-19T01:02:00Z",
  "createdBy": "data-recon",
  "updatedAt": "2026-08-19T01:02:00Z",
  "updatedBy": "data-recon",
  "active": true,
  "version": 1
}
```

---

### 2.7 Postgres → BigQuery

**Request**

```json
{
  "id": "pg-bigquery",
  "datasources": { "source": "landing", "target": "bq" },
  "migrationKey": { "type": "SINGLE", "columns": ["party_id"] },
  "source": {
    "schema": "landing",
    "table": "party",
    "fields": ["party_name", "status"]
  },
  "target": {
    "schema": "recon",
    "table": "party",
    "fields": ["party_name", "status"]
  },
  "recon": { "mode": "FIELD_DETAILS", "conditionFields": ["party_name", "status"] }
}
```

**Response** `201`

```json
{
  "domainId": "party",
  "profileId": "pg-bigquery",
  "id": "party.pg-bigquery",
  "sourceDatasource": "landing",
  "sourceType": "postgres",
  "targetDatasource": "bq",
  "targetType": "bigquery",
  "migrationKeyType": "SINGLE",
  "migrationKeyColumns": ["party_id"],
  "hashingStrategy": "TypeLenient",
  "reconMode": "FIELD_DETAILS",
  "conditionFields": ["party_name", "status"],
  "tags": [],
  "createdAt": "2026-08-19T01:02:00Z",
  "createdBy": "data-recon",
  "updatedAt": "2026-08-19T01:02:00Z",
  "updatedBy": "data-recon",
  "active": true,
  "version": 1
}
```

---

### 2.8 BigQuery → Postgres

**Request**

```json
{
  "id": "bigquery-pg",
  "datasources": { "source": "bq", "target": "master" },
  "migrationKey": { "type": "SINGLE", "columns": ["party_id"] },
  "source": {
    "schema": "recon",
    "table": "party",
    "fields": ["party_name", "status"]
  },
  "target": {
    "schema": "master",
    "table": "party",
    "fields": ["party_name", "status"]
  },
  "recon": { "mode": "MISMATCH_DETAILS" }
}
```

**Response** `201`

```json
{
  "domainId": "party",
  "profileId": "bigquery-pg",
  "id": "party.bigquery-pg",
  "sourceDatasource": "bq",
  "sourceType": "bigquery",
  "targetDatasource": "master",
  "targetType": "postgres",
  "migrationKeyType": "SINGLE",
  "migrationKeyColumns": ["party_id"],
  "hashingStrategy": "TypeLenient",
  "reconMode": "MISMATCH_DETAILS",
  "conditionFields": [],
  "tags": [],
  "createdAt": "2026-08-19T01:02:00Z",
  "createdBy": "data-recon",
  "updatedAt": "2026-08-19T01:02:00Z",
  "updatedBy": "data-recon",
  "active": true,
  "version": 1
}
```

---

### 2.9 Mongo → BigQuery

**Request**

```json
{
  "id": "mongo-bigquery",
  "datasources": { "source": "mongo", "target": "bq" },
  "migrationKey": { "type": "SINGLE", "columns": ["party_id"] },
  "source": { "collection": "party", "fields": ["party_name", "status"] },
  "target": {
    "schema": "recon",
    "table": "party",
    "fields": ["party_name", "status"]
  },
  "recon": { "mode": "COUNTS" }
}
```

**Response** `201`

```json
{
  "domainId": "party",
  "profileId": "mongo-bigquery",
  "id": "party.mongo-bigquery",
  "sourceDatasource": "mongo",
  "sourceType": "mongo",
  "targetDatasource": "bq",
  "targetType": "bigquery",
  "migrationKeyType": "SINGLE",
  "migrationKeyColumns": ["party_id"],
  "hashingStrategy": "TypeLenient",
  "reconMode": "COUNTS",
  "conditionFields": [],
  "tags": [],
  "createdAt": "2026-08-19T01:02:00Z",
  "createdBy": "data-recon",
  "updatedAt": "2026-08-19T01:02:00Z",
  "updatedBy": "data-recon",
  "active": true,
  "version": 1
}
```

---

### 2.10 BigQuery → Mongo

**Request**

```json
{
  "id": "bigquery-mongo",
  "datasources": { "source": "bq", "target": "mongo" },
  "migrationKey": { "type": "SINGLE", "columns": ["party_id"] },
  "source": {
    "schema": "recon",
    "table": "party",
    "fields": ["party_name", "status"]
  },
  "target": { "collection": "party", "fields": ["party_name", "status"] },
  "recon": { "mode": "FIELD_DETAILS", "conditionFields": ["party_name"] }
}
```

**Response** `201`

```json
{
  "domainId": "party",
  "profileId": "bigquery-mongo",
  "id": "party.bigquery-mongo",
  "sourceDatasource": "bq",
  "sourceType": "bigquery",
  "targetDatasource": "mongo",
  "targetType": "mongo",
  "migrationKeyType": "SINGLE",
  "migrationKeyColumns": ["party_id"],
  "hashingStrategy": "TypeLenient",
  "reconMode": "FIELD_DETAILS",
  "conditionFields": ["party_name"],
  "tags": [],
  "createdAt": "2026-08-19T01:02:00Z",
  "createdBy": "data-recon",
  "updatedAt": "2026-08-19T01:02:00Z",
  "updatedBy": "data-recon",
  "active": true,
  "version": 1
}
```

---

### 2.11 BigQuery → BigQuery

Register `bq-tgt` like §0.4, then:

**Request**

```json
{
  "id": "bigquery-bigquery",
  "datasources": { "source": "bq", "target": "bq-tgt" },
  "migrationKey": { "type": "SINGLE", "columns": ["party_id"] },
  "source": {
    "schema": "recon_src",
    "table": "party",
    "fields": ["party_name", "status"]
  },
  "target": {
    "schema": "recon_tgt",
    "table": "party",
    "fields": ["party_name", "status"]
  },
  "recon": { "mode": "MISMATCH_DETAILS" }
}
```

**Response** `201`

```json
{
  "domainId": "party",
  "profileId": "bigquery-bigquery",
  "id": "party.bigquery-bigquery",
  "sourceDatasource": "bq",
  "sourceType": "bigquery",
  "targetDatasource": "bq-tgt",
  "targetType": "bigquery",
  "migrationKeyType": "SINGLE",
  "migrationKeyColumns": ["party_id"],
  "hashingStrategy": "TypeLenient",
  "reconMode": "MISMATCH_DETAILS",
  "conditionFields": [],
  "tags": [],
  "createdAt": "2026-08-19T01:02:00Z",
  "createdBy": "data-recon",
  "updatedAt": "2026-08-19T01:02:00Z",
  "updatedBy": "data-recon",
  "active": true,
  "version": 1
}
```

---

### 2.12 Postgres → CSV file

**Request**

```json
{
  "id": "pg-csv",
  "datasources": { "source": "landing", "target": "csv" },
  "migrationKey": { "type": "SINGLE", "columns": ["party_id"] },
  "source": {
    "schema": "landing",
    "table": "party",
    "fields": ["party_name", "status"]
  },
  "target": {
    "table": "party",
    "fields": ["party_name", "status"]
  },
  "recon": { "mode": "FIELD_DETAILS", "conditionFields": ["party_name", "status"] }
}
```

**Response** `201`

```json
{
  "domainId": "party",
  "profileId": "pg-csv",
  "id": "party.pg-csv",
  "sourceDatasource": "landing",
  "sourceType": "postgres",
  "targetDatasource": "csv",
  "targetType": "file",
  "migrationKeyType": "SINGLE",
  "migrationKeyColumns": ["party_id"],
  "hashingStrategy": "TypeLenient",
  "reconMode": "FIELD_DETAILS",
  "conditionFields": ["party_name", "status"],
  "tags": [],
  "createdAt": "2026-08-19T01:02:00Z",
  "createdBy": "data-recon",
  "updatedAt": "2026-08-19T01:02:00Z",
  "updatedBy": "data-recon",
  "active": true,
  "version": 1
}
```

---

### 2.13 Postgres → XLSX file

**Request**

```json
{
  "id": "pg-xlsx",
  "datasources": { "source": "landing", "target": "xlsx" },
  "migrationKey": { "type": "SINGLE", "columns": ["party_id"] },
  "source": {
    "schema": "landing",
    "table": "party",
    "fields": ["party_name", "status"]
  },
  "target": {
    "table": "party",
    "fields": ["party_name", "status"]
  },
  "recon": { "mode": "MISMATCH_DETAILS" }
}
```

**Response** `201`

```json
{
  "domainId": "party",
  "profileId": "pg-xlsx",
  "id": "party.pg-xlsx",
  "sourceDatasource": "landing",
  "sourceType": "postgres",
  "targetDatasource": "xlsx",
  "targetType": "file",
  "migrationKeyType": "SINGLE",
  "migrationKeyColumns": ["party_id"],
  "hashingStrategy": "TypeLenient",
  "reconMode": "MISMATCH_DETAILS",
  "conditionFields": [],
  "tags": [],
  "createdAt": "2026-08-19T01:02:00Z",
  "createdBy": "data-recon",
  "updatedAt": "2026-08-19T01:02:00Z",
  "updatedBy": "data-recon",
  "active": true,
  "version": 1
}
```

---

### 2.14 Mongo → CSV file

**Request**

```json
{
  "id": "mongo-csv",
  "datasources": { "source": "mongo", "target": "csv" },
  "migrationKey": { "type": "SINGLE", "columns": ["party_id"] },
  "source": { "collection": "party", "fields": ["party_name", "status"] },
  "target": { "table": "party", "fields": ["party_name", "status"] },
  "recon": { "mode": "COUNTS" }
}
```

**Response** `201`

```json
{
  "domainId": "party",
  "profileId": "mongo-csv",
  "id": "party.mongo-csv",
  "sourceDatasource": "mongo",
  "sourceType": "mongo",
  "targetDatasource": "csv",
  "targetType": "file",
  "migrationKeyType": "SINGLE",
  "migrationKeyColumns": ["party_id"],
  "hashingStrategy": "TypeLenient",
  "reconMode": "COUNTS",
  "conditionFields": [],
  "tags": [],
  "createdAt": "2026-08-19T01:02:00Z",
  "createdBy": "data-recon",
  "updatedAt": "2026-08-19T01:02:00Z",
  "updatedBy": "data-recon",
  "active": true,
  "version": 1
}
```

---

### 2.15 BigQuery → CSV file

**Request**

```json
{
  "id": "bigquery-csv",
  "datasources": { "source": "bq", "target": "csv" },
  "migrationKey": { "type": "SINGLE", "columns": ["party_id"] },
  "source": {
    "schema": "recon",
    "table": "party",
    "fields": ["party_name", "status"]
  },
  "target": { "table": "party", "fields": ["party_name", "status"] },
  "recon": { "mode": "MISMATCH_DETAILS" }
}
```

**Response** `201`

```json
{
  "domainId": "party",
  "profileId": "bigquery-csv",
  "id": "party.bigquery-csv",
  "sourceDatasource": "bq",
  "sourceType": "bigquery",
  "targetDatasource": "csv",
  "targetType": "file",
  "migrationKeyType": "SINGLE",
  "migrationKeyColumns": ["party_id"],
  "hashingStrategy": "TypeLenient",
  "reconMode": "MISMATCH_DETAILS",
  "conditionFields": [],
  "tags": [],
  "createdAt": "2026-08-19T01:02:00Z",
  "createdBy": "data-recon",
  "updatedAt": "2026-08-19T01:02:00Z",
  "updatedBy": "data-recon",
  "active": true,
  "version": 1
}
```

---

### 2.16 CSV file → Postgres

**Request**

```json
{
  "id": "csv-pg",
  "datasources": { "source": "csv", "target": "master" },
  "migrationKey": { "type": "SINGLE", "columns": ["party_id"] },
  "source": { "table": "party", "fields": ["party_name", "status"] },
  "target": {
    "schema": "master",
    "table": "party",
    "fields": ["party_name", "status"]
  },
  "recon": { "mode": "FIELD_DETAILS", "conditionFields": ["party_name", "status"] }
}
```

**Response** `201`

```json
{
  "domainId": "party",
  "profileId": "csv-pg",
  "id": "party.csv-pg",
  "sourceDatasource": "csv",
  "sourceType": "file",
  "targetDatasource": "master",
  "targetType": "postgres",
  "migrationKeyType": "SINGLE",
  "migrationKeyColumns": ["party_id"],
  "hashingStrategy": "TypeLenient",
  "reconMode": "FIELD_DETAILS",
  "conditionFields": ["party_name", "status"],
  "tags": [],
  "createdAt": "2026-08-19T01:02:00Z",
  "createdBy": "data-recon",
  "updatedAt": "2026-08-19T01:02:00Z",
  "updatedBy": "data-recon",
  "active": true,
  "version": 1
}
```

---

### 2.17 CSV file → Mongo

**Request**

```json
{
  "id": "csv-mongo",
  "datasources": { "source": "csv", "target": "mongo" },
  "migrationKey": { "type": "SINGLE", "columns": ["party_id"] },
  "source": { "table": "party", "fields": ["party_name", "status"] },
  "target": { "collection": "party", "fields": ["party_name", "status"] },
  "recon": { "mode": "COUNTS" }
}
```

**Response** `201`

```json
{
  "domainId": "party",
  "profileId": "csv-mongo",
  "id": "party.csv-mongo",
  "sourceDatasource": "csv",
  "sourceType": "file",
  "targetDatasource": "mongo",
  "targetType": "mongo",
  "migrationKeyType": "SINGLE",
  "migrationKeyColumns": ["party_id"],
  "hashingStrategy": "TypeLenient",
  "reconMode": "COUNTS",
  "conditionFields": [],
  "tags": [],
  "createdAt": "2026-08-19T01:02:00Z",
  "createdBy": "data-recon",
  "updatedAt": "2026-08-19T01:02:00Z",
  "updatedBy": "data-recon",
  "active": true,
  "version": 1
}
```

---

### 2.18 CSV file → BigQuery

**Request**

```json
{
  "id": "csv-bigquery",
  "datasources": { "source": "csv", "target": "bq" },
  "migrationKey": { "type": "SINGLE", "columns": ["party_id"] },
  "source": { "table": "party", "fields": ["party_name", "status"] },
  "target": {
    "schema": "recon",
    "table": "party",
    "fields": ["party_name", "status"]
  },
  "recon": { "mode": "MISMATCH_DETAILS" }
}
```

**Response** `201`

```json
{
  "domainId": "party",
  "profileId": "csv-bigquery",
  "id": "party.csv-bigquery",
  "sourceDatasource": "csv",
  "sourceType": "file",
  "targetDatasource": "bq",
  "targetType": "bigquery",
  "migrationKeyType": "SINGLE",
  "migrationKeyColumns": ["party_id"],
  "hashingStrategy": "TypeLenient",
  "reconMode": "MISMATCH_DETAILS",
  "conditionFields": [],
  "tags": [],
  "createdAt": "2026-08-19T01:02:00Z",
  "createdBy": "data-recon",
  "updatedAt": "2026-08-19T01:02:00Z",
  "updatedBy": "data-recon",
  "active": true,
  "version": 1
}
```

---

### 2.19 CSV file → CSV file

Register `csv-tgt` like §0.5 with a different `pattern`, then:

**Request**

```json
{
  "id": "csv-csv",
  "datasources": { "source": "csv", "target": "csv-tgt" },
  "migrationKey": { "type": "SINGLE", "columns": ["party_id"] },
  "source": { "table": "party", "fields": ["party_name", "status"] },
  "target": { "table": "party", "fields": ["party_name", "status"] },
  "recon": { "mode": "FIELD_DETAILS", "conditionFields": ["party_name", "status"] }
}
```

**Response** `201`

```json
{
  "domainId": "party",
  "profileId": "csv-csv",
  "id": "party.csv-csv",
  "sourceDatasource": "csv",
  "sourceType": "file",
  "targetDatasource": "csv-tgt",
  "targetType": "file",
  "migrationKeyType": "SINGLE",
  "migrationKeyColumns": ["party_id"],
  "hashingStrategy": "TypeLenient",
  "reconMode": "FIELD_DETAILS",
  "conditionFields": ["party_name", "status"],
  "tags": [],
  "createdAt": "2026-08-19T01:02:00Z",
  "createdBy": "data-recon",
  "updatedAt": "2026-08-19T01:02:00Z",
  "updatedBy": "data-recon",
  "active": true,
  "version": 1
}
```

---

### 2.20 XLSX file → Postgres

**Request**

```json
{
  "id": "xlsx-pg",
  "datasources": { "source": "xlsx", "target": "master" },
  "migrationKey": { "type": "SINGLE", "columns": ["party_id"] },
  "source": { "table": "party", "fields": ["party_name", "status"] },
  "target": {
    "schema": "master",
    "table": "party",
    "fields": ["party_name", "status"]
  },
  "recon": { "mode": "MISMATCH_DETAILS" }
}
```

**Response** `201`

```json
{
  "domainId": "party",
  "profileId": "xlsx-pg",
  "id": "party.xlsx-pg",
  "sourceDatasource": "xlsx",
  "sourceType": "file",
  "targetDatasource": "master",
  "targetType": "postgres",
  "migrationKeyType": "SINGLE",
  "migrationKeyColumns": ["party_id"],
  "hashingStrategy": "TypeLenient",
  "reconMode": "MISMATCH_DETAILS",
  "conditionFields": [],
  "tags": [],
  "createdAt": "2026-08-19T01:02:00Z",
  "createdBy": "data-recon",
  "updatedAt": "2026-08-19T01:02:00Z",
  "updatedBy": "data-recon",
  "active": true,
  "version": 1
}
```

---

## 3. Runs (any combination)

### 3.1 Trigger one profile

**Request**

```http
POST /api/domains/party/profiles/pg-mongo/runs
```

```json
{
  "mode": "MISMATCH_DETAILS",
  "forceFull": false
}
```

**Response** `202 Accepted`

```json
{
  "domainId": "party",
  "profileId": "pg-mongo",
  "runId": 101
}
```

### 3.2 Trigger whole domain

**Request**

```http
POST /api/domains/party/runs
```

```json
{ "forceFull": true }
```

**Response** `202 Accepted`

```json
{
  "domainId": "party",
  "domainRunId": 200,
  "runIds": {
    "pg-pg": 201,
    "pg-mongo": 202,
    "pg-bigquery": 203
  }
}
```

### 3.3 List profile runs

**Request**

```http
GET /api/domains/party/profiles/pg-pg/runs?active=true
```

**Response** `200`

```json
[
  {
    "id": 101,
    "datasetId": "party.pg-pg",
    "domainId": "party",
    "profileId": "pg-pg",
    "domainRunId": null,
    "status": "COMPLETED",
    "startedAt": "2026-08-19T01:10:00Z",
    "completedAt": "2026-08-19T01:10:05Z",
    "sourceCount": 1000,
    "targetCount": 998,
    "matchedCount": 990,
    "mismatchedCount": 5,
    "sourceOnlyCount": 5,
    "targetOnlyCount": 3,
    "errorMessage": null,
    "active": true,
    "reconMode": "FIELD_DETAILS",
    "sourceQuery": null,
    "targetQuery": null,
    "conditionFields": ["party_name", "status"],
    "runScope": "FULL",
    "baselineRunId": null
  }
]
```

### 3.4 List mismatch records

**Request**

```http
GET /api/domains/party/profiles/pg-pg/runs/101/records?status=MISMATCHED
```

**Response** `200`

```json
[
  {
    "migrationKey": "P-100",
    "sourceHash": "a1b2c3…",
    "targetHash": "d4e5f6…",
    "status": "MISMATCHED",
    "fieldDiffs": "[{\"field\":\"status\",\"sourceHash\":\"…\",\"targetHash\":\"…\"}]",
    "sourcePayload": null,
    "targetPayload": null
  }
]
```

---

## 4. Versioning on update (any entity)

Updating a datasource / domain / profile **inserts a new row** and marks the previous active row inactive.

**Request**

```http
PUT /api/domains/party/profiles/pg-pg
```

```json
{
  "recon": {
    "mode": "COUNTS"
  }
}
```

**Response** `200` (note `version: 2`)

```json
{
  "domainId": "party",
  "profileId": "pg-pg",
  "id": "party.pg-pg",
  "sourceDatasource": "landing",
  "sourceType": "postgres",
  "targetDatasource": "master",
  "targetType": "postgres",
  "migrationKeyType": "SINGLE",
  "migrationKeyColumns": ["party_id"],
  "hashingStrategy": "TypeLenient",
  "reconMode": "COUNTS",
  "conditionFields": [],
  "tags": ["pg-pg"],
  "createdAt": "2026-08-19T01:02:00Z",
  "createdBy": "data-recon",
  "updatedAt": "2026-08-19T01:20:00Z",
  "updatedBy": "data-recon",
  "active": true,
  "version": 2
}
```

---

## 5. Optional query instead of schema/table

Works on postgres / bigquery (SQL) and mongo (JSON filter):

**Request**

```http
PUT /api/domains/party/profiles/pg-pg/source
```

```json
{
  "query": "SELECT party_id AS \"MigrationKey\", party_name, status FROM landing.party",
  "fields": ["party_name", "status"]
}
```

**Response** `200` — full `ProfileApiModel` for `pg-pg` (same shape as §2.1, `version` bumped).

Mongo filter example:

```json
{
  "query": "{ \"status\": { \"$ne\": null } }",
  "collection": "party",
  "fields": ["party_name", "status"]
}
```
