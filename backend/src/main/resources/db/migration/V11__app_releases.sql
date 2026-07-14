create table if not exists app_releases (
    id uuid not null,
    version_name varchar(32) not null,
    version_code bigint not null,
    apk_object_key varchar(512) not null,
    sha256_object_key varchar(512) not null,
    apk_size_bytes bigint not null,
    sha256_size_bytes bigint not null,
    apk_sha256 varchar(64) not null,
    title varchar(255) not null,
    release_notes varchar(2000) not null,
    mandatory boolean not null,
    minimum_version_code bigint not null,
    published_at timestamp with time zone not null,
    created_at timestamp with time zone not null,
    primary key (id)
);

create unique index if not exists uk_app_releases_version_name
    on app_releases (version_name);

create index if not exists idx_app_releases_version_code_published
    on app_releases (version_code desc, published_at desc);
