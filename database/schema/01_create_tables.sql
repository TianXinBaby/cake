-- Phase 2 / MySQL 8.0.19 / 仅首次初始化。
-- 先由受控初始化器选择已确认的空数据库，设置会话 time_zone='+08:00'、严格 SQL 模式。
-- __CAKE_DB_COLLATION__ 必须由初始化器校验并替换；2026-11-25 用户确认 utf8mb4_0900_as_cs。
-- 顺序：01_create_tables -> 02_create_indexes -> 03_data_constraints -> 可选 04_initial_data。
-- 主键与唯一键先创建，以满足后续复合外键。无 DROP、DELETE、TRUNCATE、自动覆盖或忽略已存在表。
-- update_time 仅设初始默认值；后续合法操作由应用显式写入，无 ON UPDATE 自动改写。

CREATE TABLE sys_user (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '用户主键',
    username VARCHAR(20) NOT NULL COMMENT '手机号登录账号，本版不可修改',
    password VARCHAR(100) NOT NULL COMMENT '仅存 BCrypt 散列，不返回接口',
    nickname VARCHAR(30) NOT NULL COMMENT '显示昵称',
    phone VARCHAR(20) NOT NULL COMMENT '账号手机号，注册时等于 username',
    role VARCHAR(10) NOT NULL DEFAULT 'CUSTOMER' COMMENT 'ADMIN/MERCHANT/CUSTOMER',
    status TINYINT NOT NULL DEFAULT 1 COMMENT '0禁用，1启用',
    token_version INT NOT NULL DEFAULT 0 COMMENT 'JWT 版本',
    login_fail_count INT NOT NULL DEFAULT 0 COMMENT '连续登录失败次数0至5',
    locked_until DATETIME(3) NULL DEFAULT NULL COMMENT '临时锁定截止时间',
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '最后修改时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_username (username),
    UNIQUE KEY uk_user_phone (phone)
) ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4 COLLATE=__CAKE_DB_COLLATION__ COMMENT='用户表';

CREATE TABLE cake_merchant (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '商家主键',
    user_id BIGINT UNSIGNED NOT NULL COMMENT '唯一绑定的 MERCHANT 账号',
    name VARCHAR(60) NOT NULL COMMENT '店铺名称',
    phone VARCHAR(20) NOT NULL COMMENT '店铺业务联系电话',
    address VARCHAR(120) NOT NULL COMMENT '实体店铺地址',
    description VARCHAR(500) NULL DEFAULT NULL COMMENT '店铺简介',
    status TINYINT NOT NULL DEFAULT 1 COMMENT '0停用，1启用',
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '最后修改时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_merchant_user (user_id)
) ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4 COLLATE=__CAKE_DB_COLLATION__ COMMENT='商家表';

CREATE TABLE cake_category (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '分类主键',
    merchant_id BIGINT UNSIGNED NOT NULL COMMENT '所属商家，创建后不可转店',
    name VARCHAR(30) NOT NULL COMMENT '本店唯一的分类名称',
    sort INT NOT NULL DEFAULT 0 COMMENT '非负展示排序',
    status TINYINT NOT NULL DEFAULT 1 COMMENT '0禁用，1启用',
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '最后修改时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_category_merchant_name (merchant_id, name),
    UNIQUE KEY uk_category_id_merchant (id, merchant_id)
) ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4 COLLATE=__CAKE_DB_COLLATION__ COMMENT='商品分类表';

CREATE TABLE cake_product (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '具体规格商品主键',
    merchant_id BIGINT UNSIGNED NOT NULL COMMENT '所属商家，禁止转店',
    category_id BIGINT UNSIGNED NOT NULL COMMENT '本店分类',
    name VARCHAR(60) NOT NULL COMMENT '商品名称',
    spec VARCHAR(30) NOT NULL COMMENT '具体规格，无尺寸填标准份',
    price DECIMAL(10,2) NOT NULL COMMENT '人民币元，单价大于0',
    stock INT NOT NULL DEFAULT 0 COMMENT '可售库存0至2147483647',
    image VARCHAR(255) NULL DEFAULT NULL COMMENT '已有图片路径或URL',
    description VARCHAR(500) NULL DEFAULT NULL COMMENT '商品纯文本说明',
    status TINYINT NOT NULL DEFAULT 0 COMMENT '0下架，1上架；新商品默认下架',
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '最后修改、扣减或回补时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_product_id_merchant (id, merchant_id)
) ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4 COLLATE=__CAKE_DB_COLLATION__ COMMENT='商品表';

