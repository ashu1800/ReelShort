CREATE TABLE vip_transfer_scan_cursors (
    id uuid NOT NULL,
    receiving_wallet_address varchar(128) NOT NULL,
    token_contract_address varchar(128) NOT NULL,
    fingerprint varchar(512),
    scan_window_started_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    primary key (id),
    constraint uk_vip_transfer_scan_cursor_snapshot
        unique (receiving_wallet_address, token_contract_address)
);
