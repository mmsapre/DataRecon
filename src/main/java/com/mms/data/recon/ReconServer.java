package com.mms.data.recon;

import com.mms.data.recon.auth.AuthConfiguration;
import com.mms.data.recon.config.BuildInfoConfiguration;
import com.mms.data.recon.config.LlmProperties;
import com.mms.data.recon.config.RecConfiguration;
import com.mms.data.recon.config.ReconDatabaseProperties;
import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.r2dbc.R2dbcAutoConfiguration;
import org.springframework.boot.autoconfigure.r2dbc.R2dbcTransactionManagerAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

/**
 * Recon metadata uses JDBC ({@code reconDataSource}); business Postgres uses named R2DBC pools
 * from {@code PostgresConnectionFactoryCatalog}. Spring Boot's primary R2DBC auto-config is
 * excluded so a missing {@code spring.r2dbc.url} does not fail startup.
 */
@SpringBootApplication(exclude = {
        R2dbcAutoConfiguration.class,
        R2dbcTransactionManagerAutoConfiguration.class
})
@EnableConfigurationProperties({
        AuthConfiguration.class,
        BuildInfoConfiguration.class,
        RecConfiguration.class,
        ReconDatabaseProperties.class,
        LlmProperties.class
})
@OpenAPIDefinition(
        info = @Info(
                title = "Data Recon",
                version = "1.0.0",
                description = "Reconcile source and target datasets. Results store hashes and match status in PostgreSQL, not business values."
        ),
        security = @SecurityRequirement(name = "basicAuth")
)
@SecurityScheme(name = "basicAuth", type = SecuritySchemeType.HTTP, scheme = "basic")
public class ReconServer {

    public static void main(String[] args) {
        SpringApplication.run(ReconServer.class, args);
    }
}
