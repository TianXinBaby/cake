# Phase 5 商品、分类与库存管理

日期：2026-11-28（Asia/Shanghai）。需求依据为根目录《第5组+肖琳+蛋糕店订单管理系统需求说明书.docx》（V1.1，实际修订 2026-11-24）第 3.2、3.4、3.7、4.3、5.1、5.3、6.1、8.2 节；原始 DOCX 未修改。

本轮对应小组成员二的分工：PROD-01～04、API-02～05、API-15～22、API-29～31、UI-02/04/10/11，数据表 `cake_category`、`cake_product`。两表已在 Phase 2 按原字典建立，本轮不需要新增或修改 DDL。完整验证记录见 [Phase 5 报告](../../reports/PHASE_5_REPORT.md)。

## 1 接口与返回

所有接口沿用统一响应 `{"code":200,"msg":"success","data":...}`；创建成功 HTTP 201、业务 code 仍为 200。ID 为十进制字符串，金额为两位小数字符串，时间为 `+08:00` ISO 8601 毫秒字符串。公开接口匿名可访问；经营接口要求 MERCHANT，并在服务层再次用登录账号绑定店铺，不采信客户端 merchantId。

| 编号 | 方法与路径 | 接收字段 | 成功结果与业务边界 |
| --- | --- | --- | --- |
| API-02 | GET `/api/products` | Query：merchantId 必填；page、size、categoryId、keyword 可选 | Product 分页；仅启用店铺、启用分类、已上架商品；售罄仍返回 |
| API-15 | GET `/api/products/{id}` | Path：id | Product；下架、分类禁用返回 40401，店铺不可用返回 40404 |
| API-16 | GET `/api/categories` | Query：merchantId 必填 | 仅该启用店铺的启用分类，按 sort、id 升序 |
| API-17 | GET `/api/merchant/products` | Query：page、size、categoryId、keyword、status 可选 | 本店 Product 分页，含下架商品、真实库存和上下架状态 |
| API-18 | GET `/api/merchant/products/{id}` | Path：id | 本店 Product；他店或不存在返回 40401 |
| API-19 | GET `/api/merchant/categories` | Query：status（0/1）可选 | 本店完整分类数组，含禁用分类 |
| API-03 | POST `/api/merchant/products` | categoryId、name、spec、price 必填；image、description 可空；可选 stock，默认 0 | HTTP 201，Product；status 固定 0，merchantId 取自登录绑定 |
| API-04 | PUT `/api/merchant/products/{id}` | 完整 PEdit：categoryId、name、spec、price 必填；image、description 可空 | Product；收 stock、status、merchantId 返回 40302，库存与归属不变 |
| API-05 | DELETE `/api/merchant/products/{id}` | Path：id | `{id,deleted:true}`；任一订单明细（含已取消）引用返回 40910 |
| API-29 | PATCH `/api/merchant/products/{id}/stock` | delta 非零整数、expectedStock 非负整数 | `{id,stock,updateTime}`；条件更新，旧值失配返回 40911，越界返回 40001 |
| API-30 | PATCH `/api/merchant/products/{id}/status` | status 0/1 | Product；分类禁用时上架返回 40913；同值返回当前对象且不改 updateTime |
| API-20 | POST `/api/merchant/categories` | name 必填；sort 可选默认 0 | HTTP 201，Category；status 固定 1，同店重名返回 40901 |
| API-21 | PUT `/api/merchant/categories/{id}` | name、sort 均必填 | Category；不接收 status；同店重名 40901，他店 40403 |
| API-22 | DELETE `/api/merchant/categories/{id}` | Path：id | `{id,deleted:true}`；仍有商品返回 40909 |
| API-31 | PATCH `/api/merchant/categories/{id}/status` | status 0/1 | Category；禁用它须无上架商品，否则 40909；同值不改 updateTime |

返回对象严格按 5.1.1：公开 `Product` 为 id、merchantId、merchantName、categoryId、categoryName、name、spec、price、stock、image、description、status；商家端另含 createTime、updateTime。公开 `Category` 为 id、merchantId、name、sort、status；商家端另含 createTime、updateTime。

## 2 公开可见性与筛选

公开列表固定 `merchant.status=1 AND category.status=1 AND product.status=1`；库存为 0 只影响下单，不影响展示。分类筛选只能命中本店分类，传入他店或禁用分类自然返回空数组，不泄露后台数据。keyword 去除首尾空白后最长 60 字符，按商品名称字面包含匹配，固定 `ESCAPE '!'` 转义 `!`、`%`、`_`，反斜杠按字面处理。商品按 id 降序、分类按 sort、id 升序，分页 page≥1、size 1～100，超出末页返回空 records。

公开详情先按 id 查询商品，再区分三种不可见原因：商品不存在或下架、分类禁用统一 40401；所属店铺不存在或停用统一 40404，不返回后台字段。

## 3 事务、锁与并发

商家写接口在事务内锁定当前 `sys_user`，复核角色=MERCHANT、status=1、token_version 与 JWT 一致，再从 `cake_merchant.user_id` 读取店铺，避免使用请求或旧会话中的归属。

