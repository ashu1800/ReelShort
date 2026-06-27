create table users (
    id uuid not null,
    username varchar(64) not null,
    password_hash varchar(120) not null,
    status varchar(24) not null,
    created_at timestamp with time zone not null,
    primary key (id),
    constraint uk_users_username unique (username)
);

create table access_tokens (
    id uuid not null,
    token_hash varchar(64) not null,
    user_id uuid not null,
    issued_at timestamp with time zone not null,
    expires_at timestamp with time zone not null,
    revoked_at timestamp with time zone,
    primary key (id),
    constraint uk_access_tokens_token_hash unique (token_hash),
    constraint fk_access_tokens_user foreign key (user_id) references users (id)
);

create table permissions (
    id uuid not null,
    code varchar(64) not null,
    description varchar(255) not null,
    primary key (id),
    constraint uk_permissions_code unique (code)
);

create table roles (
    id uuid not null,
    code varchar(64) not null,
    name varchar(120) not null,
    created_at timestamp with time zone not null,
    primary key (id),
    constraint uk_roles_code unique (code)
);

create table role_permissions (
    role_id uuid not null,
    permission_id uuid not null,
    constraint uk_role_permissions_role_permission unique (role_id, permission_id),
    constraint fk_role_permissions_role foreign key (role_id) references roles (id),
    constraint fk_role_permissions_permission foreign key (permission_id) references permissions (id)
);

create table admin_users (
    id uuid not null,
    username varchar(64) not null,
    password_hash varchar(120) not null,
    status varchar(24) not null,
    created_at timestamp with time zone not null,
    primary key (id),
    constraint uk_admin_users_username unique (username)
);

create table admin_user_roles (
    admin_user_id uuid not null,
    role_id uuid not null,
    constraint uk_admin_user_roles_user_role unique (admin_user_id, role_id),
    constraint fk_admin_user_roles_user foreign key (admin_user_id) references admin_users (id),
    constraint fk_admin_user_roles_role foreign key (role_id) references roles (id)
);

create table admin_tokens (
    id uuid not null,
    token_hash varchar(64) not null,
    admin_user_id uuid,
    username varchar(64) not null,
    issued_at timestamp with time zone not null,
    expires_at timestamp with time zone not null,
    revoked_at timestamp with time zone,
    primary key (id),
    constraint uk_admin_tokens_token_hash unique (token_hash)
);

create table admin_audit_logs (
    id uuid not null,
    admin_username varchar(64) not null,
    action varchar(64) not null,
    target_type varchar(64) not null,
    target_id uuid not null,
    summary varchar(512) not null,
    created_at timestamp with time zone not null,
    primary key (id)
);

create table content_book_cache (
    book_id varchar(128) not null,
    title varchar(255) not null,
    filtered_title varchar(255) not null,
    cover_url varchar(1024),
    chapter_count integer not null,
    updated_at timestamp with time zone not null,
    primary key (book_id)
);

create table content_episode_cache (
    id uuid not null,
    book_id varchar(128) not null,
    filtered_title varchar(255) not null,
    episodes_json text not null,
    episode_count integer not null,
    refreshed_at timestamp with time zone not null,
    last_error varchar(512),
    primary key (id),
    constraint uk_content_episode_cache_book_title unique (book_id, filtered_title)
);

create table content_shelf_cache (
    shelf_type varchar(32) not null,
    books_json text not null,
    item_count integer not null,
    refreshed_at timestamp with time zone not null,
    last_error varchar(512),
    primary key (shelf_type)
);

create table watch_records (
    id uuid not null,
    user_id uuid not null,
    book_id varchar(128) not null,
    book_title varchar(255) not null,
    filtered_title varchar(255) not null,
    episode_num integer not null,
    chapter_id varchar(128) not null,
    position_seconds integer not null,
    duration_seconds integer not null,
    progress_percent integer not null,
    updated_at timestamp with time zone not null,
    primary key (id),
    constraint uk_watch_records_user_book_episode unique (user_id, book_id, episode_num)
);

create table point_accounts (
    id uuid not null,
    user_id uuid not null,
    balance integer not null,
    updated_at timestamp with time zone not null,
    primary key (id),
    constraint uk_point_accounts_user_id unique (user_id)
);

create table point_transactions (
    id uuid not null,
    user_id uuid not null,
    amount integer not null,
    balance_after integer not null,
    source varchar(64) not null,
    book_id varchar(128),
    episode_num integer,
    stage integer,
    reason varchar(255),
    created_at timestamp with time zone not null,
    primary key (id)
);

create table watch_reward_claims (
    id uuid not null,
    user_id uuid not null,
    book_id varchar(128) not null,
    episode_num integer not null,
    stage integer not null,
    created_at timestamp with time zone not null,
    primary key (id),
    constraint uk_watch_reward_claims_stage unique (user_id, book_id, episode_num, stage)
);

create table recharge_orders (
    id uuid not null,
    user_id uuid not null,
    order_no varchar(64) not null,
    amount_cents integer not null,
    point_amount integer not null,
    status varchar(32) not null,
    payment_channel varchar(64),
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null,
    primary key (id),
    constraint uk_recharge_orders_order_no unique (order_no)
);

create table payment_events (
    id uuid not null,
    provider_event_id varchar(128) not null,
    order_no varchar(64) not null,
    payment_channel varchar(64) not null,
    amount_cents integer not null,
    status varchar(32) not null,
    failure_reason varchar(255),
    created_at timestamp with time zone not null,
    processed_at timestamp with time zone not null,
    primary key (id),
    constraint uk_payment_events_provider_event_id unique (provider_event_id)
);

create table system_configs (
    config_key varchar(128) not null,
    config_value varchar(512) not null,
    description varchar(255) not null,
    updated_at timestamp with time zone not null,
    primary key (config_key)
);
