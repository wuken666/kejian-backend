ALTER TABLE lesson_analyses
    ADD COLUMN student_matched BOOLEAN NOT NULL DEFAULT FALSE AFTER confidence,
    ADD COLUMN detected_student_names VARCHAR(255) NOT NULL DEFAULT '' AFTER student_matched,
    ADD COLUMN warnings TEXT NOT NULL AFTER detected_student_names;
