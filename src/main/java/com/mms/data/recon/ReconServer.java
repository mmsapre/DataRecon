package com.mms.data.recon;

import com.mms.data.recon.auth.AuthConfiguration;
import com.mms.data.recon.config.BigQueryDatasourcesProperties;
import com.mms.data.recon.config.BuildInfoConfiguration;
import com.mms.data.recon.config.FileDatasourcesProperties;
import com.mms.data.recon.config.LlmProperties;
import com.mms.data.recon.config.MongoDatasourcesProperties;
import com.mms.data.recon.config.PostgresDatasourcesProperties;
import com.mms.data.recon.config.RecConfiguration;
import com.mms.data.recon.config.ReconDatabaseProperties;
import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties({
        AuthConfiguration.class,
        BuildInfoConfiguration.class,
        RecConfiguration.class,
        ReconDatabaseProperties.class,
        LlmProperties.class,
        PostgresDatasourcesProperties.class,
        MongoDatasourcesProperties.class,
        BigQueryDatasourcesProperties.class,
        FileDatasourcesProperties.class
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
