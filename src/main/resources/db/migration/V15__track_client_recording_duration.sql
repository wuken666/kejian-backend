ALTER TABLE lesson_sessions
    ADD COLUMN client_captured_seconds INT NULL AFTER recording_owner_heartbeat_at;
