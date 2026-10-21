ALTER TABLE audio_processing_jobs
    ADD COLUMN heartbeat_at DATETIME(6) NULL AFTER started_at,
    ADD INDEX idx_audio_processing_jobs_status_heartbeat_at_id (status, heartbeat_at, id);

UPDATE audio_processing_jobs
SET heartbeat_at = started_at
WHERE status = 'PROCESSING';
