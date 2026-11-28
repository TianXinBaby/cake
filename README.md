# 基于 Spring Boot 与 Vue 的蛋糕店订单管理系统

面向校园周边及社区的多商家蛋糕订购平台，设置平台管理员（ADMIN）、商家（MERCHANT）和顾客（CUSTOMER）三类角色。完整需求覆盖选店、商品浏览、下单、模拟支付、商家接单、配送或自提及销售统计。

**当前代码已包含基础工程、六表数据库方案、认证与权限、统一参数错误处理、平台商家和顾客管理、商品分类库存模块，以及 UI-01 登录页与公开商家接口。现有阶段记录为 Phase 5 已交付、待审核（UI-01/API-33/34 由成员二协助补齐，待成员一评审）。** 顾客下单、订单、支付、履约和统计仍待后续阶段实现。

README 更新日期：2026-11-28（Asia/Shanghai）。本次更新补充成员二（Phase 5）的商品分类库存、真实照片与演示数据、UI-01 登录页及公开商家接口；历史阶段结论保留。

## 当前功能与阶段状态

| 模块 | 已有实现 | 状态与边界 |
| --- | --- | --- |
| 基础工程 | Spring Boot / Vue 工程、统一响应与异常、ID/金额/时间序列化、Axios 请求层 | Phase 1 交付，历史遗留的参数错误结构已在 Phase 3.1 补齐 |
| 数据库 | 六张核心表、74 个字段及索引、唯一键、外键、CHECK；独立初始化工具 | Phase 2 交付；正式管理员和演示商家种子仍暂缓 |
| 认证与本人资料 | 注册、登录、退出、查询本人、修改昵称、修改密码；JWT、BCrypt、失败锁定及令牌失效 | Phase 3 交付；前端已有 API 和 Pinia 集成，登录/注册/个人中心页面未实现 |
| 参数错误 | `data.fieldErrors` 字段到字符串数组的错误映射 | Phase 3.1 交付；无法可靠定位时 `data=null` |
| 平台管理 | ADMIN 商家查询、创建、编辑、启停；CUSTOMER 查询和启停；两个管理页面 | Phase 4 报告记录已验证、待审核，范围为 MERCH-01、USER-05、API-35～40、UI-14/15 |
| 商品、分类与库存 | 公开分类/商品分页与详情；商家本店商品、分类、上下架、库存增减；四个页面 | Phase 5 报告记录已验证、待审核，范围为 PROD-01～04、API-02～05/15～22/29～31、UI-02/04/10/11 |
| UI-01 登录与 API-33/34 | 三角色登录、按角色跳转；公开商家列表/详情 | 成员二协助补齐，待成员一评审；解开商家页与店铺电话/地址的演示阻塞 |
| 本地演示数据 | 奶油星球、4 个分类、8 个商品、真实蛋糕照片 | `database/demo/` 仅本地开发/答辩，不提交、不是正式种子 |
| 后续业务 | 公开商家页面（UI-12/13）、订单、模拟支付、履约、统计及其余页面 | 尚未实现，不计入当前交付 |

实施和验收证据见 [Phase 4 报告](reports/PHASE_4_REPORT.md)、[项目状态](PROJECT_STATUS.md)和[待办事项](TODO.md)。阶段报告保留当时结论；后续开发须按用户明确授权执行。

## 需求依据与角色边界

需求按 V1.1 和用户已确认的补充维护。当前工作区的需求文件为[第5组+肖琳+蛋糕店订单管理系统需求说明书.docx](第5组+肖琳+蛋糕店订单管理系统需求说明书.docx)，历史基线和报告使用的来源名称为《蛋糕店订单管理系统需求说明书_V1.1.docx》。本 README 按现有文件名提供链接，不改动 DOCX；需求基线和追踪矩阵保留历史来源记录。

V1.1 范围为 20 项功能、45 项 API、17 个页面、46 个验收场景和六张核心表。上述数量表示完整需求范围，当前完成范围见上一节。

| 角色 | 需求规定的权限边界 |
| --- | --- |
| ADMIN | 管理商家和 CUSTOMER 账号；订单和统计只读，不代商家接单或维护商品 |
| MERCHANT | 仅维护本店经营资源、处理本店订单；账号与店铺由平台创建 |
| CUSTOMER | 公开注册固定为 CUSTOMER；仅维护本人资料、访问本人订单 |

