package com.teacherfeedback.api.auth;

import com.teacherfeedback.api.dao.TeacherLoginSessionRepository;
import com.teacherfeedback.api.model.Teacher;
import com.teacherfeedback.api.model.TeacherLoginSession;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TeacherAuthenticationFilterTests {
    private final TeacherLoginSessionRepository sessionRepository = mock(TeacherLoginSessionRepository.class);

    @AfterEach
    void clearTeacherContext() {
        TeacherContext.clear();
    }

    // 正式模式缺少令牌时必须拒绝业务请求，不能落回默认老师。
    @Test
    void rejectsBusinessRequestWithoutTokenWhenAuthenticationIsRequired() throws Exception {
        TeacherAuthenticationFilter filter = new TeacherAuthenticationFilter(sessionRepository, true, 1L);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/students");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(TeacherContext.currentTeacherId()).isNull();
        verify(chain, never()).doFilter(request, response);
    }

    // 有效令牌只在当前请求线程内建立老师上下文，请求结束后必须清理。
    @Test
    void bindsTeacherForValidTokenAndClearsItAfterRequest() throws Exception {
        String token = "valid-token";
        Teacher teacher = mock(Teacher.class);
        when(teacher.getId()).thenReturn(42L);
        when(teacher.isActive()).thenReturn(true);
        TeacherLoginSession session = mock(TeacherLoginSession.class);
        when(session.isActiveAt(any(LocalDateTime.class))).thenReturn(true);
        when(session.getTeacher()).thenReturn(teacher);
        when(sessionRepository.findByTokenHashAndRevokedAtIsNullAndExpiresAtAfter(
            eq(TeacherLoginService.sha256(token)),
            any(LocalDateTime.class)
        )).thenReturn(Optional.of(session));
        FilterChain chain = mock(FilterChain.class);
        doAnswer(invocation -> {
            assertThat(TeacherContext.currentTeacherId()).isEqualTo(42L);
            return null;
        }).when(chain).doFilter(any(), any());

        MockHttpServletRequest request = requestWithToken(token);
        MockHttpServletResponse response = new MockHttpServletResponse();
        TeacherAuthenticationFilter filter = new TeacherAuthenticationFilter(sessionRepository, true, 1L);
        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
        assertThat(TeacherContext.currentTeacherId()).isNull();
    }

    // 本地开发模式允许显式默认老师，但过滤器仍会在请求结束时清理上下文。
    @Test
    void supportsExplicitLocalFallbackWithoutLeakingContext() throws Exception {
        TeacherAuthenticationFilter filter = new TeacherAuthenticationFilter(sessionRepository, false, 7L);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/students");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);
        doAnswer(invocation -> {
            assertThat(TeacherContext.currentTeacherId()).isEqualTo(7L);
            return null;
        }).when(chain).doFilter(any(), any());

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
        assertThat(TeacherContext.currentTeacherId()).isNull();
    }

    private MockHttpServletRequest requestWithToken(String token) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/students");
        request.addHeader("Authorization", "Bearer " + token);
        return request;
    }
}
