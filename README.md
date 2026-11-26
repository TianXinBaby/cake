# 基于 Spring Boot 与 Vue 的蛋糕店订单管理系统

本项目是 ADMIN、MERCHANT、CUSTOMER 三角色的多商家蛋糕店平台。当前执行 **Phase 4：平台商家与用户管理**，仅包含 MERCH-01、USER-05、API-35～40 及 UI-14/15。Phase 0～3.1 已获用户确认；历史阶段报告保留当时的验收结论。

本轮交付、验证和边界见 [Phase 4 报告](reports/PHASE_4_REPORT.md)、[管理接口与页面说明](docs/admin/PLATFORM_MANAGEMENT.md)、[项目状态](PROJECT_STATUS.md)和[待办](TODO.md)。继续沿用已验收的 `data.fieldErrors` 契约、JWT、BCrypt 和内存登录态。Phase 2 参数化种子继续暂缓；没有正式账号初始化或登录、注册、个人中心页面。

## 需求与阶段边界

唯一需求源是根目录[需求说明书 V1.1](蛋糕店订单管理系统需求说明书_V1.1.docx)，实际修订日期为2026-11-24。范围仍为20功能、45项API、17页面、46验收场景及六张核心表。当前DOCX与Phase0完整转录只在环境行不同，第四章相同；原件本轮保持原样。

- ENV-01：Node.js24.20.0；ENV-02：JDK18.0.2.1；ENV-03：Maven Wrapper，沿用已确认环境。
- 用户于2026-11-26明确授权Phase4，覆盖AGENTS及历史状态中的旧阶段限制。本轮只实现API-35～40及UI-14/15；历史Phase4规划中的公开商家API-33/34不在本轮范围，Phase5及其他页面未授权。
- 用户于2026-11-25确认 `utf8mb4_0900_as_cs`，区分大小写和重音；未修改服务器全局设置。
- Phase1历史为有条件通过；REQ-Q10公共结构已由用户确认，并在Phase3.1实施和回归，不修改历史报告。
- 当前三角色认证由后端独立校验；后续经营接口仍须按ADMIN只读订单、MERCHANT仅本店、CUSTOMER仅本人实现。

| 文档 | 用途 |
| --- | --- |
| [开发规则](AGENTS.md) | 原项目规则，结合最新用户授权执行 |
| [需求基线](docs/requirements/REQUIREMENTS_BASELINE.md) | Phase0完整转录，旧环境行以当前确认覆盖 |
| [追踪矩阵](docs/requirements/TRACEABILITY_MATRIX.md) | 保留Phase0原文追踪，附本轮Phase4实施状态 |
| [架构](docs/architecture/ARCHITECTURE_PLAN.md)、[阶段计划](docs/plan/PHASE_PLAN.md) | 历史规划；本轮实际范围以当前报告为准 |
| [Phase0报告](reports/PHASE_0_REPORT.md)、[Phase1报告](reports/PHASE_1_REPORT.md) | 保留历史证据，不覆盖 |
| [数据库设计](docs/database/DATABASE_DESIGN.md) | ER关系、快照和DATA01～09实现边界 |
| [数据字典](docs/database/DATA_DICTIONARY.md) | 六表74字段、类型、NULL、默认及约束 |
| [索引约束](docs/database/INDEX_AND_CONSTRAINTS.md) | PK、UK、普通索引、FK和CHECK |
| [初始化指南](docs/database/INITIALIZATION_GUIDE.md) | 环境变量、本机连接、初始化、实测和可选种子 |
| [认证设计](docs/security/AUTHENTICATION_DESIGN.md) | 六接口与前端基础集成 |
| [JWT权限](docs/security/JWT_AND_PERMISSION.md)、[密码安全](docs/security/PASSWORD_SECURITY.md) | 签名、当前身份、令牌失效、BCrypt及失败锁定 |
| [认证测试](docs/security/AUTH_API_TESTS.md) | 用例矩阵、隔离与安全复现命令 |

## 环境与工程

检查日期：2026-11-26；工作区 `D:\study\蛋糕店订单管理系统`。

