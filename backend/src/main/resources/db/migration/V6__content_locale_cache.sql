create table content_book_cache_v6 (
    id varchar(36) not null,
    book_id varchar(128) not null,
    locale varchar(32) not null,
    title varchar(255) not null,
    filtered_title varchar(255) not null,
    cover_url varchar(1024),
    description text not null,
    chapter_count integer not null,
    updated_at timestamp with time zone not null,
    primary key (id),
    constraint uk_content_book_cache_book_locale unique (book_id, locale)
);

insert into content_book_cache_v6 (
    id, book_id, locale, title, filtered_title, cover_url, description, chapter_count, updated_at
)
select substring(book_id, 1, 24) || '-' || row_number() over (order by book_id),
    book_id,
    'ENGLISH',
    title,
    filtered_title,
    cover_url,
    description,
    chapter_count,
    updated_at
from content_book_cache;

drop table content_book_cache;
alter table content_book_cache_v6 rename to content_book_cache;

create table content_shelf_cache_v6 (
    id varchar(80) not null,
    shelf_type varchar(32) not null,
    locale varchar(32) not null,
    books_json text not null,
    item_count integer not null,
    refreshed_at timestamp with time zone not null,
    last_error varchar(512),
    primary key (id),
    constraint uk_content_shelf_cache_shelf_locale unique (shelf_type, locale)
);

insert into content_shelf_cache_v6 (
    id, shelf_type, locale, books_json, item_count, refreshed_at, last_error
)
select shelf_type, shelf_type, 'ENGLISH', books_json, item_count, refreshed_at, last_error
from content_shelf_cache;

drop table content_shelf_cache;
alter table content_shelf_cache_v6 rename to content_shelf_cache;

create table content_episode_cache_v6 (
    id uuid not null,
    book_id varchar(128) not null,
    filtered_title varchar(255) not null,
    locale varchar(32) not null,
    episodes_json text not null,
    episode_count integer not null,
    refreshed_at timestamp with time zone not null,
    last_error varchar(512),
    primary key (id),
    constraint uk_content_episode_cache_book_title_locale unique (book_id, filtered_title, locale)
);

insert into content_episode_cache_v6 (
    id, book_id, filtered_title, locale, episodes_json, episode_count, refreshed_at, last_error
)
select id, book_id, filtered_title, 'ENGLISH', episodes_json, episode_count, refreshed_at, last_error
from content_episode_cache;

drop table content_episode_cache;
alter table content_episode_cache_v6 rename to content_episode_cache;

create table content_video_cache_v6 (
    id uuid not null,
    book_id varchar(128) not null,
    episode_num integer not null,
    filtered_title varchar(255) not null,
    chapter_id varchar(128) not null,
    locale varchar(32) not null,
    video_json text not null,
    refreshed_at timestamp with time zone not null,
    last_error varchar(512),
    primary key (id),
    constraint uk_content_video_cache_key_locale unique (book_id, episode_num, filtered_title, chapter_id, locale)
);

insert into content_video_cache_v6 (
    id, book_id, episode_num, filtered_title, chapter_id, locale, video_json, refreshed_at, last_error
)
select id, book_id, episode_num, filtered_title, chapter_id, 'ENGLISH', video_json, refreshed_at, last_error
from content_video_cache;

drop table content_video_cache;
alter table content_video_cache_v6 rename to content_video_cache;
