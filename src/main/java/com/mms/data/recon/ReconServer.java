package com.mms.data.recon;

import io.micronaut.runtime.Micronaut;
import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityScheme;

@OpenAPIDefinition(
        info = @Info(
                title = "Data Recon",
                version = "1.0.0",
                description = "Reconcile source and target datasets. Results store hashes and match status in PostgreSQL, not business values."
        ),
        security = @SecurityRequirement(name = "basicAuth")
)
@SecurityScheme(name = "basicAuth", type = SecuritySchemeType.HTTP, scheme = "basic")
public final class ReconServer {
    private ReconServer() {}

    public static void main(String[] args) {
        Micronaut.run(ReconServer.class, args);
    }
}
