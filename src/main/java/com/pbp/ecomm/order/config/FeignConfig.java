// config/FeignConfig.java
package com.pbp.ecomm.order.config;


import feign.Logger;
import feign.RequestInterceptor;
import feign.RetryableException;
import feign.Retryer;
import feign.codec.ErrorDecoder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

@Configuration
@EnableFeignClients(basePackages = "com.pbp.ecomm.order.client")
@Slf4j
public class FeignConfig {

    // ── Log level ─────────────────────────────────────────────────

    @Bean
    public Logger.Level feignLoggerLevel() {
        // NONE | BASIC | HEADERS | FULL
        // BASIC in production — logs method, URL, status, elapsed time
        return Logger.Level.BASIC;
    }

    // ── Auth header propagation ───────────────────────────────────
    // Forwards the JWT from incoming request to outbound Feign calls
    // so Product Service can verify the caller identity

    @Bean
    public RequestInterceptor jwtRequestInterceptor() {
        return requestTemplate -> {
            Authentication auth = SecurityContextHolder
                    .getContext().getAuthentication();

            if (auth != null && auth.getCredentials() instanceof String token) {
                requestTemplate.header("Authorization", "Bearer " + token);
            }
        };
    }

    // ── Retry config ──────────────────────────────────────────────
    // Retry on connection failure — NOT on business errors (4xx)

    @Bean
    public Retryer feignRetryer() {
        return new Retryer.Default(
                100,    // initial interval ms
                1000,   // max interval ms
                3       // max attempts
        );
    }

    // ── Error decoder ─────────────────────────────────────────────
    // Translates HTTP error responses from Product Service
    // into meaningful exceptions in Order Service

    @Bean
    public ErrorDecoder feignErrorDecoder() {
        return (methodKey, response) -> {
            HttpStatus status = HttpStatus.valueOf(response.status());
            String body = extractBody(response);

            log.error("Feign error — method={} status={} body={}",
                    methodKey, status, body);

            return switch (status) {
                case NOT_FOUND -> new Exception(
                        "Product not found — " + body
                );
                case BAD_REQUEST -> new IllegalArgumentException(
                        "Invalid request to Product Service — " + body
                );
                case SERVICE_UNAVAILABLE,
                     GATEWAY_TIMEOUT,
                     BAD_GATEWAY -> new Exception(
                        "Product Service unavailable"
                );
                case TOO_MANY_REQUESTS -> new RetryableException(
                        response.status(), "Rate limited",
                        null, (Long) null, response.request()
                );
                default -> new Exception(
                        "Unexpected error from Product Service: "
                                + status
                );
            };
        };
    }

    private String extractBody(feign.Response response) {
        try {
            if (response.body() != null) {
                return new String(response.body().asInputStream().readAllBytes());
            }
        } catch (Exception e) {
            log.warn("Could not read Feign error response body", e);
        }
        return "unknown";
    }
}