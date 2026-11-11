ALTER TABLE lesson_records ADD COLUMN scheduled_lesson_id BIGINT NULL;
ALTER TABLE lesson_records ADD CONSTRAINT fk_record_scheduled_lesson FOREIGN KEY (scheduled_lesson_id) REFERENCES scheduled_lessons(id);
