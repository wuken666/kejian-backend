CREATE TABLE students (
    id BIGINT NOT NULL AUTO_INCREMENT,
    name VARCHAR(50) NOT NULL,
    grade VARCHAR(30) NOT NULL,
    subject VARCHAR(30) NOT NULL,
    learning_goal VARCHAR(255) NOT NULL,
    status VARCHAR(80) NOT NULL,
    last_lesson_date DATE NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE lesson_records (
    id BIGINT NOT NULL AUTO_INCREMENT,
    student_id BIGINT NOT NULL,
    course_title VARCHAR(120) NOT NULL,
    duration_minutes INT NOT NULL,
    content TEXT NOT NULL,
    performance TEXT NOT NULL,
    homework TEXT NOT NULL,
    lesson_date DATE NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_lesson_records_student
        FOREIGN KEY (student_id) REFERENCES students (id) ON DELETE CASCADE,
    INDEX idx_lesson_records_student_date (student_id, lesson_date DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE feedbacks (
    id BIGINT NOT NULL AUTO_INCREMENT,
    lesson_record_id BIGINT NOT NULL,
    audience VARCHAR(20) NOT NULL,
    content LONGTEXT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_feedbacks_lesson_audience UNIQUE (lesson_record_id, audience),
    CONSTRAINT fk_feedbacks_lesson_record
        FOREIGN KEY (lesson_record_id) REFERENCES lesson_records (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
