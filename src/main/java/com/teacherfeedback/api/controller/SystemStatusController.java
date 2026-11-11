package com.teacherfeedback.api.controller;

import com.teacherfeedback.api.client.AsrClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/system")
public class SystemStatusController {
    private final AsrClient asrClient;

    public SystemStatusController(AsrClient asrClient) {
        this.asrClient = asrClient;
    }

    // 返回老师端需要的最小服务状态，不把内部服务地址和异常详情交给小程序。
    @GetMapping("/status")
    public SystemStatusResponse status() {
        AsrClient.HealthStatus asr = asrClient.checkHealth();
        return new SystemStatusResponse(
            true,
            asr.reachable(),
            asr.available(),
            asr.streamingReady()
        );
    }

    public record SystemStatusResponse(
        boolean apiReady,
        boolean asrReachable,
        boolean asrReady,
        boolean streamingReady
    ) {
    }
}
