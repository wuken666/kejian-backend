package com.teacherfeedback.api.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(name = "teacher_login_sessions")
public class TeacherLoginSession {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 过滤器在事务外读取令牌对应老师，只取轻量档案即可，避免懒加载导致真机请求被误判为失效。
    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "teacher_id", nullable = false)
    private Teacher teacher;

    @Column(name = "token_hash", nullable = false, length = 64, unique = true)
    private String tokenHash;

    @Column(name = "issued_at", nullable = false)
    private LocalDateTime issuedAt;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "revoked_at")
    private LocalDateTime revokedAt;

    protected TeacherLoginSession() {
    }

    public TeacherLoginSession(
        Teacher teacher,
        String tokenHash,
        LocalDateTime issuedAt,
        LocalDateTime expiresAt
    ) {
        this.teacher = teacher;
        this.tokenHash = tokenHash;
        this.issuedAt = issuedAt;
        this.expiresAt = expiresAt;
    }

    public boolean isActiveAt(LocalDateTime now) {
        return revokedAt == null && expiresAt.isAfter(now) && teacher.isActive();
    }

    // 注销只撤销服务端令牌记录，客户端保存的字符串失效后不能再次访问业务数据。
    public void revoke() {
        revokedAt = LocalDateTime.now();
    }

    public Teacher getTeacher() { return teacher; }
    public String getTokenHash() { return tokenHash; }
}
