alter table users add column if not exists phone_country_code varchar(8);
alter table users add column if not exists phone_number varchar(32);
alter table users add column if not exists phone_e164 varchar(32);

create unique index if not exists uk_users_phone_e164 on users (phone_e164);

alter table point_accounts
    add column if not exists frozen_points integer not null default 0;

create table if not exists sms_verification_codes (
    id uuid not null,
    purpose varchar(48) not null,
    phone_country_code varchar(8) not null,
    phone_number varchar(32) not null,
    phone_e164 varchar(32) not null,
    expires_at timestamp with time zone not null,
    used_at timestamp with time zone,
    created_at timestamp with time zone not null,
    primary key (id)
);

create index if not exists idx_sms_verification_phone_purpose
    on sms_verification_codes (phone_e164, purpose, created_at);

create table if not exists user_wallets (
    id uuid not null,
    user_id uuid not null,
    network varchar(16) not null,
    wallet_address varchar(128) not null,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null,
    primary key (id),
    constraint uk_user_wallets_user unique (user_id),
    constraint fk_user_wallets_user foreign key (user_id) references users (id)
);

create table if not exists withdrawal_requests (
    id uuid not null,
    user_id uuid not null,
    point_amount integer not null,
    usdt_amount numeric(18, 6) not null,
    usdt_per_point numeric(18, 8) not null,
    network varchar(16) not null,
    wallet_address varchar(128) not null,
    status varchar(24) not null,
    tx_hash varchar(128),
    admin_note varchar(255),
    reviewed_by varchar(64),
    created_at timestamp with time zone not null,
    reviewed_at timestamp with time zone,
    primary key (id),
    constraint fk_withdrawal_requests_user foreign key (user_id) references users (id)
);

create index if not exists idx_withdrawal_requests_user_created
    on withdrawal_requests (user_id, created_at);

create table if not exists point_transfers (
    id uuid not null,
    sender_user_id uuid not null,
    recipient_user_id uuid not null,
    sender_account varchar(32) not null,
    recipient_account varchar(32) not null,
    point_amount integer not null,
    created_at timestamp with time zone not null,
    primary key (id),
    constraint fk_point_transfers_sender foreign key (sender_user_id) references users (id),
    constraint fk_point_transfers_recipient foreign key (recipient_user_id) references users (id)
);

create index if not exists idx_point_transfers_sender_created
    on point_transfers (sender_user_id, created_at);

create index if not exists idx_point_transfers_recipient_created
    on point_transfers (recipient_user_id, created_at);
