ALTER TABLE feedbacks
    ADD COLUMN confirmed_at DATETIME(6) NULL AFTER revised_at;

-- 老师主动修改并保存过的历史反馈视为已经核对，避免升级后全部退回待确认。
UPDATE feedbacks
SET confirmed_at = revised_at
WHERE generation_source = 'MANUAL'
  AND revised_at IS NOT NULL;
