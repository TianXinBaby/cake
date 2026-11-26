-- 仅在01建表、02索引成功后首次执行。目标MySQL 8.0.19；CHECK均显式ENFORCED。
-- 外键全部RESTRICT；不使用触发器/存储过程，不添加字段。
-- CHECK只保证单行当前形状；跨行总额、明细数量、角色归属、历史不可改写及状态流转须后续Service事务保障。

ALTER TABLE cake_merchant
    ADD CONSTRAINT fk_merchant_user FOREIGN KEY (user_id) REFERENCES sys_user (id)
        ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE cake_category
    ADD CONSTRAINT fk_category_merchant FOREIGN KEY (merchant_id) REFERENCES cake_merchant (id)
        ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE cake_product
    ADD CONSTRAINT fk_product_merchant FOREIGN KEY (merchant_id) REFERENCES cake_merchant (id)
        ON DELETE RESTRICT ON UPDATE RESTRICT,
    ADD CONSTRAINT fk_product_category_merchant FOREIGN KEY (category_id, merchant_id)
        REFERENCES cake_category (id, merchant_id) ON DELETE RESTRICT ON UPDATE RESTRICT;
-- InnoDB会为复合外键自动创建(category_id,merchant_id)支持索引；这是必要物理索引，非新增业务字段或查询索引。
ALTER TABLE cake_order
    ADD CONSTRAINT fk_order_merchant FOREIGN KEY (merchant_id) REFERENCES cake_merchant (id)
        ON DELETE RESTRICT ON UPDATE RESTRICT,
    ADD CONSTRAINT fk_order_user FOREIGN KEY (user_id) REFERENCES sys_user (id)
        ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE cake_order_item
    ADD CONSTRAINT fk_item_order FOREIGN KEY (order_id) REFERENCES cake_order (id)
        ON DELETE RESTRICT ON UPDATE RESTRICT,
    ADD CONSTRAINT fk_item_product FOREIGN KEY (product_id) REFERENCES cake_product (id)
        ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE sys_user
    ADD CONSTRAINT ck_user_role CHECK (CAST(role AS BINARY) IN ('ADMIN', 'MERCHANT', 'CUSTOMER')) ENFORCED,
    ADD CONSTRAINT ck_user_status CHECK (status IN (0, 1)) ENFORCED,
    ADD CONSTRAINT ck_user_token_version CHECK (token_version >= 0) ENFORCED,
    ADD CONSTRAINT ck_user_login_fail_count CHECK (login_fail_count BETWEEN 0 AND 5) ENFORCED,
    ADD CONSTRAINT ck_user_text CHECK (CHAR_LENGTH(TRIM(username)) BETWEEN 1 AND 20 AND CHAR_LENGTH(TRIM(nickname)) BETWEEN 1 AND 30) ENFORCED,
    ADD CONSTRAINT ck_user_phone CHECK (CHAR_LENGTH(phone) = 11 AND REGEXP_LIKE(phone, '^1[3-9][0-9]{9}$', 'c')) ENFORCED;

ALTER TABLE cake_merchant
    ADD CONSTRAINT ck_merchant_status CHECK (status IN (0, 1)) ENFORCED,
    ADD CONSTRAINT ck_merchant_text CHECK (CHAR_LENGTH(TRIM(name)) BETWEEN 1 AND 60 AND CHAR_LENGTH(TRIM(address)) BETWEEN 1 AND 120) ENFORCED,
    ADD CONSTRAINT ck_merchant_phone CHECK (CHAR_LENGTH(phone) = 11 AND REGEXP_LIKE(phone, '^1[3-9][0-9]{9}$', 'c')) ENFORCED;

ALTER TABLE cake_category
    ADD CONSTRAINT ck_category_status CHECK (status IN (0, 1)) ENFORCED,
    ADD CONSTRAINT ck_category_sort CHECK (sort >= 0) ENFORCED,
    ADD CONSTRAINT ck_category_name CHECK (CHAR_LENGTH(TRIM(name)) BETWEEN 1 AND 30) ENFORCED;

ALTER TABLE cake_product
    ADD CONSTRAINT ck_product_price CHECK (price > 0) ENFORCED,
    ADD CONSTRAINT ck_product_stock CHECK (stock BETWEEN 0 AND 2147483647) ENFORCED,
    ADD CONSTRAINT ck_product_status CHECK (status IN (0, 1)) ENFORCED,
    ADD CONSTRAINT ck_product_text CHECK (CHAR_LENGTH(TRIM(name)) BETWEEN 1 AND 60 AND CHAR_LENGTH(TRIM(spec)) BETWEEN 1 AND 30) ENFORCED;

