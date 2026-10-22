package com.medtrack.common;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.*;
import org.springframework.context.annotation.*;

@Configuration
public class OpenApiConfig {
  @Bean
  org.springdoc.core.customizers.OpenApiCustomizer publicAuthOperations() {
    return api ->
        api.getPaths()
            .forEach(
                (path, item) -> {
                  if (path.startsWith("/api/v1/auth/"))
                    item.readOperations()
                        .forEach(operation -> operation.setSecurity(java.util.List.of()));
                });
  }

  @Bean
  OpenAPI medtrackOpenApi() {
    return new OpenAPI()
        .info(
            new Info()
                .title("MedTrack API")
                .version("v1")
                .description(
                    "SRS v1.0 + approved Phase 5 addendum: dose actions, inventory and weekly summary. Fictional test data only. Local HTTP development; hosted environments require HTTPS."))
        .components(
            new Components()
                .addSecuritySchemes(
                    "bearerAuth",
                    new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")))
        .addSecurityItem(new SecurityRequirement().addList("bearerAuth"));
  }
}
