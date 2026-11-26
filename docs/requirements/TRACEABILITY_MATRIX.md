# 需求追踪矩阵 V1.1

项目：基于 Spring Boot + Vue 的蛋糕店订单管理系统（多商家平台）。

唯一需求源：[蛋糕店订单管理系统需求说明书_V1.1.docx](../../蛋糕店订单管理系统需求说明书_V1.1.docx)，实际修订日期 **2026-11-24**。本文件核对原文正文及全部 25 张表，以第 3.2、5.2、5.3、6.1、8.2 节建立追踪；编号、请求契约和场景内容保留原文。

执行环境补充 **ENV-01（2026-11-24 用户确认）**：后续开发与 TC-37 重建使用 Node.js **24.20.0**，覆盖原文的 Node.js 18 执行要求；原始 DOCX 保持原样，仍单独保留其原版本记录。

**第1～7节保留Phase0文档基线及当时状态；追踪覆盖不表示测试通过。后续实际实施状态见第8节及对应阶段报告，不以历史“待实现”覆盖最新授权和交付事实。**

## 1. 追踪口径与覆盖

- “原文”保留原有功能表、API 明细、页面表或测试表中直接列出的关系；三处关系存在差异时分别展示。
- “分析映射”仅用于安排开发和回归：由原文 API、页面、操作文字推导关联，未经确认不视为需求变更。阶段分配同样属于计划，须经用户审核；当前只授权 Phase 0。
- 功能→API 保留第 3.2 节原列；API→功能及页面采用第 3.2、5.3、6.1 节显式关系的并集，新增于 API 明细原列之外的关系会标注。反向可按任一编号检索本文件。
- API 与 TC 的“原文编号”映射保留 TC 接口栏直接列出的编号；TC-33、35、37、42 等泛称接口的场景另作分析范围说明，不伪称原文逐一枚举。
- 页面路由中的全角分隔符“／”按标准前导 `/` 展示，原路径段及参数名不变。
- 所有阶段测试最终在 Phase 13 做全量验收；提前安排的测试须随所属阶段实际执行、保存记录，不能以本表代替证据。

| 对象 | 原文总数 | 本文件登记 | 状态 |
| --- | --- | --- | --- |
| 功能 | 20 | 20 / 20，USER 5、MERCH 2、PROD 4、ORDER 7、STAT 2 | 仅需求追踪 |
| API | 45 | API-01～API-45，无缺号 | 仅契约和追踪 |
| 页面 | 17 | UI-01～UI-17，无缺号 | 仅页面规划 |
| 验收场景 | 46 | TC-01～TC-46，无缺号；输入与操作、预期逐字保留 | 未执行 |
| 超时规则 | ORDER-06 | 单列后台任务，关联 API-06、23、24、25 | 无独立触发 API；未实现 |

## 2. 20 项功能正向追踪

本表“原文 API”逐项保留第 3.2 节内容；“页面”综合第 6.1 节和 API 明细。“TC 原列”仅列第 8.2 节需求栏直接点名该功能的场景；跨模块、参数、安全、快照和非功能回归见第 5、6 节。后端阶段和页面阶段均为分析规划，Phase 13 做最终验收。

| 功能编号 | 原文名称 | 原文规则 | 优先级 | 原文 API | 页面追踪（分析汇总） | TC 原列 | 后端 / 页面阶段（计划） |
| --- | --- | --- | --- | --- | --- | --- | --- |
| USER-01 | 顾客注册 | 注册手机号作为 username 与 phone，二者不可自行修改；账号唯一，密码 6～20 位；后端固定角色为 CUSTOMER、状态为启用。 | 高 | API-10 | UI-08 | TC-01、TC-02 | Phase 3 / Phase 10 |
| USER-02 | 登录与退出 | 登录校验密码、禁用与临时锁定；签发 2 小时 JWT。退出使该账号全部已有令牌失效并清除本地状态。 | 高 | API-01、11 | UI-01、所有已登录页面公共退出导航 | TC-03、TC-04、TC-05 | Phase 3 / Phase 10 |
| USER-03 | 个人信息 | 登录后读取本人账号、昵称、手机号与角色；只允许修改本人昵称，不接收 role、status 等敏感字段。 | 中 | API-12、13 | UI-07 | TC-06 | Phase 3 / Phase 10 |
| USER-04 | 修改密码 | 校验原密码和新密码后更新 BCrypt 散列，并使已有令牌失效，要求重新登录。 | 中 | API-14 | UI-07 | TC-06 | Phase 3 / Phase 10 |
| PROD-01 | 商品浏览与详情 | 匿名先选择启用商家，再查询该店启用分类和已上架商品；支持分类、关键字与分页。售罄仍展示，禁止下单。 | 高 | API-02、15、16 | UI-02、UI-10、UI-13 | TC-07、TC-41 | Phase 5 / Phase 10 |
| PROD-02 | 商品管理 | 商家分页查询、详情回显、新增和编辑本店商品；新商品默认下架，归属从登录上下文赋值且不可转店。不同规格独立记录，仅无历史引用商品可删除。 | 高 | API-03、04、05、17、18 | UI-04 | TC-08、TC-09 | Phase 5 / Phase 11 |
| PROD-03 | 上下架与库存 | 商家只对本店商品切换上架状态或按增减量调整库存；本店分类启用才可上架，条件更新防止覆盖与负库存。 | 中 | API-29、30 | UI-04 | TC-10、TC-11 | Phase 5 / Phase 11 |
| PROD-04 | 分类管理 | 商家维护本店分类的名称、排序、启停与删除；名称在本店唯一，不同店可同名。公开只返回所选启用店铺的启用分类。 | 中 | API-19、20、21、22、31 | UI-04、UI-11 | TC-09、TC-10 | Phase 5 / Phase 11 |
| ORDER-01 | 顾客下单 | 用户选择 merchantId、该店具体商品、数量及履约方式；后端校验全部商品属于该商家，计价、扣库、保存主表明细同事务，请求号防重。 | 高 | API-06 | UI-09 | TC-12、TC-13、TC-14、TC-15、TC-16、TC-17、TC-43 | Phase 6 / Phase 10 |
| ORDER-02 | 订单查询 | 用户仅本人、商家仅本店，平台管理员全平台只读；包含商家快照、状态、类型、金额、商品快照和事件时间。 | 高 | API-07、23、24、27、42、43 | UI-03、UI-05、UI-09、UI-16 | TC-18 | Phase 6 / Phase 10、Phase 11、Phase 12 |
| ORDER-03 | 订单状态推进 | 商家先接收本店已支付待接单订单，再开始制作并按配送或自提分支履约；按原状态条件更新，不跳级、回退或更改终态。 | 高 | API-08 | UI-05 | TC-25、TC-26、TC-27、TC-28 | Phase 7 / Phase 11 |
| ORDER-04 | 取消订单 | 用户仅取消本人待付款订单；商家可取消本店待付款、待接单或待制作订单，须填原因；取消与全量回补同事务且只生效一次。 | 高 | API-26、28 | UI-03、UI-05、UI-09 | TC-23、TC-24、TC-27、TC-28 | Phase 7 / Phase 10、Phase 11 |
| ORDER-05 | 模拟支付 | 用户仅支付本人未超时待付款订单；成功进入待接单并记录 pay_time，失败不推进。店铺停用时先取消未付款订单并回补，再返回店铺不可用。 | 高 | API-25 | UI-03、UI-09 | TC-19、TC-20、TC-22 | Phase 7 / Phase 10 |
| ORDER-06 | 未付款超时处理 | 下单后 30 分钟为付款截止点；后台每 60 秒扫描，支付入口主动检查超时；自动取消与回补库存。 | 高 | 无独立前台接口；API-06、23、24、25展示或检查 | UI-03、UI-09 | TC-21、TC-22 | Phase 6、Phase 7 / Phase 10 |
| STAT-01 | 销售额与订单量 | 商家按完成时间、日或月查询本店销售额与完成订单量；平台只读查询全平台或指定商家数据，展示折线图和柱状图。 | 中 | API-09、44 | UI-06、UI-17 | TC-30、TC-31 | Phase 8 / Phase 11、Phase 12 |
| STAT-02 | 热销商品排行 | 商家查看本店已完成明细销量前 10 名；平台查看全平台或指定商家排行。独立规格独立排行，显示商家、销量和销售额。 | 低 | API-32、45 | UI-06、UI-17 | TC-31、TC-32 | Phase 8 / Phase 11、Phase 12 |
| MERCH-01 | 平台商家管理 | 平台创建商家账号并绑定店铺，查询、编辑店铺信息和启停；账号、店铺同事务创建，不开放商家或管理员公开注册。 | 高 | API-35、36、37、38 | UI-14 | TC-39 | Phase 4 / Phase 12 |
| MERCH-02 | 商家浏览与选择 | 公开展示启用店铺名称、简介、联系电话和地址；用户选择店铺后仅浏览该店商品，切换店铺须确认清空当前选购。 | 高 | API-33、34 | UI-12、UI-13 | TC-41 | Phase 4 / Phase 10 |
| USER-05 | 平台用户管理 | 平台分页查询 CUSTOMER 账号并启停，禁止修改角色、明文读取密码或物理删除；商家无平台用户管理权限。 | 中 | API-39、40 | UI-15 | TC-40 | Phase 4 / Phase 12 |
| ORDER-07 | 商家接单 | 商家查看本店待接单订单，接单后进入待制作并写 accept_time；拒单走专用取消接口并填原因、回补库存，平台不代接单。 | 高 | API-41、28 | UI-05 | TC-44 | Phase 7 / Phase 11 |

## 3. 45 项 API 反向追踪与契约

### 3.1 API → 功能 → 页面 → 测试 → 阶段

方法、路径、名称与权限来自第 5.2 节原始清单。功能与页面列保留显式关系并集，并在第 6 节说明差异。API-11 原文页面范围为“所有已登录页面”，以下展开的 UI 编号仅为公共导航集成分析，公开页仅在已登录时显示退出入口。TC-33 为相关参数接口的共性校验，TC-37 为全部 45 项接口及初始化的全量核对，未在每行重复。

