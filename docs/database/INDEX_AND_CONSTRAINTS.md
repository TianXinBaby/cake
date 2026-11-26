# Phase 2 索引与约束

核对日期：2026-11-25。业务依据为当前根目录需求说明书 V1.1 第 4.4、4.5 节，并核对第 3.3 节状态白名单及第 5.1 节字段校验。范围只有六张表、74 个字段，无触发器、存储过程或额外业务字段。实际数据库执行与违反约束测试结果见 [Phase 2 报告](../../reports/PHASE_2_REPORT.md)。

## 执行顺序与计数

受控初始化器先选择已确认的空数据库，使用严格 SQL 模式与 `+08:00` 会话时间；按 `01_create_tables.sql` → `02_create_indexes.sql` → `03_data_constraints.sql` → 按需参数化种子顺序首次执行。脚本无自动重跑、覆盖、删库或清表逻辑。MySQL DDL 会提交事务；中途失败必须停止、检查现状并记录已执行语句，不得声称一次事务已经回滚整个建库过程。

| 类型 | 定义数量 | 文件与说明 |
| --- | --- | --- |
| 主键 | 6 | `database/schema/01_create_tables.sql`，均为 `id` |
| 唯一键 | 9 | 同上；先建立以支持后续复合外键 |
| 需求普通索引 | 14 | `database/indexes/02_create_indexes.sql`，列顺序与 4.4 一致 |
| 外键 | 8 | `database/constraints/03_data_constraints.sql`，全部双向 RESTRICT |
| CHECK | 29 | 同上，均显式 `ENFORCED`，实际有效性须违反约束测试证明 |
| 引擎必需支持索引 | 1 | InnoDB 为商品复合外键创建 `(category_id,merchant_id)`，预计名 `fk_product_category_merchant`；结构以真实元数据为准 |

完整结构预计有 6 个主键索引、9 个唯一索引、14 个需求普通索引及 1 个外键支持索引，共 30 个索引。不能因为需求没有另列该支持索引就删除或忽略它；现有商品查询索引从 `merchant_id` 起列，无法支持从 `category_id` 起列的复合外键。

## 主键和唯一键

| 表 | 键名 | 有序列 | 需求目的 |
| --- | --- | --- | --- |
| sys_user | PRIMARY | id | 用户自增主键 |
| sys_user | uk_user_username | username | 登录账号唯一 |
| sys_user | uk_user_phone | phone | 手机号独立唯一，不能只保留账号唯一 |
| cake_merchant | PRIMARY | id | 店铺自增主键 |
| cake_merchant | uk_merchant_user | user_id | 一个商家账号至多绑定一家店铺 |
| cake_category | PRIMARY | id | 分类自增主键 |
| cake_category | uk_category_merchant_name | merchant_id,name | 本店分类名唯一，不同店可同名 |
| cake_category | uk_category_id_merchant | id,merchant_id | 商品复合外键的被引用唯一键 |
| cake_product | PRIMARY | id | 独立规格商品自增主键 |
| cake_product | uk_product_id_merchant | id,merchant_id | 原文明确要求，保留不删减 |
| cake_order | PRIMARY | id | 订单自增主键 |
| cake_order | uk_order_no | order_no | 业务订单号最终防重 |
| cake_order | uk_order_user_request | user_id,request_id | 用户维度下单幂等最终防重 |
| cake_order_item | PRIMARY | id | 明细自增主键 |
| cake_order_item | uk_item_order_product | order_id,product_id | 同单同商品只一行，同时支持订单明细查询 |

不为商品 `name/spec` 或店铺 `name` 添加唯一约束。名称在确认的数据库排序规则下比较：用户于 2026-11-25 明确选择 `utf8mb4_0900_as_cs`，此前 REQ-Q03 的部署排序规则待确认项已获得本轮补充决定。该规则区分大小写及重音；分类同名判断按该规则执行。输入去首尾空白仍由后续应用校验，不能依靠 UNIQUE 替代规范化。

## 需求普通索引

| 表 | 索引名 | 有序列 | 用途 |
| --- | --- | --- | --- |
| cake_merchant | idx_merchant_status | status,id | 公开启用店铺及平台状态筛选 |
| cake_category | idx_category_merchant_status_sort | merchant_id,status,sort,id | 本店分类启用筛选及排序 |
| cake_product | idx_product_merchant_category_status | merchant_id,category_id,status,id | 本店分类商品与上下架筛选 |
| cake_product | idx_product_merchant_status | merchant_id,status,id | 本店商品上下架查询 |
| cake_order | idx_order_user_status_create | user_id,status,create_time,id | 本人按状态与创建时间分页 |
| cake_order | idx_order_user_create | user_id,create_time,id | 本人未指定状态分页 |
| cake_order | idx_order_merchant_status_create | merchant_id,status,create_time,id | 本店按状态与创建时间分页 |
| cake_order | idx_order_merchant_create | merchant_id,create_time,id | 本店未指定状态分页 |
| cake_order | idx_order_merchant_status_complete | merchant_id,status,complete_time,id | 本店完成时间统计 |
| cake_order | idx_order_create | create_time,id | 平台按创建时间分页 |
| cake_order | idx_order_status_create | status,create_time,id | 平台状态与创建时间筛选 |
| cake_order | idx_order_status_complete | status,complete_time,id | 平台完成时间统计 |
| cake_order | idx_order_status_expire | status,expire_time,id | 待付款超时扫描 |
| cake_order_item | idx_item_product | product_id | 商品历史引用与排行关联 |