| 项目 | 当前版本或设置 |
| --- | --- |
| JDK / 编译目标 | 18.0.2.1 / release18，现有 D:\study\jdk18 |
| Spring Boot | 3.5.16，沿用Phase1 |
| Spring Security / Nimbus JWT | Boot管理的6.5.11 / 9.37.4，HS256固定算法 |
| Maven / Wrapper | 3.9.11 / 官方3.3.4，配置及校验值保持原样 |
| MyBatis-Plus | Boot3 starter3.5.18，精确版本 |
| MySQL / Connector/J | 服务端8.0.19 / 驱动8.4.0 |
| 开发 / 专用测试库 | cake_order_system / cake_order_system_phase2_test |
| 表选项 | InnoDB / utf8mb4 / utf8mb4_0900_as_cs / DATETIME(3) |
| Node / npm | 24.20.0 / 11.19.0 |
| 前端 | 沿用Phase1 package.json及锁文件版本，新增API和Pinia认证状态 |
| Git | 非Git仓库；未init、commit、push或分支操作 |

默认Oracle javapath启动器的历史问题保留。后端命令只对当前PowerShell设置JAVA_HOME，不修改系统或用户变量、PATH、ExecutionPolicy。

```text
backend/                   原有应用及公共响应/异常处理
  .mvn/wrapper/            官方Maven Wrapper
  src/main/.../config/    数据源连接检查、脱敏SQL事件日志及原有配置
  src/main/resources/     application.yml、dev/prod配置
  src/test/               原有25项测试及数据库基础/真实测试
frontend/                  Vue/Vite、认证请求层及本轮两项平台管理页面
database/                 首次DDL、索引、约束、参数化种子及独立Java工具
docs/database/            Phase2四份数据库文档
docs/security/            Phase3认证、安全与测试文档
docs/admin/               Phase4平台管理契约、事务和页面边界
reports/                  阶段报告
```

认证代码位于backend的controller/auth、controller/user、dto、entity、mapper、security、service/auth、vo及对应测试；平台管理新增controller/admin、service/admin、AdminMapper和相关DTO/VO。前端复用api/auth.js、users.js和stores/auth.js，新增api/admin.js、后台布局及两个管理页。没有Redis、消息队列、SKU、真实支付、退款或库存流水等扩展。应用不自动运行DDL或种子，独立初始化工具不打包进入业务JAR。

## 数据库账号与密码配置位置

数据库账号和密码配置位于后端的 `spring.datasource` 节点，按运行环境分别配置：

| 运行环境 | 配置文件 |
| --- | --- |
| 本地开发（默认 `dev`） | [backend/src/main/resources/application-dev.yml](backend/src/main/resources/application-dev.yml) |
| 生产（`prod`） | [backend/src/main/resources/application-prod.yml](backend/src/main/resources/application-prod.yml) |
| 真实 MySQL 测试（`mysql-test`） | [backend/src/test/resources/application-mysql-test.yml](backend/src/test/resources/application-mysql-test.yml) |

上述文件中的账号、密码配置均为环境变量占位引用：

```yaml
spring:
  datasource:
    username: ${DB_USERNAME:}
    password: ${DB_PASSWORD:}
```

