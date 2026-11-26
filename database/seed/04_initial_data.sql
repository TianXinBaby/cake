-- Parameterized seed template: execute ONLY with database/tools/DatabaseTool.java seed.
-- :parameters are JDBC bound values, not textual SQL substitution or MySQL session variables.
-- Caller checks all six tables empty, validates supplied values and owns one commit/rollback.
-- No default account passwords, hashes, phone numbers or invented merchant details.
INSERT INTO sys_user(username,password,nickname,phone,role)
VALUES (:admin_phone,:admin_hash,:admin_nickname,:admin_phone,'ADMIN');

INSERT INTO sys_user(username,password,nickname,phone,role)
VALUES (:merchant_phone,:merchant_hash,:merchant_nickname,:merchant_phone,'MERCHANT');

INSERT INTO cake_merchant(user_id,name,phone,address)
SELECT id,:shop_name,:shop_phone,:shop_address FROM sys_user WHERE username=:merchant_phone;

INSERT INTO cake_category(merchant_id,name)
SELECT m.id,:category_name FROM cake_merchant m
JOIN sys_user u ON u.id=m.user_id WHERE u.username=:merchant_phone;

INSERT INTO cake_product(merchant_id,category_id,name,spec,price,stock)
SELECT m.id,c.id,:product_name,:product_spec,:product_price,:product_stock
FROM cake_merchant m JOIN sys_user u ON u.id=m.user_id
JOIN cake_category c ON c.merchant_id=m.id AND c.name=:category_name
WHERE u.username=:merchant_phone;
