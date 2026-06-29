create table content_video_cache (
    id uuid not null,
    book_id varchar(128) not null,
    episode_num integer not null,
    filtered_title varchar(255) not null,
    chapter_id varchar(128) not null,
    video_json text not null,
    refreshed_at timestamp with time zone not null,
    last_error varchar(512),
    primary key (id),
    constraint uk_content_video_cache_key unique (book_id, episode_num, filtered_title, chapter_id)
);
