-- 点赞、收藏、评论（单级文字评论）。内容引用沿用 watch_records 的 book_id varchar(128) 约定。
create table likes (
    id uuid not null,
    user_id uuid not null,
    book_id varchar(128) not null,
    created_at timestamp with time zone not null,
    primary key (id),
    constraint uk_likes_user_book unique (user_id, book_id)
);

create table favorites (
    id uuid not null,
    user_id uuid not null,
    book_id varchar(128) not null,
    book_title varchar(255) not null,
    filtered_title varchar(255) not null,
    cover_url varchar(512),
    chapter_count integer not null,
    created_at timestamp with time zone not null,
    primary key (id),
    constraint uk_favorites_user_book unique (user_id, book_id)
);

create table comments (
    id uuid not null,
    user_id uuid not null,
    username varchar(64) not null,
    book_id varchar(128) not null,
    content varchar(500) not null,
    created_at timestamp with time zone not null,
    primary key (id)
);

create index idx_comments_book_created on comments (book_id, created_at desc);
