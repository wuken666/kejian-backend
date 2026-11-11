package com.teacherfeedback.api.config;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class AdminAccessFilterTests {
    private final AdminAccessFilter filter = new AdminAccessFilter("secret");

    // 后台接口缺少密钥时应拒绝访问。
    @Test
    void rejectsAdminRequestWithoutToken() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/admin/dashboard");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString()).contains("后台访问密钥无效");
        verify(chain, never()).doFilter(request, response);
    }

    // 后台接口携带正确密钥时应继续进入控制器。
    @Test
    void allowsAdminRequestWithValidToken() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/admin/dashboard");
        request.addHeader("X-Admin-Token", "secret");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
    }

    // 教师端接口不应被后台密钥规则拦截。
    @Test
    void leavesTeacherApiUnchanged() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/students");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
    }
}
