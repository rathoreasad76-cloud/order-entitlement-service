package com.asadrathore.orderentitlement.api;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * Top-level OpenAPI document metadata — the parts that describe the API as a whole
 * rather than any single endpoint.
 *
 * <p>Per-endpoint detail lives on the controllers themselves ({@code @Operation},
 * {@code @ApiResponse}) so that the description of an endpoint sits next to the code
 * that implements it and is harder to leave stale. The version is filtered in from
 * the Maven build rather than hardcoded, so releasing a new version of the artifact
 * updates the published spec without anyone remembering to.
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI orderEntitlementOpenApi(@Value("${info.app.version:unknown}") String version) {
        return new OpenAPI()
                .info(new Info()
                        .title("Order & Entitlement Service API")
                        .version(version)
                        .description("""
                                Places orders and exposes the entitlements granted from them.

                                Two behaviours are worth knowing before calling this API:

                                * **Order placement is idempotent.** `POST /api/orders` requires an \
                                `Idempotency-Key` header. Retrying a request with a key that has \
                                already been used returns the order created by the original call \
                                instead of placing a second one, so a client that retries on a \
                                timeout cannot double-charge a customer.
                                * **Entitlements are granted asynchronously.** They are not created \
                                inside the order transaction; a listener reacts after the order \
                                commits. A `GET` on a customer's entitlements immediately after \
                                placing an order may therefore return before the new entitlement \
                                appears — it is eventually consistent, by design.
                                """)
                        .license(new License().name("MIT")))
                .servers(List.of(
                        new Server().url("/").description("This server")));
    }
}
