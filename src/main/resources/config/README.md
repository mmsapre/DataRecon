# Configuration (classpath)

**Pass only the Spring profile** — no extra config file location is required.

| Profile | File | Role |
|---|---|---|
| *(shared)* | [`../application.yml`](../application.yml) | Server, auth, recon DB, LLM, defaults |
| `dev` (default) | [`../application-dev.yml`](../application-dev.yml) | Local PG/Mongo/BQ (+ file) + sample domains |
| `uat` | [`../application-uat.yml`](../application-uat.yml) | UAT datasources via env vars |
| `sit` | [`../application-sit.yml`](../application-sit.yml) | SIT datasources via env vars |
| `prod` | [`../application-prod.yml`](../application-prod.yml) | PROD datasources via env vars |

```bash
# default = dev
mvn spring-boot:run

# pass profile only
mvn spring-boot:run -Dspring-boot.run.profiles=uat
export SPRING_PROFILES_ACTIVE=prod   # or PROFILE=prod
java -Dspring.profiles.active=sit -jar target/data-recon-*.jar
```

`database.yml` / `datasources.yml` / `llm.yml` / `combinations/` here are **reference samples**
(same keys as `application-*.yml`). Prefer editing the profile YAML; do not pass
`spring.config.additional-location` for normal runs.