后端独立校验 JWT、数据库当前角色、账号启停状态、令牌版本及资源归属；前端守卫用于页面访问控制。商品和订单相关归属校验须随对应业务模块实现。

核心表固定为 `sys_user`、`cake_merchant`、`cake_category`、`cake_product`、`cake_order`、`cake_order_item`。范围不包含真实支付、退款审批、外部物流、优惠券、配送费、SKU 组合、跨店合单或库存流水表。

## 技术栈与工程环境

下表依据当前工程配置、锁文件和阶段报告。具体依赖见 [backend/pom.xml](backend/pom.xml) 和 [frontend/package.json](frontend/package.json)。

| 项目 | 工程版本或约定 |
| --- | --- |
| JDK | 18.0.2.1；编译目标 release 18；本机既有路径 `D:\study\jdk18`（ENV-02） |
| Spring Boot | 3.5.16 |
| Spring Security / JWT | Boot 管理的 Spring Security、Nimbus JOSE JWT；HS256、2 小时有效期 |
| Maven | 官方 Maven Wrapper 3.3.4 / Maven 3.9.11（ENV-03） |
| MyBatis-Plus / Connector/J | 3.5.18 / 8.4.0 |
| MySQL | 测试断言与 DatabaseTool 已放宽为 8.0.x；Phase 5 在 8.0.46 全量通过，不强制全组重装 8.0.19 |
| Node.js / npm | ENV-01 基线 24.20.0 / 11.19.0；Phase 5 实测本机 22.23.1 / 10.9.8，按用户决定待全组升级后复跑 |
| Vue / Vue Router / Pinia | 3.5.46 / 4.6.5 / 3.0.4 |
| Element Plus / Axios | 2.14.8 / 1.21.1 |
| Vite / Vitest | 7.3.8 / 4.1.12 |
| Git | 当前工作区已有 Git 仓库，实查分支为 `qxy-dev`；历史文档中的“非 Git 仓库”为当时记录 |

Phase 0 历史转录中的 JDK 17 / Node.js 18 环境行与后续确认的 ENV-01～03 区分保留，当前工程按上表执行。排序规则 `utf8mb4_0900_as_cs` 区分大小写和重音，已由用户确认。

Windows 使用 PowerShell、`npm.cmd` 和工程自带的 `mvnw.cmd`。后端命令通过当前进程的 `JAVA_HOME` 指向指定 JDK，不依赖全局 Maven，不修改系统环境变量或 ExecutionPolicy。

## 项目结构

```text
backend/
  .mvn/wrapper/               Maven Wrapper 配置
  src/main/java/com/cake/platform/
    common/、config/           公共响应、序列化、数据源等配置
    controller/               auth、user、admin、catalog 控制器
    dto/、vo/                  请求校验和公开响应对象
    entity/、mapper/           认证、平台管理与商品分类库存的数据访问
    security/                 JWT、当前身份和权限校验
    service/                  auth、admin、catalog 业务与事务
    exception/                统一异常及字段错误处理
  src/main/resources/         dev / prod 配置
  src/test/                   单元测试和隔离 MySQL 集成测试
frontend/
  src/api/                    Axios、认证、本人资料、平台管理与商品分类 API
  src/stores/                 Pinia 内存认证状态
  src/router/                 路由、管理员守卫与商家守卫
  src/layouts/、components/    后台布局、商家布局及管理/商品组件
  src/views/admin/            商家管理、顾客管理页面
  src/views/auth/             UI-01 登录页
  src/views/shop/             本店商品、商品详情页面
  src/views/merchant/         商家商品管理、分类管理页面
  src/styles/                 基础、商品与登录页样式
  public/images/              真实蛋糕照片与加载失败占位图
database/                     六表 DDL、索引、约束、种子模板及独立 Java 工具
  demo/                       本地演示数据（奶油星球，非正式种子、不提交）
docs/                         需求、架构、阶段计划、数据库、安全及管理说明
reports/                      各阶段报告
```

应用不自动执行 DDL 或种子；独立数据库工具不打包进入业务 JAR。`backend/target/`、`frontend/dist/` 是生成物目录，`frontend/node_modules/` 是依赖目录。

## 数据库与密钥配置

账号和密码配置位于后端 `spring.datasource` 节点，真实值通过进程环境变量注入：