这些是需求指定的结构，存在索引不能证明 1 万订单分页的 2 秒性能目标；真实业务查询、执行计划与性能实测在后续相应阶段执行。

## 外键与删除保护

| 外键名 | 引用列 | 目标列 |
| --- | --- | --- |
| fk_merchant_user | cake_merchant(user_id) | sys_user(id) |
| fk_category_merchant | cake_category(merchant_id) | cake_merchant(id) |
| fk_product_merchant | cake_product(merchant_id) | cake_merchant(id) |
| fk_product_category_merchant | cake_product(category_id,merchant_id) | cake_category(id,merchant_id) |
| fk_order_merchant | cake_order(merchant_id) | cake_merchant(id) |
| fk_order_user | cake_order(user_id) | sys_user(id) |
| fk_item_order | cake_order_item(order_id) | cake_order(id) |
| fk_item_product | cake_order_item(product_id) | cake_product(id) |

八个外键全部 `ON DELETE RESTRICT ON UPDATE RESTRICT`，未使用级联操作、关闭外键检查或 `SET NULL`。商品复合外键使商品无法引用他店分类；单列商家外键仍按原文保留。明细没有 `merchant_id` 字段，不能凭现有两条明细外键证明订单与商品同店，后续 Service 必须同事务校验，不能擅加字段解决。

外键阻止删除仍被引用的数据；禁止物理删除账号、店铺、订单和明细的完整业务政策，仍须后续应用不提供相关接口并通过权限/事务测试落实。不存在引用不意味着业务允许删除。

## CHECK 清单

| 表 | CHECK 名 | 当前单行规则 |
| --- | --- | --- |
| sys_user | ck_user_role | 仅准确的 ADMIN、MERCHANT、CUSTOMER，使用二进制比较避免依赖排序规则放宽枚举 |
| sys_user | ck_user_status | 0 或 1 |
| sys_user | ck_user_token_version | 大于等于 0 |
| sys_user | ck_user_login_fail_count | 0～5 |
| sys_user | ck_user_text | 账号与昵称去空格后长度至少 1；上限仍受原字段容量限制 |
| sys_user | ck_user_phone | 大陆 11 位手机号格式 |
| cake_merchant | ck_merchant_status | 0 或 1 |
| cake_merchant | ck_merchant_text | 店名、地址去空格后非空及原文字数上限 |
| cake_merchant | ck_merchant_phone | 大陆 11 位手机号格式 |
| cake_category | ck_category_status | 0 或 1 |
| cake_category | ck_category_sort | 大于等于 0 |
| cake_category | ck_category_name | 去空格后非空、最多 30 字符 |
| cake_product | ck_product_price | 大于 0，容量由 DECIMAL(10,2) 限定 |
| cake_product | ck_product_stock | 0～2147483647 |
| cake_product | ck_product_status | 0 或 1 |
| cake_product | ck_product_text | 商品名、规格去空格后非空及原文字数上限 |
| cake_order | ck_order_amount | 总金额大于 0，容量由 DECIMAL(10,2) 限定 |
| cake_order | ck_order_status | 0、1、2、3、4、5、6、7 |
| cake_order | ck_order_type | 1 配送或 2 自提 |
| cake_order | ck_order_text | 商家快照名、订单号、联系人去空格后非空及原文字数上限 |
| cake_order | ck_order_phone | 大陆 11 位手机号格式 |
| cake_order | ck_order_address | 配送地址非 NULL 且非空，自提地址必须 NULL |
| cake_order | ck_order_expire | expire_time 精确等于 create_time 加 30 分钟 |
| cake_order | ck_order_state_events | 八种状态的事件 NULL/非 NULL 矩阵及取消原因 |
| cake_order | ck_order_event_times | 已发生事件的先后、支付严格早于截止、取消不早于已发生支付/接单 |
| cake_order_item | ck_item_price | 单价大于 0 |
| cake_order_item | ck_item_quantity | 合并后数量 1～99 |
| cake_order_item | ck_item_subtotal | 小计大于 0 且精确等于 price×quantity |
| cake_order_item | ck_item_snapshot | 商品名称、规格快照去空格后非空及原文字数上限 |

