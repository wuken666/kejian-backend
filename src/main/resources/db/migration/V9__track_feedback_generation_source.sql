ALTER TABLE feedbacks
    ADD COLUMN generation_source VARCHAR(20) NOT NULL DEFAULT 'AI' AFTER content;

UPDATE feedbacks
SET generation_source = 'MANUAL'
WHERE revised_at IS NOT NULL;
