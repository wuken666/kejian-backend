CREATE TABLE student_hotwords (
    id BIGINT NOT NULL AUTO_INCREMENT,
    student_id BIGINT NOT NULL,
    term VARCHAR(30) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_student_hotwords_term UNIQUE (student_id, term),
    CONSTRAINT fk_student_hotwords_student
        FOREIGN KEY (student_id) REFERENCES students (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE asr_evaluations (
    id BIGINT NOT NULL AUTO_INCREMENT,
    student_id BIGINT NULL,
    original_filename VARCHAR(255) NOT NULL,
    course_title VARCHAR(120) NOT NULL,
    reference_text TEXT NOT NULL,
    recognized_text TEXT NOT NULL,
    reference_characters INT NOT NULL,
    recognized_characters INT NOT NULL,
    substitutions INT NOT NULL,
    deletions INT NOT NULL,
    insertions INT NOT NULL,
    character_error_rate DECIMAL(10, 6) NOT NULL,
    inference_seconds DECIMAL(10, 3) NOT NULL,
    hotwords_text TEXT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_asr_evaluations_student
        FOREIGN KEY (student_id) REFERENCES students (id) ON DELETE SET NULL,
    INDEX idx_asr_evaluations_created (created_at DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
