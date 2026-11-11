package com.teacherfeedback.api.controller;

import com.teacherfeedback.api.auth.TeacherContext;
import com.teacherfeedback.api.dao.ClientErrorEventRepository;
import com.teacherfeedback.api.model.ClientErrorEvent;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/client-errors")
public class ClientErrorLogController {
    private static final Logger log = LoggerFactory.getLogger(ClientErrorLogController.class);
    private final ClientErrorEventRepository clientErrorEventRepository;

    public ClientErrorLogController(ClientErrorEventRepository clientErrorEventRepository) {
        this.clientErrorEventRepository = clientErrorEventRepository;
    }

    // 接收浏览器异常摘要，不保存课堂内容、反馈正文和录音数据。
    @PostMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void record(
        @Valid @RequestBody ClientErrorLogRequest request,
        HttpServletRequest servletRequest
    ) {
        String userAgent = safe(servletRequest.getHeader("User-Agent"));
        log.error(
            "client_error area={} context={} error_name={} message={} user_agent={}",
            safe(request.area()),
            safe(request.context()),
            safe(request.errorName()),
            safe(request.message()),
            userAgent
        );
        clientErrorEventRepository.save(new ClientErrorEvent(
            TeacherContext.currentTeacherId(),
            request.area(),
            request.context(),
            request.errorName(),
            request.message(),
            userAgent
        ));
    }

    // 清除换行符，避免浏览器错误文本破坏结构化日志格式。
    private String safe(String value) {
        return value == null ? "unknown" : value.replace('\r', ' ').replace('\n', ' ');
    }

    public record ClientErrorLogRequest(
        @Size(max = 40) String area,
        @NotBlank @Size(max = 80) String context,
        @Size(max = 100) String errorName,
        @Size(max = 500) String message
    ) {
    }
}
