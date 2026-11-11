ALTER TABLE teachers ADD COLUMN avatar_data MEDIUMTEXT NULL;
ALTER TABLE teachers ADD COLUMN profile_completed BOOLEAN NOT NULL DEFAULT FALSE;
UPDATE teachers SET profile_completed = TRUE WHERE name <> '微信老师';
