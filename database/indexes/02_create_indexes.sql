-- 仅在01_create_tables.sql成功后首次执行；原文4.4全部14个普通索引，保持列顺序。
-- 不重复执行，不自动删除或重建现有索引。
CREATE INDEX idx_merchant_status ON cake_merchant (status, id);
CREATE INDEX idx_category_merchant_status_sort ON cake_category (merchant_id, status, sort, id);
CREATE INDEX idx_product_merchant_category_status ON cake_product (merchant_id, category_id, status, id);
CREATE INDEX idx_product_merchant_status ON cake_product (merchant_id, status, id);
CREATE INDEX idx_order_user_status_create ON cake_order (user_id, status, create_time, id);
CREATE INDEX idx_order_user_create ON cake_order (user_id, create_time, id);
CREATE INDEX idx_order_merchant_status_create ON cake_order (merchant_id, status, create_time, id);
CREATE INDEX idx_order_merchant_create ON cake_order (merchant_id, create_time, id);
CREATE INDEX idx_order_merchant_status_complete ON cake_order (merchant_id, status, complete_time, id);
CREATE INDEX idx_order_create ON cake_order (create_time, id);
CREATE INDEX idx_order_status_create ON cake_order (status, create_time, id);
CREATE INDEX idx_order_status_complete ON cake_order (status, complete_time, id);
CREATE INDEX idx_order_status_expire ON cake_order (status, expire_time, id);
CREATE INDEX idx_item_product ON cake_order_item (product_id);
