ALTER TABLE lesson_session_segments
    ADD COLUMN asr_status VARCHAR(20) NOT NULL DEFAULT 'UNKNOWN',
    ADD COLUMN audio_quality VARCHAR(20) NOT NULL DEFAULT 'UNKNOWN',
    ADD COLUMN audio_rms DECIMAL(8, 6) NULL,
    ADD COLUMN clipping_ratio DECIMAL(8, 6) NULL,
    ADD COLUMN asr_inference_seconds DECIMAL(10, 3) NULL;

UPDATE lesson_session_segments
SET asr_status = CASE
    WHEN NULLIF(TRIM(transcript), '') IS NOT NULL THEN 'TRANSCRIBED'
    ELSE 'UNKNOWN'
END;
