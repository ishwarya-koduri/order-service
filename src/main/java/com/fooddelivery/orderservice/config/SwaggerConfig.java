package com.fooddelivery.orderservice.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.parameters.Parameter;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Swagger / OpenAPI configuration.
 * Defines API metadata and adds global headers to all endpoints.
 */
@Configuration
public class SwaggerConfig {

    private static final ApplicationLogger log = ApplicationLogger.getLogger(SwaggerConfig.class);

    /**
     * API metadata shown in Swagger UI header.
     */
    @Bean
    public OpenAPI openAPI() {
        log.info("openAPI started — configuring Swagger metadata");

        OpenAPI api = new OpenAPI()
                .info(new Info()
                        .title("Order Service API")
                        .description("""
                                Food Delivery Platform — Order Processing Service.
                                
                                Handles order placement, status management and coordinates
                                with payment and notification services via Kafka events.
                                
                                Authentication: Pass X-Customer-Id header on every request.
                                Idempotency: Pass Idempotency-Key header on POST /orders.
                                """)
                        .version("1.0.0")
                        .contact(new Contact()
                                .name("Order Service Team")
                                .email("orders@fooddelivery.com"))
                        .license(new License()
                                .name("Private")
                                .url("https://fooddelivery.com")));

        log.info("openAPI completed — Swagger metadata configured");
        return api;
    }

    /**
     * Adds X-Customer-Id and X-Correlation-Id as global headers on every endpoint.
     * This way they appear in Swagger UI without repeating @Parameter on every method.
     */
    @Bean
    public OperationCustomizer globalHeaders() {
        return (operation, handlerMethod) -> {
            operation.addParametersItem(
                    new Parameter()
                            .in("header")
                            .name("X-Customer-Id")
                            .description("Authenticated customer UUID. Required on all endpoints.")
                            .required(true)
                            .example("550e8400-e29b-41d4-a716-446655440000")
            );

            operation.addParametersItem(
                    new Parameter()
                            .in("header")
                            .name("X-Correlation-Id")
                            .description("Optional. Client-supplied correlation ID for request tracing. " +
                                    "If not provided, one is generated automatically and returned in the response header.")
                            .required(false)
                            .example("my-client-trace-001")
            );

            return operation;
        };
    }
}