alter table admin_users add column if not exists totp_secret varchar(64);
alter table admin_users add column if not exists totp_enabled boolean not null default false;
