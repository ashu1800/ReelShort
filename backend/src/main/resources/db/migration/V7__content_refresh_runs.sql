create table content_refresh_runs (
    id uuid not null,
    trigger_source varchar(32) not null,
    shelf_type varchar(32) not null,
    locale varchar(32) not null,
    status varchar(32) not null,
    started_at timestamp with time zone not null,
    finished_at timestamp with time zone not null,
    duration_millis bigint not null,
    item_count integer not null,
    error_message varchar(1024),
    primary key (id)
);

create index idx_content_refresh_runs_started_at on content_refresh_runs (started_at desc);
create index idx_content_refresh_runs_shelf_locale on content_refresh_runs (shelf_type, locale);
