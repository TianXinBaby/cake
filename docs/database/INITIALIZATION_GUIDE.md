# Phase 2 数据库初始化指南

本文对应 MySQL 8.0.x（历史实测基线 8.0.19，Phase 5 起测试与建库工具接受 8.0.x）、JDK18.0.2.1 和当前工程。以下命令为复现操作说明；本轮实际执行记录见 [报告](../../reports/PHASE_2_REPORT.md)。不安装或切换软件，不改系统环境变量、ExecutionPolicy 或已有数据库。

## 已确认配置与保护规则

| 项目 | 设置 |
| --- | --- |
| 开发库 | cake_order_system |
| 隔离测试库 | cake_order_system_phase2_test |
| 引擎/字符集/排序 | InnoDB / utf8mb4 / utf8mb4_0900_as_cs（用户已确认） |
| 连接 | 本机 127.0.0.1:3306；用户名和密码由本机提供 |
| 会话时区 | +08:00；DATETIME(3) |
| 版本 | Boot3.5.16、MyBatis-Plus3.5.18、Connector/J8.4.0 |

初始化器只接受本机连接、合法库名及白名单 collation。目标不存在则创建；存在时必须字符集/排序一致且完全无表/视图。已有对象时拒绝，不能将 `IF NOT EXISTS` 当成结构匹配或覆盖保护。不要对正式业务库执行测试。

本阶段没有 DROP DATABASE、清空、级联操作或自动修复表结构。DDL 隐式提交，执行中断可能留下部分表/索引；必须检查执行日志和元数据后由用户决定处理，不删除或直接覆盖。若以后需要另一空库复现，须显式设置 DB_NAME 并确认其用途。

## 环境变量

| 变量 | 用途/默认 |
| --- | --- |
| JAVA_HOME | 当前PowerShell内设 D:\study\jdk18，Wrapper使用 |
| DB_HOST / DB_PORT | 开发默认127.0.0.1 / 3306；初始化器只支持本机 |
| DB_NAME | 开发默认cake_order_system；prod必须显式提供 |
| DB_USERNAME / DB_PASSWORD | 无内置账号或密码；通过本机交互注入 |
| CAKE_DB_COLLATION | 初始化明确设utf8mb4_0900_as_cs |
| DB_TEST_NAME | 实测专用库，必须显式设cake_order_system_phase2_test，与开发库不同 |
| CAKE_MYSQL_TEST_ENABLED | 设true才启用真实数据库测试；未设时跳过并明确显示 |
| SPRING_PROFILES_ACTIVE | dev / prod；普通测试使用test，真实测试mysql-test |

禁止在聊天、命令历史、源码或SQL写密码。下面的 `Read-Host -AsSecureString` 隐藏输入，转换后的 DB_PASSWORD 只存活在当前进程及其子进程，命令结束后删除；不要使用 `Get-ChildItem Env:` 输出变量值，不启用 Maven -X、JDBC追踪或记录敏感参数的日志。

## 首次准备运行器依赖

在项目根目录打开 PowerShell：

```powershell
Set-Location -LiteralPath 'D:\study\蛋糕店订单管理系统'
$env:JAVA_HOME = 'D:\study\jdk18'
Push-Location -LiteralPath '.\backend'
try {
    .\mvnw.cmd -B dependency:build-classpath '-Dmdep.outputFile=target/database-classpath.txt'
    if ($LASTEXITCODE -ne 0) { throw '依赖解析失败' }
} finally { Pop-Location }
$taskClassPath = (Get-Content -LiteralPath '.\backend\target\database-classpath.txt' -Raw).Trim()
```

PowerShell 中 Maven 的 `-D...` 完整参数需加引号，避免含点参数被拆分。该步骤只使用当前项目固定依赖及Maven缓存，不安装全局软件。

## 连接检查与首次建表

```powershell
$env:DB_HOST = '127.0.0.1'
$env:DB_PORT = '3306'
$env:DB_NAME = 'cake_order_system'
$env:CAKE_DB_COLLATION = 'utf8mb4_0900_as_cs'
& "$env:JAVA_HOME\bin\java.exe" -cp $taskClassPath '.\database\tools\DatabaseTool.java' check
& "$env:JAVA_HOME\bin\java.exe" -cp $taskClassPath '.\database\tools\DatabaseTool.java' init
```

`DB_USERNAME`、`DB_PASSWORD` 未设置时，每次命令在本机终端交互输入，密码隐藏；非交互环境缺凭据时直接失败，不猜测。`check` 仅验证连接、精确版本和会话时区，不建库。`init` 在任何DDL前检查空库；依次读取：

1. `database/schema/01_create_tables.sql`：六表及主键/唯一键。
2. `database/indexes/02_create_indexes.sql`：14项需求普通索引。
3. `database/constraints/03_data_constraints.sql`：8外键及29 CHECK。

