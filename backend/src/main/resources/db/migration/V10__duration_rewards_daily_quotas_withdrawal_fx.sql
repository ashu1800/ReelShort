create table point_daily_earning_rules (
    earning_date date not null,
    base_maximum integer not null,
    fluctuation_maximum_percent integer not null,
    created_at timestamp with time zone not null,
    primary key (earning_date)
);

create table point_daily_earning_quotas (
    id uuid not null,
    user_id uuid not null,
    earning_date date not null,
    fluctuation_percent integer not null,
    effective_maximum integer not null,
    earned_points integer not null,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null,
    primary key (id),
    constraint uk_point_daily_earning_quota_user_date unique (user_id, earning_date)
);

create index idx_point_daily_earning_quotas_date on point_daily_earning_quotas (earning_date);

create table watch_episode_reward_claims (
    id uuid not null,
    user_id uuid not null,
    book_id varchar(128) not null,
    episode_num integer not null,
    duration_seconds integer not null,
    calculated_points integer not null,
    awarded_points integer not null,
    created_at timestamp with time zone not null,
    primary key (id),
    constraint uk_watch_episode_reward_claim unique (user_id, book_id, episode_num)
);

insert into watch_episode_reward_claims (
    id, user_id, book_id, episode_num, duration_seconds, calculated_points, awarded_points, created_at
)
select ranked.id, ranked.user_id, ranked.book_id, ranked.episode_num, 0,
       ranked.awarded_points, ranked.awarded_points, ranked.created_at
from (
    select claims.id, claims.user_id, claims.book_id, claims.episode_num, claims.created_at,
           coalesce((
               select sum(transactions.amount)
               from point_transactions transactions
               where transactions.user_id = claims.user_id
                 and transactions.book_id = claims.book_id
                 and transactions.episode_num = claims.episode_num
                 and transactions.source = 'WATCH_REWARD'
           ), 0) as awarded_points,
           row_number() over (
               partition by claims.user_id, claims.book_id, claims.episode_num
               order by claims.created_at, claims.id
           ) as row_number_in_episode
    from watch_reward_claims claims
) ranked
where ranked.row_number_in_episode = 1;

insert into point_daily_earning_rules (earning_date, base_maximum, fluctuation_maximum_percent, created_at)
select current_date,
       coalesce((select cast(config_value as integer) from system_configs
                 where config_key = 'points.daily-earned.maximum'), 1000),
       coalesce((select cast(config_value as integer) from system_configs
                 where config_key = 'points.daily-earned.fluctuation-percent'), 35),
       current_timestamp
where not exists (select 1 from point_daily_earning_rules where earning_date = current_date);

insert into point_daily_earning_quotas (
    id, user_id, earning_date, fluctuation_percent, effective_maximum, earned_points, created_at, updated_at
)
select min(transactions.id),
       transactions.user_id,
       current_date,
       0,
       rule.base_maximum,
       cast(least(sum(transactions.amount), rule.base_maximum) as integer),
       current_timestamp,
       current_timestamp
from point_transactions transactions
join point_daily_earning_rules rule on rule.earning_date = current_date
where transactions.source = 'WATCH_REWARD'
  and transactions.created_at >= current_date
  and transactions.created_at < current_date + 1
  and rule.base_maximum > 0
group by transactions.user_id, rule.base_maximum
having sum(transactions.amount) > 0;

create table content_episode_runtime_cache (
    id uuid not null,
    book_id varchar(128) not null,
    episode_num integer not null,
    chapter_id varchar(128) not null,
    duration_seconds integer not null,
    refreshed_at timestamp with time zone not null,
    primary key (id),
    constraint uk_content_episode_runtime unique (book_id, episode_num, chapter_id)
);

alter table withdrawal_requests add column if not exists cny_per_point numeric(18, 8);
alter table withdrawal_requests add column if not exists cny_per_usd numeric(18, 8);
alter table withdrawal_requests add column if not exists minimum_usd numeric(18, 2);
