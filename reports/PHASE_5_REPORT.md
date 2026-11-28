# Phase 5 商品、分类与库存管理实施报告

日期：2026-11-28（Asia/Shanghai）。项目：基于 Spring Boot + Vue 的蛋糕店订单管理系统。

**结论：成员二分工范围（PROD-01～04、API-02～05、API-15～22、API-29～31、UI-02/04/10/11）已完成实现，并接入真实照片与「奶油星球」本地演示数据。为打通前置依赖，成员二另协助补齐成员一的公开商家 API-33/34 与 UI-01 登录页；后端 54 项 Catalog 契约测试、19 项 Catalog 集成测试、13 项公开商家契约测试、3 项公开商家集成测试及全量 452 项套件在 MySQL 8.0.46 上全部通过（失败/错误/跳过 0）；前端 151 项测试通过，生产构建成功；打包 JAR 完成 28 项 HTTP 联调，并用真实登录页登录后重拍商家三页、公开页和移动端截图。当前为 Phase 5 待审核，未提交 Git，未修改原始 DOCX，未连接用户开发库。**

## 1 授权范围与需求核对

本轮依据用户转达的小组分工：功能 PROD-01～04；接口 API-02～05、15～22、29～31（共 15 项）；页面 UI-02、04、10、11；数据表 `cake_category`、`cake_product`。未实现 API-06/07/23～28、32～45，未实现登录、注册、个人中心、订单、支付、统计页面，不新增业务表或字段。

编码前核对第 3.2、3.4（BR-09/10/12）、3.7、4.3.2、4.3.3、5.1、5.3、6.1、8.2 节，以及 Phase 4 的既有架构、统一响应、字段错误契约、令牌与商家归属检查。`cake_category`、`cake_product` 已由 Phase 2 按原字典建表并含 29 项 CHECK、复合外键与索引，本轮不修改 DDL。

用户补充决定：MySQL 测试断言放宽到 8.0.x 全版本（不强制全组重装 8.0.19）；Node 22.23.1 先完成开发与基础测试，等全组统一 24.20.0 后整体复跑；API-33/34 原计划等成员一补全，后经用户确认由成员二协助补齐（见第 9 节）；报告先保持副本形式。

在上述决定之后，为避免 02 模块因登录页/公开商家接口无法演示，成员二经用户确认协助补齐 **API-33/34 + UI-01** 两项前置：只新增公开商家读取与登录页，不修改成员一认证、平台管理的既有业务逻辑；成员一仍保有评审和最终归属。

`git status --short` 起止均在 `qxy-dev` 分支，未执行 init、commit、push、reset 或 rebase。

## 2 后端交付

### 2.1 接口

| 编号 | 方法与路径 | 实现要点 |
| --- | --- | --- |
| API-02 | GET `/api/products` | merchantId 必填；启用店铺+启用分类+上架商品；categoryId 属本店才命中；keyword 字面匹配；id 降序分页 |
| API-15 | GET `/api/products/{id}` | 下架/分类禁用 40401；店铺不可用 40404；公开对象无时间字段 |
| API-16 | GET `/api/categories` | 仅启用分类，sort、id 升序；店铺不可用 40404 |
| API-17 | GET `/api/merchant/products` | 固定本店；categoryId 只能本店；status 0/1 可选；含真实库存与上下架状态 |
| API-18 | GET `/api/merchant/products/{id}` | 本店详情；他店统一 40401 |
| API-19 | GET `/api/merchant/categories` | 本店全部分类；status 过滤；拒绝客户端 merchantId（40302） |
| API-03 | POST `/api/merchant/products` | 201；status 固定 0；merchantId 取登录绑定；禁用分类允许建下架商品；价格/库存严格校验 |
| API-04 | PUT `/api/merchant/products/{id}` | 只改 PEdit 字段；stock/status/merchantId 40302；已上架改入禁用分类 40913；不覆盖订单快照 |
| API-05 | DELETE `/api/merchant/products/{id}` | 检查全部订单明细（含已取消）→ 40910；删除与外键同事务 |
| API-29 | PATCH `/api/merchant/products/{id}/stock` | `stock=stock+delta` 条件更新，匹配 expectedStock 与 0～2147483647；40911/40001 |
| API-30 | PATCH `/api/merchant/products/{id}/status` | 上架校验分类启用；同值幂等且不改 updateTime；40913 |
| API-20 | POST `/api/merchant/categories` | 201；status 固定 1；同店重名 40901（msg“分类名称已存在”） |
| API-21 | PUT `/api/merchant/categories/{id}` | name、sort 必填；不接收 status；他店 40403；重名 40901 |
| API-22 | DELETE `/api/merchant/categories/{id}` | 仍有商品 40909；空分类成功；重复删除 40403 |
| API-31 | PATCH `/api/merchant/categories/{id}/status` | 禁用前锁本分类上架商品；有上架 40909；同值幂等 |

