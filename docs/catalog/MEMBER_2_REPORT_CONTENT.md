# 成员二：商品、分类与库存模块（报告 / 答辩材料）

本文件面向课程《分析设计报告》和答辩 PPT，内容与当前代码、测试、截图一一对应，可直接选取使用。项目根目录：`cake/`；实现日期：2026-11-28。

## 0 小组分工中的成员二

组长给出的分工卡片可直接整理为：

| 序号 | 成员 | 负责模块 | 具体内容 | 标签 |
| --- | --- | --- | --- | --- |
| 02 | 成员二 | 商品、分类与库存模块 | 商品浏览、商品管理、分类管理、库存维护 | 业务核心模块 |

对应的需求编号为 PROD-01～04；接口 API-02～05、15～22、29～31（共 15 个）；页面 UI-02、UI-04、UI-10、UI-11；数据表 `cake_category`、`cake_product`。

## 1 模块在系统中的位置

本系统是“平台—商家—顾客”三类角色的多商家蛋糕订购平台。商品、分类与库存模块处在业务链路的中间：

- 平台管理员创建商家账号和店铺（成员一负责）；
- 商家维护本店分类、商品、上下架与库存（本模块）；
- 顾客先选店，再浏览该店分类和商品，进入商品详情并下单（下单由订单模块负责）；
- 订单创建时按数据库价格计价并扣减库存，取消时回补（订单模块负责，本模块提供库存字段和条件更新基础）。

本模块只处理“商品目录与库存”，不处理订单、支付、统计和用户账号，避免越界修改其他成员代码。

## 2 需求对应关系

| 功能 | 需求要点 | 接口 | 页面 |
| --- | --- | --- | --- |
| PROD-01 商品浏览与详情 | 匿名先选启用商家，再查启用分类、上架商品；支持分类/关键字/分页；售罄仍展示 | API-02、15、16 | UI-02、UI-10（UI-13 由成员一） |
| PROD-02 商品管理 | 商家分页、详情、新增、编辑本店商品；新商品默认下架；归属不可转店；不同规格独立记录；仅无历史引用商品可删 | API-03、04、05、17、18 | UI-04 |
| PROD-03 上下架与库存 | 本店商品切换上下架；按增减量调整库存；分类启用才可上架；条件更新防覆盖和负库存 | API-29、30 | UI-04 |
| PROD-04 分类管理 | 本店分类名称、排序、启停、删除；同店唯一、跨店可同名；公开只返回启用分类 | API-19、20、21、22、31 | UI-11（兼 UI-04 分类选择） |

## 3 数据库设计

两表均在项目 Phase 2 按需求说明书 4.3.2、4.3.3 建立，使用 InnoDB、utf8mb4、`utf8mb4_0900_as_cs`、DATETIME(3)。

`cake_category`（7 字段）：id、merchant_id、name、sort、status、create_time、update_time。

- `(merchant_id, name)` 唯一键：同店分类名唯一，不同店可同名；
- `merchant_id` 外键关联 `cake_merchant`，创建后不可转店；
- `sort ≥ 0`、`status ∈ {0,1}` 的 CHECK；名字去空白后 1～30 字符。

`cake_product`（12 字段）：id、merchant_id、category_id、name、spec、price、stock、image、description、status、create_time、update_time。

- `(id, merchant_id)` 唯一键 + `(category_id, merchant_id)` 复合外键：保证商品只能使用本店分类，跨店分类在数据库层也写不进去；
- `price DECIMAL(10,2) > 0`、`stock 0～2147483647`、`status 0/1`、名称 1～60、规格 1～30；
- 一条商品记录就是“一种规格”，例如同名蛋糕的 6 寸、8 寸是两条独立记录，各自价格和库存，不设计 SKU 表；
- 订单明细保存商品名、规格、单价快照，商品后续改名改价不影响历史订单。

关系：商家 1—n 分类；商家 1—n 商品；分类 1—n 商品；订单明细 n—1 商品。

## 4 后端设计