`spring.datasource.username` 对应 `DB_USERNAME`，`spring.datasource.password` 对应 `DB_PASSWORD`，两者均无内置默认凭据。真实账号和密码通过当前 PowerShell 进程的环境变量注入，隐藏输入命令见下方[启动](#启动)部分，不将真实凭据写入 YAML 或 README。

通用配置 [backend/src/main/resources/application.yml](backend/src/main/resources/application.yml) 设置默认环境为 `dev`。独立数据库工具 `database/tools/DatabaseTool.java` 直接读取同名环境变量 `DB_USERNAME`、`DB_PASSWORD`，不读取上述 YAML 文件。

## 数据库首次初始化

完整操作见[初始化指南](docs/database/INITIALIZATION_GUIDE.md)。Phase2已建开发库和专用测试库，本轮仅复用，**不要再次执行 init**；运行器遇已有表会拒绝。脚本用于首次初始化，DDL隐式提交，不能据失败推断已回滚。

准备独立工具的固定依赖并检查连接：

```powershell
Set-Location -LiteralPath 'D:\study\蛋糕店订单管理系统\backend'
$env:JAVA_HOME = 'D:\study\jdk18'
.\mvnw.cmd -B dependency:build-classpath '-Dmdep.outputFile=target/database-classpath.txt'
Set-Location -LiteralPath 'D:\study\蛋糕店订单管理系统'
$taskClassPath = (Get-Content -LiteralPath '.\backend\target\database-classpath.txt' -Raw).Trim()
& "$env:JAVA_HOME\bin\java.exe" -cp $taskClassPath '.\database\tools\DatabaseTool.java' check
```

缺少DB_USERNAME/DB_PASSWORD时在本机终端交互询问，密码隐藏、不保存。默认本机127.0.0.1:3306；首次初始化的CAKE_DB_COLLATION明确设 `utf8mb4_0900_as_cs`。真实密码不写入命令、文件、SQL或报告。

## 启动

后端使用本机隐藏交互设置进程环境：

```powershell
Set-Location -LiteralPath 'D:\study\蛋糕店订单管理系统\backend'
$env:JAVA_HOME = 'D:\study\jdk18'
$env:DB_USERNAME = Read-Host '本地数据库用户名'
$taskPassword = Read-Host '本地数据库密码' -AsSecureString
$env:DB_PASSWORD = [Net.NetworkCredential]::new('', $taskPassword).Password
$taskJwtSecret = Read-Host '安全配置提供的JWT密钥Base64编码（至少32随机字节）' -AsSecureString
$env:JWT_SECRET_BASE64 = [Net.NetworkCredential]::new('', $taskJwtSecret).Password
try { .\mvnw.cmd spring-boot:run }
finally {
    Remove-Item Env:DB_PASSWORD -ErrorAction SilentlyContinue
    Remove-Variable taskPassword -ErrorAction SilentlyContinue
    Remove-Item Env:JWT_SECRET_BASE64 -ErrorAction SilentlyContinue
    Remove-Variable taskJwtSecret -ErrorAction SilentlyContinue
}
```

默认dev、开发库cake_order_system，强制数据库会话+08:00。prod采用VERIFY_IDENTITY，要求部署时提供可信证书和完整连接变量；本轮未部署。缺凭据或连接失败有固定安全提示。SQL开发日志仅记录准备事件，不记录SQL、参数、结果或凭据。

前端另开窗口，已有依赖时直接启动：

```powershell
Set-Location -LiteralPath 'D:\study\蛋糕店订单管理系统\frontend'
npm.cmd run dev
```

默认后端127.0.0.1:8080、Vite127.0.0.1:5173，/api保持原路径代理。本轮页面路径为 `/admin/merchants` 和 `/admin/users`，需已有ADMIN内存会话；路由守卫读取本人接口再次确认权限。UI-01登录页尚未授权，缺少会话时回根入口提示先登录；刷新后须重登，不增加调试登录或令牌粘贴入口。未实现路径404不代表API通过。依赖缺失需重建时仍按原锁文件 `npm.cmd ci`，本轮不重新安装依赖。

## 测试与构建

无数据库的回归及打包：

```powershell
Set-Location -LiteralPath 'D:\study\蛋糕店订单管理系统\backend'
$env:JAVA_HOME = 'D:\study\jdk18'
.\mvnw.cmd -B clean test
.\mvnw.cmd -B package
```

普通test明确跳过真实MySQL套件。实际MySQL验证必须按[认证测试说明](docs/security/AUTH_API_TESTS.md)设置 `CAKE_MYSQL_TEST_ENABLED=true`、`DB_TEST_NAME=cake_order_system_phase2_test`、本机凭据和测试专属随机JWT密钥，再执行 `mvnw.cmd -B test` 或 `package`。不得将skip写成真实数据库通过。认证夹具按准确ID清理，原数据库测试事务回滚，最终核对六表计数与结构；不重置自增序列。

前端使用 `npm.cmd test`、`npm.cmd run build` 验证，保留既有测试覆盖，并将“仅根空路由”断言随本轮授权更新为管理路由及权限覆盖。Pinia只在内存保存令牌；刷新页面后须重新登录。后端JAR及测试XML位于target，生成物不作为手写业务源码。

公共响应仍为code/msg/data。可定位的HTTP400／40001参数错误返回 `data.fieldErrors: {"phone":["手机号格式不正确"]}`，支持多字段及同字段多消息；键为实际API公开lowerCamelCase字段，嵌套路径按JSON名称映射。无法定位、畸形JSON、整个请求体缺失、仅含未知字段的错误仍data=null，不增加_global。受保护字段仍40302，其他认证/业务/系统码及状态不变。Axios核心实现保持原样，RequestError.data原样透传。

历史Phase3.1验证为后端298项、前端59项全过，后端package和前端build成功；这些是历史基线，不计为本轮新增结果。本轮测试、数据库起止审计、JAR及浏览器证据见Phase4报告。阶段验证不替代未来商品、订单、完整TC01～46或生产验收。

本轮完成后停止于Phase4待审核，不进入Phase5，不执行git init、commit或push，不执行正式种子。