CREATE TABLE cake_order (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '订单主键',
    merchant_id BIGINT UNSIGNED NOT NULL COMMENT '所选商家，创建后不变',
    merchant_name VARCHAR(60) NOT NULL COMMENT '下单时店铺名称快照',
    order_no VARCHAR(32) NOT NULL COMMENT '后端生成的唯一业务订单号',
    user_id BIGINT UNSIGNED NOT NULL COMMENT '下单 CUSTOMER 账号',
    request_id CHAR(36) NOT NULL COMMENT '客户端UUID下单请求号',
    request_hash CHAR(64) NOT NULL COMMENT '规范化请求SHA256，不含数据库价格或状态',
    total_amount DECIMAL(10,2) NOT NULL COMMENT '后端计算的明细金额合计',
    status TINYINT NOT NULL DEFAULT 0 COMMENT '0待付款7待接单1待制作5制作中2配送中6待自提3完成4取消',
    order_type TINYINT NOT NULL COMMENT '1配送，2到店自提',
    receiver VARCHAR(30) NOT NULL COMMENT '联系人姓名快照',
    phone VARCHAR(20) NOT NULL COMMENT '联系电话快照',
    address VARCHAR(120) NULL DEFAULT NULL COMMENT '配送地址快照，自提为NULL',
    remark VARCHAR(200) NULL DEFAULT NULL COMMENT '本次下单备注',
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '订单创建时间',
    expire_time DATETIME(3) NOT NULL COMMENT '同一数据库创建时间加30分钟',
    pay_time DATETIME(3) NULL DEFAULT NULL COMMENT '首次模拟支付成功时间',
    accept_time DATETIME(3) NULL DEFAULT NULL COMMENT '首次商家接单时间',
    start_time DATETIME(3) NULL DEFAULT NULL COMMENT '开始制作时间',
    delivery_time DATETIME(3) NULL DEFAULT NULL COMMENT '配送出店时间，仅配送',
    ready_time DATETIME(3) NULL DEFAULT NULL COMMENT '制作备妥时间，仅自提',
    complete_time DATETIME(3) NULL DEFAULT NULL COMMENT '完成时间，销售统计基准',
    cancel_time DATETIME(3) NULL DEFAULT NULL COMMENT '取消生效时间',
    cancel_reason VARCHAR(200) NULL DEFAULT NULL COMMENT '取消原因，取消状态必填',
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '最后合法状态变更时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_order_no (order_no),
    UNIQUE KEY uk_order_user_request (user_id, request_id)
) ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4 COLLATE=__CAKE_DB_COLLATION__ COMMENT='订单表';

CREATE TABLE cake_order_item (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '明细主键',
    order_id BIGINT UNSIGNED NOT NULL COMMENT '所属订单',
    product_id BIGINT UNSIGNED NOT NULL COMMENT '原商品ID',
    product_name VARCHAR(60) NOT NULL COMMENT '下单商品名称快照',
    product_spec VARCHAR(30) NOT NULL COMMENT '下单商品规格快照',
    price DECIMAL(10,2) NOT NULL COMMENT '下单单价快照',
    quantity INT NOT NULL COMMENT '合并后购买数量1至99',
    subtotal DECIMAL(10,2) NOT NULL COMMENT '单价乘数量，后端精确计算',
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '与主表同事务建立时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_item_order_product (order_id, product_id)
) ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4 COLLATE=__CAKE_DB_COLLATION__ COMMENT='订单明细表';