| 用途 | 配置文件 |
| --- | --- |
| 通用配置、默认 `dev`、JWT 密钥引用 | [application.yml](backend/src/main/resources/application.yml) |
| 本地开发 | [application-dev.yml](backend/src/main/resources/application-dev.yml) |
| 生产 | [application-prod.yml](backend/src/main/resources/application-prod.yml) |
| 真实 MySQL 测试 | [application-mysql-test.yml](backend/src/test/resources/application-mysql-test.yml) |

```yaml
spring:
  datasource:
    username: ${DB_USERNAME:}
    password: ${DB_PASSWORD:}
```

| 环境变量 | 用途与默认值 |
| --- | --- |
| `DB_HOST` / `DB_PORT` | dev 默认 `127.0.0.1:3306`；prod 必须提供主机 |
| `DB_NAME` | dev 默认 `cake_order_system`；prod 必须提供库名 |
| `DB_USERNAME` / `DB_PASSWORD` | 数据库凭据，无内置默认账号或密码 |
| `JWT_SECRET_BASE64` | HS256 密钥的 Base64 编码；解码后至少 32 个随机字节，无默认密钥 |
| `SPRING_PROFILES_ACTIVE` | 默认 `dev`；生产使用 `prod` |
| `SERVER_ADDRESS` / `SERVER_PORT` | 默认 `127.0.0.1` / `8080` |
| `DB_TEST_NAME` | 真实测试显式使用隔离库 `cake_order_system_phase2_test` |
| `CAKE_MYSQL_TEST_ENABLED` | 仅为 `true` 时启用真实 MySQL 测试 |

数据库会话时区固定为 `+08:00`。prod 使用 `VERIFY_IDENTITY`，部署时还需提供可信证书配置；生产 TLS 和密钥托管尚未验收。开发 SQL 日志仅记录准备事件，不输出 SQL、参数、结果或凭据。

数据库首次初始化和连接检查见[初始化指南](docs/database/INITIALIZATION_GUIDE.md)。历史报告记录开发库及专用测试库已建，已有表时不要重复执行 `init`；工具会拒绝已有对象，DDL 失败也不表示已经回滚。`database/tools/DatabaseTool.java` 直接读取环境变量，不读取 Spring YAML。

正式账号种子仍暂缓，当前没有可供 README 公布的默认管理员、演示账号或密码。真实凭据与 JWT 密钥不写入 YAML、SQL、README 或命令字面量。

## 启动项目步骤（Windows / PowerShell）

按“检查环境 → 确认数据库与依赖 → 启动后端 → 启动前端 → 浏览器检查”的顺序操作。后端和前端分别占用一个 PowerShell 窗口，运行期间保持窗口打开。以下命令用于本机开发，不表示本次文档更新已经执行了启动或安装。

### 1. 检查运行环境



分别为 JDK `18.0.2.1`、Node.js `v24.20.0`、npm `11.19.0`。后端使用项目内的 Maven Wrapper，无需另装全局 Maven。若版本不符，先确认环境，不自动切换或修改系统配置；当前本机为 Node 22.23.1，Phase 5 已在该版本通过测试与构建，按用户决定等全组升级后再整体复跑。

### 2. 确认 MySQL 和前端依赖

本机已有 MySQL 服务名为 `MySQL80`，可查看状态：

```powershell
Get-Service -Name 'MySQL80'
```

状态应为 `Running`；未运行时，在 Windows“服务”中启动该服务。其他机器以实际服务名为准。使用数据库客户端确认 `127.0.0.1:3306` 可连接，开发库 `cake_order_system` 已有六张核心表，并准备可访问该库的数据库用户名和密码。MySQL 8.0.x 均可，测试与建库工具不再强制 8.0.19。

历史报告记录开发库已经建立，日常启动无需执行 SQL、`init` 或种子。新环境尚无数据库时，先按[初始化指南](docs/database/INITIALIZATION_GUIDE.md)确认并完成获授权的首次初始化，再继续启动。若需要「奶油星球」本地演示账号、分类和 8 个商品，按[本地演示数据说明](database/demo/README.md)在本地库执行脚本；该数据与账号仅用于本机演示，不属于正式种子。

已有 `frontend/node_modules/` 时直接进入下一步。首次准备且缺少依赖、已获准恢复依赖时，执行：