### 4.1 分层

| 层 | 文件 | 职责 |
| --- | --- | --- |
| Controller | `controller/catalog/PublicCatalogController.java`、`MerchantCatalogController.java` | 路由、方法级 MERCHANT 校验、拒绝多余请求体和查询参数 |
| DTO | `dto/CatalogRequests.java` | 15 个接口的严格白名单、字段校验、ID/金额/库存解析、关键字转义 |
| Service | `service/catalog/PublicCatalogService.java`、`MerchantCatalogService.java` | 公开可见性、事务边界、锁顺序、错误码 |
| Mapper | `mapper/CatalogMapper.java` | 注解 SQL，全部参数绑定；商家语句强制 merchant_id |
| VO | `vo/*View.java` | 公开/商家两套返回对象，价格固定两位小数字符串 |
| 错误码 | `exception/CatalogErrorCode.java` | 40401/40403/40404、40901/40909/40910/40911/40913 |
| 前端 API | `frontend/src/api/catalog.js` | 15 个请求方法，白名单字段与字符串 ID |

### 4.2 接口清单

| 接口 | 方法与路径 | 关键规则 |
| --- | --- | --- |
| API-02 | GET `/api/products` | merchantId 必填；启用店铺+启用分类+上架；categoryId 属本店才命中；keyword 字面匹配；id 降序 |
| API-15 | GET `/api/products/{id}` | 下架/分类禁用 40401；店铺不可用 40404 |
| API-16 | GET `/api/categories` | 启用分类，sort、id 升序 |
| API-17 | GET `/api/merchant/products` | 固定本店；含下架商品、真实库存；status/categoryId/keyword 筛选 |
| API-18 | GET `/api/merchant/products/{id}` | 本店详情；他店 40401 |
| API-19 | GET `/api/merchant/categories` | 本店全部分类；status 可选；不收 merchantId |
| API-03 | POST `/api/merchant/products` | 201；status 固定 0；归属取登录绑定；禁用分类可建下架商品 |
| API-04 | PUT `/api/merchant/products/{id}` | 不收 stock/status/merchantId；上架商品改入禁用分类 40913 |
| API-05 | DELETE `/api/merchant/products/{id}` | 任一订单明细引用（含已取消）→ 40910 |
| API-29 | PATCH `/api/merchant/products/{id}/stock` | delta、expectedStock 条件更新；40911 |
| API-30 | PATCH `/api/merchant/products/{id}/status` | 上架校验分类启用；同值幂等 |
| API-20 | POST `/api/merchant/categories` | 201；status 固定 1；同店重名 40901 |
| API-21 | PUT `/api/merchant/categories/{id}` | 只改 name/sort；重名 40901、他店 40403 |
| API-22 | DELETE `/api/merchant/categories/{id}` | 有商品 40909；空分类可删 |
| API-31 | PATCH `/api/merchant/categories/{id}/status` | 禁用前须无上架商品，否则 40909；同值幂等 |

公开对象字段：id、merchantId、merchantName、categoryId、categoryName、name、spec、price、stock、image、description、status；商家端另含 createTime、updateTime。金额为两位小数字符串，ID 为十进制字符串。

### 4.3 公开可见性

公开列表固定 `merchant.status=1 AND category.status=1 AND product.status=1`。因此：停用店铺的商品不公开；禁用分类下的商品不公开；下架商品不公开；库存为 0 仍然公开，只在下单时禁止。分类筛选如果传入他店分类，结果自然为空，不泄露后台商品。

### 4.4 事务、锁与并发（报告重点）

1. **库存条件更新**：不用“先查再写”，而是
   `UPDATE cake_product SET stock=stock+delta WHERE id=? AND merchant_id=? AND stock=expectedStock AND stock+delta BETWEEN 0 AND 2147483647`。
   影响行数为 1 才成功；为 0 时再区分“商品不存在（40401）”与“库存已变化（40911）”。两个窗口拿同一旧库存同时调整，只有一个成功，另一个收到 40911，不发生覆盖或负库存。
