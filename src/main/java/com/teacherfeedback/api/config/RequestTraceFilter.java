package com.teacherfeedback.api.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;

@Component
public class RequestTraceFilter extends OncePerRequestFilter {
    private static final Logger log = LoggerFactory.getLogger(RequestTraceFilter.class);
    private static final String TRACE_HEADER = "X-Trace-Id";
    private static final Pattern SAFE_TRACE_ID = Pattern.compile("[A-Za-z0-9-]{8,64}");

    @Override
    protected void doFilterInternal(
        HttpServletRequest request,
        HttpServletResponse response,
        FilterChain filterChain
    ) throws ServletException, IOException {
        String traceId = traceId(request.getHeader(TRACE_HEADER));
        long startedAt = System.nanoTime();
        MDC.put("traceId", traceId);
        response.setHeader(TRACE_HEADER, traceId);
        log.info(
            "api_request phase=start method={} path={}",
            request.getMethod(),
            request.getRequestURI()
        );
        try {
            filterChain.doFilter(request, response);
        } finally {
            log.info(
                "api_request phase=finish method={} path={} status={} duration_ms={}",
                request.getMethod(),
                request.getRequestURI(),
                response.getStatus(),
                elapsedMillis(startedAt)
            );
            MDC.remove("traceId");
        }
    }

    private String traceId(String requestedTraceId) {
        if (requestedTraceId != null && SAFE_TRACE_ID.matcher(requestedTraceId).matches()) {
            return requestedTraceId;
        }
        return UUID.randomUUID().toString().replace("-", "");
    }

    private long elapsedMillis(long startedAt) {
        return (System.nanoTime() - startedAt) / 1_000_000;
    }
}
