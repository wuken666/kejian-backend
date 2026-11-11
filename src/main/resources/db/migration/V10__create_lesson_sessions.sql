CREATE TABLE lesson_sessions (
    id BIGINT NOT NULL AUTO_INCREMENT,
    student_id BIGINT NOT NULL,
    course_title VARCHAR(120) NOT NULL,
    status VARCHAR(20) NOT NULL,
    started_at DATETIME(6) NOT NULL,
    resumed_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_lesson_sessions_student
        FOREIGN KEY (student_id) REFERENCES students (id) ON DELETE CASCADE,
    INDEX idx_lesson_sessions_status_updated (status, updated_at DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE lesson_session_segments (
    id BIGINT NOT NULL AUTO_INCREMENT,
    lesson_session_id BIGINT NOT NULL,
    client_segment_id VARCHAR(80) NOT NULL,
    sequence_number INT NOT NULL,
    duration_seconds INT NOT NULL,
    transcript TEXT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_lesson_session_segments_client
        UNIQUE (lesson_session_id, client_segment_id),
    CONSTRAINT uk_lesson_session_segments_sequence
        UNIQUE (lesson_session_id, sequence_number),
    CONSTRAINT fk_lesson_session_segments_session
        FOREIGN KEY (lesson_session_id) REFERENCES lesson_sessions (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
