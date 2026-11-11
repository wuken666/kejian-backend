package com.teacherfeedback.api.controller;

import com.teacherfeedback.api.auth.TeacherLoginService;
import com.teacherfeedback.api.dto.AuthDtos.TeacherResponse;
import com.teacherfeedback.api.dto.AuthDtos.WechatLoginRequest;
import com.teacherfeedback.api.dto.AuthDtos.WechatLoginResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final TeacherLoginService loginService;

    public AuthController(TeacherLoginService loginService) {
        this.loginService = loginService;
    }

    @PostMapping("/wechat/login")
    public WechatLoginResponse login(@Valid @RequestBody WechatLoginRequest request) {
        return loginService.login(request.code());
    }

    @GetMapping("/me")
    public TeacherResponse me() {
        return loginService.currentTeacher();
    }

    @org.springframework.web.bind.annotation.PatchMapping("/me")
    public TeacherResponse updateProfile(@Valid @RequestBody com.teacherfeedback.api.dto.AuthDtos.UpdateProfileRequest request) {
        return loginService.updateProfile(request);
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest request) {
        loginService.logout(request.getHeader("Authorization"));
        return ResponseEntity.noContent().build();
    }
}
