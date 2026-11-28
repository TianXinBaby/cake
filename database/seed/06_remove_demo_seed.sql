-- 删除 05_demo_seed.sql 建立的演示账号、店铺、分类、商品及其演示订单。
-- 只按固定演示账号 13900000001 清理，不触碰其他账号或店铺；禁止在共享/生产环境执行。

SET NAMES utf8mb4;
START TRANSACTION;

SET @uid = (SELECT id FROM sys_user WHERE username = '13900000001' AND role = 'MERCHANT');
SET @shop = (SELECT id FROM cake_merchant WHERE user_id = @uid);

DELETE FROM cake_order_item
WHERE order_id IN (SELECT id FROM cake_order WHERE merchant_id = @shop);
DELETE FROM cake_order WHERE merchant_id = @shop;
DELETE FROM cake_product WHERE merchant_id = @shop;
DELETE FROM cake_category WHERE merchant_id = @shop;
DELETE FROM cake_merchant WHERE id = @shop;
DELETE FROM sys_user WHERE id = @uid;

COMMIT;

SELECT @uid AS removed_user_id, @shop AS removed_merchant_id;
