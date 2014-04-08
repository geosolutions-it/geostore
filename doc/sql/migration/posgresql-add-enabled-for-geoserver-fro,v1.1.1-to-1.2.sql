ALTER TABLE gs_user ADD COLUMN enabled character(1) NOT NULL DEFAULT 'Y';
ALTER TABLE gs_usergroup ADD COLUMN enabled character(1) NOT NULL DEFAULT 'Y';
-- ALTER TABLE geostore.gs_user ADD COLUMN enabled character(1) NOT NULL DEFAULT 'Y';
-- ALTER TABLE geostore.gs_usergroup ADD COLUMN enabled character(1) NOT NULL DEFAULT 'Y';