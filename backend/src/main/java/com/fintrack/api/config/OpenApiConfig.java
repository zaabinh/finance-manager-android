package com.fintrack.api.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration(proxyBeanMethods = false)
@Profile("dev & !prod")
public class OpenApiConfig {

    @Bean
    OpenAPI financeManagerOpenApi() {
        return new OpenAPI().info(new Info()
                .title("Finance Manager API")
                .version("v1")
                .description("REST API for Finance Manager."));
    }
}
