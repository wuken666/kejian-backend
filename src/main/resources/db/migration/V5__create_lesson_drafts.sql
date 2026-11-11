CREATE TABLE lesson_drafts (
    id BIGINT NOT NULL AUTO_INCREMENT,
    student_id BIGINT NOT NULL,
    course_title VARCHAR(120) NOT NULL,
    duration_minutes INT NULL,
    content TEXT NOT NULL,
    performance TEXT NOT NULL,
    homework TEXT NOT NULL,
    analysis_id BIGINT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_lesson_drafts_student UNIQUE (student_id),
    CONSTRAINT fk_lesson_drafts_student
        FOREIGN KEY (student_id) REFERENCES students (id) ON DELETE CASCADE,
    CONSTRAINT fk_lesson_drafts_analysis
        FOREIGN KEY (analysis_id) REFERENCES lesson_analyses (id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
