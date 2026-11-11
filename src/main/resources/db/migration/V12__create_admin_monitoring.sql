CREATE TABLE teachers (
    id BIGINT NOT NULL AUTO_INCREMENT,
    name VARCHAR(50) NOT NULL,
    phone VARCHAR(30) NOT NULL DEFAULT '',
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

INSERT INTO teachers (id, name, phone, status, created_at, updated_at)
VALUES (1, '试用老师', '', 'ACTIVE', NOW(6), NOW(6));

ALTER TABLE students
    ADD COLUMN teacher_id BIGINT NOT NULL DEFAULT 1 AFTER id,
    ADD CONSTRAINT fk_students_teacher
        FOREIGN KEY (teacher_id) REFERENCES teachers (id),
    ADD INDEX idx_students_teacher (teacher_id);

CREATE TABLE client_error_events (
    id BIGINT NOT NULL AUTO_INCREMENT,
    teacher_id BIGINT NULL,
    area VARCHAR(40) NOT NULL,
    context VARCHAR(80) NOT NULL,
    error_name VARCHAR(100) NOT NULL,
    message VARCHAR(500) NOT NULL,
    user_agent VARCHAR(500) NOT NULL,
    occurred_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_client_error_events_teacher
        FOREIGN KEY (teacher_id) REFERENCES teachers (id) ON DELETE SET NULL,
    INDEX idx_client_error_events_occurred (occurred_at DESC),
    INDEX idx_client_error_events_teacher_occurred (teacher_id, occurred_at DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