### 2.2 事务、锁与权限

- 每个商家写事务先 `SELECT ... FOR UPDATE` 锁当前 `sys_user`，复核角色、启用状态、token_version，再从 `cake_merchant.user_id` 取得店铺；不接受请求中的 merchantId。
- 商品新增/编辑/上下架按“分类 → 商品”顺序加锁；库存只锁商品并条件更新；分类禁用锁分类后锁本分类上架商品；删除商品/分类分别检查订单明细与商品引用，外键在并发场景兜底。
- 公开接口只返回启用店铺、启用分类、已上架商品；停用店铺统一 40404，不泄露后台数据。
- 参数错误沿用 Phase 3.1：HTTP 400／40001，`data.fieldErrors` 实际字段→字符串数组；受保护字段优先 40302；无法定位返回 data=null。

### 2.3 关键实现选择（原文未逐字规定）

1. keyword 只匹配商品名称，按字面包含，固定 `ESCAPE '!'` 转义 `!`、`%`、`_`。
2. 公开商品按 id 降序、分类按 sort、id 升序；与 5.1 原文一致。
3. 编辑接口先校验分类再校验商品，因此“商品与分类都非法”时返回 40403。
4. API-30/31 同值返回当前对象且不写 updateTime（原文明确要求）；API-04/21 普通编辑每次刷新 updateTime。
5. image 只接受空、`/` 开头站内路径或 http(s) URL；空字符串按 NULL 保存。
6. 公开详情先区分商品不可见（40401）与店铺不可用（40404）。

## 3 页面与本地演示数据

| 页面 | 路由 | 内容 |
| --- | --- | --- |
| UI-01（协助补齐） | `/login` | 三角色共用登录；不提供角色选择；按服务器返回角色跳转 ADMIN→`/admin/merchants`、MERCHANT→`/merchant/products`、CUSTOMER→公开入口；错误提示 40101/40301/42301 |
| UI-02 | `/shop/:merchantId` | 店铺名、电话、地址、简介、分类切换、关键字、分页、商品卡、售罄标识、加载/空/错误状态 |
| UI-10 | `/shop/products/:id` | 商家/分类/规格/价格/库存/图片/纯文本详情；售罄禁用；40401/40404 友好返回 |
| UI-04 | `/merchant/products` | 本店筛选分页、创建、编辑、库存增减（expectedStock）、上下架确认、删除确认、字段错误、40911 后刷新库存 |
| UI-11 | `/merchant/categories` | 本店分类列表、状态筛选、新增/改名/排序、启停确认、删除确认、40901/40909 提示 |

新增 `MerchantLayout` 商家布局与商家路由守卫（每次读取服务器当前账号）；新增 `catalog.css`；`ProductImage` 在地址为空或加载失败时回退 `cake-placeholder.svg`。所有展示使用文本插值，不执行用户输入。