| 操作 | 锁顺序与条件 | 冲突结果 |
| --- | --- | --- |
| 新增商品 | 锁本店分类 → 插入 status=0 商品 | 分类不存在/非本店 40403 |
| 编辑商品 | 锁目标分类 → 锁商品 → 再读商品 | 商品 40401、分类 40403、已上架改入禁用分类 40913 |
| 上下架 | 读商品取分类 → 锁分类 → 锁商品 → 复核分类未变 → 条件更新 | 分类禁用 40913，分类被并发改变 40911 |
| 库存调整 | 锁商品 → `stock=stock+delta` 条件更新，WHERE 同时校验 merchant_id、stock=expectedStock、结果在 0～2147483647 | 旧值 40911；delta=0、调整后负数或超限 40001 |
| 删除商品 | 锁商品 → 统计全部订单明细（含已取消）→ 删除 | 有引用 40910；外键在并发新增引用时兜底 |
| 分类启停 | 锁分类 → 禁用时锁本分类上架商品 → 更新 | 仍有上架商品 40909；同值幂等 |
| 分类删除 | 锁分类 → 锁本分类全部商品 → 删除 | 仍有商品 40909；外键兜底 |

商品与分类维护统一按“分类 → 商品”加锁，与分类停用检查使用同一分类行锁；库存只锁商品行，不覆盖订单扣减或回补结果。删除校验与删除同事务，唯一键 `(merchant_id,name)` 与 `(id,merchant_id)` 复合外键提供最终一致性保护。

## 4 参数错误与接口实现选择

- 参数失败仍为 HTTP 400／40001，`data.fieldErrors` 仅含实际公开字段与字符串数组；未知普通字段返回不可定位的 40001，受保护字段（id、merchantId、status、stock、createTime 等）优先 40302。
- price 只接受最多两位小数的十进制字符串，`>0` 且 `≤99999999.99`；stock、sort、delta、expectedStock 只接受 JSON 整数；分类/商品 ID 只接受正十进制字符串（最长 64 位无符号范围）。
- image 允许空、`/` 开头的站内路径或 http(s) URL，最长 255；description 最长 500，图片与纯文本不在前端执行。
- 普通编辑不接收 stock/status/merchantId；新商品即使传入 status 也返回 40302，服务层固定为 0。
- 公开与后台 keyword 均只匹配商品名称；分类名称不参与商品关键字搜索。这是原文未细化的实现选择。
- 编辑接口先校验分类再校验商品，因此“商品不存在且分类也非法”时返回 40403；单资源场景仍按原文 40401/40403 区分。
- API-30/31 同值返回当前对象且不改 updateTime，属于原文明确要求的幂等；API-04/21 的普通编辑每次刷新 updateTime。

## 5 页面

| 页面 | 路由 | 已实现内容 | 边界 |
| --- | --- | --- | --- |
| UI-01 登录页（协助补齐） | `/login` | 三角色共用、无角色选择、按服务器角色跳转、安全 redirect、401/403/423 提示 | 成员一评审归属；MERCHANT 暂无订单页，登录后落在商品管理 |
| UI-02 本店商品页 | `/shop/:merchantId` | 店铺名、电话、地址、简介、分类切换、关键字、分页、商品卡；售罄标识；40404/参数错误/空状态/加载态 | 电话/地址来自 API-34；下单动作等待订单模块 |
| UI-10 商品详情页 | `/shop/products/:id` | 商家、分类、规格、价格、库存、图片、纯文本详情；售罄禁止下单；40401/40404 友好提示与返回本店商品 | 下单按钮等待订单模块（Phase 6/10），当前明确禁用并说明 |
| UI-04 商家商品管理 | `/merchant/products` | 本店筛选分页、创建、编辑、库存增减（带 expectedStock）、上下架确认、删除确认、字段错误、40911 刷新提示 | 无他店切换、无平台入口；merchantId 只读 |
| UI-11 商家分类管理 | `/merchant/categories` | 分类列表、状态筛选、新增/改名/排序、启停确认、删除确认、40901/40909 就地提示 | 当前无分类商品数统计接口，页面不推测数量 |

商家路由守卫每次进入读取服务器当前账号；未登录访问后台/商家页跳转 `/login` 并保留目标地址，登录后按服务器角色返回。真实照片位于 `frontend/public/images/cake-01.png`～`cake-08.png`（4:3），`ProductImage.vue` 在图片地址为空或加载失败时回退到 `cake-placeholder.svg`。演示店铺「奶油星球」的数据由可提交的 `database/seed/05_demo_seed.sql` 提供（无明文密码，账号密码单独提供）；`database/demo/` 是旧的本机脚本，不提交。

## 6 验证范围与遗留

本轮完成 54 项 Catalog 契约、19 项 Catalog 集成、13 项公开商家契约、3 项公开商家集成、全量 452 项后端套件（MySQL 8.0.46，失败/错误/跳过 0）、151 项前端测试、生产构建、JAR 真实启动和 28 项 HTTP 联调检查，并用 8 张真实照片与「奶油星球」演示数据重拍 UI-01/02/04/10/11、表单与移动端截图。真实浏览器验证使用隔离临时 MySQL；UI-01 用真实登录后进入商家三页，不再依赖临时会话页，结束后已删除临时数据，不影响开发库。

未完成/待确认：

1. API-33/34 与 UI-01 由成员二协助补齐，需成员一评审归属；如成员一已有同名实现，以其版本为准合并测试。
2. 成员一 UI-05 商家订单页尚未实现，MERCHANT 登录后暂落在商品管理，不伪造订单入口；UI-07/08/12/13 仍待页面阶段。
3. MySQL 测试断言与 `DatabaseTool` 已按用户决定放宽为 8.0.x，全量套件在 8.0.46 通过；不再强制全组安装 8.0.19，临时实例验证后已删除，未改用户数据库。
4. 真实照片已接入（`frontend/public/images/cake-0X.png`），单张最大约 1.7 MB，本地演示可用；部署或弱网演示前建议压缩到 300～500 KB。
5. 完整 TC-07～11、29、35、36、38、41、42、46 仍需等待订单、统计等模块后在 Phase 13 统一验收，本轮只验证商品分类库存及公开店铺/登录相关部分。
