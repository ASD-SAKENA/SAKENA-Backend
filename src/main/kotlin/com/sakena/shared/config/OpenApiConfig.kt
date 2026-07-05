package com.sakena.shared.config

import io.swagger.v3.oas.models.Components
import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.info.Info
import io.swagger.v3.oas.models.info.License
import io.swagger.v3.oas.models.security.SecurityRequirement
import io.swagger.v3.oas.models.security.SecurityScheme
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class OpenApiConfig {

    @Bean
    fun sakenaOpenAPI(): OpenAPI {
        return OpenAPI()
            .info(
                Info()
                    .title("Sakena API")
                    .description("Sakena backend service — REST API documentation.")
                    .version("v0.0.1")
                    .license(License().name("Proprietary"))
            )
            // اضافه کردن SecurityScheme از نوع HTTP Bearer
            .components(
                Components()
                    .addSecuritySchemes(
                        "bearerAuth",
                        SecurityScheme()
                            .type(SecurityScheme.Type.HTTP)
                            .scheme("bearer")
                            .bearerFormat("JWT")
                    )
            )
    }
}
