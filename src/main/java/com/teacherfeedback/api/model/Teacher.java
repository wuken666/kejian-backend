package com.teacherfeedback.api.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(name = "teachers")
public class Teacher {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 50)
    private String name;

    @Column(nullable = false, length = 30)
    private String phone;

    @Column(nullable = false, length = 20)
    private String status;

    @Column(name = "avatar_data", columnDefinition = "MEDIUMTEXT")
    private String avatarData;

    @Column(name = "profile_completed", nullable = false)
    private boolean profileCompleted;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    protected Teacher() {
    }

    // 首次微信登录自动创建老师档案，后续可在管理后台补充正式姓名。
    public Teacher(String name) {
        LocalDateTime now = LocalDateTime.now();
        this.name = name == null || name.isBlank() ? "微信老师" : name.strip();
        this.phone = "";
        this.status = "ACTIVE";
        this.createdAt = now;
        this.updatedAt = now;
    }

    public Long getId() { return id; }
    public String getAvatarData() { return avatarData == null ? "" : avatarData; }
    public boolean isProfileCompleted() { return profileCompleted; }
    public void updateProfile(String name, String avatarData) {
        this.name = name.strip();
        if (avatarData != null) this.avatarData = avatarData;
        this.profileCompleted = true;
        this.updatedAt = LocalDateTime.now();
    }
    public String getName() { return name; }
    public String getPhone() { return phone; }
    public String getStatus() { return status; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }

    public boolean isActive() {
        return "ACTIVE".equalsIgnoreCase(status);
    }
}
