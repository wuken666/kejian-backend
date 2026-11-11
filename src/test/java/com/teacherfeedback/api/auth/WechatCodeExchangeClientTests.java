package com.teacherfeedback.api.auth;

import org.junit.jupiter.api.Test;
import org.springframework.web.util.DefaultUriBuilderFactory;
import static org.junit.jupiter.api.Assertions.*;

class WechatCodeExchangeClientTests {
    @Test
    void parsesWechatJsonEvenWhenDeliveredAsText() {
        assertEquals("test-openid", WechatCodeExchangeClient.parseResponse("{\"openid\":\"test-openid\"}").get("openid"));
        assertEquals(40029, WechatCodeExchangeClient.parseResponse("{\"errcode\":40029}").get("errcode"));
    }
    @Test
    void usesAnAbsoluteWechatUrlAndEncodesQueryValues() {
        var uri = WechatCodeExchangeClient.sessionUri("wx-test", "test-secret", "code+&value");
        assertEquals("https", uri.getScheme());
        assertEquals("api.weixin.qq.com", uri.getHost());
        assertEquals("/sns/jscode2session", uri.getPath());
        assertTrue(uri.getRawQuery().contains("js_code=code%2B%26value"));
        assertTrue(uri.getRawQuery().contains("grant_type=authorization_code"));
    }

    @Test
    void absoluteUrlMustNotBePassedAsAPath() {
        var broken = new DefaultUriBuilderFactory().builder()
            .path("https://api.weixin.qq.com/sns/jscode2session").build();
        assertNull(broken.getHost());
    }
}
