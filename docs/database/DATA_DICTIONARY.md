# Phase 2 数据字典

核对日期：2026-11-25。唯一业务来源为根目录《蛋糕店订单管理系统需求说明书_V1.1.docx》第 4.3 节；本文件的 74 行字段说明直接从当前 DOCX 六张数据字典表只读提取，SQL 类型按原文长度与 UNSIGNED 标记合并展示。原始 DOCX 未修改。

本轮用户授权 Phase 2；本文件记录结构设计与实际脚本定义，数据库执行与测试结果以 [阶段报告](../../reports/PHASE_2_REPORT.md) 为准，字典存在不代表真实数据库验证通过。

| 表 | 字段数 | 原文节号 |
| --- | --- | --- |
| sys_user | 12 | 4.3.1 |
| cake_merchant | 9 | 4.3.6 |
| cake_category | 7 | 4.3.2 |
| cake_product | 12 | 4.3.3 |
| cake_order | 25 | 4.3.4 |
| cake_order_item | 9 | 4.3.5 |
| 合计 | 74 | 六张核心表，无额外业务字段 |

通用定义：全部表为 InnoDB、utf8mb4；2026-11-25 用户确认排序规则 `utf8mb4_0900_as_cs`。创建脚本保留 `__CAKE_DB_COLLATION__`，只允许受控初始化器以确认值替换；未提供排序规则不得执行初始化。数据库、JDBC 会话按北京时间解释；所有日期时间列 `DATETIME(3)` 保留毫秒，默认“当前时间”实现为 `CURRENT_TIMESTAMP(3)`。

`无` 表示未设置默认值且插入必须显式提供；`NULL` 是数据库真实空值；`AUTO_INCREMENT` 只用于六个主键。所有 ID 及外键为 `BIGINT UNSIGNED`，未将其缩窄为有符号类型；接口后续使用正十进制字符串，Java 全范围映射验证是独立事项。

`update_time` 仅有创建默认值，不设置 `ON UPDATE` 自动改写：后续应用在合法业务修改时显式刷新，重复设置商品/分类/店铺状态及订单终态不得因此被自动改写。`expire_time` 没有默认值，由后续应用基于同一数据库创建时刻加 30 分钟提供，CHECK 校验等式。

## sys_user

| 字段 | SQL 类型 | 可空 | 默认值 | 原文约束 | 原文字段说明 |
| --- | --- | --- | --- | --- | --- |
| `id` | `BIGINT UNSIGNED` | 否 | `AUTO_INCREMENT` | PK，UNSIGNED | 用户主键。 |
| `username` | `VARCHAR(20)` | 否 | `无` | UK；注册手机号 | 登录账号，注册时等于 phone；本版不可修改。 |
| `password` | `VARCHAR(100)` | 否 | `无` | BCrypt 散列 | 只存带盐散列，不存明文或可逆密文；不向接口返回。 |
| `nickname` | `VARCHAR(30)` | 否 | `无` | 1～30 字符 | 显示昵称，允许本人修改。 |
| `phone` | `VARCHAR(20)` | 否 | `无` | UK；大陆手机号 | 等于注册账号，本版只读；订单电话为独立快照。 |
| `role` | `VARCHAR(10)` | 否 | `'CUSTOMER'` | ADMIN、MERCHANT 或 CUSTOMER | ADMIN 初始化创建；MERCHANT 由平台创建并绑定店铺；公开注册固定 CUSTOMER。 |
| `status` | `TINYINT` | 否 | `1` | 0 禁用；1 启用 | 禁用立即阻止登录与受保护请求。 |
| `token_version` | `INT` | 否 | `0` | ≥0 | JWT 版本；退出或改密码时原子加 1，原令牌失效。 |
| `login_fail_count` | `INT` | 否 | `0` | 0～5 | 连续失败次数；成功登录清零，锁定结束后开始新一轮。 |
| `locked_until` | `DATETIME(3)` | 是 | `NULL` | 临时锁定截止时间 | 第五次失败设当前时间加 10 分钟；不等于账号禁用。 |
| `create_time` | `DATETIME(3)` | 否 | `CURRENT_TIMESTAMP(3)` | 创建时写入 | 账号创建时间，此后不变。 |
| `update_time` | `DATETIME(3)` | 否 | `CURRENT_TIMESTAMP(3)` | 变更时刷新 | 资料、密码、状态或登录控制字段更新的时间。 |