| API | 方法与原路径 | 原文名称 / 权限 | 功能关系并集 | 页面关系并集 | TC 原文编号 / 分析补充 | 实现阶段 |
| --- | --- | --- | --- | --- | --- | --- |
| API-01 | POST `/api/auth/login` | 用户登录；匿名 | USER-02 | UI-01 | TC-03、TC-04 | Phase 3 |
| API-02 | GET `/api/products` | 公开商品分页查询；匿名 | PROD-01 | UI-02、UI-13 | TC-07、TC-41 | Phase 5 |
| API-03 | POST `/api/merchant/products` | 新增商品；商家仅本店 | PROD-02 | UI-04 | TC-08、TC-38；分析：TC-35、TC-42 | Phase 5 |
| API-04 | PUT `/api/merchant/products/{id}` | 编辑商品基本信息；商家仅本店 | PROD-02 | UI-04 | TC-08、TC-29、TC-38；分析：TC-35、TC-42、TC-46 | Phase 5 |
| API-05 | DELETE `/api/merchant/products/{id}` | 删除无历史引用商品；商家仅本店 | PROD-02 | UI-04 | TC-09；分析：TC-35、TC-42 | Phase 5 |
| API-06 | POST `/api/orders` | 创建订单；顾客本人 | ORDER-01、ORDER-06 | UI-09 | TC-12、TC-13、TC-14、TC-15、TC-16、TC-17、TC-38、TC-43、TC-45 | Phase 6 |
| API-07 | GET `/api/merchant/orders` | 商家订单分页查询；商家仅本店 | ORDER-02 | UI-05 | TC-18、TC-34；分析：TC-42 | Phase 6 |
| API-08 | PATCH `/api/merchant/orders/{id}/status` | 商家推进订单状态；商家仅本店 | ORDER-03 | UI-05 | TC-25、TC-26、TC-27、TC-28、TC-45；分析：TC-35、TC-42 | Phase 7 |
| API-09 | GET `/api/merchant/stats/sales` | 销售额与订单量统计；商家仅本店 | STAT-01 | UI-06 | TC-30、TC-31、TC-35；分析：TC-42 | Phase 8 |
| API-10 | POST `/api/auth/register` | 顾客注册；匿名 | USER-01 | UI-08 | TC-01、TC-02 | Phase 3 |
| API-11 | POST `/api/auth/logout` | 退出登录；三类已登录角色 | USER-02 | 原文：所有已登录页面；分析：UI-02、UI-03、UI-04、UI-05、UI-06、UI-07、UI-09、UI-10、UI-11、UI-12、UI-13、UI-14、UI-15、UI-16、UI-17 | TC-05 | Phase 3 |
| API-12 | GET `/api/users/me` | 读取本人资料；三类已登录角色 | USER-03 | UI-07 | TC-04、TC-05、TC-06 | Phase 3 |
| API-13 | PATCH `/api/users/me` | 修改本人昵称；三类已登录角色 | USER-03 | UI-07 | TC-06；分析：TC-29 | Phase 3 |
| API-14 | PATCH `/api/users/me/password` | 修改本人密码；三类已登录角色 | USER-04 | UI-07 | TC-06 | Phase 3 |
| API-15 | GET `/api/products/{id}` | 公开商品详情；匿名 | PROD-01 | UI-10 | TC-07 | Phase 5 |
| API-16 | GET `/api/categories` | 公开分类查询；匿名 | PROD-01 | UI-02、UI-13 | TC-07、TC-41 | Phase 5 |
| API-17 | GET `/api/merchant/products` | 后台商品分页查询；商家仅本店 | PROD-02 | UI-04 | TC-08；分析：TC-42 | Phase 5 |
| API-18 | GET `/api/merchant/products/{id}` | 后台商品详情；商家仅本店 | PROD-02 | UI-04 | TC-08；分析：TC-42 | Phase 5 |
| API-19 | GET `/api/merchant/categories` | 后台分类查询；商家仅本店 | PROD-04 | UI-04、UI-11 | TC-10；分析：TC-42 | Phase 5 |
| API-20 | POST `/api/merchant/categories` | 新增分类；商家仅本店 | PROD-04 | UI-11 | TC-10；分析：TC-35、TC-42 | Phase 5 |
| API-21 | PUT `/api/merchant/categories/{id}` | 编辑分类名称与排序；商家仅本店 | PROD-04 | UI-11 | TC-10；分析：TC-35、TC-42 | Phase 5 |
| API-22 | DELETE `/api/merchant/categories/{id}` | 删除空分类；商家仅本店 | PROD-04 | UI-11 | TC-09、TC-10；分析：TC-35、TC-42 | Phase 5 |
| API-23 | GET `/api/orders` | 本人订单分页查询；顾客本人 | ORDER-02、ORDER-06 | UI-03 | TC-18、TC-34 | Phase 6 |
| API-24 | GET `/api/orders/{id}` | 本人订单详情；顾客本人 | ORDER-02、ORDER-06 | UI-03、UI-09 | TC-18、TC-29 | Phase 6 |
| API-25 | POST `/api/orders/{id}/payment` | 本人订单模拟支付；顾客本人 | ORDER-05、ORDER-06 | UI-03、UI-09 | TC-19、TC-20、TC-21、TC-22、TC-25、TC-26、TC-45 | Phase 7 |
| API-26 | POST `/api/orders/{id}/cancel` | 顾客取消本人待付款订单；顾客本人 | ORDER-04 | UI-03、UI-09 | TC-23、TC-24、TC-27 | Phase 7 |
| API-27 | GET `/api/merchant/orders/{id}` | 后台订单详情；商家仅本店 | ORDER-02 | UI-05 | TC-18、TC-29；分析：TC-42 | Phase 6 |
| API-28 | POST `/api/merchant/orders/{id}/cancel` | 商家取消未制作订单；商家仅本店 | ORDER-04、ORDER-07 | UI-05 | TC-23、TC-24、TC-27、TC-28、TC-44；分析：TC-35、TC-42、TC-46 | Phase 7 |
| API-29 | PATCH `/api/merchant/products/{id}/stock` | 商家调整商品库存；商家仅本店 | PROD-03 | UI-04 | TC-11；分析：TC-35、TC-42 | Phase 5 |
| API-30 | PATCH `/api/merchant/products/{id}/status` | 商家商品上下架；商家仅本店 | PROD-03 | UI-04 | TC-10；分析：TC-35、TC-42 | Phase 5 |
| API-31 | PATCH `/api/merchant/categories/{id}/status` | 商家启停分类；商家仅本店 | PROD-04 | UI-11 | TC-10；分析：TC-35、TC-42 | Phase 5 |
| API-32 | GET `/api/merchant/stats/hot-products` | 商品热销前十排行；商家仅本店 | STAT-02 | UI-06 | TC-31、TC-32、TC-35；分析：TC-42 | Phase 8 |
| API-33 | GET `/api/merchants` | 公开商家分页查询；匿名 | MERCH-02 | UI-12 | TC-41 | Phase 4 |
| API-34 | GET `/api/merchants/{id}` | 公开商家详情；匿名 | MERCH-02 | UI-13 | TC-41 | Phase 4 |
| API-35 | GET `/api/admin/merchants` | 平台商家分页查询；平台管理员 | MERCH-01 | UI-14 | TC-39 | Phase 4 |
| API-36 | POST `/api/admin/merchants` | 平台创建商家账号与店铺；平台管理员 | MERCH-01 | UI-14 | TC-39；分析：TC-35 | Phase 4 |
| API-37 | PUT `/api/admin/merchants/{id}` | 平台编辑店铺信息；平台管理员 | MERCH-01 | UI-14 | TC-29、TC-39；分析：TC-35 | Phase 4 |
| API-38 | PATCH `/api/admin/merchants/{id}/status` | 平台启停店铺；平台管理员 | MERCH-01 | UI-14 | TC-39、TC-45；分析：TC-35 | Phase 4 |
| API-39 | GET `/api/admin/users` | 平台用户分页查询；平台管理员 | USER-05 | UI-15 | TC-40 | Phase 4 |
| API-40 | PATCH `/api/admin/users/{id}/status` | 平台启停用户账号；平台管理员 | USER-05 | UI-15 | TC-40；分析：TC-35 | Phase 4 |
| API-41 | POST `/api/merchant/orders/{id}/accept` | 商家接收本店订单；商家仅本店 | ORDER-07 | UI-05 | TC-25、TC-26、TC-44、TC-45；分析：TC-35、TC-42、TC-46 | Phase 7 |
| API-42 | GET `/api/admin/orders` | 平台订单分页只读查询；平台管理员 | ORDER-02 | UI-16 | TC-18、TC-34、TC-46 | Phase 6 |
| API-43 | GET `/api/admin/orders/{id}` | 平台订单详情只读查询；平台管理员 | ORDER-02 | UI-16 | TC-18、TC-46 | Phase 6 |
| API-44 | GET `/api/admin/stats/sales` | 平台销售额与订单量统计；平台管理员 | STAT-01 | UI-17 | TC-46 | Phase 8 |
| API-45 | GET `/api/admin/stats/hot-products` | 平台商品热销排行；平台管理员 | STAT-02 | UI-17 | TC-46 | Phase 8 |

### 3.2 每项原文请求、返回与限制

下列逐项保留第 5.3 节的“对应功能／页面、请求、成功与返回、异常与限制”，避免追踪关系抹平原文差异。对象字段（User、Category、Product、OrderSummary、OrderDetail、Sales、HotProducts、Merchant）、QOrder、PEdit、OCreate 的完整公共契约见[需求基线](REQUIREMENTS_BASELINE.md)及源文第 5.1 节；不得自行补充未声明字段。

HTTP 与业务码共同使用：400/40001 参数，401/40101或40102 认证，403/40301或40302 权限，404/40401～40405 资源，409/40901～40915 业务冲突，423/42301 临时锁定，500/50001 系统故障。具体业务码含义以原文第 3.7 节及每项限制为准，不表示 40901～40915 各接口全部适用。受保护 API 均执行第 5.1、5.4 节身份、数据库当前角色/状态、token_version 和归属校验；每项列出的异常之外仍适用这些公共约定。

#### API-01 用户登录

`POST /api/auth/login`；权限：匿名。

