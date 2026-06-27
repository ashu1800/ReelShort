create table system_alerts (
    id uuid not null,
    alert_key varchar(160) not null,
    severity varchar(32) not null,
    status varchar(32) not null,
    title varchar(255) not null,
    detail varchar(512) not null,
    first_seen_at timestamp with time zone not null,
    last_seen_at timestamp with time zone not null,
    acknowledged_at timestamp with time zone,
    acknowledged_by varchar(64),
    resolved_at timestamp with time zone,
    primary key (id),
    constraint uk_system_alerts_alert_key unique (alert_key)
);
