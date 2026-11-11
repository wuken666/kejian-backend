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
@Table(name = "teacher_wechat_bindings")
public class TeacherWechatBinding {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "teacher_id", nullable = false)
    private Teacher teacher;

    @Column(nullable = false, length = 64)
    private String appid;

    @Column(nullable = false, length = 128)
    private String openid;

    @Column(length = 128)
    private String unionid;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "last_login_at", nullable = false)
    private LocalDateTime lastLoginAt;

    protected TeacherWechatBinding() {
    }

    public TeacherWechatBinding(Teacher teacher, String appid, String openid, String unionid) {
        LocalDateTime now = LocalDateTime.now();
        this.teacher = teacher;
        this.appid = appid.strip();
        this.openid = openid.strip();
        this.unionid = normalize(unionid);
        this.createdAt = now;
        this.lastLoginAt = now;
    }

    // 每次登录刷新绑定时间；openid 仍只作为指定小程序内的身份标识。
    public void markLogin(String unionid) {
        this.unionid = normalize(unionid);
        this.lastLoginAt = LocalDateTime.now();
    }

    public Teacher getTeacher() { return teacher; }
    public String getAppid() { return appid; }
    public String getOpenid() { return openid; }

    private String normalize(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
