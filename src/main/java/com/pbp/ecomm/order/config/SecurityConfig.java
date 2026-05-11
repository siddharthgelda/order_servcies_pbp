// config/SecurityConfig.java
package com.pbp.ecomm.order.config;

import com.pbp.ecomm.order.filter.JwtAuthenticationFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private static final String[] PUBLIC_ENDPOINTS = {
            "/actuator/health",
            "/actuator/info",
            "/v3/api-docs/**",
            "/swagger-ui/**"
    };
    private final JwtAuthenticationFilter jwtFilter;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        return http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .sessionManagement(s ->
                        s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth

                        // ── Public ─────────────────────────────────────
                        .requestMatchers(PUBLIC_ENDPOINTS).permitAll()

                        // ── Customer ───────────────────────────────────
                        .requestMatchers(HttpMethod.POST,
                                "/api/v1/orders"
                        ).hasAnyRole("CUSTOMER", "ADMIN")

                        .requestMatchers(HttpMethod.GET,
                                "/api/v1/orders/my-orders"
                        ).hasAnyRole("CUSTOMER", "ADMIN")

                        .requestMatchers(HttpMethod.GET,
                                "/api/v1/orders/*/tracking"
                        ).hasAnyRole("CUSTOMER", "ADMIN")

                        .requestMatchers(HttpMethod.POST,
                                "/api/v1/orders/*/cancel"
                        ).hasAnyRole("CUSTOMER", "ADMIN")

                        // ── Admin ──────────────────────────────────────
                        .requestMatchers(HttpMethod.GET,
                                "/api/v1/orders",
                                "/api/v1/orders/user/**"
                        ).hasRole("ADMIN")

                        .requestMatchers(HttpMethod.PATCH,
                                "/api/v1/orders/*/status"
                        ).hasRole("ADMIN")

                        .requestMatchers(HttpMethod.GET,
                                "/api/v1/orders/*/history"
                        ).hasRole("ADMIN")

                        .anyRequest().authenticated()
                )
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint((req, res, e) -> {
                            res.setStatus(401);
                            res.setContentType("application/json");
                            res.getWriter().write(
                                    "{\"error\":\"UNAUTHORIZED\"," +
                                            "\"message\":\"Authentication required\"}"
                            );
                        })
                        .accessDeniedHandler((req, res, e) -> {
                            res.setStatus(403);
                            res.setContentType("application/json");
                            res.getWriter().write(
                                    "{\"error\":\"FORBIDDEN\"," +
                                            "\"message\":\"Insufficient permissions\"}"
                            );
                        })
                )
                .addFilterBefore(jwtFilter,
                        UsernamePasswordAuthenticationFilter.class)
                .build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(List.of(
                "http://localhost:4200",
                "https://shop.yourdomain.com"
        ));
        config.setAllowedMethods(List.of(
                "GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"
        ));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source =
                new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", config);
        return source;
    }
}