## cake_merchant

| 字段 | SQL 类型 | 可空 | 默认值 | 原文约束 | 原文字段说明 |
| --- | --- | --- | --- | --- | --- |
| `id` | `BIGINT UNSIGNED` | 否 | `AUTO_INCREMENT` | PK，UNSIGNED | 商家主键。 |
| `user_id` | `BIGINT UNSIGNED` | 否 | `无` | FK，UK，UNSIGNED | 关联角色为 MERCHANT 的 sys_user.id；一账号一店铺，创建后不换绑。 |
| `name` | `VARCHAR(60)` | 否 | `无` | 1～60 字符 | 店铺名称；不同商家允许同名，以 id 区分。 |
| `phone` | `VARCHAR(20)` | 否 | `无` | 大陆手机号 | 店铺业务联系电话，不等同于商家账号手机号。 |
| `address` | `VARCHAR(120)` | 否 | `无` | 1～120 字符 | 实体店铺地址，供用户选择及到店自提。 |
| `description` | `VARCHAR(500)` | 是 | `NULL` | 最多 500 字符 | 店铺简介，纯文本展示。 |
| `status` | `TINYINT` | 否 | `1` | 0 停用；1 启用 | 停用后不公开展示、不接新单；保留账号与历史订单履约。 |
| `create_time` | `DATETIME(3)` | 否 | `CURRENT_TIMESTAMP(3)` | 创建时写入 | 商家创建时间。 |
| `update_time` | `DATETIME(3)` | 否 | `CURRENT_TIMESTAMP(3)` | 变更时刷新 | 店铺信息或状态最后修改时间。 |

## cake_category

| 字段 | SQL 类型 | 可空 | 默认值 | 原文约束 | 原文字段说明 |
| --- | --- | --- | --- | --- | --- |
| `id` | `BIGINT UNSIGNED` | 否 | `AUTO_INCREMENT` | PK，UNSIGNED | 分类主键。 |
| `merchant_id` | `BIGINT UNSIGNED` | 否 | `无` | FK，UNSIGNED | 所属商家，关联 cake_merchant.id；创建后不可更改。 |
| `name` | `VARCHAR(30)` | 否 | `无` | 与 merchant_id 联合 UK；1～30 字符 | 名称去首尾空白后在本店唯一；不同商家可使用相同名称。 |
| `sort` | `INT` | 否 | `0` | ≥0 | 展示顺序，升序；相同值按 id 升序。 |
| `status` | `TINYINT` | 否 | `1` | 0 禁用；1 启用 | 禁用前要求所属商品全部下架。 |
| `create_time` | `DATETIME(3)` | 否 | `CURRENT_TIMESTAMP(3)` | 创建时写入 | 分类创建时间。 |
| `update_time` | `DATETIME(3)` | 否 | `CURRENT_TIMESTAMP(3)` | 变更时刷新 | 分类名称、排序或状态最后修改时间。 |

## cake_product

