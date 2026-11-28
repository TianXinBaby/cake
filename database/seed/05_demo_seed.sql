-- 课程演示种子（可提交，非生产数据）。
-- 前置：MySQL 8.0.x，已按 database/schema、indexes、constraints 初始化六张表。
-- 演示账号：13900000001（MERCHANT）。密码不写入仓库，由项目负责人在小组内单独提供。
-- 账号密码使用 BCrypt 哈希存储；请勿把本文件用于公网或生产环境。
-- 若演示账号已存在，本脚本会在第一条插入处失败并停止，不会覆盖已有数据。

SET NAMES utf8mb4;
START TRANSACTION;

INSERT INTO sys_user (username, password, nickname, phone, role, status, token_version, login_fail_count)
VALUES ('13900000001',
        '$2a$10$Dx4UAGa6u0O3wyKXMUcwUOTG52BH.DwQBjmYYG48CJDSfBrnEpt9e',
        '奶油星球店主', '13900000001', 'MERCHANT', 1, 0, 0);
SET @uid = LAST_INSERT_ID();

INSERT INTO cake_merchant (user_id, name, phone, address, description, status)
VALUES (@uid, '奶油星球', '18800188800', '星空',
        '主打创意动物奶油蛋糕，坚持每日新鲜现做，打造舌尖上的甜蜜小星球。', 1);
SET @shop = LAST_INSERT_ID();

INSERT INTO cake_category (merchant_id, name, sort, status) VALUES
(@shop, '奶油蛋糕', 0, 1),
(@shop, '慕斯蛋糕', 1, 1),
(@shop, '芝士蛋糕', 2, 1),
(@shop, '节日限定', 3, 1);

SET @cream = (SELECT id FROM cake_category WHERE merchant_id = @shop AND name = '奶油蛋糕');
SET @mousse = (SELECT id FROM cake_category WHERE merchant_id = @shop AND name = '慕斯蛋糕');
SET @cheese = (SELECT id FROM cake_category WHERE merchant_id = @shop AND name = '芝士蛋糕');
SET @festival = (SELECT id FROM cake_category WHERE merchant_id = @shop AND name = '节日限定');

INSERT INTO cake_product (merchant_id, category_id, name, spec, price, stock, image, description, status) VALUES
(@shop, @cream,    '经典草莓奶油蛋糕', '6寸',  128.00, 32, '/images/cake-01.png', '新鲜草莓搭配动物淡奶油，口感清甜不腻', 1),
(@shop, @cream,    '巧克力熔岩蛋糕',   '4寸',   68.00, 15, '/images/cake-02.png', '浓郁黑巧流心，加热后口感更佳', 1),
(@shop, @mousse,   '芒果慕斯蛋糕',     '6寸',  138.00, 24, '/images/cake-03.png', '新鲜芒果果茸制作，入口即化解腻', 1),
(@shop, @mousse,   '提拉米苏',         '4寸',   78.00, 18, '/images/cake-04.png', '意式经典配方，咖啡酒香搭配马斯卡彭奶酪', 1),
(@shop, @cheese,   '原味重芝士蛋糕',   '6寸',  118.00,  0, '/images/cake-05.png', '新西兰进口芝士，口感绵密浓郁', 1),
(@shop, @cream,    '红丝绒蛋糕',       '6寸',  148.00, 10, '/images/cake-06.png', '红丝绒胚搭配奶油芝士霜，高颜值人气款', 0),
(@shop, @festival, '圣诞姜饼限定蛋糕', '6寸',  168.00,  5, '/images/cake-07.png', '节日专属款，姜饼风味奶油顶', 1),
(@shop, @cream,    '荔浦芋泥麻薯千层', '6寸',  138.00, 28, '/images/cake-08.png', '手工现煎千层皮，搭配绵密荔浦芋泥与Q弹麻薯', 1);

COMMIT;

SELECT @shop AS merchant_id, @uid AS user_id,
       (SELECT COUNT(*) FROM cake_product WHERE merchant_id = @shop) AS product_count,
       (SELECT COUNT(*) FROM cake_category WHERE merchant_id = @shop) AS category_count,
       (SELECT COUNT(*) FROM cake_product WHERE merchant_id = @shop AND status = 1) AS on_sale_count;