对应功能：USER-02；页面：UI-01。

请求：JSON：username、password 必填。账号为注册手机号；不接收 role。

成功与返回：HTTP 200；data={token,tokenType:"Bearer",expiresAt,user:User}。连续失败第 5 次写 lockedUntil=当前时间+10分钟；锁定期间拒绝登录，期满下一次尝试先清零，成功登录清零。

异常与限制：40101 账号密码错误，40301 禁用，42301 临时锁定。失败计数更新必须并发安全；不存在账号仍返回统一错误。

#### API-02 公开商品分页查询

`GET /api/products`；权限：匿名。

对应功能：PROD-01；页面：UI-02。

请求：Query：merchantId 必填；page、size、categoryId、keyword 可选，categoryId 必须属于所选店铺。

成功与返回：HTTP 200；Product 分页，仅所选可用商家的上架商品及启用分类；售罄可见，无匹配 records=[]。

异常与限制：40001 merchantId 缺失或格式错误；40404 商家不可用；非本店或禁用分类筛选返回空列表。

#### API-03 新增商品

`POST /api/merchant/products`；权限：商家仅本店。

对应功能：PROD-02；页面：UI-04。

请求：JSON：PEdit，另可传 stock，默认 0；不得指定 status。categoryId 必须存在，禁用分类中允许新建下架商品。

成功与返回：HTTP 201；data=Product，status 固定为 0。

异常与限制：40001 参数、金额或库存越界；40403 分类不存在。

#### API-04 编辑商品基本信息

`PUT /api/merchant/products/{id}`；权限：商家仅本店。

对应功能：PROD-02；页面：UI-04。

请求：Path：id；JSON：完整 PEdit；不接收 stock、status。已上架商品改入禁用分类时拒绝，须先下架。

成功与返回：HTTP 200；data=更新后的 Product。仅修改当前商品，不修改订单快照。

异常与限制：40401 商品不存在；40403 分类不存在；40913 分类禁用；40001 参数错误。

#### API-05 删除无历史引用商品

`DELETE /api/merchant/products/{id}`；权限：商家仅本店。

对应功能：PROD-02；页面：UI-04。

请求：Path：id；无请求体。校验所有订单明细，包括已取消订单，均无该商品引用。

成功与返回：HTTP 200；data={id,deleted:true}。删除校验与删除同事务，以外键阻止并发新增引用。

异常与限制：40401 不存在；40910 已有订单引用，提示使用下架；重复删除返回 40401。

#### API-06 创建订单

`POST /api/orders`；权限：顾客本人。

对应功能：ORDER-01、ORDER-06；页面：UI-09。

请求：JSON：OCreate，必须包含所选 merchantId；归属、计价与事务见 BR-01～06。userId、商品价格、金额和状态均由后端确定。

成功与返回：首次成功 HTTP 201，data=OrderDetail，replayed=false，status=0；同 requestId 同内容重放 HTTP 200、replayed=true，返回原订单当前状态。createTime、expireTime 由后端生成。

异常与限制：40404 商家不存在；40915 店铺不可用；40001 混店商品；40401 商品不存在；40902 下架；40903 库存不足；40913 分类禁用；40907 内容冲突；40912 处理中；50001 整单回滚。

#### API-07 商家订单分页查询

`GET /api/merchant/orders`；权限：商家仅本店。

对应功能：ORDER-02；页面：UI-05。

请求：Query：QOrder，另可选 userId；后端强制 merchant_id=当前店铺，客户端不得指定或覆盖 merchantId；全部条件按 AND 组合。

成功与返回：HTTP 200；OrderSummary 分页，仅本店用户订单。

异常与限制：40001 条件或时间错误；用户或平台管理员调用商家接口返回 40302。

#### API-08 商家推进订单状态

`PATCH /api/merchant/orders/{id}/status`；权限：商家仅本店。

对应功能：ORDER-03；页面：UI-05。

请求：Path：本店订单 id；JSON：expectedStatus、status 必填。目标只允许 5、2、6、3，制作须当前已接单为 1；7→1 只能使用 API-41。

成功与返回：HTTP 200；OrderDetail，写 startTime、deliveryTime、readyTime 或 completeTime；更新条件含 id、merchant_id、expectedStatus、合法类型分支。

异常与限制：40402 不存在或非本店；40906 原状态变化、跳级、回退、重复或类型错误；不允许支付、接单或取消动作。

#### API-09 销售额与订单量统计

`GET /api/merchant/stats/sales`；权限：商家仅本店。

对应功能：STAT-01；页面：UI-06。

请求：Query：granularity、startDate、endDate 必填；固定统计当前商家，禁止传入 merchantId 覆盖。日期规则见 STAT-R02、03。

成功与返回：HTTP 200；data=Sales；只统计 complete_time 落在范围内的已完成订单，订单金额按主表独立聚合。

异常与限制：40001 日期错误、倒置、跨度超限、按月但非月初；无数据返回补零序列，不返回 404。

#### API-10 顾客注册

`POST /api/auth/register`；权限：匿名。

对应功能：USER-01；页面：UI-08。

请求：JSON：phone、nickname、password 必填；后端设置 username=phone、role=CUSTOMER、status=1，不接收权限字段。

成功与返回：HTTP 201；data=User，不自动登录、不返回密码散列。

异常与限制：40901 手机号或账号重复；40001 格式错误；40302 尝试传入 role、status 等敏感字段。

#### API-11 退出登录

`POST /api/auth/logout`；权限：三类已登录角色。

对应功能：USER-02；页面：所有已登录页面。

请求：无请求体；从 JWT 取当前用户，原子递增其 token_version。

成功与返回：HTTP 200；data=null。该账号所有已有 JWT 同时失效；前端清除 Pinia 与本地令牌并回登录页。

异常与限制：40102 登录已失效；前端即使收到 401 也清除本地状态。

#### API-12 读取本人资料

`GET /api/users/me`；权限：三类已登录角色。

对应功能：USER-03；页面：UI-07。

请求：无参数；不接收其他用户 ID。

成功与返回：HTTP 200；data=User，仅当前账号。

异常与限制：40102 未登录；40301 账号禁用。

#### API-13 修改本人昵称

`PATCH /api/users/me`；权限：三类已登录角色。

对应功能：USER-03；页面：UI-07。

请求：JSON：nickname 必填，1～30 字符；手机号、账号、角色和状态均只读。

成功与返回：HTTP 200；data=更新后的 User。

异常与限制：40001 昵称格式错误；40302 修改受保护字段。

#### API-14 修改本人密码

`PATCH /api/users/me/password`；权限：三类已登录角色。

对应功能：USER-04；页面：UI-07。

请求：JSON：oldPassword、newPassword 必填；6～20 位可见非空白字符，新旧密码不同。

成功与返回：HTTP 200；data=null。原密码验证、写新 BCrypt 散列、token_version 加 1 同事务；完成后重新登录。

异常与限制：40101 原密码错误；40001 新密码格式错误或与原密码相同；不返回新令牌。

#### API-15 公开商品详情

`GET /api/products/{id}`；权限：匿名。

对应功能：PROD-01；页面：UI-10。

请求：Path：id。

成功与返回：HTTP 200；data=Product，展示名称、规格、单价、库存、图片与描述。

异常与限制：40401 商品不存在、下架或分类禁用；40404 所属店铺不可用，不泄露后台商品。

#### API-16 公开分类查询

`GET /api/categories`；权限：匿名。

对应功能：PROD-01；页面：UI-02。

请求：Query：merchantId 必填，必须指向可用店铺。

成功与返回：HTTP 200；data=Category 数组，仅启用分类，按 sort、id 升序；无数据 []。

异常与限制：40001 缺少或非法 merchantId；40404 店铺不可用；本店无启用分类返回 []。

#### API-17 后台商品分页查询

`GET /api/merchant/products`；权限：商家仅本店。

对应功能：PROD-02；页面：UI-04。

请求：Query：page、size、categoryId、keyword、status 可选；后端固定本店，categoryId 只能本店，不收客户端 merchantId。可查本店下架商品。

成功与返回：HTTP 200；data=Product 分页，含真实当前库存与上下架状态。

异常与限制：40001 参数错误。

#### API-18 后台商品详情

`GET /api/merchant/products/{id}`；权限：商家仅本店。

对应功能：PROD-02；页面：UI-04。

请求：Path：id。

成功与返回：HTTP 200；data=Product，用于编辑回显与库存刷新，可查下架商品。

异常与限制：40401 商品不存在或非本店。

#### API-19 后台分类查询

`GET /api/merchant/categories`；权限：商家仅本店。

对应功能：PROD-04；页面：UI-11、04。

请求：Query：status（0/1）可选；仅返回当前店铺完整分类数组，不收 merchantId。

成功与返回：HTTP 200；data=Category 数组，含启用与禁用记录；供分类管理与商品编辑选择。

异常与限制：40001 状态错误。

#### API-20 新增分类

`POST /api/merchant/categories`；权限：商家仅本店。

对应功能：PROD-04；页面：UI-11。

请求：JSON：name 必填；sort 可选，默认 0，非负整数；status 固定为 1。

成功与返回：HTTP 201；data=Category。

异常与限制：40001 参数错误；40901 分类名称重复，msg 明确为“分类名称已存在”。

#### API-21 编辑分类名称与排序

`PUT /api/merchant/categories/{id}`；权限：商家仅本店。

对应功能：PROD-04；页面：UI-11。

请求：Path：id；JSON：name、sort 均必填；不接收 status。

成功与返回：HTTP 200；data=Category。

异常与限制：40403 分类不存在或非本店；40901 本店分类名称重复；40001 参数错误。

#### API-22 删除空分类

`DELETE /api/merchant/categories/{id}`；权限：商家仅本店。

对应功能：PROD-04；页面：UI-11。

请求：Path：本店分类 id；仅本分类下无任何商品时可删，无请求体。

成功与返回：HTTP 200；data={id,deleted:true}。

异常与限制：40403 不存在；40909 分类仍有商品；外键与事务保证并发安全。

#### API-23 本人订单分页查询

`GET /api/orders`；权限：顾客本人。

对应功能：ORDER-02、ORDER-06；页面：UI-03。