2. **分类启用才能上架**：上下架时先锁分类行，再锁商品行，确认分类未变化后条件更新；分类被禁用时上架返回 40913。
3. **禁用分类前先下架商品**：锁分类后，锁定本分类全部上架商品，数量不为 0 返回 40909；与商品上架使用同一分类行锁，两个操作不会同时成功。
4. **删除保护**：商品删除先锁商品并统计 `cake_order_item`（包括已取消订单），有引用返回 40910，只能下架；分类删除先锁分类并锁定本分类商品，有商品返回 40909。唯一键、复合外键在并发时兜底。
5. **归属不可伪造**：商家写接口在事务内重新锁当前账号，核对角色、启用状态、token_version，再从 `cake_merchant.user_id` 取店铺；客户端传 merchantId 直接 40302，读写他店资源统一 404。
6. **普通编辑不碰库存与状态**：编辑接口只更新基本信息，请求里带 stock/status 会被 40302 拒绝，避免“编辑覆盖订单扣减”。

### 4.5 参数校验与错误码

- 价格只接受最多两位小数的十进制字符串，`>0` 且不超过 `99999999.99`，后端用 `BigDecimal` 精确比较，不做浮点或静默舍入。
- 名称、规格、分类名、关键字均按字符数校验并去掉首尾空白；关键字最长 60，LIKE 使用固定转义字符，`%`、`_`、`!` 按字面匹配。
- 400／40001 返回 `data.fieldErrors`（字段 → 字符串数组）；受保护字段（id、merchantId、status、stock、createTime 等）返回 403／40302。
- 业务错误：40401 商品不存在/下架，40403 分类不存在/非本店，40404 店铺不可用，40901 分类重名，40909 分类仍有商品/上架商品，40910 商品有历史订单，40911 库存或商品已变化，40913 分类禁用。

## 5 前端设计

| 页面 | 路由 | 说明 |
| --- | --- | --- |
| UI-02 本店商品页 | `/shop/:merchantId` | 匿名可看；分类切换、搜索、分页、商品卡、售罄标识；店铺停用显示友好提示 |
| UI-10 商品详情页 | `/shop/products/:id` | 商家、分类、规格、价格、库存、图片、纯文本详情；售罄禁用下单；40401/40404 可返回 |
| UI-04 商家商品管理 | `/merchant/products` | 本店筛选分页；新增/编辑/删除；库存增减（显示 expectedStock）；上下架确认；字段错误就地显示 |
| UI-11 商家分类管理 | `/merchant/categories` | 本店分类列表；新增/改名/排序；启停确认；删除；40901/40909 提示 |

公共实现：`ProductImage.vue` 统一图片比例与加载失败回退；`ConfirmDialog.vue` 统一二次确认与错误展示；`MerchantLayout.vue` 商家导航与退出；商家路由守卫每次读取服务器当前账号，前端守卫只做交互，后端仍独立鉴权。

四页均包含加载、空、错误状态，输入按纯文本渲染；价格与库存以服务器返回为准，前端不计算订单金额。

## 6 关键流程（答辩可讲）

**新增商品**：商家打开表单 → 选择本店分类（禁用分类会标注）→ 填写名称/规格/价格/库存/图片/详情 → 前端预校验 → `POST /api/merchant/products` → 后端固定 status=0、merchantId 取登录绑定 → 返回 201 与商品 → 列表刷新。新商品默认下架，避免未审核商品直接出现在顾客端。

**调整库存**：列表点“库存” → 弹窗显示当前库存并作为 expectedStock → 输入增减量 → 后端条件更新 → 成功返回新库存；若期间订单扣减或其他窗口改过库存，返回 40911，页面重新读取最新库存并提示，商家确认后重试。

**分类禁用与商品上架**：禁用分类时后端先锁分类，再锁该分类全部上架商品，有上架商品直接 40909；商家先下架商品再禁用。分类禁用后，商品上架返回 40913。两个操作使用同一分类行锁，不会出现“分类已禁用但商品又上架”的状态。