MySQL 8.0.19 支持强制执行 CHECK；版本支持与 `ENFORCED` 声明均不能替代实际违反约束测试。CHECK 接受 TRUE 或 UNKNOWN，因此事件矩阵先使用 `IS NULL/IS NOT NULL`，每项时间比较用事件存在条件保护，避免必需事件为 NULL 时绕过约束。依据：[MySQL 官方 CHECK 约束说明](https://dev.mysql.com/doc/refman/8.0/en/create-table-check-constraints.html)。

## 订单事件矩阵

`有` 为必填已发生，`空` 为必须 NULL；配送/自提分支只允许对应事件。

| 状态 | pay | accept | start | delivery | ready | complete | cancel / reason |
| --- | --- | --- | --- | --- | --- | --- | --- |
| 0 待付款 | 空 | 空 | 空 | 空 | 空 | 空 | 空 / 空 |
| 7 待接单 | 有 | 空 | 空 | 空 | 空 | 空 | 空 / 空 |
| 1 待制作 | 有 | 有 | 空 | 空 | 空 | 空 | 空 / 空 |
| 5 制作中 | 有 | 有 | 有 | 空 | 空 | 空 | 空 / 空 |
| 2 配送中 | 有 | 有 | 有 | 有 | 空 | 空 | 空 / 空；类型必须 1 |
| 6 待自提 | 有 | 有 | 有 | 空 | 有 | 空 | 空 / 空；类型必须 2 |
| 3 已完成 | 有 | 有 | 有 | 配送有、自提空 | 自提有、配送空 | 有 | 空 / 空 |
| 4 已取消 | 可空或保留 | 可空或保留；有接单则必须有支付 | 空 | 空 | 空 | 空 | 有 / 非空原因 |

支付满足 `create_time≤pay_time<expire_time`；已发生履约事件满足 `pay≤accept≤start≤delivery/ready≤complete`；取消不早于创建及已发生支付、接单。接单、制作、履约、完成及取消可以晚于付款截止点。支付、接单及后续事件允许同一毫秒，未擅加严格大于关系。

## DATA-01～DATA-09 的承担边界

| 规则 | 本阶段数据库承担 | 后续应用事务与验证承担 |
| --- | --- | --- |
| DATA-01 | 74 列原文类型/NULL/默认，PK、UK、FK、29 个可执行 CHECK | 所有输入白名单与业务校验；CHECK 实测不能用静态阅读代替 |
| DATA-02 | 同订单同商品唯一、正金额、数量范围、小计等式 | 每单 1～20 条明细、订单总金额等于明细和；主明细同事务、明细及终态不可改写 |
| DATA-03 | 各非终态当前行事件矩阵和订单类型分支 | 只能通过合法动作逐步迁移，不能跨状态伪造一整套有效事件 |
| DATA-04 | 完成、取消互斥、分支时间、取消原因及未制作取消形状 | 已发生事件不可重写，取消与库存回补同事务且仅一次 |
| DATA-05 | 截止等式、付款边界、已发生事件时间链 | 事件取数据库当前时间且不接收客户端值、数据库时间裁决、并发锁；单行 CHECK 无法认证时间来源 |
| DATA-06 | 默认 CURRENT_TIMESTAMP(3)、订单号唯一及长度容量 | update_time 仅合法修改刷新、订单号生成/碰撞重试及终态不改写 |
| DATA-07 | 规格非空、独立 ID/价格/库存/状态、快照列及历史商品 FK | 创建时复制真实快照、历史查询读取快照、后续资料修改不回写历史 |
| DATA-08 | 初始数据可受现有类型、唯一键、外键及 CHECK 校验 | 安全参数化种子、BCrypt、账号与店铺同事务、公开注册/平台创建角色白名单 |
| DATA-09 | merchant_id 非空、商家 FK、商品分类同店复合 FK | 商家关联账号当前角色必须 MERCHANT、下单账号必须 CUSTOMER、订单明细同店、资源归属与快照保护 |

`DECIMAL(10,2)` 保证保存值的精度，严格 SQL 模式对越界提供保护，但 MySQL 对多于两位小数的有效范围输入仍可能转换并舍入。需求要求的“输入超过两位小数直接拒绝”必须由后续参数与 BigDecimal 校验先行处理，不得把数据库转换成功误记为 TC-38 通过。[MySQL 官方定点类型说明](https://dev.mysql.com/doc/refman/8.0/en/fixed-point-types.html)。

原有库存上限与全量回补冲突（REQ-Q05）、停店与超时同时发生时的原因/错误码优先级（REQ-Q04）、排行商家快照标签选择（REQ-Q08）仍待用户决定。脚本未擅改库存类型、状态、字段或业务规则。
