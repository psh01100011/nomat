UPDATE audio_processing_jobs
SET heartbeat_at = started_at
WHERE status = 'PROCESSING'
  AND heartbeat_at IS NULL;
