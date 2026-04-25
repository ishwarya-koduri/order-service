package com.fooddelivery.orderservice.filter;

import com.fooddelivery.orderservice.config.ApplicationLogger;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Validates that every request carries a non-empty X-Customer-Id header.
 * Runs after MdcLoggingFilter so correlationId is already in MDC when this logs.
 */
@Component
@Order(2)
public class AuthFilter extends OncePerRequestFilter {

    private static final ApplicationLogger log = ApplicationLogger.getLogger(AuthFilter.class);

    public static final String CUSTOMER_ID_HEADER = "X-Customer-Id";

    private static final String[] EXCLUDED_PATHS = {
            "/actuator",
            "/swagger-ui",
            "/v3/api-docs",
            "/api-docs"
    };

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {

        log.info("AuthFilter started — {} {}", request.getMethod(), request.getRequestURI());

        if (isExcluded(request.getRequestURI())) {
            log.debug("AuthFilter — skipping excluded path {}", request.getRequestURI());
            filterChain.doFilter(request, response);
            return;
        }

        String customerId = request.getHeader(CUSTOMER_ID_HEADER);

        if (customerId == null || customerId.isBlank()) {
            log.warn("AuthFilter — missing {} header on {} {}",
                    CUSTOMER_ID_HEADER, request.getMethod(), request.getRequestURI());
            sendUnauthorized(response);
            return;
        }

        log.info("AuthFilter completed — customerId={} authorized", customerId);
        filterChain.doFilter(request, response);
    }

    private boolean isExcluded(String path) {
        for (String excluded : EXCLUDED_PATHS) {
            if (path.startsWith(excluded)) {
                return true;
            }
        }
        return false;
    }

    private void sendUnauthorized(HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json");
        response.getWriter().write("""
                {
                  "status": 401,
                  "error": "Unauthorized",
                  "message": "X-Customer-Id header is required"
                }
                """);
    }
}