**公开浏览**：顾客进入 `/shop/:merchantId` → 公开分类与商品接口只返回启用店铺、启用分类、上架商品 → 售罄商品仍展示但禁止下单 → 点卡片进入详情 → 详情页显示数据库价格与库存。

## 7 测试与验证结果（2026-11-28）

| 项目 | 结果 |
| --- | --- |
| 后端请求契约单测 | 54 项 Catalog + 13 项公开商家通过 |
| 后端真实 MySQL/HTTP 集成测试 | 19 项 Catalog + 3 项公开商家（隔离临时 MySQL 8.0.46） |
| 后端全量套件 | 452 项通过，失败/错误/跳过 0（MySQL 8.0.46） |
| 后端打包 | 成功，JAR 真实启动 |
| JAR + 真实接口联调 | 28 项检查全部通过，另验证奶油星球登录、7/8 件商品与 API-33/34 |
| 前端测试 | 11 个文件、151 项通过（本模块新增 55 项，登录页 11 项） |
| 前端生产构建 | 成功 |
| 浏览器截图 | Chrome 158：UI-01、UI-02、UI-10、UI-04、UI-11 及移动端 390×844，使用真实照片与真实登录 |

集成测试覆盖了本店隔离、他店 404、创建默认下架、编辑不覆盖库存、删除历史引用、分类重名/禁用、库存旧值并发、分类禁用与上架竞争、事务故障回滚、最大无符号 ID，以及公开商家可见性/40404/字面关键字/分页等场景。详细命令、日志和证据见 `reports/PHASE_5_REPORT.md`。

## 8 截图清单（可直接放进报告/PPT）

| 文件 | 建议用途 |
| --- | --- |
| `backend/target/phase5-browser/ui-01-login.png` | 报告 6.x：三角色登录页（UI-01） |
| `backend/target/phase5-browser/ui-02-shop-products.png` | 报告 6.x 页面设计：顾客本店商品页（含店铺电话/地址） |
| `backend/target/phase5-browser/ui-10-product-detail.png` | 报告 6.x：商品详情页 |
| `backend/target/phase5-browser/ui-04-merchant-products.png` | 报告 7.x：商家商品管理列表（真实登录后） |
| `backend/target/phase5-browser/ui-04-create-dialog.png` | 报告 7.x：新增商品表单与默认下架 |
| `backend/target/phase5-browser/ui-11-merchant-categories.png` | 报告 7.x：分类管理（真实登录后） |
| `backend/target/phase5-browser/ui-11-create-dialog.png` | 报告 7.x：新增分类 |
| `backend/target/phase5-browser/ui-02-mobile.png` | 报告非功能/兼容性：390px 窄屏 |

截图使用本地演示店铺“奶油星球”、4 个分类和 8 个商品（含售罄、下架各一个），使用 `cake-01.png`～`cake-08.png` 真实照片，不是真实用户数据；商家页通过 UI-01 真实登录进入。

## 9 答辩讲解要点

**1 分钟版**：我负责商品、分类和库存。顾客端可以按店浏览启用分类和上架商品，售罄仍展示但不能下单；商家端只能维护本店商品和分类，新商品默认下架，库存用“增减量 + 原库存”条件更新，避免覆盖订单扣减；分类要先下架商品才能禁用；有历史订单的商品只能下架不能删除。

**3 分钟版**（按“做了什么—怎么保证正确—怎么验证”）：15 个接口、4 个页面、两套返回对象；公开可见性三层过滤；库存条件更新与 40911；分类与商品锁顺序；删除的历史引用保护；452 项后端测试、151 项前端测试、28 项真实接口检查、真实登录截图。为解决前置依赖，另协助补齐 UI-01 登录页与公开商家 API-33/34，待成员一评审。

**可能被问的问题**：

