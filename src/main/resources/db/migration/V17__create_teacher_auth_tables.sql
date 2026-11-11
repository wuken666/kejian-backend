CREATE TABLE teacher_wechat_bindings (
    id BIGINT NOT NULL AUTO_INCREMENT,
    teacher_id BIGINT NOT NULL,
    appid VARCHAR(64) NOT NULL,
    openid VARCHAR(128) NOT NULL,
    unionid VARCHAR(128) NULL,
    created_at DATETIME(6) NOT NULL,
    last_login_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_teacher_wechat_appid_openid UNIQUE (appid, openid),
    CONSTRAINT fk_teacher_wechat_binding_teacher
        FOREIGN KEY (teacher_id) REFERENCES teachers (id) ON DELETE CASCADE,
    INDEX idx_teacher_wechat_binding_teacher (teacher_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE teacher_login_sessions (
    id BIGINT NOT NULL AUTO_INCREMENT,
    teacher_id BIGINT NOT NULL,
    token_hash CHAR(64) NOT NULL,
    issued_at DATETIME(6) NOT NULL,
    expires_at DATETIME(6) NOT NULL,
    revoked_at DATETIME(6) NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_teacher_login_session_token UNIQUE (token_hash),
    CONSTRAINT fk_teacher_login_session_teacher
        FOREIGN KEY (teacher_id) REFERENCES teachers (id) ON DELETE CASCADE,
    INDEX idx_teacher_login_session_active (token_hash, revoked_at, expires_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
