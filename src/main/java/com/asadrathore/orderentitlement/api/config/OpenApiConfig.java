package com.asadrathore.orderentitlement.api.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * API-level OpenAPI metadata. Endpoint-level docs live on the controllers themselves;
 * springdoc serves the spec at {@code /v3/api-docs} and Swagger UI at {@code /swagger-ui.html}.
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI orderEntitlementOpenApi(@Value("${spring.application.name}") String applicationName) {
        return new OpenAPI()
                .info(new Info()
                        .title(applicationName)
                        .version("0.1.0")
                        .description("Places orders idempotently and grants product entitlements to customers."));
    }
}
