package com.teacherfeedback.api.auth;

import com.teacherfeedback.api.dao.TeacherLoginSessionRepository;
import com.teacherfeedback.api.dao.TeacherRepository;
import com.teacherfeedback.api.dao.TeacherWechatBindingRepository;
import com.teacherfeedback.api.dto.AuthDtos.TeacherResponse;
import com.teacherfeedback.api.dto.AuthDtos.WechatLoginResponse;
import com.teacherfeedback.api.model.Teacher;
import com.teacherfeedback.api.model.TeacherLoginSession;
import com.teacherfeedback.api.model.TeacherWechatBinding;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;

import static org.springframework.http.HttpStatus.NOT_FOUND;
import static org.springframework.http.HttpStatus.UNAUTHORIZED;

@Service
public class TeacherLoginService {
    private static final SecureRandom RANDOM = new SecureRandom();

    private final TeacherRepository teacherRepository;
    private final TeacherWechatBindingRepository bindingRepository;
    private final TeacherLoginSessionRepository sessionRepository;
    private final WechatCodeExchangeClient wechatClient;
    private final long sessionTtlHours;

    public TeacherLoginService(
        TeacherRepository teacherRepository,
        TeacherWechatBindingRepository bindingRepository,
        TeacherLoginSessionRepository sessionRepository,
        WechatCodeExchangeClient wechatClient,
        @Value("${auth.session-ttl-hours:168}") long sessionTtlHours
    ) {
        this.teacherRepository = teacherRepository;
        this.bindingRepository = bindingRepository;
        this.sessionRepository = sessionRepository;
        this.wechatClient = wechatClient;
        this.sessionTtlHours = Math.max(1, sessionTtlHours);
    }

    @Transactional
    public WechatLoginResponse login(String code) {
        WechatCodeExchangeClient.WechatIdentity identity = wechatClient.exchange(code.strip());
        TeacherWechatBinding binding = bindingRepository
            .findByAppidAndOpenid(identity.appid(), identity.openid())
            .orElseGet(() -> createBinding(identity));
        binding.markLogin(identity.unionid());
        bindingRepository.save(binding);

        Teacher teacher = binding.getTeacher();
        if (!teacher.isActive()) {
            throw new ResponseStatusException(UNAUTHORIZED, "当前老师账号不可用");
        }
        LocalDateTime issuedAt = LocalDateTime.now();
        LocalDateTime expiresAt = issuedAt.plusHours(sessionTtlHours);
        String accessToken = randomToken();
        sessionRepository.save(new TeacherLoginSession(
            teacher,
            sha256(accessToken),
            issuedAt,
            expiresAt
        ));
        return new WechatLoginResponse(accessToken, expiresAt, toResponse(teacher));
    }

    @Transactional(readOnly = true)
    public TeacherResponse currentTeacher() {
        Long teacherId = TeacherContext.currentTeacherId();
        if (teacherId == null) {
            throw new ResponseStatusException(UNAUTHORIZED, "请先登录");
        }
        return teacherRepository.findById(teacherId)
            .filter(Teacher::isActive)
            .map(this::toResponse)
            .orElseThrow(() -> new ResponseStatusException(UNAUTHORIZED, "登录状态已失效"));
    }

    @Transactional
    public void logout(String authorizationHeader) {
        String token = bearerToken(authorizationHeader);
        if (token.isBlank()) return;
        sessionRepository.findByTokenHashAndRevokedAtIsNullAndExpiresAtAfter(
            sha256(token),
            LocalDateTime.now()
        ).ifPresent(session -> {
            session.revoke();
            sessionRepository.save(session);
        });
    }

    private TeacherWechatBinding createBinding(WechatCodeExchangeClient.WechatIdentity identity) {
        Teacher teacher = teacherRepository.save(new Teacher("微信老师"));
        return new TeacherWechatBinding(teacher, identity.appid(), identity.openid(), identity.unionid());
    }

    private TeacherResponse toResponse(Teacher teacher) {
        return new TeacherResponse(teacher.getId(), teacher.getName(), teacher.getStatus(), teacher.getAvatarData(), teacher.isProfileCompleted());
    }

    @Transactional
    public TeacherResponse updateProfile(com.teacherfeedback.api.dto.AuthDtos.UpdateProfileRequest request) {
        Long id = TeacherContext.currentTeacherId();
        if (id == null) throw new ResponseStatusException(UNAUTHORIZED, "请先登录");
        Teacher teacher = teacherRepository.findById(id).filter(Teacher::isActive)
            .orElseThrow(() -> new ResponseStatusException(UNAUTHORIZED, "登录状态已失效"));
        String avatar = request.avatarData();
        if (avatar != null && !avatar.isBlank()) {
            try {
                if (!avatar.startsWith("data:image/jpeg;base64,") && !avatar.startsWith("data:image/png;base64,")) throw new IllegalArgumentException();
                byte[] bytes = java.util.Base64.getDecoder().decode(avatar.substring(avatar.indexOf(',') + 1));
                if (bytes.length > 128000) throw new IllegalArgumentException();
                try (var stream = javax.imageio.ImageIO.createImageInputStream(new java.io.ByteArrayInputStream(bytes))) {
                    var readers = javax.imageio.ImageIO.getImageReaders(stream);
                    if (!readers.hasNext()) throw new IllegalArgumentException();
                    var reader = readers.next();
                    try { reader.setInput(stream); if (reader.getWidth(0) > 1024 || reader.getHeight(0) > 1024) throw new IllegalArgumentException(); }
                    finally { reader.dispose(); }
                }
            } catch (Exception error) {
                throw new ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST, "请选择较小的 PNG 或 JPEG 头像");
            }
        }
        teacher.updateProfile(request.name(), avatar);
        return toResponse(teacher);
    }

    private String randomToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public static String bearerToken(String header) {
        if (header == null || !header.regionMatches(true, 0, "Bearer ", 0, 7)) return "";
        return header.substring(7).strip();
    }

    public static String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                .digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(64);
            for (byte item : digest) result.append(String.format("%02x", item));
            return result.toString();
        } catch (Exception exception) {
            throw new IllegalStateException("无法创建登录令牌摘要", exception);
        }
    }
}
