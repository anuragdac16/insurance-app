package com.insurance.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Title and description shown at /swagger-ui.html. */
@Configuration
public class OpenApiConfig {

    @Bean
    OpenAPI insuranceOpenApi() {
        return new OpenAPI().info(new Info()
                .title("Insurance Platform API")
                .version("0.1.0")
                .description("Policies, claims and notifications"));
    }
}
