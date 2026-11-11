ALTER TABLE lesson_sessions
    ADD COLUMN recording_owner_token VARCHAR(80) NULL,
    ADD COLUMN recording_owner_heartbeat_at DATETIME(6) NULL;
