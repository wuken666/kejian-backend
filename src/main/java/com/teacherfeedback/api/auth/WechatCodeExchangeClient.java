package com.teacherfeedback.api.auth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.util.UriComponentsBuilder;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.net.URI;
import java.util.Map;

import static org.springframework.http.HttpStatus.BAD_GATEWAY;
import static org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE;

@Component
public class WechatCodeExchangeClient {
    private static final Logger log = LoggerFactory.getLogger(WechatCodeExchangeClient.class);
    private static final String SESSION_URL = "https://api.weixin.qq.com/sns/jscode2session";
    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final RestClient restClient;
    private final String appId;
    private final String appSecret;

    public WechatCodeExchangeClient(
        @Value("${auth.wechat.app-id}") String appId,
        @Value("${auth.wechat.app-secret}") String appSecret,
        @Value("${auth.wechat.connect-timeout-ms:3000}") int connectTimeoutMs,
        @Value("${auth.wechat.read-timeout-ms:8000}") int readTimeoutMs
    ) {
        this.appId = appId;
        this.appSecret = appSecret;
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofMillis(Math.max(500, connectTimeoutMs)));
        requestFactory.setReadTimeout(Duration.ofMillis(Math.max(1000, readTimeoutMs)));
        this.restClient = RestClient.builder().requestFactory(requestFactory).build();
    }

    // 用一次性 code 换取 openid；AppSecret 只在后端环境变量中使用，不返回给小程序。
    @SuppressWarnings("unchecked")
    public WechatIdentity exchange(String code) {
        if (!StringUtils.hasText(appId) || !StringUtils.hasText(appSecret)) {
            throw new ResponseStatusException(
                SERVICE_UNAVAILABLE,
                "微信登录尚未完成服务端配置"
            );
        }
        try {
            String body = restClient.get()
                .uri(sessionUri(appId, appSecret, code))
                .retrieve()
                .body(String.class);
            Map<String, Object> payload = parseResponse(body);
            int errorCode = number(payload, "errcode");
            String openid = text(payload, "openid");
            if (errorCode != 0 || !StringUtils.hasText(openid)) {
                log.warn("wechat_login code exchange rejected errcode={}", errorCode);
                throw new ResponseStatusException(BAD_GATEWAY, "微信登录凭证无效或已过期");
            }
            return new WechatIdentity(appId, openid, text(payload, "unionid"));
        } catch (ResponseStatusException exception) {
            throw exception;
        } catch (RestClientResponseException exception) {
            log.warn("wechat_login code exchange failed status={}", exception.getStatusCode().value());
            throw new ResponseStatusException(BAD_GATEWAY, "微信登录服务暂时不可用", exception);
        } catch (RuntimeException exception) {
            log.warn("wechat_login code exchange failed error_type={} cause_types={}",
                exception.getClass().getSimpleName(), safeCauseTypes(exception));
            throw new ResponseStatusException(BAD_GATEWAY, "微信登录服务暂时不可用", exception);
        }
    }

    // Exception messages may contain the request URL and its credentials.
    static String safeCauseTypes(Throwable failure) {
        StringBuilder types = new StringBuilder();
        for (int depth = 0; failure != null && depth < 8; depth++) {
            if (!types.isEmpty()) types.append(" -> ");
            types.append(failure.getClass().getSimpleName());
            failure = failure.getCause();
        }
        return types.toString();
    }

    static URI sessionUri(String appId, String secret, String code) {
        return UriComponentsBuilder.fromUriString(SESSION_URL)
            .queryParam("appid", "{appid}")
            .queryParam("secret", "{secret}")
            .queryParam("js_code", "{code}")
            .queryParam("grant_type", "authorization_code")
            .encode().buildAndExpand(appId, secret, code).toUri();
    }

    @SuppressWarnings("unchecked")
    static Map<String, Object> parseResponse(String body) {
        return JSON.readValue(body, Map.class);
    }

    private String text(Map<String, Object> payload, String key) {
        Object value = payload == null ? null : payload.get(key);
        return value == null ? "" : String.valueOf(value).strip();
    }

    private int number(Map<String, Object> payload, String key) {
        Object value = payload == null ? null : payload.get(key);
        if (value instanceof Number number) return number.intValue();
        try {
            return value == null ? 0 : Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException ignored) {
            return -1;
        }
    }

    public record WechatIdentity(String appid, String openid, String unionid) {
    }
}