真实照片已放入 `frontend/public/images/`：`cake-01.png`～`cake-08.png`（约 1224×918，4:3），数据库 `image` 保存 `/images/cake-0X.png`。本地演示数据由 `database/demo/` 提供（非正式种子，不提交），店铺「奶油星球」，4 个分类、8 个商品，含 1 个库存 0 的售罄商品和 1 个下架商品。

UI-02 的店铺电话/地址由 API-34（公开商家详情）提供，现已接入；访问 `/merchant/products` 等商家页会先跳转 UI-01 登录，登录成功后按角色返回。尚未实现的 UI-05 商家订单页仍由成员一/四后续阶段负责，登录后暂落在商品管理，不伪造订单入口。UI-10 的下单按钮等待订单模块，当前明确禁用。

## 4 测试与构建

### 4.1 后端

| 命令/检查 | 结果 |
| --- | --- |
| `mvnw.cmd -B -Dtest=CatalogRequestsTests test` | 54 项通过，失败/错误/跳过 0 |
| `mvnw.cmd -B -Dtest=CatalogHttpMySqlTests test`（`CAKE_MYSQL_TEST_ENABLED=true`，隔离临时 MySQL 8.0.46） | 19 项通过，失败/错误/跳过 0 |
| `mvnw.cmd -B -Dtest=PublicMerchantRequestsTests test` | 13 项通过 |
| `mvnw.cmd -B -Dtest=PublicMerchantHttpMySqlTests test`（同一临时库） | 3 项通过（公开可见性、字段、40404、分页/字面关键字） |
| `mvnw.cmd -B test`（真实库开关开启、测试进程随机 JWT 密钥、MySQL 8.0.46） | **452 项通过，失败 0、错误 0、跳过 0；BUILD SUCCESS** |
| `mvnw.cmd -B -DskipTests package` | 打包成功，生成可执行 JAR |
| `java -jar ...` + 真实 HTTP/浏览器检查 | 28 项 HTTP 检查通过；公开商家 API、UI-01 登录与登录后商家三页实测通过（见 4.3、4.4） |

Catalog 集成测试覆盖：公开列表/详情/分类可见性、售罄与下架、停用店铺 40404、关键字字面搜索、分页越界、本店隔离、他店 40401/40403、创建默认下架、编辑不改库存与状态、删除历史引用 40910、分类重名 40901、禁用 40909、上下架 40913、库存旧值 40911 与并发双写、分类禁用与上架竞争、三角色/匿名、写事务复核版本/角色/状态、故障回滚、最大无符号 ID 与超范围 ID。

MySQL 版本：按用户决定，`AdminHttpMySqlTests`、`AuthHttpMySqlTests`、`MySqlDatabaseTests` 的版本断言由 `8.0.19` 放宽为 `8.0.`，`DatabaseTool` 的连接检查同步接受 `8.0.x` 并打印实际版本；全量套件已在 8.0.46 上实际通过。未执行正式种子，未连接用户开发库；临时实例及数据目录在验证后删除。

### 4.2 前端

| 命令 | 结果 |
| --- | --- |
| `npm.cmd test` | 11 个文件、151 项通过（新增 66 项：校验 21、API 9、公开页 9、商家页 16、登录页 11） |
| `npm.cmd run build` | 成功；主 JS 约 1,174 kB（gzip 约 387 kB）、CSS 约 378 kB（gzip 51.9 kB），保留既有大 chunk 提示 |

前端测试覆盖分类/关键字/分页参数、40404/40401 提示、纯文本渲染、商品详情售罄、商家守卫与 401/403、创建/编辑白名单与字段错误、库存 40911 刷新、上下架 40913、删除 40910、分类 40901/40909、取消不写请求、旧列表延迟响应保护，以及 UI-01 三角色跳转、登录错误、重复提交与安全重定向。

**环境偏差**：本机默认 Node 为 v22.23.1、npm 10.9.8，未安装 ENV-01 指定的 Node 24.20.0；本轮依赖未变，测试与构建在该版本完成。按用户决定先保留现状，等全组统一 24.20.0 后整体复跑。

### 4.3 打包 JAR 与真实接口联调

