package com.teacherfeedback.api.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@Component
public class AdminAccessFilter extends OncePerRequestFilter {
    private final byte[] expectedToken;

    public AdminAccessFilter(@Value("${admin.access-token}") String accessToken) {
        this.expectedToken = accessToken.getBytes(StandardCharsets.UTF_8);
    }

    // 仅拦截后台接口，教师端接口继续沿用现有访问方式。
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/api/admin/");
    }

    // 使用独立后台密钥校验请求，避免分享教师端链接时暴露运营数据。
    @Override
    protected void doFilterInternal(
        HttpServletRequest request,
        HttpServletResponse response,
        FilterChain filterChain
    ) throws ServletException, IOException {
        String suppliedToken = request.getHeader("X-Admin-Token");
        byte[] suppliedBytes = suppliedToken == null
            ? new byte[0]
            : suppliedToken.getBytes(StandardCharsets.UTF_8);
        if (!MessageDigest.isEqual(expectedToken, suppliedBytes)) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            response.setContentType("application/json");
            response.getWriter().write("{\"message\":\"后台访问密钥无效\"}");
            return;
        }
        filterChain.doFilter(request, response);
    }
}
