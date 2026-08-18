# Config moved

Data Recon Spring configuration is under **[`src/main/resources/`](../src/main/resources/)**.

**Pass only the profile** (`dev` | `uat` | `sit` | `prod`):

```bash
mvn spring-boot:run -Dspring-boot.run.profiles=uat
# or SPRING_PROFILES_ACTIVE / PROFILE
```

See [`src/main/resources/config/README.md`](../src/main/resources/config/README.md).
