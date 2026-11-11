ALTER TABLE feedbacks
    ADD COLUMN revised_at DATETIME(6) NULL AFTER created_at;
