package com.teacherfeedback.api.config;

import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RequestTraceFilterTests {

    @Test
    void keepsSafeTraceIdAndClearsLoggingContext() throws Exception {
        RequestTraceFilter filter = new RequestTraceFilter();
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/students/1/lesson-analyses");
        request.addHeader("X-Trace-Id", "teacher-flow-20260723");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertEquals("teacher-flow-20260723", response.getHeader("X-Trace-Id"));
        assertNull(MDC.get("traceId"));
    }

    @Test
    void replacesUnsafeTraceId() throws Exception {
        RequestTraceFilter filter = new RequestTraceFilter();
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/students");
        request.addHeader("X-Trace-Id", "bad\nforged-log-line");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        String traceId = response.getHeader("X-Trace-Id");
        assertTrue(traceId.matches("[a-f0-9]{32}"));
        assertFalse(traceId.contains("\n"));
        assertNull(MDC.get("traceId"));
    }
}
