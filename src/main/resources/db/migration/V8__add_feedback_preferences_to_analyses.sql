ALTER TABLE lesson_analyses
    ADD COLUMN feedback_tone VARCHAR(20) NOT NULL DEFAULT 'professional' AFTER feedback_draft,
    ADD COLUMN feedback_length VARCHAR(20) NOT NULL DEFAULT 'standard' AFTER feedback_tone;
