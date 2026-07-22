alter table admin_users drop column if exists totp_secret;
alter table admin_users drop column if exists totp_enabled;
