package com.deeptier.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.Components;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI deepTierOpenAPI() {
        final String internalKeyScheme = "InternalKey";
        return new OpenAPI()
                .info(new Info()
                        .title("DeepTier API")
                        .description("SAP Hackfest 2026 - Supply Chain Risk Cockpit. " +
                                "All arithmetic (exposure, Monte Carlo, optimizer) runs in deterministic Java. " +
                                "AI agents only classify signals and draft RFQs via /internal/tools/*.")
                        .version("1.0.0-SNAPSHOT")
                        .contact(new Contact().name("DeepTier Team").url("https://github.com/deeptier"))
                        .license(new License().name("MIT")))
                .addSecurityItem(new SecurityRequirement().addList(internalKeyScheme))
                .components(new Components()
                        .addSecuritySchemes(internalKeyScheme, new SecurityScheme()
                                .name("X-Internal-Key")
                                .type(SecurityScheme.Type.APIKEY)
                                .in(SecurityScheme.In.HEADER)
                                .description("Required for /internal/tools/* endpoints")));
    }
}