| 字段 | SQL 类型 | 可空 | 默认值 | 原文约束 | 原文字段说明 |
| --- | --- | --- | --- | --- | --- |
| `id` | `BIGINT UNSIGNED` | 否 | `AUTO_INCREMENT` | PK，UNSIGNED | 具体规格商品主键。 |
| `merchant_id` | `BIGINT UNSIGNED` | 否 | `无` | FK，UNSIGNED | 所属商家；由当前商家身份赋值，禁止转移到其他店铺。 |
| `category_id` | `BIGINT UNSIGNED` | 否 | `无` | FK，UNSIGNED | 关联本店 cake_category.id；分类 merchant_id 必须与商品一致。 |
| `name` | `VARCHAR(60)` | 否 | `无` | 1～60 字符 | 商品名称，不同规格可使用相同名称。 |
| `spec` | `VARCHAR(30)` | 否 | `无` | 1～30 字符 | 具体规格，如 6 寸；无尺寸时填标准份，不得为空。 |
| `price` | `DECIMAL(10,2)` | 否 | `无` | >0 | 当前单价，各规格独立定价。 |
| `stock` | `INT` | 否 | `0` | 0～2147483647 | 可售库存；创建订单扣减，合法取消回补。 |
| `image` | `VARCHAR(255)` | 是 | `NULL` | 合法图片路径或 URL | 商品图片地址；本版录入已有地址，不提供上传服务。 |
| `description` | `VARCHAR(500)` | 是 | `NULL` | 最多 500 字符 | 商品详情说明，纯文本展示。 |
| `status` | `TINYINT` | 否 | `0` | 0 下架；1 上架 | 新商品默认下架；上架须分类启用。 |
| `create_time` | `DATETIME(3)` | 否 | `CURRENT_TIMESTAMP(3)` | 创建时写入 | 商品记录创建时间。 |
| `update_time` | `DATETIME(3)` | 否 | `CURRENT_TIMESTAMP(3)` | 变更时刷新 | 普通编辑、上下架、库存扣减或回补时间。 |

## cake_order

| 字段 | SQL 类型 | 可空 | 默认值 | 原文约束 | 原文字段说明 |
| --- | --- | --- | --- | --- | --- |
| `id` | `BIGINT UNSIGNED` | 否 | `AUTO_INCREMENT` | PK，UNSIGNED | 订单主键。 |
| `merchant_id` | `BIGINT UNSIGNED` | 否 | `无` | FK，UNSIGNED | 所选商家，关联 cake_merchant.id；创建后不可更改。 |
| `merchant_name` | `VARCHAR(60)` | 否 | `无` | 下单快照 | 下单时店铺名称；后续店铺改名或停用不影响历史展示。 |
| `order_no` | `VARCHAR(32)` | 否 | `无` | UK | 业务订单号，后端生成，不接受前端指定。 |
| `user_id` | `BIGINT UNSIGNED` | 否 | `无` | FK，UNSIGNED | 下单顾客，关联 sys_user.id；从认证上下文取值。 |
| `request_id` | `CHAR(36)` | 否 | `无` | 与 user_id 联合 UK | 客户端生成的 UUID 下单请求号。 |
| `request_hash` | `CHAR(64)` | 否 | `无` | 规范化请求 SHA256 | 只比较请求业务内容，不含数据库价格或系统状态。 |
| `total_amount` | `DECIMAL(10,2)` | 否 | `无` | >0；等于明细和 | 订单金额，由后端计算，创建后不可更改。 |
| `status` | `TINYINT` | 否 | `0` | 集合 0、1、2、3、4、5、6、7 | 唯一状态字典见 3.3.1；不得按数值大小推断顺序。 |
| `order_type` | `TINYINT` | 否 | `无` | 1 配送；2 到店自提 | 决定履约分支；创建后不可修改。 |
| `receiver` | `VARCHAR(30)` | 否 | `无` | 1～30 字符 | 联系人姓名快照；配送与自提均必填。 |
| `phone` | `VARCHAR(20)` | 否 | `无` | 大陆手机号 | 订单联系电话快照，不随用户资料变化。 |
| `address` | `VARCHAR(120)` | 是 | `NULL` | 配送必填；自提 NULL | 配送地址快照，创建后不变。 |
| `remark` | `VARCHAR(200)` | 是 | `NULL` | 最多 200 字符 | 顾客下单备注，只记录本次订单要求。 |
| `create_time` | `DATETIME(3)` | 否 | `CURRENT_TIMESTAMP(3)` | 创建时写入 | 订单创建时间，不作为销售统计时间。 |
| `expire_time` | `DATETIME(3)` | 否 | `无` | create_time 加 30 分钟 | 待付款截止点，由后端使用同一数据库时间计算。 |
| `pay_time` | `DATETIME(3)` | 是 | `NULL` | 模拟支付成功写入 | 第一次支付成功时间；取消已支付订单时保留。 |
| `accept_time` | `DATETIME(3)` | 是 | `NULL` | 7→1 时写入 | 本店商家首次接单时间；后续取消时保留，不得重写。 |
| `start_time` | `DATETIME(3)` | 是 | `NULL` | 1→5 时写入 | 实际开始制作时间，不等于支付时间。 |
| `delivery_time` | `DATETIME(3)` | 是 | `NULL` | 5→2 时写入 | 配送出店时间，仅配送订单使用。 |
| `ready_time` | `DATETIME(3)` | 是 | `NULL` | 5→6 时写入 | 制作备妥时间，仅自提订单使用。 |
| `complete_time` | `DATETIME(3)` | 是 | `NULL` | 2 或 6→3 时写入 | 完成时间；所有销售指标的统计基准。 |
| `cancel_time` | `DATETIME(3)` | 是 | `NULL` | 0、7 或 1→4 时写入 | 取消生效时间，与库存回补同事务。 |
| `cancel_reason` | `VARCHAR(200)` | 是 | `NULL` | status=4 时必填 | 人工取消或拒单原因；系统固定值 UNPAID_TIMEOUT 或 MERCHANT_UNAVAILABLE。 |
| `update_time` | `DATETIME(3)` | 否 | `CURRENT_TIMESTAMP(3)` | 变更时刷新 | 最后一次合法状态变更时间；终态后不再改写。 |