28 项联调检查（此前隔离库与旧演示种子）覆盖公开分类/商品/详情、金额两位小数、无时间字段、40401/40001、商家登录、后台列表与时间字段、拒绝客户端 merchantId、匿名 401、创建默认下架、编辑不覆盖库存、库存条件调整与旧值 40911、上架、分类禁用 40909、分类新增/重名 40901/编辑/删除/有商品 40909、删除商品、顾客注册登录、顾客访问经营接口 40302；结果 28/28 通过，证据 `backend/target/phase5-evidence/api-smoke.json`。

新演示数据（奶油星球，隔离库）另行验证：`POST /api/auth/login` 返回 MERCHANT 且 merchantId=1；`GET /api/products?merchantId=1` 返回 7 件在售（8 件中 1 件下架）；`GET /api/merchant/products` 返回 8 件；`GET /api/merchants/1` 返回店名、电话、地址、简介；`GET /api/merchants?keyword=奶油` 返回 1 条。随后用同一 JAR、Vite 与 Chrome 通过 **UI-01 真实登录** 进入商家商品、分类页并重拍截图。验证结束后停止 JAR、Vite 与临时 MySQL，删除临时数据目录；开发库与测试库未写入。

### 4.4 浏览器截图

隔离 MySQL + 打包 JAR + Vite 开发代理 + headless Chrome 158 实际渲染，截图位于 `backend/target/phase5-browser/`，全部替换为真实照片和奶油星球演示数据：

| 文件 | 内容 |
| --- | --- |
| ui-01-login.png | UI-01 1440×1000，三角色共用登录页，无角色选择 |
| ui-02-shop-products.png | UI-02 1440×1000，奶油星球、电话/地址/简介、4 个分类、7 件在售商品、售罄与分页 |
| ui-10-product-detail.png | UI-10 1440×1000，草莓奶油蛋糕的商家、规格、价格、库存、详情与按钮状态 |
| ui-04-merchant-products.png | UI-04 1440×1000（真实登录后），8 个本店商品、下架商品、0 库存、上下架操作 |
| ui-04-create-dialog.png | UI-04 新增商品表单与必填标记 |
| ui-11-merchant-categories.png | UI-11 1440×1000（真实登录后），本店 4 个分类、启停与排序 |
| ui-11-create-dialog.png | UI-11 新增分类表单 |
| ui-02-mobile.png | UI-02 390×844（CDP 设备模拟），两列商品无横向溢出 |

UI-01 登录页通过真实表单登录 DEMO 账号，随后在同一浏览器会话内进入 `/merchant/products`、`/merchant/categories`，不再使用临时 HTML 会话页；登录页与商家三页的截图因此可复现。公开页 UI-02 使用同一 JAR 的 API-34 请求展示店铺电话与地址。

## 5 数据库与环境

| 项目 | 实际结果 |
| --- | --- |
| JDK | 18.0.2.1（`D:\dev-tool-installers\JDK\Java\jdk-18.0.2.1`） |
| Node / npm | v22.23.1 / 10.9.8（与 ENV-01 的 24.20.0 不一致，按用户决定暂保留，见 4.2） |
| Maven Wrapper | 3.3.4 / Maven 3.9.11 |
| Spring Boot / MyBatis-Plus | 3.5.16 / 3.5.18 |
| MySQL | 隔离临时实例 8.0.46；测试断言与 DatabaseTool 已放宽为 8.0.x，全量套件实测通过 |
| 数据库脚本 | 复用 Phase 2 六表 DDL，无新增迁移；新增 `database/demo/` 本地演示脚本，不属于正式种子 |
| 用户数据库 | 未连接、未写入开发库 `cake_order_system` 或测试库 `cake_order_system_phase2_test` |
| 系统环境 | 未改 PATH、JAVA_HOME、ExecutionPolicy，未安装依赖 |

## 6 变更文件

新增后端 17 个生产文件：