```powershell
Set-Location -LiteralPath '“修改成你的项目位置”\蛋糕店订单管理系统\frontend'（例如‘D:\study\蛋糕店订单管理系统’）
npm.cmd ci
```

`npm.cmd ci` 按已有锁文件恢复依赖，日常启动不必重复运行。首次运行 Maven Wrapper 时也可能联网下载固定版本的 Maven 及项目依赖。

### 3. 在窗口一启动后端

下面的命令使用本机开发配置，交互输入数据库凭据，并在内存中生成 32 字节随机开发用 JWT 密钥，无需手工编写密钥或将密码填入 YAML：

```powershell
Set-Location -LiteralPath '“修改成你的项目位置”\蛋糕店订单管理系统\backend'//（例如‘D:\study\蛋糕店订单管理系统’）
$env:JAVA_HOME = 'D:\study\jdk18'
$env:SPRING_PROFILES_ACTIVE = 'dev'
$env:DB_HOST = '127.0.0.1'
$env:DB_PORT = '3306'
$env:DB_NAME = 'cake_order_system'
$env:SERVER_ADDRESS = '127.0.0.1'
$env:SERVER_PORT = '8080'
try {
    $env:DB_USERNAME = Read-Host '本地数据库用户名'
    $taskPassword = Read-Host '本地数据库密码' -AsSecureString
    $env:DB_PASSWORD = [Net.NetworkCredential]::new('', $taskPassword).Password
    $taskJwtBytes = [byte[]]::new(32)
    $taskJwtRng = [Security.Cryptography.RandomNumberGenerator]::Create()
    try { $taskJwtRng.GetBytes($taskJwtBytes) }
    finally { $taskJwtRng.Dispose() }
    $env:JWT_SECRET_BASE64 = [Convert]::ToBase64String($taskJwtBytes)
    .\mvnw.cmd spring-boot:run
}
finally {
    Remove-Item Env:DB_PASSWORD,Env:JWT_SECRET_BASE64 -ErrorAction SilentlyContinue
    if ($null -ne $taskJwtBytes) {
        [Array]::Clear($taskJwtBytes, 0, $taskJwtBytes.Length)
    }
    Remove-Variable taskPassword,taskJwtBytes,taskJwtRng -ErrorAction SilentlyContinue
}
```

密码输入时不显示字符，输入完成后按 Enter。等待日志出现 `Started CakeOrderPlatformApplication`，随后数据库启动检查未报错，且进程持续运行。若出现 `APPLICATION FAILED TO START`、数据库连接失败或密钥错误，应先处理错误再继续。

本示例的随机密钥只用于本地开发，每次重启后端会生成新密钥，旧 JWT 随之失效。需要跨重启保留有效令牌的环境应由外部安全配置注入同一密钥；生产配置仍按前文管理。

### 4. 在窗口二启动前端

保持后端运行，另开一个 PowerShell 窗口执行：

```powershell
Set-Location -LiteralPath '“修改成你的项目位置”\蛋糕店订单管理系统\frontend'//（例如‘D:\study\蛋糕店订单管理系统’）
npm.cmd run dev
```

终端显示 Vite 已就绪以及 `http://127.0.0.1:5173/` 后，保持该窗口运行。Vite 将 `/api` 原路径代理到后端 `http://127.0.0.1:8080`。

### 5. 检查服务响应并打开页面

可在另一个 PowerShell 窗口执行以下只读请求：

```powershell
curl.exe --include --max-time 5 http://127.0.0.1:8080/api/users/me
curl.exe --include --max-time 5 http://127.0.0.1:5173/api/users/me
```

没有携带登录令牌时，两次请求预期均为 HTTP `401`、业务码 `40102`，提示“登录已失效，请重新登录”。第一次检查后端鉴权响应，第二次检查前端开发代理；这些响应不能单独证明数据库健康或业务流程已验收。

随后在浏览器打开：