## cake_order_item

| 字段 | SQL 类型 | 可空 | 默认值 | 原文约束 | 原文字段说明 |
| --- | --- | --- | --- | --- | --- |
| `id` | `BIGINT UNSIGNED` | 否 | `AUTO_INCREMENT` | PK，UNSIGNED | 明细主键。 |
| `order_id` | `BIGINT UNSIGNED` | 否 | `无` | FK，UNSIGNED | 所属订单，关联 cake_order.id。 |
| `product_id` | `BIGINT UNSIGNED` | 否 | `无` | FK，UNSIGNED | 原商品 ID，关联 cake_product.id。 |
| `product_name` | `VARCHAR(60)` | 否 | `无` | 下单快照 | 下单时商品名称，历史展示不读取当前名称。 |
| `product_spec` | `VARCHAR(30)` | 否 | `无` | 下单快照 | 下单时规格，历史展示不读取当前规格。 |
| `price` | `DECIMAL(10,2)` | 否 | `无` | >0；下单快照 | 下单时单价，后续商品改价不影响。 |
| `quantity` | `INT` | 否 | `无` | 1～99 | 合并同商品后购买数量。 |
| `subtotal` | `DECIMAL(10,2)` | 否 | `无` | price×quantity | 该行小计，后端计算并校验金额上限。 |
| `create_time` | `DATETIME(3)` | 否 | `CURRENT_TIMESTAMP(3)` | 创建时写入 | 明细建立时间，与订单在同一事务保存。 |

## 约束与后续应用边界

完整主键、唯一键、普通索引、8 个 RESTRICT 外键和 29 个 CHECK 见 [索引与约束](INDEX_AND_CONSTRAINTS.md)。金额统一人民币元 `DECIMAL(10,2)`，上限 99999999.99；`INT` 库存范围 0～2147483647，明细数量 1～99。

订单保存商家名称、联系人、电话、地址与金额快照；明细保存商品名称、规格与单价快照。关联原始 ID 用于归属及历史引用保护，展示历史时不得以当前店铺或商品资料覆盖快照。

NOT NULL、类型容量、唯一键、外键和单行 CHECK 无法证明跨表业务已经完成。订单 1～20 条明细、金额等于明细和、明细与主订单同商家、商家账号必须 MERCHANT、下单账号必须 CUSTOMER、单价输入不得静默舍入、创建及取消库存事务、状态流转白名单和历史不可改写，均须在后续授权阶段由 Service 事务、参数校验及相应并发测试落实。本阶段不创建业务 Entity、Mapper、Service 或 Controller。