- `controller/catalog/PublicCatalogController.java`、`MerchantCatalogController.java`
- `dto/CatalogRequests.java`
- `entity/AccountState.java`、`CategoryRecord.java`、`ProductRecord.java`、`StockRecord.java`
- `exception/CatalogErrorCode.java`
- `mapper/CatalogMapper.java`
- `service/catalog/PublicCatalogService.java`、`MerchantCatalogService.java`
- `vo/CategoryView.java`、`DeletionView.java`、`MerchantCategoryView.java`、`MerchantProductView.java`、`ProductView.java`、`StockView.java`

新增后端测试 2 个：`catalog/CatalogRequestsTests.java`、`catalog/CatalogHttpMySqlTests.java`。

协助补齐成员一前置（新增 7 个生产文件）：`controller/merchant/PublicMerchantController.java`、`dto/PublicMerchantRequests.java`、`entity/PublicMerchantRecord.java`、`exception/PublicMerchantErrorCode.java`、`mapper/PublicMerchantMapper.java`、`service/merchant/PublicMerchantService.java`、`vo/PublicMerchantView.java`；测试 2 个：`merchant/PublicMerchantRequestsTests.java`、`merchant/PublicMerchantHttpMySqlTests.java`。

新增前端：`api/catalog.js` 及其测试、`catalog/validation.js` 及其测试、`catalog/useMerchantCatalog.js`、`components/catalog/ProductImage.vue`、`ConfirmDialog.vue`、`layouts/MerchantLayout.vue`、`shop/shop.test.js`、`merchant/merchantCatalog.test.js`、`views/shop/ShopProductsView.vue`、`ProductDetailView.vue`、`views/merchant/ProductsView.vue`、`CategoriesView.vue`、`styles/catalog.css`、真实照片 `public/images/cake-01.png`～`cake-08.png`、`cake-placeholder.svg`。

协助补齐 UI-01：新增 `views/auth/LoginView.vue`、`styles/login.css`、`auth/login.test.js`。

新增本地演示数据：`database/demo/05_local_demo.sql`、`06_remove_local_demo.sql`、`README.md`（仅本地开发/答辩，非正式种子，不提交）。

修改：`frontend/src/router/index.js`（新增 UI-01/商家路由与统一登录跳转）、`frontend/src/main.js`（引入 catalog.css、login.css）、`frontend/src/application.test.js`（路由清单）；`frontend/src/layouts/AdminLayout.vue`、`frontend/src/admin/useAdminList.js` 与商家布局/组合式函数改为 401 后跳转 `/login`；`AdminHttpMySqlTests`、`AuthHttpMySqlTests`、`MySqlDatabaseTests` 的 MySQL 版本断言放宽为 8.0.x，`database/tools/DatabaseTool.java` 同步接受 8.0.x。成员一认证、平台管理的核心业务逻辑未改。

文档：新增 `docs/catalog/CATALOG_MANAGEMENT.md`、`docs/catalog/MEMBER_2_REPORT_CONTENT.md`、本报告，以及由原分析设计报告副本插入 7.7 节和页面截图生成的 `Web应用开发项目分析设计报告_含成员二章节.docx`（原 DOCX 未修改）；更新 `README.md`、`PROJECT_STATUS.md`、`TODO.md`、`docs/requirements/TRACEABILITY_MATRIX.md`。原始 DOCX、AGENTS、数据库六表 DDL 未改。

## 7 证据与可重建命令

- `backend/target/surefire-reports/`：后端 452 项测试 XML/TXT（最新一次真实 MySQL 运行）。
- `backend/target/phase5-evidence/api-smoke.json`：28 项 HTTP 检查结果（旧演示种子）。
- `backend/target/phase5-evidence/jar-stdout.log`、`jar-stderr.log`：JAR 启动日志。
- `backend/target/phase5-browser/`：8 张截图（含 UI-01 登录页）。
- `database/demo/`：本地演示数据与清理脚本。
- 前端测试输出与构建输出在终端记录；`frontend/dist/` 已由构建更新。

