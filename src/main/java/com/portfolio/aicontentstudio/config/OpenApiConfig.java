package com.portfolio.aicontentstudio.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration for OpenAPI (Swagger) documentation.
 * Adds Global Security Scheme for Bearer Authentication.
 */
@Configuration
@OpenAPIDefinition(
        info = @Info(
                title = "AI Content Studio API",
                version = "v1.0",
                description = "Backend API documentation for AI Content Studio project."
        ),
        security = @SecurityRequirement(name = "bearerAuth")
)
@SecurityScheme(
        name = "bearerAuth",
        type = SecuritySchemeType.HTTP,
        scheme = "bearer",
        bearerFormat = "JWT"
)
public class OpenApiConfig {
}
