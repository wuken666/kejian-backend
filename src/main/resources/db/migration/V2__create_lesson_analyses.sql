CREATE TABLE lesson_analyses (
    id BIGINT NOT NULL AUTO_INCREMENT,
    student_id BIGINT NOT NULL,
    ocr_text LONGTEXT NOT NULL,
    course_title VARCHAR(120) NOT NULL,
    duration_minutes INT NULL,
    content TEXT NOT NULL,
    performance TEXT NOT NULL,
    homework TEXT NOT NULL,
    feedback_draft LONGTEXT NOT NULL,
    missing_fields VARCHAR(255) NOT NULL,
    confidence DECIMAL(4, 3) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    confirmed_at DATETIME(6) NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_lesson_analyses_student
        FOREIGN KEY (student_id) REFERENCES students (id) ON DELETE CASCADE,
    INDEX idx_lesson_analyses_student_created (student_id, created_at DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