请求：Query：QOrder；不允许 userId，后端强制加当前 user_id 条件。

成功与返回：HTTP 200；data=OrderSummary 分页；返回服务器计算的状态、截止时间和 allowedActions。

异常与限制：40001 筛选错误；40302 商家与平台管理员应使用各自订单查询接口。

#### API-24 本人订单详情

`GET /api/orders/{id}`；权限：顾客本人。

对应功能：ORDER-02、ORDER-06；页面：UI-03、09。

请求：Path：id；必须关联当前登录顾客。

成功与返回：HTTP 200；data=OrderDetail，明细使用全部下单快照。

异常与限制：40402 订单不存在或非本人；不可通过修改 URL 越权。

#### API-25 本人订单模拟支付

`POST /api/orders/{id}/payment`；权限：顾客本人。

对应功能：ORDER-05、ORDER-06；页面：UI-03、09。

请求：Path：id；JSON：result 必填，success 或 fail，不接收金额。必须未超时、status=0，详见 PAY-01～04。

成功与返回：result=success 合法时 HTTP 200、OrderDetail，status=7 并写 payTime；等待本店商家接单。result=fail 返回 40914，订单不变。

异常与限制：40402 不存在或非本人；40904 已支付；40908 超时；40915 店铺不可用，后二者先提交取消及回补；40906 状态禁止；40914 模拟失败。

#### API-26 顾客取消本人待付款订单

`POST /api/orders/{id}/cancel`；权限：顾客本人。

对应功能：ORDER-04；页面：UI-03、09。

请求：Path：id；JSON：reason 必填，1～200 字符；只允许当前状态 0。

成功与返回：HTTP 200；data=OrderDetail，status=4；取消时间、原因和全部库存回补同事务。

异常与限制：40402 不存在或非本人；40905 已取消；40906 已支付或其他不允许状态；不再次回补。

#### API-27 后台订单详情

`GET /api/merchant/orders/{id}`；权限：商家仅本店。

对应功能：ORDER-02；页面：UI-05。

请求：Path：id。

成功与返回：HTTP 200；data=OrderDetail，含 userId、商品快照、地址与全生命周期时间。

异常与限制：40402 订单不存在或非本店。

#### API-28 商家取消未制作订单

`POST /api/merchant/orders/{id}/cancel`；权限：商家仅本店。

对应功能：ORDER-04；页面：UI-05。

请求：Path：本店订单 id；JSON：reason 必填，1～200 字符；仅状态 0、7 或 1，状态 7 表示拒单。

成功与返回：HTTP 200；data=OrderDetail，status=4，回补全部库存；原 payTime 保留，已模拟支付款项仅作废。

异常与限制：40402 不存在或非本店；40905 已取消；40906 制作中及之后不得取消；平台管理员不能调用此接口。

#### API-29 商家调整商品库存

`PATCH /api/merchant/products/{id}/stock`；权限：商家仅本店。

对应功能：PROD-03；页面：UI-04。

请求：Path：id；JSON：delta、expectedStock 必填。delta 为非零整数，expectedStock 为非负整数，stock+delta 结果须在 INT 非负范围；不得直接覆盖 stock。

成功与返回：HTTP 200；data={id,stock,updateTime}，条件更新必须匹配当前库存；与扣减、回补并发时以数据库结果为准。

异常与限制：40401 不存在；40911 当前库存已变化；40001 调整后负数、超限或 delta=0。

#### API-30 商家商品上下架

`PATCH /api/merchant/products/{id}/status`；权限：商家仅本店。

对应功能：PROD-03；页面：UI-04。

请求：Path：id；JSON：status（0/1）必填；上架须分类启用。库存 0 允许上架，但前台显示售罄且禁止下单。

成功与返回：HTTP 200；data=Product。重复设置同一展示状态返回当前对象且不改 updateTime；此规则不适用于订单状态。

异常与限制：40401 不存在；40913 分类禁用；40001 状态非法。

#### API-31 商家启停分类

`PATCH /api/merchant/categories/{id}/status`；权限：商家仅本店。

对应功能：PROD-04；页面：UI-11。

请求：Path：id；JSON：status（0/1）必填；禁用前须无上架商品，检查与更新同事务，和商品上架使用相同分类锁。

成功与返回：HTTP 200；data=Category；重复设置当前分类状态返回当前对象且不改 updateTime。

异常与限制：40403 不存在；40909 分类仍有上架商品；40001 参数错误。

#### API-32 商品热销前十排行

`GET /api/merchant/stats/hot-products`；权限：商家仅本店。

对应功能：STAT-02；页面：UI-06。

请求：Query：startDate、endDate 必填，半开区间最多 366 天；固定本店，禁止用 merchantId 覆盖；固定前 10 名。

成功与返回：HTTP 200；data=HotProducts；仅已完成明细按 productId 聚合，销量和金额及标签规则见 3.6。

异常与限制：40001 日期或范围错误；无数据 items=[]；顾客访问 40302。

#### API-33 公开商家分页查询

`GET /api/merchants`；权限：匿名。

对应功能：MERCH-02；页面：UI-12。

请求：Query：page、size、keyword（店铺名≤60）可选。

成功与返回：Merchant 分页，仅店铺 status=1 且商家账号启用；按 id 降序，无匹配为空。

异常与限制：40001 参数错误；不公开停用店铺或账号信息。

#### API-34 公开商家详情

`GET /api/merchants/{id}`；权限：匿名。

对应功能：MERCH-02；页面：UI-13。

请求：Path：商家 id。

成功与返回：Merchant，展示店名、电话、地址和简介；商品及分类分别用 API-02、16 并传该 id。

异常与限制：40404 商家不存在、停用或账号禁用。

#### API-35 平台商家分页查询

`GET /api/admin/merchants`；权限：平台管理员。

对应功能：MERCH-01；页面：UI-14。

请求：Query：page、size、keyword、status（0/1）可选。

成功与返回：Merchant 分页，含停用店铺及绑定账号信息，用于列表与编辑回显。

异常与限制：40001 筛选错误；MERCHANT、CUSTOMER 调用 40302。

#### API-36 平台创建商家账号与店铺

`POST /api/admin/merchants`；权限：平台管理员。

对应功能：MERCH-01；页面：UI-14。

请求：JSON：accountPhone、nickname、password、name、phone、address 必填，description 可空；沿用手机号、密码及长度约束。拒绝 role、userId、status。

成功与返回：HTTP 201；Merchant。一个事务创建 username=accountPhone、role=MERCHANT、status=1 的账号和启用店铺；密码散列，不返回密码，不转换已有用户角色。

异常与限制：40901 账号手机号已使用；40001 参数错误；任一步失败账号和店铺均回滚。

#### API-37 平台编辑店铺信息

`PUT /api/admin/merchants/{id}`；权限：平台管理员。

对应功能：MERCH-01；页面：UI-14。

请求：Path：id；JSON：name（1～60）、phone、address（1～120）必填，description≤500 可空；不收 userId、role、status 或账号手机号。

成功与返回：Merchant；只更新店铺信息，保持账号绑定及历史订单 merchant_name 快照。

异常与限制：40404 不存在；40001 字段错误；40302 尝试换绑或提权。

#### API-38 平台启停店铺

`PATCH /api/admin/merchants/{id}/status`；权限：平台管理员。

对应功能：MERCH-01；页面：UI-14。

请求：Path：id；JSON：status（0/1）必填。

成功与返回：Merchant；停用后隐藏公开入口、阻止新单，不改写历史订单；已支付订单仍由本店商家履约。重复设置同值返回当前对象。

异常与限制：40404 不存在；40001 状态错误；停用不物理删除账号、商品或订单。

#### API-39 平台用户分页查询

`GET /api/admin/users`；权限：平台管理员。

对应功能：USER-05；页面：UI-15。

请求：Query：page、size、keyword、status 可选；后端固定 role=CUSTOMER，不接收 role 筛选。

成功与返回：User 分页，只含用户账号；昵称或手机号按关键字查询，不返回密码等内部字段。

异常与限制：40001 参数错误；其他角色调用 40302。

#### API-40 平台启停用户账号

`PATCH /api/admin/users/{id}/status`；权限：平台管理员。

对应功能：USER-05；页面：UI-15。

请求：Path：CUSTOMER 用户 id；JSON：status（0/1）必填。

成功与返回：User；变更状态并递增 token_version 同事务，旧令牌失效，启用后须重新登录；历史订单不删除。

异常与限制：40405 用户不存在或不是 CUSTOMER；40001 状态错误；禁止管理 ADMIN、MERCHANT 或变更角色。

#### API-41 商家接收本店订单

`POST /api/merchant/orders/{id}/accept`；权限：商家仅本店。

对应功能：ORDER-07；页面：UI-05。

请求：Path：本店订单 id；JSON：expectedStatus 必填且为 7。

成功与返回：OrderDetail；条件 id、merchant_id、status=7，原子变为 1 并写 acceptTime，不再扣库。店铺已停用时仍可处理此前已支付订单。

异常与限制：40402 不存在或非本店；40906 非待接单或重复接单；不写重复时间、不产生二次扣库。

#### API-42 平台订单分页只读查询

`GET /api/admin/orders`；权限：平台管理员。

对应功能：ORDER-02；页面：UI-16。

请求：Query：QOrder，merchantId、userId 可选。

成功与返回：OrderSummary 分页，按商家或用户筛选，不提供接单、取消或履约按钮，allowedActions=[]。

异常与限制：40001 筛选错误；其他角色调用 40302。

#### API-43 平台订单详情只读查询

`GET /api/admin/orders/{id}`；权限：平台管理员。

对应功能：ORDER-02；页面：UI-16。

请求：Path：id。

成功与返回：OrderDetail，含商家与商品快照、顾客联系信息和事件时间；allowedActions=[]。

异常与限制：40402 订单不存在；不提供任何订单写操作。

#### API-44 平台销售额与订单量统计

`GET /api/admin/stats/sales`；权限：平台管理员。

对应功能：STAT-01；页面：UI-17。

请求：Query：granularity、startDate、endDate 必填，merchantId 可选。

成功与返回：Sales；不传 merchantId 为全平台，传值按该店过滤，含停用店历史；完成时间、补零规则同 API-09。

