# Configuration (classpath)

**Pass only the Spring profile** (`dev` | `uat` | `sit` | `prod`).

| File | Contains |
|---|---|
| [`../application.yml`](../application.yml) | Recon DB + shared auth / optional LLM / engine defaults |
| [`../application-dev.yml`](../application-dev.yml) (and uat/sit/prod) | Empty catalogs — filled via API |

## Runtime catalog (API)

Datasources, domains, profiles, and **tags** are registered after startup:

```text
POST   /api/datasources
POST   /api/domains
POST   /api/domains/{domainId}/profiles
GET    /api/datasources?tag=...
GET    /api/domains?tag=...
GET    /api/domains/{domainId}/profiles?tag=...
```

YAML under `config/combinations/` is reference-only sample payloads, not loaded unless you choose to.

```bash
mvn spring-boot:run
mvn spring-boot:run -Dspring-boot.run.profiles=uat
```
