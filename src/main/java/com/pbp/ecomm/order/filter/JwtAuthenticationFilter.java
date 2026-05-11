package com.pbp.ecomm.order.filter;


import com.fasterxml.jackson.databind.ObjectMapper;
import com.pbp.ecomm.order.JwtService;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.UnsupportedJwtException;
import io.jsonwebtoken.security.SignatureException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final List<String> PUBLIC_PATHS = List.of(
            "/api/v1/products",             // GET product listing
            "/api/v1/products/*",           // GET single product
            "/api/v1/products/search",      // GET search
            "/api/v1/categories",           // GET category tree
            "/api/v1/categories/*",         // GET single category
            "/actuator/health",             // K8s liveness probe
            "/actuator/info",               // Deployment metadata
            "/v3/api-docs/**",              // Swagger
            "/swagger-ui/**"
    );
    private static final AntPathMatcher PATH_MATCHER = new AntPathMatcher();
    private final JwtService jwtService;

    // ── Public endpoints — filter skips JWT validation entirely ───
//    private final TokenBlacklistService blacklistService;
    private final ObjectMapper objectMapper;

    // ─────────────────────────────────────────────────────────────
    // Core filter logic
    // ─────────────────────────────────────────────────────────────

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain chain)
            throws ServletException, IOException {

        // Step 1 — Skip public endpoints entirely (no token needed)
        if (isPublicPath(request)) {
            log.trace("Public path — skipping JWT check: {}", request.getRequestURI());
            chain.doFilter(request, response);
            return;
        }

        // Step 2 — Extract token from Authorization header
        String token = extractToken(request);
        if (token == null) {
            // No token present — let Spring Security handle 401
            chain.doFilter(request, response);
            return;
        }

        // Step 3 — Validate and authenticate
        try {
            authenticateRequest(token, request);
        } catch (ExpiredJwtException e) {
            log.debug("Expired JWT from {}: {}", getClientIp(request), e.getMessage());
            writeErrorResponse(response, HttpStatus.UNAUTHORIZED,
                    "TOKEN_EXPIRED", "Access token has expired");
            return;
        } catch (SignatureException e) {
            log.warn("Invalid JWT signature from {}", getClientIp(request));
            writeErrorResponse(response, HttpStatus.UNAUTHORIZED,
                    "INVALID_SIGNATURE", "Token signature verification failed");
            return;
        } catch (MalformedJwtException | UnsupportedJwtException e) {
            log.warn("Malformed JWT from {}: {}", getClientIp(request), e.getMessage());
            writeErrorResponse(response, HttpStatus.UNAUTHORIZED,
                    "MALFORMED_TOKEN", "Token format is invalid");
            return;
        } catch (TokenBlacklistedException e) {
            log.info("Blacklisted token used from {}", getClientIp(request));
            writeErrorResponse(response, HttpStatus.UNAUTHORIZED,
                    "TOKEN_REVOKED", "Token has been revoked — please log in again");
            return;
        } catch (Exception e) {
            log.error("Unexpected JWT filter error: {}", e.getMessage(), e);
            writeErrorResponse(response, HttpStatus.INTERNAL_SERVER_ERROR,
                    "AUTH_ERROR", "Authentication processing failed");
            return;
        }

        chain.doFilter(request, response);
    }

    // ─────────────────────────────────────────────────────────────
    // Core authentication logic
    // ─────────────────────────────────────────────────────────────

    private void authenticateRequest(String token, HttpServletRequest request) {

        // 1. Validate signature + expiry (throws on invalid)
        jwtService.validateToken(token);

        // 2. Check Redis blacklist — catches logged-out tokens
        String jti = jwtService.extractJti(token);
      /*  if (blacklistService.isBlacklisted(jti)) {
            throw new TokenBlacklistedException("Token jti=" + jti + " is blacklisted");
        }*/

        // 3. Prevent refresh tokens being used as access tokens
        String tokenType = jwtService.extractClaim(token, "type");
        if (!"ACCESS".equals(tokenType)) {
            throw new MalformedJwtException("Invalid token type: " + tokenType);
        }

        // 4. Build authentication principal from token claims
        //    Product service does NOT load from DB on every request —
        //    user context comes entirely from the validated token claims
        UUID userId = jwtService.extractUserId(token);
        String email = jwtService.extractClaim(token, "email");
        String role = jwtService.extractClaim(token, "role");

        AuthenticatedUser principal = AuthenticatedUser.builder()
                .userId(userId)
                .email(email)
                .role(role)
                .build();

        List<SimpleGrantedAuthority> authorities =
                List.of(new SimpleGrantedAuthority("ROLE_" + role));

        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(
                        principal,      // principal — available via @AuthenticationPrincipal
                        null,           // credentials — null after authentication
                        authorities
                );

        authentication.setDetails(
                new WebAuthenticationDetailsSource().buildDetails(request)
        );

        // 5. Set in SecurityContext — downstream controllers can read this
        SecurityContextHolder.getContext().setAuthentication(authentication);

        log.debug("Authenticated user={} role={} path={}",
                userId, role, request.getRequestURI());
    }

    // ─────────────────────────────────────────────────────────────
    // Helper — skip filter for public GET product endpoints
    // ─────────────────────────────────────────────────────────────

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        // Only skip for GET methods on public paths
        // POST /api/v1/products still needs ADMIN token
        if (!"GET".equalsIgnoreCase(request.getMethod())) {
            return false;
        }
        return isPublicPath(request);
    }

    private boolean isPublicPath(HttpServletRequest request) {
        String path = request.getRequestURI();
        return PUBLIC_PATHS.stream()
                .anyMatch(pattern -> PATH_MATCHER.match(pattern, path));
    }

    // ─────────────────────────────────────────────────────────────
    // Token extraction
    // ─────────────────────────────────────────────────────────────

    private String extractToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");

        if (StringUtils.hasText(header) && header.startsWith("Bearer ")) {
            String token = header.substring(7).trim();
            return StringUtils.hasText(token) ? token : null;
        }

        log.trace("No Bearer token in Authorization header for {}",
                request.getRequestURI());
        return null;
    }

    // ─────────────────────────────────────────────────────────────
    // Error response writer
    // ─────────────────────────────────────────────────────────────

    private void writeErrorResponse(HttpServletResponse response,
                                    HttpStatus status,
                                    String errorCode,
                                    String message) throws IOException {

        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");

        Map<String, Object> body = Map.of(
                "error", errorCode,
                "message", message,
                "status", status.value(),
                "timestamp", Instant.now().toString()
        );

        response.getWriter().write(objectMapper.writeValueAsString(body));
    }

    // ─────────────────────────────────────────────────────────────
    // Client IP extraction (handles proxies / load balancers)
    // ─────────────────────────────────────────────────────────────

    private String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (StringUtils.hasText(ip) && !"unknown".equalsIgnoreCase(ip)) {
            return ip.split(",")[0].trim();     // first IP in chain = real client
        }
        ip = request.getHeader("X-Real-IP");
        if (StringUtils.hasText(ip)) return ip;
        return request.getRemoteAddr();
    }

    // ─────────────────────────────────────────────────────────────
    // Inner exception — blacklisted token
    // ─────────────────────────────────────────────────────────────

    static class TokenBlacklistedException extends RuntimeException {
        public TokenBlacklistedException(String message) {
            super(message);
        }
    }

}