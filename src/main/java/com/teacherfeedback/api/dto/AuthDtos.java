package com.teacherfeedback.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

public final class AuthDtos {
    private AuthDtos() {
    }

    public record WechatLoginRequest(
        @NotBlank @Size(max = 128) String code
    ) {
    }

    public record TeacherResponse(
        Long id,
        String name,
        String status,
        String avatarData,
        boolean profileCompleted
    ) {
    }

    public record UpdateProfileRequest(@NotBlank @Size(max = 50) String name, @Size(max = 180000) String avatarData) {}

    public record WechatLoginResponse(
        String accessToken,
        LocalDateTime expiresAt,
        TeacherResponse teacher
    ) {
    }
}
