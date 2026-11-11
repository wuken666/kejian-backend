package com.teacherfeedback.api.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.springframework.http.HttpStatus.BAD_GATEWAY;
import static org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE;

@Component
public class AliyunMarketplaceOcrClient implements OcrClient {
    private static final Logger log = LoggerFactory.getLogger(AliyunMarketplaceOcrClient.class);
    private static final List<String> TEXT_KEYS = List.of("content", "text", "word", "words");

    private final RestClient restClient;
    private final String endpoint;
    private final String appCode;
    private final String imageField;

    public AliyunMarketplaceOcrClient(
        @Value("${ai.aliyun-ocr.endpoint}") String endpoint,
        @Value("${ai.aliyun-ocr.app-code}") String appCode,
        @Value("${ai.aliyun-ocr.image-field}") String imageField
    ) {
        this.restClient = RestClient.create();
        this.endpoint = endpoint;
        this.appCode = appCode;
        this.imageField = imageField;
    }

    @Override
    @SuppressWarnings("unchecked")
    public String recognize(byte[] imageBytes) {
        long startedAt = System.nanoTime();
        String callId = newCallId();
        if (!StringUtils.hasText(endpoint) || !StringUtils.hasText(appCode)) {
            log.warn(
                "external_call provider=aliyun_ocr operation=recognize phase=failure "
                    + "call_id={} duration_ms={} outcome=config_missing",
                callId,
                elapsedMillis(startedAt)
            );
            throw new ResponseStatusException(
                SERVICE_UNAVAILABLE,
                "OCR 服务尚未配置，请设置 ALIYUN_OCR_ENDPOINT 和 ALIYUN_OCR_APPCODE"
            );
        }

        log.info(
            "external_call provider=aliyun_ocr operation=recognize phase=start "
                + "call_id={} image_bytes={}",
            callId,
            imageBytes.length
        );
        try {
            Map<String, Object> response = restClient.post()
                .uri(endpoint)
                .header("Authorization", "APPCODE " + appCode)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of(imageField, Base64.getEncoder().encodeToString(imageBytes)))
                .retrieve()
                .body(Map.class);

            String text = extractText(response);
            if (!StringUtils.hasText(text)) {
                throw new ResponseStatusException(BAD_GATEWAY, "OCR 服务未返回可识别文字");
            }
            String recognizedText = text.strip();
            log.info(
                "external_call provider=aliyun_ocr operation=recognize phase=success "
                    + "call_id={} duration_ms={} output_chars={}",
                callId,
                elapsedMillis(startedAt),
                recognizedText.length()
            );
            return recognizedText;
        } catch (ResponseStatusException exception) {
            log.warn(
                "external_call provider=aliyun_ocr operation=recognize phase=failure "
                    + "call_id={} duration_ms={} status={} error_type={}",
                callId,
                elapsedMillis(startedAt),
                exception.getStatusCode().value(),
                exception.getClass().getSimpleName()
            );
            throw exception;
        } catch (RestClientResponseException exception) {
            log.warn(
                "external_call provider=aliyun_ocr operation=recognize phase=failure "
                    + "call_id={} duration_ms={} status={} error_type={}",
                callId,
                elapsedMillis(startedAt),
                exception.getStatusCode().value(),
                exception.getClass().getSimpleName()
            );
            throw new ResponseStatusException(BAD_GATEWAY, "OCR 识别失败，请稍后重试", exception);
        } catch (Exception exception) {
            log.warn(
                "external_call provider=aliyun_ocr operation=recognize phase=failure "
                    + "call_id={} duration_ms={} status=unknown error_type={}",
                callId,
                elapsedMillis(startedAt),
                exception.getClass().getSimpleName()
            );
            throw new ResponseStatusException(BAD_GATEWAY, "OCR 识别失败，请稍后重试", exception);
        }
    }

    private long elapsedMillis(long startedAt) {
        return (System.nanoTime() - startedAt) / 1_000_000;
    }

    private String newCallId() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    private String extractText(Map<String, Object> response) {
        if (response == null || response.isEmpty()) return "";

        for (String key : TEXT_KEYS) {
            Object value = response.get(key);
            if (value instanceof String text && StringUtils.hasText(text)) return text;
        }

        Set<String> lines = new LinkedHashSet<>();
        collectText(response, lines);
        return String.join("\n", lines);
    }

    private void collectText(Object value, Set<String> lines) {
        if (value instanceof Map<?, ?> map) {
            for (String key : TEXT_KEYS) {
                Object candidate = map.get(key);
                if (candidate instanceof String text && StringUtils.hasText(text)) {
                    lines.add(text.strip());
                }
            }
            for (Object child : new ArrayList<>(map.values())) collectText(child, lines);
        } else if (value instanceof List<?> list) {
            for (Object child : list) collectText(child, lines);
        }
    }
}