重建命令（在项目根目录，使用当前环境）：

```powershell
# 后端
Set-Location backend
$env:JAVA_HOME = 'D:\dev-tool-installers\JDK\Java\jdk-18.0.2.1'
.\mvnw.cmd -B test   # 无真实库开关时 120 项 MySQL 套件跳过
# 真实 MySQL：设置 CAKE_MYSQL_TEST_ENABLED=true、DB_TEST_NAME、DB_USERNAME、DB_PASSWORD、JWT_SECRET_BASE64 后
.\mvnw.cmd -B test
.\mvnw.cmd -B -DskipTests package

# 前端
Set-Location ../frontend
npm.cmd test
npm.cmd run build
```

## 8 风险、待确认与验收边界

1. **协助补齐的评审**：API-33/34 与 UI-01 为解决 02 演示阻塞而由成员二协助实现，需成员一评审后再计入其正式交付；若成员一后续提交同名实现，应以其版本为准并合并测试。
2. **UI-05 仍未实现**：登录后 MERCHANT 暂落在 `/merchant/products`，未伪造商家订单页；订单、支付、履约仍属成员三/四范围。
3. **Node 版本**：本机 22.23.1 与 ENV-01 的 24.20.0 不一致；按用户决定先完成开发与基础测试，全组升级后整体复跑。MySQL 版本已按用户决定放宽为 8.0.x，并在 8.0.46 全量通过。
4. **图片体积**：8 张照片合计约 9 MB（单张最大约 1.7 MB），本地演示可用；部署或弱网演示前建议压缩到单张 300～500 KB，页面无需改代码。
5. **分类商品数**：未新增统计接口，UI-11 不显示分类下商品数量，避免扩大 API 范围。
6. **演示数据**：`database/demo/` 为本地临时账号/店铺/商品，不提交、不作为正式种子；演示密码仅用于本机课程演示。
7. **验收场景**：TC-07～11 的商品分类库存部分、TC-29 商品快照部分、TC-35 商家写接口、TC-36 页面、TC-41 的 A 店商品与公开店铺部分已有证据；TC-38 仅覆盖商品金额部分，TC-42 仅覆盖商品分类隔离，TC-46 未执行。完整场景仍随订单、统计模块在 Phase 13 验收，不整体标为通过。
8. **数据库脚本**：本轮无新增 DDL；成员二分工中提到的“建表 SQL”即 `database/schema/01_create_tables.sql`、`database/indexes/02_create_indexes.sql`、`database/constraints/03_data_constraints.sql` 中既有定义。

## 9 协助补齐成员一前置：API-33/34 与 UI-01

| 项目 | 实现 | 验证 |
| --- | --- | --- |
| API-33 | GET `/api/merchants`；page/size/keyword，仅启用店铺且绑定账号启用；id 降序；公开字段 id/name/phone/address/description/status | 13 项契约测试 + 3 项真实 MySQL/HTTP；字面关键字、分页、停用店铺/账号、40302/40001 |
| API-34 | GET `/api/merchants/{id}`；不可用统一 40404；不泄露账号与内部字段 | 同上；UI-02 实际展示电话、地址、简介 |
| UI-01 | `/login`；手机号 + 密码；无角色选择；按服务器角色跳转；支持安全 redirect；401/403/423 错误提示；已登录访问自动跳转 | 11 项组件测试；Chrome 真实登录后进入商家商品/分类页并截图 |
| 未改动 | 成员一 `AuthController`/`AuthenticationService`/`AdminService` 等核心认证与平台管理逻辑 | 原 436 项套件保留并通过；新增 16 项后共 452 项 |

**协助范围边界**：只补公开商家读取和登录页，不实现成员一的 UI-07/08/12/13/14/15，不改认证规则、令牌、密码策略或平台管理写逻辑；提交前需成员一确认归属与评审。

**停止于 Phase 5 待审核。未获新授权不进入订单/统计阶段，不提交 Git，不执行正式种子，不修改原始需求 DOCX。**
