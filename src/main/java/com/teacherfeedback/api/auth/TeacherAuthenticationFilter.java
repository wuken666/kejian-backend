package com.teacherfeedback.api.auth;

import com.teacherfeedback.api.dao.TeacherLoginSessionRepository;
import com.teacherfeedback.api.model.TeacherLoginSession;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
public class TeacherAuthenticationFilter extends OncePerRequestFilter {
    private final TeacherLoginSessionRepository sessionRepository;
    private final boolean requireToken;
    private final long defaultTeacherId;

    public TeacherAuthenticationFilter(
        TeacherLoginSessionRepository sessionRepository,
        @Value("${auth.require-teacher-token:true}") boolean requireToken,
        @Value("${auth.default-teacher-id:1}") long defaultTeacherId
    ) {
        this.sessionRepository = sessionRepository;
        this.requireToken = requireToken;
        this.defaultTeacherId = defaultTeacherId;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return request.getRequestURI().equals("/api/auth/wechat/login")
            || request.getRequestURI().startsWith("/api/admin/")
            || "OPTIONS".equalsIgnoreCase(request.getMethod());
    }

    @Override
    protected void doFilterInternal(
        HttpServletRequest request,
        HttpServletResponse response,
        FilterChain filterChain
    ) throws ServletException, IOException {
        try {
            String token = TeacherLoginService.bearerToken(request.getHeader("Authorization"));
            if (token.isBlank()) {
                if (requireToken) {
                    unauthorized(response, "请先登录");
                    return;
                }
                // 本地单老师联调的显式回退；生产环境必须打开令牌校验并关闭该路径。
                TeacherContext.set(defaultTeacherId);
            } else {
                TeacherLoginSession session = sessionRepository
                    .findByTokenHashAndRevokedAtIsNullAndExpiresAtAfter(
                        TeacherLoginService.sha256(token),
                        LocalDateTime.now()
                    )
                    .orElse(null);
                if (session == null || !session.isActiveAt(LocalDateTime.now())) {
                    unauthorized(response, "登录状态已失效");
                    return;
                }
                TeacherContext.set(session.getTeacher().getId());
            }
            filterChain.doFilter(request, response);
        } finally {
            TeacherContext.clear();
        }
    }

    private void unauthorized(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"message\":\"" + message + "\"}");
    }
}