运行器对 collation 作白名单校验后替换 `__CAKE_DB_COLLATION__`，不要原样把模板提交给 mysql。它只在末尾粗验六表74列；字段逐项、索引、外键及违规数据必须另跑实测。重复 `init` 应失败，已有数据保持原样。

## 专用测试库与真实测试

本轮已建立测试库，不要重复初始化。仅在该库尚不存在或明确为空时，把上段 `DB_NAME` 设为 `cake_order_system_phase2_test` 执行一次 init，再恢复开发库名。随后：

```powershell
$env:DB_NAME = 'cake_order_system'
$env:DB_TEST_NAME = 'cake_order_system_phase2_test'
$env:DB_USERNAME = Read-Host '本地数据库用户名'
$taskPassword = Read-Host '本地数据库密码' -AsSecureString
$env:DB_PASSWORD = [Net.NetworkCredential]::new('', $taskPassword).Password
$env:CAKE_MYSQL_TEST_ENABLED = 'true'
Push-Location -LiteralPath '.\backend'
try {
    .\mvnw.cmd -B test
    if ($LASTEXITCODE -ne 0) { throw '数据库测试失败，请检查报告' }
} finally {
    Pop-Location
    Remove-Item Env:DB_PASSWORD,Env:CAKE_MYSQL_TEST_ENABLED -ErrorAction SilentlyContinue
    Remove-Variable taskPassword -ErrorAction SilentlyContinue
}
```

测试只允许指定的独立测试库，验证字段、NULL/默认、PK/UK、普通及外键支持索引、FK RESTRICT、CHECK和状态时间链。完整性数据在事务中插入并回滚，每次验证清理；测试不提交演示账号。不将应用层角色权限、订单并发或TC-01～46视为已完成。测试失败时先检查清理与残留，不在正式库继续试错。

## 后端启动与配置隔离

dev 为默认开发profile，数据源用户名/密码无默认。用上段隐藏方式设置 DB_USERNAME、DB_PASSWORD，然后在 backend 执行：

```powershell
.\mvnw.cmd -B spring-boot:run
```

结束后删除当前进程中的 DB_PASSWORD。生产profile为prod，DB_HOST/DB_NAME必须提供，JDBC使用 `sslMode=VERIFY_IDENTITY`，需部署时配置匹配服务端证书的Java信任库；本轮没有部署或生产TLS验收。dev使用PREFERRED，不允许自动抓取公钥。普通 test 不连接数据库，mysql-test专用于真实连接；没有H2或内存库替代MySQL证据。

MyBatis-Plus只完成基础工厂配置，无业务Mapper。开发日志只输出准备语句事件，SQL/参数/结果不输出；连接失败返回固定安全错误提示，不输出JDBC原始异常、URL或密码。数据库不可用时的启动检查应失败，不以应用端口可监听代替JDBC健康证明。

## 可选账号种子

用户已选择本轮暂不执行种子。`database/seed/04_initial_data.sql` 是具名绑定模板，只能由 `DatabaseTool.java seed` 通过PreparedStatement执行，不是直接SOURCE文件。运行器校验六表都无数据，在同一事务中生成两账号、一店、一分类、一商品，任何失败回滚。

| 环境变量 | 必须提供的资料 |
| --- | --- |
| CAKE_SEED_ADMIN_PHONE / ADMIN_HASH / ADMIN_NICKNAME | 完整变量名均以CAKE_SEED_开头；ADMIN手机号、BCrypt散列、昵称 |
| CAKE_SEED_MERCHANT_PHONE / MERCHANT_HASH / MERCHANT_NICKNAME | 商家账号手机号、BCrypt散列、昵称 |
| CAKE_SEED_SHOP_NAME / SHOP_PHONE / SHOP_ADDRESS | 店名、店铺联系电话、地址 |
| CAKE_SEED_CATEGORY_NAME | 一项必要分类 |
| CAKE_SEED_PRODUCT_NAME / PRODUCT_SPEC / PRODUCT_PRICE / PRODUCT_STOCK | 一项商品名称、规格、正价格（至多两位小数）、非负INT库存 |

上表每个斜杠后的名称也需完整前缀，例如 `CAKE_SEED_ADMIN_HASH`、`CAKE_SEED_PRODUCT_STOCK`。未设值时本机提示输入，BCrypt散列也隐藏输入；要求$2a/$2b/$2y格式及工作因子至少10。无需提供明文应用密码；本阶段不实现登录或自行生成固定弱密码。没有默认真实手机号，不编造店铺或小组资料。商品按原字典默认下架，种子不创建订单。

```powershell
$env:DB_NAME = 'cake_order_system'
& "$env:JAVA_HOME\bin\java.exe" -cp $taskClassPath '.\database\tools\DatabaseTool.java' seed
```

执行前须用户准备上述资料并确认执行；当前未执行项继续留在TODO。缺资料不会填占位手机号或弱散列。

