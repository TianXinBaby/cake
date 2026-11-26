# Phase 4 平台商家与用户管理

日期：2026-11-26（Asia/Shanghai）。唯一需求来源为根目录《蛋糕店订单管理系统需求说明书_V1.1.docx》第 3.2、3.7、4.3、5.1、5.3、6.1、8.2 节。

本轮用户明确授权 MERCH-01、USER-05、API-35～40 及 UI-14/15，覆盖历史计划中 Phase 4 包含公开商家接口、平台页面留至 Phase 12 的阶段安排。本轮不实现 API-33/34，不进入 Phase 5，不创建登录、注册或其他业务页面。历史文档和报告保留原阶段事实；本轮执行证据见 [Phase 4 报告](../../reports/PHASE_4_REPORT.md)。

## 接口与返回

所有接口仅 ADMIN 可访问。继续使用现有 JWT 验签、数据库当前角色/启停状态/令牌版本校验及统一响应 `code/msg/data`。所有 ID 在接口中为正十进制字符串，数据库仍为 BIGINT UNSIGNED，Java 使用 BigInteger；时间为 `+08:00` ISO 8601 毫秒字符串。

| 编号 | 方法与路径 | 接收字段 | 成功结果 |
| --- | --- | --- | --- |
| API-35 | GET `/api/admin/merchants` | Query：page、size、keyword、status，可选 | Merchant 分页，包括停用店铺与绑定账号 |
| API-36 | POST `/api/admin/merchants` | accountPhone、nickname、password、name、phone、address 必填；description 可空 | HTTP 201，Merchant；业务 code 仍为 200 |
| API-37 | PUT `/api/admin/merchants/{id}` | name、phone、address 必填；description 可空 | HTTP 200，Merchant；仅更新店铺信息 |
| API-38 | PATCH `/api/admin/merchants/{id}/status` | status 整数 0 或 1 | HTTP 200，Merchant |
| API-39 | GET `/api/admin/users` | Query：page、size、keyword、status，可选 | User 分页；固定 CUSTOMER，keyword 匹配昵称或手机号 |
| API-40 | PATCH `/api/admin/users/{id}/status` | status 整数 0 或 1 | HTTP 200，User；仅允许 CUSTOMER |

Merchant 字段为 id、name、phone、address、description、status、userId、username、createTime、updateTime。`username` 表示绑定账号的手机号；`phone` 表示店铺联系电话，两者可以不同。不扩展额外账号维护字段。User 复用已验收的公开对象：id、username、nickname、phone、role、status、createTime，不返回密码散列、tokenVersion、失败次数或锁定控制字段。

分页返回 `{records,total,page,size}`；page 默认 1 且至少为 1，size 默认 10、范围 1～100，超出末页返回空 records。keyword 最长 60 字符，按字面包含匹配。现有工程没有 MyBatis-Plus 分页拦截器，本轮沿用注解 Mapper，绑定 COUNT 与 LIMIT/OFFSET，不新增分页依赖。商家按店铺名称匹配 keyword，两个列表均按 id 降序稳定排列；这是原文未进一步限定部分的实现选择。通配符通过固定 LIKE 转义字符处理，不拼接用户 SQL。

## 事务和权限

创建接口固定 `username=phone=accountPhone`、`role=MERCHANT`、账号和店铺启用，使用现有 BCrypt10 加密密码。在一个事务内保存账号和店铺，账号唯一冲突返回 40901，店铺写入故障时账号同时回滚。`cake_merchant.user_id` 的唯一键和外键保证账号存在且至多绑定一家店，MERCHANT 角色由服务层固定赋值；不把 FK 当成跨表角色校验。

编辑店铺不允许换绑、修改账号手机号、角色或状态；状态只通过独立接口维护。店铺停用不改变账号状态、tokenVersion、商品、库存或历史订单。隐藏公开入口、阻止新单以及已支付订单履约的消费方逻辑属于后续对应接口阶段，本轮仅交付店铺状态维护，不宣称 TC-45 已全量通过。

用户状态接口锁定目标并再次检查 CUSTOMER，目标不存在或为 ADMIN/MERCHANT 均返回 HTTP 404／40405。状态实际改变时，status、token_version 递增和 update_time 在同一 SQL、同一事务中提交。禁用后现有保护请求返回 40301；恢复后旧令牌返回 40102，必须重新登录。相同状态请求返回当前对象，不重复写时间或版本；用户接口的这一行为是实现的幂等选择，原文明确要求的是“变更状态并递增版本”。

写事务中重新检查操作管理员的当前身份和版本，用户状态更新与已有改密、退出使用同一用户行锁。平台没有账号、店铺或订单物理删除接口，没有商家账号禁用入口，不代商家经营。

## 参数错误与页面

沿用 Phase 3.1 契约：HTTP 400／40001 可定位错误位于 `data.fieldErrors`，字段名为实际 lowerCamelCase API 字段，值为字符串数组；可以同时返回多个字段及同字段多条消息。不增加 `_global`，无法可靠定位时 data 为 null。受保护字段按 HTTP 403／40302 拒绝，不静默赋值。创建时允许的 accountPhone、nickname、password 在编辑和状态接口中不能借用为账号维护入口。

UI-14 `/admin/merchants` 提供列表、筛选、分页、创建、编辑与启停确认；UI-15 `/admin/users` 仅提供 CUSTOMER 列表、筛选、分页与启停确认。表单就地显示字段错误，纯文本渲染输入，不使用 HTML 注入；请求期间显示加载或提交状态，失败保留可修正表单，危险动作可取消。账号手机号和店铺电话分开标注，创建密码不在列表或成功响应展示。

管理员路由守卫复用现有 Pinia 和本人资料接口重新核实会话，后端仍独立鉴权。本阶段没有 UI-01 登录页，缺少管理员会话时回到现有根入口并提示先登录；不增加令牌粘贴或调试登录入口。真实浏览器验证可在隔离测试库通过现有认证 API 建立内存会话，此验证不等于已交付完整登录页面流程。刷新后重登的原有内存令牌策略保持。

## 验证范围

新增测试应覆盖六接口正向/参数/角色、CUSTOMER 目标限制、字面搜索、分页、创建唯一冲突和并发、故障回滚、状态同值与并发、禁用恢复后的旧 JWT、历史数据保护，以及两页的表单和交互。复用 `cake_order_system_phase2_test`，只按本轮生成的准确 ID 清理夹具，不执行 DDL、初始化、种子、全表清空或自增序列重置。

执行命令、实际数量、浏览器和数据保护证据以阶段报告为准；完整 TC-29、35、36、37、39、40、45 仍须区分本阶段可测部分与尚未开发模块，不将构建或组件测试当成全系统验收。
