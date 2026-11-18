CREATE TABLE feedback_jobs (
    id VARCHAR(36) PRIMARY KEY,
    teacher_id BIGINT NOT NULL,
    student_id BIGINT NOT NULL,
    request_key VARCHAR(64) NOT NULL,
    request_json LONGTEXT NOT NULL,
    course_title VARCHAR(120) NOT NULL,
    status VARCHAR(16) NOT NULL,
    lesson_id BIGINT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uq_feedback_job_request (teacher_id, student_id, request_key),
    KEY ix_feedback_job_queue (status, created_at),
    CONSTRAINT fk_feedback_job_student FOREIGN KEY (student_id) REFERENCES students(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