ALTER TABLE cake_order
    ADD CONSTRAINT ck_order_amount CHECK (total_amount > 0) ENFORCED,
    ADD CONSTRAINT ck_order_status CHECK (status IN (0, 1, 2, 3, 4, 5, 6, 7)) ENFORCED,
    ADD CONSTRAINT ck_order_type CHECK (order_type IN (1, 2)) ENFORCED,
    ADD CONSTRAINT ck_order_text CHECK (CHAR_LENGTH(TRIM(merchant_name)) BETWEEN 1 AND 60 AND CHAR_LENGTH(TRIM(order_no)) BETWEEN 1 AND 32 AND CHAR_LENGTH(TRIM(receiver)) BETWEEN 1 AND 30) ENFORCED,
    ADD CONSTRAINT ck_order_phone CHECK (CHAR_LENGTH(phone) = 11 AND REGEXP_LIKE(phone, '^1[3-9][0-9]{9}$', 'c')) ENFORCED,
    ADD CONSTRAINT ck_order_address CHECK (
        (order_type = 1 AND address IS NOT NULL AND CHAR_LENGTH(TRIM(address)) BETWEEN 1 AND 120)
        OR (order_type = 2 AND address IS NULL)
    ) ENFORCED,
    ADD CONSTRAINT ck_order_expire CHECK (expire_time = DATE_ADD(create_time, INTERVAL 30 MINUTE)) ENFORCED,
    -- 每个分支均显式判断NULL，避免CHECK把UNKNOWN视为可接受。
    -- 已取消可来自未付款、待接单、待制作；保留已发生支付及接单，不允许制作后取消。
    ADD CONSTRAINT ck_order_state_events CHECK (
        (status = 0 AND pay_time IS NULL AND accept_time IS NULL AND start_time IS NULL
            AND delivery_time IS NULL AND ready_time IS NULL AND complete_time IS NULL
            AND cancel_time IS NULL AND cancel_reason IS NULL)
        OR (status = 7 AND pay_time IS NOT NULL AND accept_time IS NULL AND start_time IS NULL
            AND delivery_time IS NULL AND ready_time IS NULL AND complete_time IS NULL
            AND cancel_time IS NULL AND cancel_reason IS NULL)
        OR (status = 1 AND pay_time IS NOT NULL AND accept_time IS NOT NULL AND start_time IS NULL
            AND delivery_time IS NULL AND ready_time IS NULL AND complete_time IS NULL
            AND cancel_time IS NULL AND cancel_reason IS NULL)
        OR (status = 5 AND pay_time IS NOT NULL AND accept_time IS NOT NULL AND start_time IS NOT NULL
            AND delivery_time IS NULL AND ready_time IS NULL AND complete_time IS NULL
            AND cancel_time IS NULL AND cancel_reason IS NULL)
        OR (status = 2 AND order_type = 1 AND pay_time IS NOT NULL AND accept_time IS NOT NULL
            AND start_time IS NOT NULL AND delivery_time IS NOT NULL AND ready_time IS NULL
            AND complete_time IS NULL AND cancel_time IS NULL AND cancel_reason IS NULL)
        OR (status = 6 AND order_type = 2 AND pay_time IS NOT NULL AND accept_time IS NOT NULL
            AND start_time IS NOT NULL AND ready_time IS NOT NULL AND delivery_time IS NULL
            AND complete_time IS NULL AND cancel_time IS NULL AND cancel_reason IS NULL)
        OR (status = 3 AND pay_time IS NOT NULL AND accept_time IS NOT NULL AND start_time IS NOT NULL
            AND complete_time IS NOT NULL AND cancel_time IS NULL AND cancel_reason IS NULL
            AND ((order_type = 1 AND delivery_time IS NOT NULL AND ready_time IS NULL)
                OR (order_type = 2 AND ready_time IS NOT NULL AND delivery_time IS NULL)))
        OR (status = 4 AND cancel_time IS NOT NULL AND cancel_reason IS NOT NULL
            AND CHAR_LENGTH(TRIM(cancel_reason)) BETWEEN 1 AND 200
            AND start_time IS NULL AND delivery_time IS NULL AND ready_time IS NULL AND complete_time IS NULL
            AND (accept_time IS NULL OR pay_time IS NOT NULL))
    ) ENFORCED,
    -- 仅比较已经发生的事件；expire_time只限制付款，不限制接单/制作/完成/取消。
    ADD CONSTRAINT ck_order_event_times CHECK (
        (pay_time IS NULL OR (pay_time >= create_time AND pay_time < expire_time))
        AND (accept_time IS NULL OR (pay_time IS NOT NULL AND accept_time >= pay_time))
        AND (start_time IS NULL OR (accept_time IS NOT NULL AND start_time >= accept_time))
        AND (delivery_time IS NULL OR (start_time IS NOT NULL AND delivery_time >= start_time))
        AND (ready_time IS NULL OR (start_time IS NOT NULL AND ready_time >= start_time))
        AND (complete_time IS NULL OR
            (order_type = 1 AND delivery_time IS NOT NULL AND complete_time >= delivery_time)
            OR (order_type = 2 AND ready_time IS NOT NULL AND complete_time >= ready_time))
        AND (cancel_time IS NULL OR (cancel_time >= create_time
            AND (pay_time IS NULL OR cancel_time >= pay_time)
            AND (accept_time IS NULL OR cancel_time >= accept_time)))
    ) ENFORCED;

ALTER TABLE cake_order_item
    ADD CONSTRAINT ck_item_price CHECK (price > 0) ENFORCED,
    ADD CONSTRAINT ck_item_quantity CHECK (quantity BETWEEN 1 AND 99) ENFORCED,
    ADD CONSTRAINT ck_item_subtotal CHECK (subtotal > 0 AND subtotal = price * quantity) ENFORCED,
    ADD CONSTRAINT ck_item_snapshot CHECK (CHAR_LENGTH(TRIM(product_name)) BETWEEN 1 AND 60 AND CHAR_LENGTH(TRIM(product_spec)) BETWEEN 1 AND 30) ENFORCED;