| 服务或页面 | 默认地址 | 使用条件 |
| --- | --- | --- |
| 后端 | `http://127.0.0.1:8080` | 数据库连接和 JWT 配置齐全 |
| 登录页 UI-01 | `http://127.0.0.1:5173/login` | 三角色共用；登录后按账号角色跳转 |
| 前端根入口 | `http://127.0.0.1:5173/` | 基础出口及访问提示 |
| 商家管理 UI-14 | `http://127.0.0.1:5173/admin/merchants` | 通过 `/login` 使用 ADMIN 登录 |
| 顾客管理 UI-15 | `http://127.0.0.1:5173/admin/users` | 通过 `/login` 使用 ADMIN 登录 |
| 本店商品页 UI-02 | `http://127.0.0.1:5173/shop/1` | 匿名可访问，`1` 换成实际商家 ID（奶油星球演示数据为 1） |
| 商品详情页 UI-10 | `http://127.0.0.1:5173/shop/products/1` | 匿名可访问，`1` 换成实际商品 ID |
| 商家商品管理 UI-04 | `http://127.0.0.1:5173/merchant/products` | 通过 `/login` 使用 MERCHANT 登录 |
| 商家分类管理 UI-11 | `http://127.0.0.1:5173/merchant/categories` | 通过 `/login` 使用 MERCHANT 登录 |

**UI-01 已交付（成员二协助补齐，待成员一评审）。** 未登录访问后台或商家页会跳转 `/login` 并保留目标地址；登录成功按服务器返回角色跳转：ADMIN→商家管理、MERCHANT→商品管理、CUSTOMER→公开入口并提示后续页面。页面不提供角色选择，角色完全由账号决定。Pinia 令牌仅保存在内存，刷新后需重新登录；正式管理员种子仍暂缓，本地演示可使用 `database/demo/` 的临时商家账号（仅本机，见该目录说明）。

### 6. 停止项目

在自己启动的前端、后端 PowerShell 窗口分别按 `Ctrl+C`。等待命令退出；后端示例的 `finally` 会清理当前进程中的数据库密码和 JWT 密钥。项目停止无需停止 MySQL 服务。

### 常见启动问题

| 现象 | 检查方法或处理方向 |
| --- | --- |
| Java 启动器异常或找不到 Java | 按上文使用 `D:\study\jdk18`，确认该目录下的 `bin/java.exe` 存在 |
| 数据库凭据未配置或连接失败 | 检查 MySQL 服务、主机端口、开发库、用户名及密码；在窗口一重新执行完整启动块 |
| JWT 密钥格式或长度错误 | 使用上文内存随机生成步骤；外部密钥须为 Base64 编码且解码后至少 32 字节 |
| 8080 或 5173 端口占用 | 确认占用者；只关闭自己启动的旧实例。若调整后端端口，须同步 Vite 代理目标 |
| 找不到 Vite 或前端依赖 | 确认位于 `frontend` 目录，并按步骤 2 判断是否需恢复依赖 |
| 根页面空白、管理页跳转登录 | 先访问 `/login` 完成登录；刷新后令牌仅内存保存，需要重新登录 |

## 已实现接口

以下 29 项为当前已有控制器和服务的业务接口。请求字段、错误码和事务边界见[认证设计](docs/security/AUTHENTICATION_DESIGN.md)、[平台管理说明](docs/admin/PLATFORM_MANAGEMENT.md)及[商品分类库存说明](docs/catalog/CATALOG_MANAGEMENT.md)。

