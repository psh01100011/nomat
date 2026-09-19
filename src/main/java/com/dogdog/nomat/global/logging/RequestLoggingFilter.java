package com.dogdog.nomat.global.logging;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerMapping;

@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestLoggingFilter extends OncePerRequestFilter {

    public static final String REQUEST_ID_HEADER = "X-Request-ID";
    public static final String REQUEST_ID_MDC_KEY = "requestId";

    private static final Pattern SAFE_REQUEST_ID = Pattern.compile("^[A-Za-z0-9._-]{1,64}$");

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String requestId = resolveRequestId(request.getHeader(REQUEST_ID_HEADER));
        String previousRequestId = MDC.get(REQUEST_ID_MDC_KEY);
        long startedAt = System.nanoTime();
        MDC.put(REQUEST_ID_MDC_KEY, requestId);
        response.setHeader(REQUEST_ID_HEADER, requestId);

        try {
            filterChain.doFilter(request, response);
        } finally {
            logCompletedRequest(request, response, startedAt);
            restoreRequestId(previousRequestId);
        }
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return "/actuator/health".equals(request.getServletPath());
    }

    private String resolveRequestId(String candidate) {
        if (candidate != null && SAFE_REQUEST_ID.matcher(candidate).matches()) {
            return candidate;
        }
        return UUID.randomUUID().toString();
    }

    private void logCompletedRequest(
            HttpServletRequest request,
            HttpServletResponse response,
            long startedAt
    ) {
        long durationMs = (System.nanoTime() - startedAt) / 1_000_000;
        int status = response.getStatus();
        String message = "event=http_request_completed method={} path={} status={} durationMs={}";
        String path = loggablePath(request);

        if (status >= 500) {
            log.warn(message, request.getMethod(), path, status, durationMs);
        } else if (status >= 400) {
            log.debug(message, request.getMethod(), path, status, durationMs);
        } else {
            log.info(message, request.getMethod(), path, status, durationMs);
        }
    }

    private String loggablePath(HttpServletRequest request) {
        Object routePattern = request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
        if (routePattern instanceof String pattern) {
            return request.getContextPath() + pattern;
        }
        return request.getRequestURI().replaceAll(
                "(/rooms/invites/)[^/]+(/join)",
                "$1{inviteToken}$2"
        );
    }

    private void restoreRequestId(String previousRequestId) {
        if (previousRequestId == null) {
            MDC.remove(REQUEST_ID_MDC_KEY);
        } else {
            MDC.put(REQUEST_ID_MDC_KEY, previousRequestId);
        }
    }
}