异常与限制：40001 参数或范围错误；40404 指定商家不存在，停用商家可查。

#### API-45 平台商品热销排行

`GET /api/admin/stats/hot-products`；权限：平台管理员。

对应功能：STAT-02；页面：UI-17。

请求：Query：startDate、endDate 必填，merchantId 可选。

成功与返回：HotProducts；全平台或指定商家已完成明细前 10 名，每项含商家标识和快照；口径同 API-32。

异常与限制：40001 参数或范围错误；40404 指定商家不存在；无数据 items=[]。

## 4. 17 个页面反向追踪

原文第 6.1 节的页面交互、功能/API 关系保留如下；角色限制为按第 2.2、5.1 节汇总的分析。公共 API-11 由所有已登录页面导航承接，未擅自在每个原文页面 API 列中追加。每页分析回归场景来自其原文 API 与对应 TC，UI-01～17 全部还需执行 TC-36。Phase 9 只形成视觉和公共组件体系，不在该阶段冒称 17 页完成。

| 页面 | 路由 | 所属角色 / 权限限制 | 原文页面功能与操作 | 原文功能/API关系 | 分析回归 TC | 页面阶段 |
| --- | --- | --- | --- | --- | --- | --- |
| UI-01 登录页 | `/login` | 匿名；三类角色共用登录 | 三类角色共用登录，成功后 ADMIN 进入平台商家管理、MERCHANT 进入本店订单、CUSTOMER 进入商家列表；不提供角色提权选择。 | USER-02；API-01 | TC-03、TC-04、TC-36 | Phase 10 |
| UI-02 本店商品页 | `/shop/:merchantId` | 匿名及三类角色公开只读 | 先展示所选店铺名称、电话与地址，再筛选本店分类、商品和规格；售罄禁用下单，切换店铺须确认清空当前选购。 | PROD-01；API-02、16 | TC-05、TC-07、TC-36、TC-41 | Phase 10 |
| UI-03 我的订单页 | `/orders/mine` | CUSTOMER，仅本人 | 仅本人订单，显示所属商家快照；按状态、类型和日期筛选。待付款可模拟支付、取消，支付后显示“待商家接单”，已支付无用户取消按钮。 | ORDER-02、04、05、06；API-23、24、25、26 | TC-05、TC-18、TC-19、TC-20、TC-21、TC-22、TC-23、TC-24、TC-25、TC-26、TC-27、TC-29、TC-34、TC-36、TC-45 | Phase 10 |
| UI-04 商家商品管理 | `/merchant/products` | MERCHANT，仅本店 | 仅本店商品查询、分页、编辑、删除、上下架和库存调整；分类选择只列本店，merchantId 只读，保留库存增减量和历史引用保护。 | PROD-02、03、04；API-03、04、05、17、18、19、29、30 | TC-05、TC-08、TC-09、TC-10、TC-11、TC-29、TC-35、TC-36、TC-38、TC-42、TC-46 | Phase 11 |
| UI-05 商家订单管理 | `/merchant/orders` | MERCHANT，仅本店 | 仅本店订单和详情；待接单显示接单、拒单，接单后才可开始制作，再配送或备妥、完成。写操作二次确认，拒单取消必填原因，自提核对订单号与电话。 | ORDER-02、03、04、07；API-07、08、27、28、41 | TC-05、TC-18、TC-23、TC-24、TC-25、TC-26、TC-27、TC-28、TC-29、TC-34、TC-35、TC-36、TC-42、TC-44、TC-45、TC-46 | Phase 11 |
| UI-06 商家数据统计 | `/merchant/dashboard` | MERCHANT，仅本店 | 按日/月和完成时间查询本店销售额、订单量及前 10 名商品；不提供切换至他店的数据入口，无数据补零。 | STAT-01、02；API-09、32 | TC-05、TC-30、TC-31、TC-32、TC-35、TC-36、TC-42 | Phase 11 |
| UI-07 个人中心 | `/profile` | ADMIN / MERCHANT / CUSTOMER，仅本人 | 三类角色只修改本人昵称；账号和手机号只读。密码修改需原密码与新密码确认，成功重新登录；商家额外显示只读绑定店铺。 | USER-03、04；API-12、13、14 | TC-04、TC-05、TC-06、TC-29、TC-36 | Phase 10 |
| UI-08 注册页 | `/register` | 匿名；仅注册为 CUSTOMER | 仅用户注册：手机号、昵称、密码及确认；不提供管理员或商家角色选择，提示商家账号由平台创建，成功跳登录。 | USER-01；API-10 | TC-01、TC-02、TC-36 | Phase 10 |
| UI-09 订单确认页 | `/checkout` | CUSTOMER，仅本人 | 显示唯一所选商家、规格数量、履约方式与联系信息；检测跨店商品并阻止提交。请求带 merchantId 和 requestId，成功采用后端金额，结果可支付或取消。 | ORDER-01、02、04、05、06；API-06、24、25、26 | TC-05、TC-12、TC-13、TC-14、TC-15、TC-16、TC-17、TC-18、TC-19、TC-20、TC-21、TC-22、TC-23、TC-24、TC-25、TC-26、TC-27、TC-29、TC-36、TC-38、TC-43、TC-45 | Phase 10 |
| UI-10 商品详情页 | `/shop/products/:id` | 匿名及三类角色公开只读 | 展示商品所属商家、规格、价格、库存及详情；携带该店归属进入确认页，匿名先登录，店铺停用或商品不可见时友好返回商家列表。 | PROD-01；API-15 | TC-05、TC-07、TC-36 | Phase 10 |
| UI-11 商家分类管理 | `/merchant/categories` | MERCHANT，仅本店 | 仅本店分类列表、新增、名称排序编辑、启停与删除；同店名称唯一，不同店可同名，约束失败显示具体提示。 | PROD-04；API-19、20、21、22、31 | TC-05、TC-09、TC-10、TC-35、TC-36、TC-42 | Phase 11 |
| UI-12 商家列表 | `/merchants` | 匿名及三类角色公开只读 | 展示启用店铺名称、简介与搜索分页，选择一家店进入店铺详情；不显示停用商家。 | MERCH-02；API-33 | TC-05、TC-36、TC-41 | Phase 10 |
| UI-13 店铺详情 | `/merchants/:id` | 匿名及三类角色公开只读 | 展示店名、电话、地址、简介；进入本店商品页，商品查询固定携带该 merchantId；不可用店铺提示并返回列表。 | MERCH-02、PROD-01；API-34、02、16 | TC-05、TC-07、TC-36、TC-41 | Phase 10 |
| UI-14 平台商家管理 | `/admin/merchants` | ADMIN | 查询商家与绑定账号，创建商家账号和店铺、编辑店铺信息、启停确认；不删除店铺、不代操作商品订单。 | MERCH-01；API-35、36、37、38 | TC-05、TC-29、TC-35、TC-36、TC-39、TC-45 | Phase 12 |
| UI-15 平台用户管理 | `/admin/users` | ADMIN | 分页筛选用户昵称、手机号及启用状态，确认启停；不显示密码、不改角色、不删除历史订单。 | USER-05；API-39、40 | TC-05、TC-35、TC-36、TC-40 | Phase 12 |
| UI-16 平台订单查看 | `/admin/orders` | ADMIN | 全平台订单按商家、用户和状态筛选，只读详情含接单时间；不出现支付、接单、取消或履约按钮。 | ORDER-02；API-42、43 | TC-05、TC-18、TC-34、TC-36、TC-46 | Phase 12 |
| UI-17 平台数据统计 | `/admin/dashboard` | ADMIN | 查看全平台或指定商家的销售额、订单量和热销排行；沿用完成时间和补零规则，店铺停用不丢历史统计。 | STAT-01、02；API-44、45 | TC-05、TC-36、TC-46 | Phase 12 |

## 5. 46 个验收场景原文及反向映射

“原文需求／规则”“原文接口或页面”“输入与操作”“预期结果”逐项保留源文第 8.2 节。源文把输入条件和执行操作合并在同一列，本表保留这一表达；未自行补造测试数据或把场景执行为事实。分析映射用于补齐回归范围，具体测试脚本、实际结果和证据留待开发阶段。所有场景当前状态均为 **未执行**。

