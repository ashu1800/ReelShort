-- 0001_init.sql —— 卡密服务初始 schema（SQLite）
-- 时间列全部为 unix 秒（UTC）；展示时由应用层转换为 Asia/Shanghai。

CREATE TABLE card_types (
    id               INTEGER PRIMARY KEY AUTOINCREMENT,
    name             TEXT    NOT NULL UNIQUE,
    duration_minutes INTEGER NOT NULL DEFAULT 0,   -- 0 = 永久
    max_rebinds      INTEGER NOT NULL DEFAULT 3,
    enabled          INTEGER NOT NULL DEFAULT 1,
    sort_order       INTEGER NOT NULL DEFAULT 0,
    created_at       INTEGER NOT NULL,
    updated_at       INTEGER NOT NULL
);

CREATE TABLE cards (
    id                INTEGER PRIMARY KEY AUTOINCREMENT,
    code              TEXT    NOT NULL UNIQUE,      -- 规范形：大写、无分隔符
    type_id           INTEGER REFERENCES card_types(id),
    type_name         TEXT    NOT NULL DEFAULT '',  -- 快照，类型改名/删除不影响历史卡
    duration_minutes  INTEGER NOT NULL DEFAULT 0,
    state             TEXT    NOT NULL DEFAULT 'unused' CHECK (state IN ('unused','active','disabled')),
    max_rebinds       INTEGER NOT NULL DEFAULT 3,
    rebind_count      INTEGER NOT NULL DEFAULT 0,
    bound_machine     TEXT    NOT NULL DEFAULT '',
    bound_at          INTEGER NOT NULL DEFAULT 0,
    activated_at      INTEGER NOT NULL DEFAULT 0,
    expires_at        INTEGER,                      -- NULL = 未激活或永久
    online_session_id INTEGER,
    online_machine    TEXT    NOT NULL DEFAULT '',
    online_at         INTEGER NOT NULL DEFAULT 0,
    batch_no          TEXT    NOT NULL DEFAULT '',
    remark            TEXT    NOT NULL DEFAULT '',
    created_by        TEXT    NOT NULL DEFAULT '',
    created_at        INTEGER NOT NULL,
    updated_at        INTEGER NOT NULL,
    deleted_at        INTEGER
);

CREATE INDEX idx_cards_state          ON cards(state);
CREATE INDEX idx_cards_expires_at     ON cards(expires_at);
CREATE INDEX idx_cards_batch_no       ON cards(batch_no);
CREATE INDEX idx_cards_bound_machine  ON cards(bound_machine);
CREATE INDEX idx_cards_created_at     ON cards(created_at DESC);
CREATE INDEX idx_cards_type_id        ON cards(type_id);

CREATE TABLE card_sessions (
    id                INTEGER PRIMARY KEY AUTOINCREMENT,
    token_hash        TEXT    NOT NULL UNIQUE,      -- 只存 SHA-256，明文令牌从不落库
    card_id           INTEGER NOT NULL REFERENCES cards(id),
    machine_code      TEXT    NOT NULL,
    ip                TEXT    NOT NULL DEFAULT '',
    user_agent        TEXT    NOT NULL DEFAULT '',
    created_at        INTEGER NOT NULL,
    last_heartbeat_at INTEGER NOT NULL,
    expires_at        INTEGER NOT NULL,
    closed_at         INTEGER,
    close_reason      TEXT    NOT NULL DEFAULT ''
);

CREATE INDEX idx_sessions_card     ON card_sessions(card_id, closed_at);
CREATE INDEX idx_sessions_expires  ON card_sessions(expires_at);
CREATE INDEX idx_sessions_closed   ON card_sessions(closed_at);

CREATE TABLE card_logs (
    id           INTEGER PRIMARY KEY AUTOINCREMENT,
    card_id      INTEGER,
    code_masked  TEXT    NOT NULL DEFAULT '',
    action       TEXT    NOT NULL,
    result       TEXT    NOT NULL,
    message      TEXT    NOT NULL DEFAULT '',
    machine_code TEXT    NOT NULL DEFAULT '',
    ip           TEXT    NOT NULL DEFAULT '',
    user_agent   TEXT    NOT NULL DEFAULT '',
    created_at   INTEGER NOT NULL
);

CREATE INDEX idx_logs_created ON card_logs(created_at DESC);
CREATE INDEX idx_logs_card    ON card_logs(card_id, created_at DESC);
CREATE INDEX idx_logs_action  ON card_logs(action, created_at DESC);

CREATE TABLE admin_users (
    id                   INTEGER PRIMARY KEY AUTOINCREMENT,
    username             TEXT    NOT NULL UNIQUE,
    password_hash        TEXT    NOT NULL,
    display_name         TEXT    NOT NULL DEFAULT '',
    must_change_password INTEGER NOT NULL DEFAULT 0,
    created_at           INTEGER NOT NULL,
    updated_at           INTEGER NOT NULL,
    last_login_at        INTEGER NOT NULL DEFAULT 0
);

CREATE TABLE admin_sessions (
    id           INTEGER PRIMARY KEY AUTOINCREMENT,
    token_hash   TEXT    NOT NULL UNIQUE,
    admin_id     INTEGER NOT NULL REFERENCES admin_users(id),
    ip           TEXT    NOT NULL DEFAULT '',
    user_agent   TEXT    NOT NULL DEFAULT '',
    created_at   INTEGER NOT NULL,
    expires_at   INTEGER NOT NULL,
    last_seen_at INTEGER NOT NULL
);

CREATE INDEX idx_admin_sessions_admin   ON admin_sessions(admin_id);
CREATE INDEX idx_admin_sessions_expires ON admin_sessions(expires_at);

CREATE TABLE admin_audit_logs (
    id          INTEGER PRIMARY KEY AUTOINCREMENT,
    admin_id    INTEGER NOT NULL,
    username    TEXT    NOT NULL DEFAULT '',
    action      TEXT    NOT NULL,
    target_type TEXT    NOT NULL DEFAULT '',
    target_id   TEXT    NOT NULL DEFAULT '',
    detail      TEXT    NOT NULL DEFAULT '',
    ip          TEXT    NOT NULL DEFAULT '',
    created_at  INTEGER NOT NULL
);

CREATE INDEX idx_audit_created ON admin_audit_logs(created_at DESC);
CREATE INDEX idx_audit_admin   ON admin_audit_logs(admin_id, created_at DESC);

CREATE TABLE settings (
    key        TEXT PRIMARY KEY,
    value      TEXT NOT NULL,
    updated_at INTEGER NOT NULL
);