- 为什么库存不用“直接设置库存”？——并发会覆盖订单扣减；条件更新把判断交给数据库。
- 为什么新商品默认下架？——防止未准备完成的商品直接暴露；上架还要校验分类启用。
- 为什么有历史订单不能删商品？——订单明细要保留商品快照，外键也限制；下架即可停止销售。
- 不同规格怎么处理？——一条记录一种规格，独立价格库存，不建 SKU 表，符合需求。
- 商家能不能改别人的商品？——merchantId 从登录绑定读取，客户端传了也 40302；他店资源统一 404。
- 售罄为什么还能看到？——需求要求展示但禁止下单，库存只影响下单，不影响可见性。

## 10 需要补充的真实素材

1. **真实蛋糕照片**：已完成，`frontend/public/images/cake-01.png`～`cake-08.png`（4:3，约 1224×918）。单张最大约 1.7 MB，部署或弱网演示前建议压缩到 300～500 KB。
2. **演示店铺资料**：已完成，店铺“奶油星球”，电话 18800188800，地址“星空”；UI-02 已通过 API-34 展示电话、地址和简介。
3. **演示商品清单**：已完成，8 个商品、4 个分类，含售罄与下架各一个；可提交种子见 `database/seed/05_demo_seed.sql`，旧的本地脚本见 `database/demo/`（含明文密码、不提交）。
4. **账号**：可提交种子使用商家账号 `13900000001`，密码由项目负责人在小组内单独提供；`database/demo/README.md` 仅是本机旧说明，不随仓库分发。正式种子仍暂缓。
5. **报告排版**：已生成 `Web应用开发项目分析设计报告_含成员二章节.docx`（原报告副本 + 新增 7.7 节 + 4 张页面截图），可直接打开检查；如需更新目录页码，在 Word 中右键目录选择“更新域”。Markdown 版本保留在本文件，便于继续修改文字。

## 11 与成员一的衔接

| 事项 | 当前状态 | 建议 |
| --- | --- | --- |
| API-33/34 公开商家列表/详情 | 已由成员二协助实现，待成员一评审 | UI-02 已展示电话/地址；成员一如已有实现，以其版本为准 |
| UI-01 登录页 | 已由成员二协助实现，待成员一评审 | 支持三角色登录与按角色跳转；商家页面不再依赖临时会话 |
| 商家演示账号 | 可提交种子 `database/seed/`（账号 `13900000001`，密码单独提供） | 执行种子后登录奶油星球商家中心；或由 ADMIN 用 API-36 创建正式商家账号 |
| 订单扣减/回补 | 未实现（Phase 6/7） | 本模块库存字段与条件更新已就绪，订单模块直接复用 |
| 统一响应/字段错误/JWT | 已实现并复用 | 本模块未改成员一业务实现；按用户决定仅放宽其 MySQL 测试断言到 8.0.x |

## 12 关键代码位置

| 内容 | 路径 |
| --- | --- |
| 15 个接口控制器 | `backend/src/main/java/com/cake/platform/controller/catalog/` |
| 请求校验 | `backend/src/main/java/com/cake/platform/dto/CatalogRequests.java` |
| 公开/商家服务 | `backend/src/main/java/com/cake/platform/service/catalog/` |
| SQL 与锁 | `backend/src/main/java/com/cake/platform/mapper/CatalogMapper.java` |
| 错误码 | `backend/src/main/java/com/cake/platform/exception/CatalogErrorCode.java` |
| 返回对象 | `backend/src/main/java/com/cake/platform/vo/`（CategoryView、ProductView、Merchant*View、StockView 等） |
| 前端 API | `frontend/src/api/catalog.js` |
| 前端校验 | `frontend/src/catalog/validation.js` |
| 四个页面 | `frontend/src/views/shop/`、`frontend/src/views/merchant/` |
| 商家布局与守卫 | `frontend/src/layouts/MerchantLayout.vue`、`frontend/src/router/index.js` |
| 说明与报告 | `docs/catalog/CATALOG_MANAGEMENT.md`、`reports/PHASE_5_REPORT.md` |
