package com.teacherfeedback.api.controller;

import com.teacherfeedback.api.client.AsrClient;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SystemStatusControllerTests {
    // 确认老师端只收到最小状态，并正确区分 FunASR 完整可用和不可用。
    @Test
    void mapsAsrHealthToTeacherFacingStatus() {
        AsrClient asrClient = mock(AsrClient.class);
        when(asrClient.checkHealth()).thenReturn(new AsrClient.HealthStatus(true, true, true));

        SystemStatusController.SystemStatusResponse response = new SystemStatusController(asrClient).status();

        assertTrue(response.apiReady());
        assertTrue(response.asrReachable());
        assertTrue(response.asrReady());
        assertTrue(response.streamingReady());
    }

    @Test
    void reportsUnavailableWhenAsrIsNotReachable() {
        AsrClient asrClient = mock(AsrClient.class);
        when(asrClient.checkHealth()).thenReturn(AsrClient.HealthStatus.unavailable());

        SystemStatusController.SystemStatusResponse response = new SystemStatusController(asrClient).status();

        assertTrue(response.apiReady());
        assertFalse(response.asrReachable());
        assertFalse(response.asrReady());
        assertFalse(response.streamingReady());
    }
}
