CREATE TABLE vip_order_allocation_lock (
    id smallint NOT NULL,
    primary key (id),
    constraint ck_vip_order_allocation_lock_singleton check (id = 1)
);

INSERT INTO vip_order_allocation_lock (id)
SELECT 1
WHERE NOT EXISTS (SELECT 1 FROM vip_order_allocation_lock WHERE id = 1);

ALTER TABLE vip_orders ADD CONSTRAINT ck_vip_orders_base_scale
    check (base_usdt_amount IS NULL OR base_usdt_amount = round(base_usdt_amount, 6));
ALTER TABLE vip_orders ADD CONSTRAINT ck_vip_orders_payable_scale
    check (payable_usdt_amount IS NULL OR payable_usdt_amount = round(payable_usdt_amount, 6));