| 编号 | 方法与路径 | 功能与访问范围 |
| --- | --- | --- |
| API-01 | `POST /api/auth/login` | 三角色统一登录 |
| API-02 | `GET /api/products` | 匿名分页浏览所选店铺的上架商品 |
| API-03 | `POST /api/merchant/products` | MERCHANT 新增本店商品，默认下架 |
| API-04 | `PUT /api/merchant/products/{id}` | MERCHANT 编辑本店商品基本信息，不改库存/状态/归属 |
| API-05 | `DELETE /api/merchant/products/{id}` | MERCHANT 删除无历史订单引用的商品 |
| API-10 | `POST /api/auth/register` | 公开注册，固定 CUSTOMER |
| API-11 | `POST /api/auth/logout` | 已登录账号退出，使该账号旧 JWT 失效 |
| API-12 | `GET /api/users/me` | 查询本人公开资料 |
| API-13 | `PATCH /api/users/me` | 修改本人昵称 |
| API-14 | `PATCH /api/users/me/password` | 验证旧密码、修改密码，使旧 JWT 失效 |
| API-15 | `GET /api/products/{id}` | 匿名查看上架商品详情 |
| API-16 | `GET /api/categories` | 匿名查询所选店铺的启用分类 |
| API-33 | `GET /api/merchants` | 匿名分页查询启用店铺（成员二协助补齐） |
| API-34 | `GET /api/merchants/{id}` | 匿名查看启用店铺详情（成员二协助补齐） |
| API-17 | `GET /api/merchant/products` | MERCHANT 分页查询本店商品，含下架商品 |
| API-18 | `GET /api/merchant/products/{id}` | MERCHANT 查看本店商品详情 |
| API-19 | `GET /api/merchant/categories` | MERCHANT 查询本店全部分类 |
| API-20 | `POST /api/merchant/categories` | MERCHANT 新增本店分类 |
| API-21 | `PUT /api/merchant/categories/{id}` | MERCHANT 编辑本店分类名称与排序 |
| API-22 | `DELETE /api/merchant/categories/{id}` | MERCHANT 删除本店空分类 |
| API-29 | `PATCH /api/merchant/products/{id}/stock` | MERCHANT 按增减量条件调整本店库存 |
| API-30 | `PATCH /api/merchant/products/{id}/status` | MERCHANT 上架/下架本店商品 |
| API-31 | `PATCH /api/merchant/categories/{id}/status` | MERCHANT 启用/禁用本店分类 |
| API-35 | `GET /api/admin/merchants` | ADMIN 分页查询商家 |
| API-36 | `POST /api/admin/merchants` | ADMIN 在同一事务创建 MERCHANT 账号和店铺 |
| API-37 | `PUT /api/admin/merchants/{id}` | ADMIN 编辑店铺资料，绑定账号不变 |
| API-38 | `PATCH /api/admin/merchants/{id}/status` | ADMIN 启停店铺，不修改商家账号状态 |
| API-39 | `GET /api/admin/users` | ADMIN 分页查询 CUSTOMER |
| API-40 | `PATCH /api/admin/users/{id}/status` | ADMIN 启停 CUSTOMER，状态变化时递增令牌版本 |

商品接口强制登录商家的本店归属，公开接口只返回启用店铺、启用分类和上架商品；库存调整使用 `delta + expectedStock` 条件更新，不接收直接覆盖。金额为两位小数字符串，商品与分类 ID 为十进制字符串。

公共响应保持 `code/msg/data`。ID 使用十进制字符串，避免前端整数精度丢失；时间采用 `+08:00` ISO 8601 毫秒字符串。密码使用 BCrypt；登录连续失败第 5 次锁定 10 分钟。退出、改密和顾客账号状态变化使用令牌版本使旧 JWT 失效。

可定位的 HTTP 400 / 业务码 40001 参数错误示例：

```json
{
  "code": 40001,
  "msg": "参数错误",
  "data": {
    "fieldErrors": {
      "phone": ["手机号格式不正确"]
    }
  }
}
```

`fieldErrors` 的键为实际公开的 lowerCamelCase API 字段，值为字符串数组，支持多字段和同字段多条消息。无法可靠定位、畸形 JSON 或整个请求体缺失时 `data=null`，不增加 `_global`；受保护字段继续按 40302 拒绝。Axios 通过 `RequestError.data` 保留后端错误数据，页面展示字段消息。

## 测试与构建

普通后端回归不启用真实数据库套件；在未设置 `CAKE_MYSQL_TEST_ENABLED=true` 的 PowerShell 中执行：

```powershell
Set-Location -LiteralPath 'D:\study\蛋糕店订单管理系统\backend'
$env:JAVA_HOME = 'D:\study\jdk18'
.\mvnw.cmd -B test
.\mvnw.cmd -B package
```

真实 MySQL 测试另按[认证测试说明](docs/security/AUTH_API_TESTS.md)注入本机凭据和测试专用随机 JWT 密钥，并设置 `CAKE_MYSQL_TEST_ENABLED=true`、`DB_TEST_NAME=cake_order_system_phase2_test`。测试会写入并清理隔离夹具，不在开发库或正式库执行；跳过的套件不能记为真实数据库、事务或并发验证通过。

前端在已有依赖的目录执行：

```powershell
Set-Location -LiteralPath 'D:\study\蛋糕店订单管理系统\frontend'
npm.cmd test
npm.cmd run build
```

最近一次阶段验证记录如下，来源为 2026-11-28 的 [Phase 5 报告](reports/PHASE_5_REPORT.md)；Phase 4 的历史结果保留在对应报告：