| TC | 原文需求 / 规则 | 原文接口或页面 | 原文输入与操作 | 原文预期结果 |
| --- | --- | --- | --- | --- |
| TC-01 | USER-01 | API-10 | 使用新手机号正常注册。 | 仅生成 CUSTOMER；密码为散列；同号再注册 40901。 |
| TC-02 | USER-01；权限 | API-10 | 公开注册附带 role=ADMIN、role=MERCHANT、merchantId 或 status。 | 40302 拒绝，无新增管理员、商家账号或越权用户。 |
| TC-03 | USER-02 | API-01 | 错误账号密码、连续失败5次、锁定期再登录及期满重试。 | 错误统一40101；第五次锁10分钟；期满可重试，成功清零。 |
| TC-04 | USER-02；权限 | API-01、12 | 禁用账号登录；已有JWT后禁用账号；篡改、过期JWT。 | 禁用40301；过期或篡改40102，保护请求不执行。 |
| TC-05 | USER-02 | API-11、12 | 同账号两次登录，退出其中一次后重用两个旧JWT。 | 退出成功，两个旧令牌均失效，本地登录态清除。 |
| TC-06 | USER-03、USER-04 | API-12、13、14 | 改昵称；尝试改角色；错误原密码及正确修改密码。 | 只改本人昵称；敏感字段拒绝；错误原密码40101；成功后旧JWT失效。 |
| TC-07 | PROD-01 | API-02、15、16 | 先选择店铺，匿名筛选本店分类、商品详情，查看下架或售罄商品。 | 只见所选可用店铺上架商品和启用分类；售罄不可下单，下架 40401，停用店铺 40404。 |
| TC-08 | PROD-02；规格 | API-03、04、17、18 | 商家在本店建立同名 6 寸、8 寸两商品并编辑；尝试使用他店分类。 | 独立 ID、价格、库存；默认下架、普通编辑不覆盖库存；他店分类 40403，不保存。 |
| TC-09 | PROD-02、PROD-04 | API-05、22 | 删除空分类与无引用商品；再删除有商品分类与有明细商品。 | 前者成功；后者40909或40910，历史订单明细保留。 |
| TC-10 | PROD-03、PROD-04 | API-19～22、30、31 | 商家维护本店分类，测同店重名、不同店同名、分类停用及上架。 | 同店重名 40901、不同店同名允许；有上架商品不能停用，禁用分类不可上架。 |
| TC-11 | PROD-03；BR-09 | API-29 | 增减库存；两窗口用同一旧expectedStock调整；尝试调成负数。 | 第一次合法更新，旧值40911；负数40001，无覆盖或负库存。 |
| TC-12 | ORDER-01；BR-01～03 | API-06 | 选一家商家，提交其两个商品的配送订单，另测篡改金额字段。 | 合法请求按数据库价格计价；额外金额字段拒绝；订单明细库存同事务。 |
| TC-13 | ORDER-01；BR-01 | API-06 | 同商品重复行合并；数量0、100、缺联系人、配送无地址或自提带地址。 | 合并后1～99；非法参数40001，数据库无订单、明细或扣库。 |
| TC-14 | ORDER-01；BR-04 | API-06 | 多商品中最后一个库存不足，另测商品已删除或下架。 | 整单失败并回滚先前扣减；指出不足商品；无部分订单。 |
| TC-15 | ORDER-01；BR-04 | API-06 | 库存1，两顾客同时各买1。 | 仅一笔成功，另一笔不足；最终库存0，无超卖。 |
| TC-16 | ORDER-01；BR-05、06 | API-06 | 同用户同requestId同内容并发、网络超时后重试；再换内容同号。 | 至多一单一扣，重复返回原单；不同内容40907；处理中复用原号。 |
| TC-17 | ORDER-01；BR-03、11 | API-06 | 扣库后或保存第二条明细前注入数据库失败。 | 主表、所有明细、全部库存回滚；同请求号可重新尝试。 |
| TC-18 | ORDER-02；权限 | API-07、23、24、27、42、43 | 用户查本人和他人订单；商家查本店和他店订单；平台只读查询全平台。 | 用户仅本人、商家仅本店；非归属统一 40402；平台全平台可读且 allowedActions=[]，无订单写权限。 |
| TC-19 | ORDER-05；PAY-01 | API-25 | 本人未超时待付款模拟成功后重复支付。 | 第一次 0→7、payTime 仅写一次，acceptTime 为空；重复 40904，不重复扣库。 |
| TC-20 | ORDER-05；PAY-02 | API-25 | 本人待付款传result=fail；他人或匿名调用支付。 | 模拟失败40914状态时间不变；他人40402，匿名40102。 |
| TC-21 | ORDER-06 | API-25；超时任务 | 数据库时间等于或晚于expireTime时支付；重复运行超时扫描。 | 支付不成功；提交取消与全量回补，仅一次；扫描正常时延后≤60秒。 |
| TC-22 | ORDER-05、ORDER-06；BR-08 | API-25；超时任务 | 在截止点附近并发支付与超时取消。 | 只一个分支成功；不存在已支付又已回补的非取消订单。 |
| TC-23 | ORDER-04；BR-07 | API-26、28 | 用户取消本人待付款；商家拒绝待接单及取消待制作；用户试取消已支付。 | 商家限本店，合法取消全量回补并保留支付和已发生接单时间；用户已支付取消 40906。 |
| TC-24 | ORDER-04；BR-07、08 | API-26、28 | 并发重复取消；回补第2商品前注入故障。 | 仅一个取消生效且一回补；故障则状态与全部库存一起回滚。 |
| TC-25 | ORDER-03 | API-25、41、08 | 用户选店，配送订单按 0→7→1→5→2→3，商家先接单再制作。 | 支付、接单、制作、配送与完成时间正确；制作后不可取消，完成后统计可见。 |
| TC-26 | ORDER-03 | API-25、41、08 | 自提订单按 0→7→1→5→6→3，尝试未接单直接制作或 5→2。 | 自提备妥、核对交付后完成；错误配送分支40906。 |
| TC-27 | ORDER-03、ORDER-04 | API-08、26、28 | 尝试0→3、5→1、重复推进、对3或4变更及回补。 | 全部40906或40905；事件时间、金额和库存不被重写。 |
| TC-28 | ORDER-03、ORDER-04；BR-08 | API-08、28 | 本店已接单待制作订单同时收到开始制作与商家取消。 | 仅一个提交；若制作先成功则取消失败，若取消先成功则制作失败。 |
| TC-29 | 规格；DATA-07 | API-37、04、24、27 | 下单后改店铺名、商品名称、规格、价格及用户昵称。 | 历史商家、商品与联系快照及金额保持，归属不转移。 |
| TC-30 | STAT-01；STAT-R01～03 | API-09 | 构造创建、支付、完成时间跨日跨月订单，混入取消及未完成。 | 仅完成时间计入；起点含、终点不含；订单不因多明细重复计数。 |
| TC-31 | STAT-01、STAT-02；STAT-R04 | API-09、32 | 查询全无数据与中间缺失日期的范围。 | 汇总为0.00与0；日/月序列补零；排行为空数组。 |
| TC-32 | STAT-02；STAT-R05 | API-32 | 超过10种商品、同销量并列、独立规格、下架及改名商品。 | 按销量、明细额、ID稳定排序取10；读最新完成快照标签，不丢下架历史。 |
| TC-33 | 参数；异常处理 | 全部相关接口 | 缺字段、非法枚举、超长、错误日期、反向范围、统计超过366天。 | 40001及字段提示；不泄露SQL，不写脏数据。 |
| TC-34 | NFR-01 | API-07、23、42 | 1 万订单覆盖至少两家商家，平台查全量、商家查本店、用户查本人；记录配置和查询条件并逐项计时。 | 各常规分页与筛选响应≤2秒，保存原始计时记录；商家和用户响应不含越权数据。 |
| TC-35 | NFR-02；权限 | 后台写接口与API-09、32 | 用户直调平台与商家接口，商家直调平台接口；关键字含 SQL、HTML、通配符。 | 角色不符 40302；商家接口强制本店；参数绑定、字面搜索，页面不执行输入脚本。 |
| TC-36 | NFR-04、05；界面 | UI-01～17 | 两浏览器、两分辨率检查表单、确认、加载、错误与空状态。 | 无错位遮挡；收到响应1秒内反馈；跳登录和错误恢复正确。 |
| TC-37 | NFR-06、07；DATA-01 | 全部接口与初始化 | 按README重建数据库、分别启动前后端并核对Knife4j。 | 六张表及商家归属约束、索引完整；45 项接口与实现一致，测试记录可追溯。 |
| TC-38 | 金额；DATA-02 | API-03、04、06 | 单价多于2位小数、单行或订单总额超过DECIMAL上限。 | 40001拒绝，不静默舍入或溢出；无库存扣减、无半笔订单。 |
| TC-39 | MERCH-01；DATA-08 | API-35～38；UI-14 | 平台新建商家及店铺，另测重复账号、创建中故障、编辑与停用。 | 仅 ADMIN 允许；账号为 MERCHANT 且一店一号；失败全回滚；改名不改历史快照，停用不删除数据。 |
| TC-40 | USER-05；权限 | API-39、40；UI-15 | 平台查询并停用、启用用户，旧 JWT 重试；尝试修改 ADMIN、MERCHANT 或角色。 | 仅 CUSTOMER 可管理；禁用保护请求失败，恢复后须重新登录；旧令牌失效，历史订单保留。 |
| TC-41 | MERCH-02；PROD-01 | API-33、34、02、16；UI-12、13 | 准备 A、B 两家商家，选 A 浏览分类商品，再停用 B。 | A 页面仅显示 A 商品；停用 B 不公开展示，直接详情 40404；有清晰店铺名称、地址与电话。 |
| TC-42 | 商家隔离；BR-12 | 商家商品、分类、订单、统计接口 | A 商家用 B 的商品、分类、订单 ID 查询或写入，并尝试传 merchantId=B。 | 非归属资源统一 404；伪造归属拒绝且不写入，统计只含 A；B 数据和库存保持。 |
| TC-43 | ORDER-01；BR-01、03、05 | API-06；UI-09 | 同单混入 A、B 商品，或 merchantId=A 但全为 B 商品；同请求号换店重试。 | 混店 40001、无订单无扣库；同号换店 40907，不复用原单为新店下单。 |
| TC-44 | ORDER-07；BR-07、08 | API-41、28；UI-05 | 本店待接单 7 接单到 1、重复接单；另单拒单、接单和取消并发。 | 接单时间一次、无二次扣库；重复 40906；拒单全量回补且保留 payTime，竞争只一方提交。 |
| TC-45 | BR-12；PAY-01 | API-38、06、25、41、08 | 停用商家后新下单；原未付款订单支付；原已支付订单接单与履约。 | 新单 40915 不扣库；未付款先取消回补再报错；已支付由本店正常接单或拒单、履约，历史保留。 |
| TC-46 | 平台权限；STAT-R07 | API-42～45；UI-16、17 | 平台按 A/B 商家查订单统计；尝试调用商家接单、改商品、取消订单接口。 | 全平台及按店只读口径正确，含停用店历史；平台经营写请求 40302，订单 actions 为空。 |

### 5.1 TC → 功能 / API / UI / 阶段分析

本表是分析映射；原文映射仍以上表为准。通过 API 测试身份或越权边界时，关联的页面只表示后续 UI 回归点，不能据此声明该 TC 已直接测过页面。TC-33 仅针对“全部相关接口”；TC-37 明确覆盖全部接口与初始化；TC-36 明确覆盖全部页面。

“首测 / 回归阶段”包含分项准备与回归范围；完整场景须等待全部依赖具备后才能执行，不能在最早关联阶段就记为整项通过。例如 TC-29 的完整历史快照场景依赖到 Phase 6，TC-45 的完整停店支付/履约场景依赖到 Phase 7；页面回归另随 Phase 10～12 执行，最终统一在 Phase 13 验收。

