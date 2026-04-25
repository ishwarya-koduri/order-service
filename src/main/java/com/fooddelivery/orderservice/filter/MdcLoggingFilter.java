package com.fooddelivery.orderservice.filter;

import com.fooddelivery.orderservice.config.ApplicationLogger;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * Injects a correlationId into MDC for every incoming request.
 * Every log line produced during the request automatically includes the correlationId.
 * Runs first — before all other filters.
 */
@Component
@Order(1)
public class MdcLoggingFilter extends OncePerRequestFilter {

    private static final ApplicationLogger log = ApplicationLogger.getLogger(MdcLoggingFilter.class);

    private static final String CORRELATION_ID_HEADER = "X-Correlation-Id";
    private static final String MDC_CORRELATION_KEY   = "correlationId";

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {

        String correlationId = request.getHeader(CORRELATION_ID_HEADER);

        if (correlationId == null || correlationId.isBlank()) {
            correlationId = UUID.randomUUID().toString();
        }

        MDC.put(MDC_CORRELATION_KEY, correlationId);

        // Echo correlationId back so clients can match their request to our logs
        response.setHeader(CORRELATION_ID_HEADER, correlationId);

        log.debug("MdcLoggingFilter — correlationId={} assigned to {} {}",
                correlationId, request.getMethod(), request.getRequestURI());

        try {
            filterChain.doFilter(request, response);
        } finally {
            // Always clear MDC — Tomcat reuses threads, stale correlationId would leak to next request
            MDC.clear();
        }
    }
}