| 检查 | Phase 5 报告中的结果 |
| --- | --- |
| 后端新增契约测试 | 54 项 Catalog + 13 项公开商家，失败/错误/跳过 0 |
| 后端新增真实 MySQL 测试 | 19 项 Catalog + 3 项公开商家（隔离临时 MySQL 8.0.46） |
| 后端全量套件 | 452 项在 MySQL 8.0.46 通过，失败/错误/跳过 0 |
| 后端打包 | 在完整测试后以 `-DskipTests package` 打包成功 |
| JAR 真实联调 | 连接隔离临时库，28 项 HTTP 检查全部通过；另验证奶油星球登录、7/8 件商品与 API-33/34 |
| 前端测试 | 11 个文件、151 项通过（原 85 + 新增 66，含登录页 11） |
| 前端构建 | 成功；保留主 chunk 超过 500 kB 的体积提示 |
| 浏览器截图 | Chrome 158；UI-01、UI-02、UI-10、UI-04、UI-11、两个表单与 1440×1000 / 390×844，使用真实照片与真实登录 |

这些结果仅覆盖已交付阶段，全部 46 个验收场景、17 个页面、订单支付履约流程及生产部署仍待对应阶段验收。后端测试 XML 位于 `backend/target/surefire-reports/`；Phase 5 证据位于 `backend/target/phase5-evidence/` 和 `backend/target/phase5-browser/`。

## 文档导航与后续事项

| 文档 | 用途 |
| --- | --- |
| [AGENTS.md](AGENTS.md) | 项目规则及修改边界，执行时结合当前明确授权 |
| [PROJECT_STATUS.md](PROJECT_STATUS.md)、[TODO.md](TODO.md) | 阶段状态、待审核内容和需求待确认项；历史 Git 状态以当前实查为准 |
| [需求基线](docs/requirements/REQUIREMENTS_BASELINE.md)、[追踪矩阵](docs/requirements/TRACEABILITY_MATRIX.md) | 原文完整转录及功能/API/UI/验收追踪 |
| [架构计划](docs/architecture/ARCHITECTURE_PLAN.md)、[阶段计划](docs/plan/PHASE_PLAN.md) | 架构约定、Phase 0～13 的范围与依赖 |
| [数据库设计](docs/database/DATABASE_DESIGN.md)、[数据字典](docs/database/DATA_DICTIONARY.md) | 六表关系、74 字段及数据约束 |
| [索引与约束](docs/database/INDEX_AND_CONSTRAINTS.md)、[初始化指南](docs/database/INITIALIZATION_GUIDE.md) | 建库条件、工具、连接及隔离验证 |
| [认证设计](docs/security/AUTHENTICATION_DESIGN.md)、[JWT 与权限](docs/security/JWT_AND_PERMISSION.md) | 六个认证/本人接口、身份与令牌规则 |
| [密码安全](docs/security/PASSWORD_SECURITY.md)、[认证测试](docs/security/AUTH_API_TESTS.md) | BCrypt、失败锁定、测试与安全复现 |
| [平台管理说明](docs/admin/PLATFORM_MANAGEMENT.md) | 六管理接口、事务、权限及两页面 |
| [商品分类库存说明](docs/catalog/CATALOG_MANAGEMENT.md)、[成员二报告材料](docs/catalog/MEMBER_2_REPORT_CONTENT.md) | 15 个商品分类库存接口、四页面、事务并发及报告/答辩素材 |
| [本地演示数据](database/demo/README.md) | 奶油星球临时商家账号、分类与 8 个商品；仅本地、不提交 |
| [Phase 0](reports/PHASE_0_REPORT.md)、[Phase 1](reports/PHASE_1_REPORT.md)、[Phase 2](reports/PHASE_2_REPORT.md)、[Phase 3](reports/PHASE_3_REPORT.md)、[Phase 3.1](reports/PHASE_3_1_REPORT.md)、[Phase 4](reports/PHASE_4_REPORT.md)、[Phase 5](reports/PHASE_5_REPORT.md) | 各阶段当时的实施与验证证据 |

当前待办包括 Phase 4 审核、正式账号种子及后续页面流程，以及商家账号禁用来源、支付超时与停店优先级、库存回补溢出、排行商家名称快照和密码 Unicode 长度口径等需求问题，详见 TODO。未经明确授权，不进入下一阶段，不自动执行种子、提交或推送。