| TC | 分析功能映射 | 原文编号展开的 API | 补充 API / 泛称范围（分析） | 分析 UI 回归点 | 首测 / 回归阶段（计划） | 最终验收 / 状态 |
| --- | --- | --- | --- | --- | --- | --- |
| TC-01 | USER-01 | API-10 | — | UI-08 | Phase 3 | Phase 13 / 未执行 |
| TC-02 | USER-01 | API-10 | — | UI-08 | Phase 3 | Phase 13 / 未执行 |
| TC-03 | USER-02 | API-01 | — | UI-01 | Phase 3 | Phase 13 / 未执行 |
| TC-04 | USER-02、USER-03 | API-01、API-12 | — | UI-01、UI-07 | Phase 3 | Phase 13 / 未执行 |
| TC-05 | USER-02、USER-03 | API-11、API-12 | — | UI-02、UI-03、UI-04、UI-05、UI-06、UI-07、UI-09、UI-10、UI-11、UI-12、UI-13、UI-14、UI-15、UI-16、UI-17 | Phase 3 | Phase 13 / 未执行 |
| TC-06 | USER-03、USER-04 | API-12、API-13、API-14 | — | UI-07 | Phase 3 | Phase 13 / 未执行 |
| TC-07 | PROD-01 | API-02、API-15、API-16 | — | UI-02、UI-10、UI-13 | Phase 5 | Phase 13 / 未执行 |
| TC-08 | PROD-02 | API-03、API-04、API-17、API-18 | — | UI-04 | Phase 5 | Phase 13 / 未执行 |
| TC-09 | PROD-02、PROD-04 | API-05、API-22 | — | UI-04、UI-11 | Phase 5 | Phase 13 / 未执行 |
| TC-10 | PROD-03、PROD-04 | API-19、API-20、API-21、API-22、API-30、API-31 | — | UI-04、UI-11 | Phase 5 | Phase 13 / 未执行 |
| TC-11 | PROD-03 | API-29 | — | UI-04 | Phase 5 | Phase 13 / 未执行 |
| TC-12 | ORDER-01、ORDER-06 | API-06 | — | UI-09 | Phase 6 | Phase 13 / 未执行 |
| TC-13 | ORDER-01、ORDER-06 | API-06 | — | UI-09 | Phase 6 | Phase 13 / 未执行 |
| TC-14 | ORDER-01、ORDER-06 | API-06 | — | UI-09 | Phase 6 | Phase 13 / 未执行 |
| TC-15 | ORDER-01、ORDER-06 | API-06 | — | UI-09 | Phase 6 | Phase 13 / 未执行 |
| TC-16 | ORDER-01、ORDER-06 | API-06 | — | UI-09 | Phase 6 | Phase 13 / 未执行 |
| TC-17 | ORDER-01、ORDER-06 | API-06 | — | UI-09 | Phase 6 | Phase 13 / 未执行 |
| TC-18 | ORDER-02、ORDER-06 | API-07、API-23、API-24、API-27、API-42、API-43 | — | UI-03、UI-05、UI-09、UI-16 | Phase 6 | Phase 13 / 未执行 |
| TC-19 | ORDER-05、ORDER-06 | API-25 | — | UI-03、UI-09 | Phase 7 | Phase 13 / 未执行 |
| TC-20 | ORDER-05、ORDER-06 | API-25 | — | UI-03、UI-09 | Phase 7 | Phase 13 / 未执行 |
| TC-21 | ORDER-05、ORDER-06 | API-25 | — | UI-03、UI-09 | Phase 7 | Phase 13 / 未执行 |
| TC-22 | ORDER-05、ORDER-06 | API-25 | — | UI-03、UI-09 | Phase 7 | Phase 13 / 未执行 |
| TC-23 | ORDER-04、ORDER-07 | API-26、API-28 | — | UI-03、UI-05、UI-09 | Phase 7 | Phase 13 / 未执行 |
| TC-24 | ORDER-04、ORDER-07 | API-26、API-28 | — | UI-03、UI-05、UI-09 | Phase 7 | Phase 13 / 未执行 |
| TC-25 | ORDER-03、ORDER-05、ORDER-06、ORDER-07 | API-08、API-25、API-41 | — | UI-03、UI-05、UI-09 | Phase 7 | Phase 13 / 未执行 |
| TC-26 | ORDER-03、ORDER-05、ORDER-06、ORDER-07 | API-08、API-25、API-41 | — | UI-03、UI-05、UI-09 | Phase 7 | Phase 13 / 未执行 |
| TC-27 | ORDER-03、ORDER-04、ORDER-07 | API-08、API-26、API-28 | — | UI-03、UI-05、UI-09 | Phase 7 | Phase 13 / 未执行 |
| TC-28 | ORDER-03、ORDER-04、ORDER-07 | API-08、API-28 | — | UI-05 | Phase 7 | Phase 13 / 未执行 |
| TC-29 | MERCH-01、ORDER-02、ORDER-06、PROD-02、USER-03 | API-04、API-24、API-27、API-37 | API-13 | UI-03、UI-04、UI-05、UI-07、UI-09、UI-14 | Phase 3、Phase 4、Phase 5、Phase 6 | Phase 13 / 未执行 |
| TC-30 | STAT-01 | API-09 | — | UI-06 | Phase 8 | Phase 13 / 未执行 |
| TC-31 | STAT-01、STAT-02 | API-09、API-32 | — | UI-06 | Phase 8 | Phase 13 / 未执行 |
| TC-32 | STAT-02 | API-32 | — | UI-06 | Phase 8 | Phase 13 / 未执行 |
| TC-33 | USER-01、USER-02、USER-03、USER-04、USER-05、MERCH-01、MERCH-02、PROD-01、PROD-02、PROD-03、PROD-04、ORDER-01、ORDER-02、ORDER-03、ORDER-04、ORDER-05、ORDER-06、ORDER-07、STAT-01、STAT-02（按参数相关性细化） | — | 全部相关参数接口；不将泛称伪称逐一原列 | — | Phase 3、Phase 4、Phase 5、Phase 6、Phase 7、Phase 8 | Phase 13 / 未执行 |
| TC-34 | ORDER-02、ORDER-06 | API-07、API-23、API-42 | — | UI-03、UI-05、UI-16 | Phase 6 | Phase 13 / 未执行 |
| TC-35 | MERCH-01、ORDER-03、ORDER-04、ORDER-07、PROD-02、PROD-03、PROD-04、STAT-01、STAT-02、USER-05 | API-09、API-32 | API-03、API-04、API-05、API-08、API-20、API-21、API-22、API-28、API-29、API-30、API-31、API-36、API-37、API-38、API-40、API-41；包括后台写接口及字面搜索/纯文本展示 | UI-04、UI-05、UI-06、UI-11、UI-14、UI-15 | Phase 4、Phase 5、Phase 7、Phase 8 | Phase 13 / 未执行 |
| TC-36 | USER-01、USER-02、USER-03、USER-04、USER-05、MERCH-01、MERCH-02、PROD-01、PROD-02、PROD-03、PROD-04、ORDER-01、ORDER-02、ORDER-03、ORDER-04、ORDER-05、ORDER-06、ORDER-07、STAT-01、STAT-02 | — | 页面交互涉及的 API 按页面对应范围回归 | UI-01、UI-02、UI-03、UI-04、UI-05、UI-06、UI-07、UI-08、UI-09、UI-10、UI-11、UI-12、UI-13、UI-14、UI-15、UI-16、UI-17 | Phase 9、Phase 10、Phase 11、Phase 12 | Phase 13 / 未执行 |
| TC-37 | USER-01、USER-02、USER-03、USER-04、USER-05、MERCH-01、MERCH-02、PROD-01、PROD-02、PROD-03、PROD-04、ORDER-01、ORDER-02、ORDER-03、ORDER-04、ORDER-05、ORDER-06、ORDER-07、STAT-01、STAT-02 | — | API-01～API-45及初始化（原文泛称“全部接口与初始化”；重建按 ENV-01 使用 Node.js 24.20.0） | — | Phase 1、Phase 2、Phase 3、Phase 4、Phase 5、Phase 6、Phase 7、Phase 8、Phase 10、Phase 11、Phase 12 | Phase 13 / 未执行 |
| TC-38 | ORDER-01、ORDER-06、PROD-02 | API-03、API-04、API-06 | — | UI-04、UI-09 | Phase 5、Phase 6 | Phase 13 / 未执行 |
| TC-39 | MERCH-01 | API-35、API-36、API-37、API-38 | — | UI-14 | Phase 4 | Phase 13 / 未执行 |
| TC-40 | USER-05 | API-39、API-40 | — | UI-15 | Phase 4 | Phase 13 / 未执行 |
| TC-41 | MERCH-02、PROD-01 | API-02、API-16、API-33、API-34 | — | UI-02、UI-12、UI-13 | Phase 4、Phase 5 | Phase 13 / 未执行 |
| TC-42 | ORDER-02、ORDER-03、ORDER-04、ORDER-07、PROD-02、PROD-03、PROD-04、STAT-01、STAT-02 | — | API-03、API-04、API-05、API-07、API-08、API-09、API-17、API-18、API-19、API-20、API-21、API-22、API-27、API-28、API-29、API-30、API-31、API-32、API-41；原文泛称商家商品、分类、订单、统计接口 | UI-04、UI-05、UI-06、UI-11 | Phase 5、Phase 6、Phase 7、Phase 8 | Phase 13 / 未执行 |
| TC-43 | ORDER-01、ORDER-06 | API-06 | — | UI-09 | Phase 6 | Phase 13 / 未执行 |
| TC-44 | ORDER-04、ORDER-07 | API-28、API-41 | — | UI-05 | Phase 7 | Phase 13 / 未执行 |
| TC-45 | MERCH-01、ORDER-01、ORDER-03、ORDER-05、ORDER-06、ORDER-07 | API-06、API-08、API-25、API-38、API-41 | — | UI-03、UI-05、UI-09、UI-14 | Phase 4、Phase 6、Phase 7 | Phase 13 / 未执行 |
| TC-46 | ORDER-02、ORDER-04、ORDER-07、PROD-02、STAT-01、STAT-02 | API-42、API-43、API-44、API-45 | API-04、API-28、API-41 | UI-04、UI-05、UI-16、UI-17 | Phase 5、Phase 6、Phase 7、Phase 8 | Phase 13 / 未执行 |

