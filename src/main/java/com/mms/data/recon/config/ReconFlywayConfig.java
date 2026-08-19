package com.mms.data.recon.config;

import org.flywaydb.core.api.output.MigrateResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.flyway.FlywayMigrationStrategy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Ensures recon catalog/result tables exist on startup via Flyway.
 * When {@code mms.recon.database.recreate-on-start=true}, drops and rebuilds the schema first (dev wipe).
 */
@Configuration
public class ReconFlywayConfig {

    private static final Logger log = LoggerFactory.getLogger(ReconFlywayConfig.class);

    @Bean
    public FlywayMigrationStrategy reconFlywayMigrationStrategy(ReconDatabaseProperties database) {
        return flyway -> {
            if (database.isRecreateOnStart()) {
                log.warn(
                        "mms.recon.database.recreate-on-start=true — cleaning schema '{}' then migrating",
                        database.resolvedSchema()
                );
                flyway.clean();
            }
            MigrateResult result = flyway.migrate();
            log.info(
                    "Flyway migrated recon schema '{}' (migrations applied={})",
                    database.resolvedSchema(),
                    result.migrationsExecuted
            );
        };
    }
}
