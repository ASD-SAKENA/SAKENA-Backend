package com.sakena.shared.config

import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.info.Info
import io.swagger.v3.oas.models.info.License
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class OpenApiConfig {

    @Bean
    fun sakenaOpenAPI(): OpenAPI =
        OpenAPI().info(
            Info()
                .title("Sakena API")
                .description("Sakena backend service — REST API documentation.")
                .version("v0.0.1")
                .license(License().name("Proprietary")),
        )
}