## 6. 跨模块规则、后台任务与原文差异

### 6.1 无独立接口的超时后台任务

追踪对象为原有 **ORDER-06**，本文件不新建业务需求编号或 HTTP 端点。后端定时任务计划于 Phase 7 实现，Phase 6 下单先保存同一数据库时间计算的 create_time 与 expire_time；Phase 10 的 UI-03、UI-09 展示截止时间与刷新状态。

- 数据库当前时间达到 create_time + 30 分钟即不得支付；后台每 60 秒扫描全部已超时 status=0 订单，中断恢复后补扫，正常扫描处理最多延后 60 秒。
- 每笔超时取消与全量库存回补同事务、仅一次；支付与扫描锁同一订单，按获锁后的状态和数据库时间决定分支。
- API-25 主动检查超时或商家不可用时，先提交取消及回补，再返回 40908 或 40915，不能因业务异常回滚已要求生效的取消。
- API-06 创建并返回 expireTime；API-23、24 返回服务器状态、截止时间及 allowedActions；API-25 主动判断支付资格。前端倒计时仅提示，不能替代数据库判定。
- 专项原文验收 TC-21、22；关联事务回归 TC-17、23、24、27、28、45，以及商家隔离 TC-42。任务没有开放任意触发接口。当前均未实现、未执行。

### 6.2 规则到场景的重点追踪（分析）

这些关系是根据原文规则和场景操作汇总的回归范围，不替代第 5 节原文需求栏。

| 规则范围 | 关联对象 | 原文核心 TC / 分析回归 | 开发及验收阶段 |
| --- | --- | --- | --- |
| BR-01～04、DATA-02、DATA-09：同店下单、精确金额、事务和防超卖 | ORDER-01；API-06；UI-09 | TC-12～17、38、43；TC-42隔离回归 | Phase 6；Phase 10页面；Phase 13全验收 |
| BR-05、06、11：requestId、规范化内容、唯一冲突回滚后新事务读取 | ORDER-01；API-06；UI-09 | TC-16、17、43 | Phase 6 / Phase 13 |
| BR-07、08、PAY-01～04：状态白名单、支付、接单、取消竞争和一次回补 | ORDER-03～07；API-08、25、26、28、41及超时任务；UI-03、05、09 | TC-19～28、44、45 | Phase 7；Phase 10/11页面；Phase 13全验收 |
| BR-09、10：库存条件更新、分类与历史引用保护 | PROD-02～04；API-03～05、19～22、29～31；UI-04、11 | TC-08～11、29、38、42 | Phase 5；Phase 11页面；Phase 13全验收 |
| BR-12、DATA-08、09：商家隔离、启停与角色边界 | MERCH-01、02、USER-05、各经营功能；平台和商家接口 | TC-02、04、18、35、39～46 | Phase 3～8；Phase 10～12页面；Phase 13全验收 |
| DATA-03～07：不可改历史快照与事件时间 | ORDER-01～07、PROD-02、MERCH-01；订单详情及统计 | TC-19、23、25～29、32、39、44、45 | Phase 4～8 / Phase 13 |
| STAT-R01～07：完成时间、半开区间、补零、稳定排行与平台只读 | STAT-01、02；API-09、32、44、45；UI-06、17 | TC-30～32、33、42、46 | Phase 8；Phase 11/12页面；Phase 13全验收 |
| 认证、安全与参数；第7章非功能要求 | 全部保护接口、参数相关接口与UI-01～17 | TC-01～06、33～37、40、42、46 | 逐阶段执行；Phase 13性能/兼容性/重建验收 |

### 6.3 保留的原文关系差异与待确认事项

1. **API-28 的功能关系不完全一致**：第 3.2 节 ORDER-07 明列 API-41、28，页面 UI-05 也有接单/拒单；第 5.3 节 API-28 的“对应功能”只列 ORDER-04。本文件保留两种原列，追踪并集为 ORDER-04、ORDER-07；未修改原始需求。
2. **公开分类的描述和显式 API 归属不同层次**：PROD-04 描述包含公开分类，但第 3.2 节 API 列只列商家分类 API-19～22、31；API-16 的明细归属是 PROD-01。原列照录，不能因此给 PROD-04 擅自替换接口归属。
3. **UI-13 的 API 映射在页面表更完整**：UI-13 原列 API-34、02、16，而 API-02、16 的明细页面只列 UI-02；追踪并集同时保留 UI-02、UI-13。API-19 原文明确同时供 UI-11、UI-04；它是 UI-04 的分类选择依赖，不能误删。
4. **共性场景使用泛称**：TC-33 为“全部相关接口”，TC-35 为“后台写接口与API-09、32”，TC-37 为“全部接口与初始化”，TC-42 为“商家商品、分类、订单、统计接口”；本文件把逐项展开标为分析，测试实现前须按具体请求类型细化，不能虚构原文已经枚举。TC-29 输入包含改昵称，原接口栏未列 API-13，本文件将其列作分析补充；TC-46 的经营写入负测同理。
5. **NFR 引用缺少编号定义**：TC-34～37 引用 NFR-01、02、04、05、06、07；第 7 章仅有类别、描述和验收方式的五行表格，没有定义这些 NFR 编号及对应关系。保留 TC 原文，待用户确认编号解释；不得自行补成既定需求。
6. **API-11 没有独立页面编号**：原文为所有已登录页面的公共导航，不增加 UI-18；API-11 仍计入45项接口，USER-02不因无独立退出页而遗漏。
7. **库存回补上界存在实现风险**：BR-09 允许人工调整至 INT 上限，BR-07 要求取消全量回补；若已有订单占用库存后又把可售库存调至上限，回补可能溢出。原文没有约定该组合的处理方式；关联 API-29、26、28、25及超时任务，TC-11、21、23、24。须等待用户确认，不能自行新增库存表、截断库存或放弃回补；这项风险尚未形成新增 TC 编号。
8. **排行商家标签规则待明确**：STAT-R05明确商品名称/规格取范围内最新完成明细快照，返回 merchantName；店名同时存在订单 merchant_name 历史快照。排行 merchantName 采用哪个完成订单快照未单独明确，关联 API-32、45和TC-29、32、46；不能擅自用当前店名覆盖历史。
9. **测试场景是验收目标**：当前无测试执行记录；TC-15、16、17、21、22、24、28、44 的并发、故障注入与事务验证及 TC-34 的原始性能计时必须待实际工程具备后执行。

## 7. 文档检查结论与后续维护

- 已逐项登记 20 项功能、45 项 API、17 个页面及 46 个验收场景；TC 表的五列原文内容逐格保留。功能表、API 清单、页面表和 TC 表的提取内容已与原 DOCX 对应表逐格核对，无差异。
- API 方法与路径保持源文清单；API 明细的请求、返回、异常及限制保持源文表达，未将设计补充伪装为原文。
- 每项功能均有原文 TC 需求栏直接追踪；每项 API 均有原文数字编号 TC 追踪或全量接口核对场景；17 页均受 TC-36 覆盖，并补充按 API 的页面回归映射。
- 每次后续阶段只更新获授权范围内的实现状态及实际测试证据；新增需求、API 或规则须先获得用户确认并同步原始需求变更记录及本矩阵。
- **Phase 0 的追踪登记完成；系统实现与验收均未开始。停止于 Phase 0，等待用户审核。**

## 8. Phase 4 实施追踪 2026-11-26

用户确认Phase0完成、Phase1有条件通过、Phase2通过、Phase3遗留解决、Phase3.1通过，并仅授权MERCH-01、USER-05、API-35～40和UI-14/15。原文契约不变；历史Phase4的公开商家API-33/34和其他页面不在本轮范围。实现选择和证据详见[管理说明](../admin/PLATFORM_MANAGEMENT.md)及[Phase4报告](../../reports/PHASE_4_REPORT.md)。

| 需求/API/UI | 本轮实现 | 实际验证与边界 |
| --- | --- | --- |
| MERCH-01 / API-35 | ADMIN商家分页，含绑定账号与停用店铺，字面店名/状态筛选 | 真实MySQL分页、通配符字面匹配、权限、空页及参数测试通过 |
| MERCH-01 / API-36 | 固定MERCHANT启用账号和店铺同事务创建，BCrypt，HTTP201 | 正向、三角色重复账号、并发唯一、第二步故障回滚及字段白名单通过 |
| MERCH-01 / API-37 | 仅编辑店铺信息，不改账号/绑定/历史快照 | 40404、敏感字段、历史订单明细和库存保持测试通过 |
| MERCH-01 / API-38 | 店铺启停，同值返回当前对象且无写入副作用 | 账号状态/版本不变、商家仍可认证、历史保护通过；后续公开/下单消费逻辑未实现 |
| USER-05 / API-39 | 固定CUSTOMER，昵称或手机号/状态筛选分页 | 排除ADMIN/MERCHANT、role筛选拒绝、字面搜索和公开返回字段通过 |
| USER-05 / API-40 | 仅CUSTOMER状态维护，状态与版本同SQL同事务 | 非CUSTOMER40405、禁用40301、恢复后旧JWT40102、重复同值/并发/故障回滚通过 |
| UI-14 | `/admin/merchants`查询、创建、编辑、启停确认 | 组件交互及Chrome/Edge，1440×1000和390×844相关检查通过 |
| UI-15 | `/admin/users`查询、筛选、分页、启停确认 | 守卫、字段错误、取消/确认、双浏览器双分辨率及真实JWT行为验证通过 |

本轮新增后端65项，连同原298项共363项全部通过，无失败、错误或跳过；前端原59项加新增26项共85项通过；两端构建成功。测试计数为本轮实际执行结果，不能与Phase0追踪数量相加。

TC-39、TC-40的本阶段接口、事务、权限和页面操作已有证据；TC-29只验证平台修改不回写既有订单/明细，TC-35只验证本轮平台权限/字面搜索/纯文本，TC-36只验证两管理页相关浏览器部分，TC-37未做全系统重建，TC-45未执行下单支付履约消费方联调。它们不整体标为通过。TC-41和其API-33/34本轮未实现。UI-01登录页、正式种子、生产TLS及完整46场景仍未验收；停止于Phase4待审核，不进入Phase5。
