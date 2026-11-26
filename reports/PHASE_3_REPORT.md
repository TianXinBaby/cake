# Phase 3 用户认证与权限系统阶段报告

执行日期：2026-11-25，时区 Asia/Shanghai。项目路径：`D:\study\蛋糕店订单管理系统`。

**阶段结论：PARTIAL，待审核，不标记整体 PASS。** 六个认证及本人接口、后端权限基础和前端认证基础已实现；后端完整 255 项、前端 54 项测试全部通过，后端和前端构建成功。`data.fieldErrors` 公共结构仍未确认，继续保持原参数错误 data=null，公共字段级错误定位不能记为完成。密码补充字符长度口径另作为待明确边界记录。

本轮用户明确授权 Phase 3，优先于 AGENTS 和历史状态中的旧阶段限制。完成后停止，不进入 Phase 4，不初始化 Git，不创建真实账号种子。

## 1 范围与需求证据

唯一需求基线为根目录 V1.1 DOCX，实际修订日期 2026-11-24。完整读取正文及表格共 463 个正文块、462 个非空来源块，并与 Phase 0 带来源编号的基线核对；只有 B041 环境行存在已知历史差异，当前原件已为 JDK18.0.2.1/Node24.20.0，旧转录为 JDK17/Node18。其他正文/表格一致，原件未改写。

本轮核对 AGENTS、README、PROJECT_STATUS、TODO、需求基线/追踪矩阵、架构/阶段计划、四份数据库文档、Phase0～2报告，以及后端POM、Wrapper、配置、公共响应/异常、数据源、前端依赖锁/Axios/Pinia/Router及全部原测试。此前没有业务Entity/Mapper，因此只新增认证需要的内部账号映射与Mapper，没有重复工程。

实现追踪如下；UI编号是后续接入位置，不代表本轮创建了页面。

| 功能 | 接口 | 当前实现与验证 | 页面/验收边界 |
| --- | --- | --- | --- |
| USER-01 | API-10 POST /api/auth/register | CUSTOMER注册、输入/保护字段、唯一冲突、BCrypt、201且无JWT | UI-08未开发，覆盖TC-01/02服务端相关规则 |
| USER-02 | API-01 POST /api/auth/login | 三角色登录、锁定、2小时JWT、当前账号校验 | UI-01未开发，覆盖TC-03/04认证规则 |
| USER-02 | API-11 POST /api/auth/logout | 原子版本递增、全部旧JWT失效、前端清理 | 无实际退出入口页面 |
| USER-03 | API-12 GET /api/users/me | 仅本人公开User、当前角色/状态/版本/绑定 | UI-07未开发，TC-05相关API规则 |
| USER-03 | API-13 PATCH /api/users/me | 只改本人昵称、只读字段拒绝 | 其他用户和安全字段不变 |
| USER-04 | API-14 PATCH /api/users/me/password | 旧密码、新旧不同、BCrypt与版本同事务 | TC-06相关API规则；无浏览器页面验收 |

没有平台商家/用户管理、分类商品、库存、订单、支付、统计、短信、第三方登录、找回密码、刷新令牌或额外认证接口。仅使用现有六张表，认证只访问sys_user与cake_merchant。

## 2 环境与依赖变更

| 项目 | 实际值 |
| --- | --- |
| JDK | 18.0.2.1，直接使用现有 D:\study\jdk18；编译release18 |
| Spring Boot | 3.5.16，沿用Phase1 |
| Maven / Wrapper | 3.9.11 / 官方3.3.4，Wrapper文件未改 |
| Spring Security | 6.5.11，Boot BOM管理 |
| JWT | spring-security-oauth2-jose6.5.11、Nimbus JOSE JWT9.37.4，Boot解析版本 |
| MyBatis-Plus | 3.5.18，沿用Phase2 |
| MySQL / JDBC | 服务端8.0.19 / Connector/J8.4.0 |
| Node / npm | 24.20.0 / 11.19.0 |
| Vue / Pinia / Axios | 3.5.46 / 3.0.4 / 1.21.1，原package.json与锁文件不变 |

POM只新增 `spring-boot-starter-security` 和 `spring-security-oauth2-jose`，使用Phase0已有标准JWT候选方案，无独立第三方版本漂移。解析依赖写入target/phase3-dependency-tree.txt。没有升级/降级既有技术版本，没有前端依赖安装、软件安装、系统环境或ExecutionPolicy变更。

技术选型参考[Spring Security JWT官方文档](https://docs.spring.io/spring-security/reference/6.5/servlet/oauth2/resource-server/jwt.html)及[密码存储官方文档](https://docs.spring.io/spring-security/reference/6.5/features/authentication/password-storage.html)。本项目的具体两小时、锁定及版本规则仍以V1.1为准。

## 3 后端实现

注册使用逐接口字段白名单；phone匹配大陆11位手机号，昵称去除Unicode首尾空白后按code point验证1～30，密码拒绝空白、控制和不可见格式字符。四个注册保护字段role/status/merchantId/tokenVersion返回40302，其他未知字段40001；本人昵称接口的账号、手机号、角色、状态、用户ID和绑定等只读字段拒绝。JSON和Query同时含保护字段与普通未知字段时，保护字段优先，避免依赖字段顺序。

注册固定username=phone、CUSTOMER、启用、版本0、失败0、NULL锁定；create_time/update_time采用数据库默认时间。BCryptPasswordEncoder(10)生成随机盐散列。响应User白名单不含散列或内部安全字段，注册不自动登录、不返回JWT。

登录在事务内按username执行SELECT FOR UPDATE。同一账号失败计数串行更新，1～4次返回40101，第5次写入计数5和数据库当前时间+10分钟的locked_until，返回42301及统一格式lockedUntil。锁定期间不再增加次数，期满后的下一次尝试从新周期开始，成功清零并清锁。失败结果先正常退出TransactionTemplate提交，再抛业务错误，防止失败计数被异常回滚。不存在账号执行虚拟BCrypt比较，与错误密码保持相同HTTP/业务码/提示。

JWT固定HS256，包含sub/role/ver/iat/exp，有效期恰好7200秒，当前时间达到exp立即拒绝，无额外宽限。expiresAt与令牌截止时刻一致，序列化为毫秒和+08:00。JWT_SECRET_BASE64从外部环境读取，解码至少32随机字节；缺失、格式错误或过短时安全失败启动，没有源码默认密钥。本轮测试密钥运行时随机生成且不保存。

JWT过滤器校验单个Bearer头、格式、签名、算法、时效和claim类型/范围，然后每次读取数据库当前用户、status、token_version、role及店铺绑定。ROLE_ADMIN、ROLE_MERCHANT、ROLE_CUSTOMER映射明确，SecurityContext包含userId/role/tokenVersion及商家merchantId；数据库当前角色为最终依据，签名JWT中的旧角色不能提权。MERCHANT无绑定或非MERCHANT存在绑定时记录安全完整性事件，返回50001/requestId，不签发正常商家令牌。店铺停用与账号禁用分别处理。

安全链为STATELESS，禁用表单登录、HTTP Basic、默认退出和请求缓存；没有自动默认账号。建立平台、商家、顾客路径及方法权限基础，未实现这些未来业务接口。方法权限拒绝显式转为40302，防止被原兜底异常转为500。原统一异常Java未改，新增认证处理复用code/msg/data；RequestTraceFilter提前于安全链执行，过滤器错误使用同一请求号，日志不含请求体、密码、完整JWT、SQL或vendor异常。

三角色均可读取和修改本人昵称；接口不采信其他账号ID，GET资料和退出也拒绝多余输入。退出在事务内再次锁定并复核版本，再原子递增token_version。修改密码锁内验证旧密码和新旧不同，用同一SQL、同一事务更新新BCrypt、版本和数据库update_time。二者成功均data=null、无新JWT；全部旧令牌在下一次保护请求失效。并发旧版本写入不能覆盖新版本。

完整职责和契约见四份[安全文档](../docs/security/AUTHENTICATION_DESIGN.md)。密码长度当前采用Java UTF-16代码单元6～20；补充平面/组合字符的显示长度口径未由需求进一步定义，详见PASSWORD_SECURITY，不冒称全部Unicode显示字符规则已获确认。

## 4 前端认证基础

保留唯一Axios实例和原统一响应处理，集中注入Bearer；匿名登录/注册不携带旧令牌。40102在业务错误和HTTP错误分支均清理对应会话，退出收到HTTP401也清理；原密码错误40101和网络失败保留会话，仍采用原消息机制。发出请求时保存会话快照，旧响应不会覆盖或清理后来登录的新会话。

Pinia提供token/tokenType/expiresAt/user/isAuthenticated，公开用户字段白名单排除内部安全字段；六API为login、register、logout、getCurrentUser、updateNickname、updatePassword。读取本人信息刷新当前用户，退出/改密成功清理状态。没有前端修改权限的独立方法，界面角色不代替后端校验。

由于未确认浏览器持久化方案，本轮采用仅内存Pinia，不引入存储框架或localStorage/sessionStorage。刷新页面须重新登录。当前没有登录入口页面，因此只完成状态清理，没有伪造跳转目的地或创建页面；所有原应用、Router、样式和UI文件保留。

## 5 数据库保护与初始化账号

开始时通过已有本机凭据实际连接MySQL8.0.19，会话+08:00，开发库cake_order_system及既有测试库cake_order_system_phase2_test六表均0行。未运行init/seed/DDL，表、74字段、索引、FK、CHECK、字段类型和角色集合未改。

认证集成测试检查实际catalog等于DB_TEST_NAME，名称以_test结尾且与开发库不同；使用运行期合成账号、真实BCrypt和提交事务，按准确生成ID清理自建店铺/账号，并逐例恢复六表计数。原数据库套件事务回滚。最终两库六表仍0行，数据SHA256均为 `e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855`。

规范化SHOW CREATE TABLE后，分别比较本轮起止摘要，结果均相同：

| 数据库 | 起止结构SHA256 |
| --- | --- |
| cake_order_system | ee3bebe42cae1a8e7381932029b6f0e2e8cd272d644c4ff25f4d2cb290db5f87 |
| cake_order_system_phase2_test | 15cda01419f260c563f5f78386f371eed939283ce5ec01a2a5bacf5599fbbc75 |

摘要忽略AUTO_INCREMENT当前序列数值，只核对DDL定义；不同库之间不冒称这些摘要彼此相同。测试分配/失败主键更新可能改变InnoDB序列元信息，回滚并不等于序列恢复。本轮没有重置序列，没有开发库测试写入或历史数据改写。

既有参数化ADMIN/MERCHANT初始化器和SQL保持原样，账号所需外部安全参数未齐全，因此未执行种子、未创建未知凭据的真实账号。测试账号不能作为正式账号交付。数据库密码和JWT密钥未写入源码、配置、SQL、报告或本轮运行日志。

## 6 失败修复与测试结果

真实全量测试首轮254项，2失败/28错误/0跳过。两失败为方法权限拒绝错误地返回500，已在新增认证异常处理中修复。28错误来自旧数据库夹具用signed long读取已超过signed范围的类别ID。后续255项轮次方法权限已通过，旧28项仍暴露Connector/J把BigInteger默认绑定为signed BIGINT的问题。

修复仅涉及旧MySqlDatabaseTests的ID类型和绑定：持久化ID改为BigInteger，生成主键不再longValue截断；插入显式setBigDecimal，测试专属JdbcTemplate将BigInteger查询/更新参数转换为等值BigDecimal。原28个测试名称、SQL、断言目的和事务回滚全部保留，没有删除或跳过。数据库结构未变，也未用ALTER AUTO_INCREMENT掩盖问题。生产MyBatis BigIntegerTypeHandler本身使用DECIMAL绑定。

最终 `mvnw.cmd -B package` 开启真实MySQL测试，**255项全过，失败0、错误0、跳过0，BUILD SUCCESS，22.909秒**：

| Surefire套件 | 测试 | 失败 | 错误 | 跳过 |
| --- | ---: | ---: | ---: | ---: |
| CakeOrderPlatformApplicationTests | 1 | 0 | 0 | 0 |
| ApiResponseTests | 6 | 0 | 0 | 0 |
| GlobalExceptionHandlerTests | 17 | 0 | 0 | 0 |
| ValidationErrorExtractorTests | 1 | 0 | 0 | 0 |
| DatabaseInfrastructureTests | 4 | 0 | 0 | 0 |
| MySqlDatabaseTests | 28 | 0 | 0 | 0 |
| AuthHttpMySqlTests | 101 | 0 | 0 | 0 |
| AuthRequestsTests | 55 | 0 | 0 | 0 |
| JwtAuthenticationFilterTests | 17 | 0 | 0 | 0 |
| JwtServiceTests | 25 | 0 | 0 | 0 |
| 合计 | **255** | **0** | **0** | **0** |

原57项已包含Phase1的25项，新增198项，不将25+57重复相加，也不把多轮执行累加为不同测试。101项真实认证测试覆盖注册参数/敏感字段/并发重复、锁定五次/到期/成功复位、同号8线程与异号独立、伪造/过期/版本/删除/禁用令牌、三角色/上下文/绑定、本人/昵称、退出、并发改密与全部旧令牌失效。回滚测试执行真实密码SQL后注入异常，验证密码、版本、update_time一起回滚且旧令牌仍可用。

最终复核将退出和三角色改密的第二个旧JWT改为提前1秒签发，明确断言两个令牌不同且失效前均有效，避免同一秒相同claim生成相同令牌导致验证不足；随后重新执行完整255项通过，未增加测试数量。

JWT单测覆盖固定算法、不同密钥、篡改、缺claim、精确两小时、到期前1纳秒/到期点、未来iat和缺失/过短密钥；无unsigned最大ID写入数据库，使用BigInteger令牌及上下文验证其表达。

前端 `npm.cmd test`：4文件、**54项全过，失败0、跳过0**，包含原28+新增26；覆盖状态、公开字段、六API、Bearer、40102/退出401、原密码错误、网络错误、旧响应竞争和无敏感日志。`npm.cmd run build`成功，保留既有大于500kB chunk提示，JS约1,041.65kB；未擅改打包策略。

## 7 实际命令、构建及运行

| 实际执行 | 结果 |
| --- | --- |
| 直接JDK路径java -version、node --version、npm.cmd --version | 18.0.2.1、24.20.0、11.19.0 |
| git status --short --branch | 非Git仓库；没有git init等写操作 |
| mvnw.cmd -B -DskipTests compile | 编译成功，不把skipTests当测试通过 |
| mvnw.cmd -B test（初步未开真实开关） | 基础通过、真实套件明确skip，不计真实MySQL证据 |
| mvnw.cmd -B dependency:tree -DoutputFile=target/phase3-dependency-tree.txt | 成功，已核对精确版本 |
| mvnw.cmd -B package（真实开关开启） | 首两轮失败原因与修复见上一节；最终255/255成功 |
| npm.cmd test / npm.cmd run build | 54/54及生产构建成功，原大包提示保留 |
| Java/JDBC只读两库起止审计 | 8.0.19、会话+08:00、结构/数据起止一致、最终0行 |
| java -jar打包应用，默认dev，临时端口62382 | PID11068启动并保持运行，测试后停止 |
| 实际HTTP GET /api/users/me（匿名） | 401/40102，无Session Cookie或Basic挑战 |
| 实际HTTP POST /api/auth/login（不存在账号） | 401/40101，开发库未写测试账号 |
| 实际HTTP登录传role | 403/40302 |
| 不提供JWT_SECRET_BASE64启动同一JAR | 退出1，安全配置错误提示，无数据库密码 |
| JAR内容与凭据扫描 | 0个测试/Fixture/测试yml/独立工具入包；源码与运行日志未发现本轮数据库密码 |

JAR为 `backend/target/cake-order-platform-0.0.1-SNAPSHOT.jar`，33,811,868字节。执行时仅当前进程使用用户提供的数据库凭据和随机测试JWT密钥，未保存真实密钥；启动验证进程均已停止，用户MySQL服务未停止。

Surefire XML、最终/前两轮日志、依赖树、数据库起止摘要、运行检查和文件清单位于backend/target/phase3-*及surefire-reports，属于可重建验证生成物。没有把本机测试记为部署、真实浏览器、Knife4j手动验收、生产TLS或完整46场景通过。

## 8 新增及修改文件

新增29个手写源文件/文档：

```text
backend/src/main/java/com/cake/platform/config/AuthenticationConfiguration.java
backend/src/main/java/com/cake/platform/controller/auth/AuthController.java
backend/src/main/java/com/cake/platform/controller/auth/AuthExceptionHandler.java
backend/src/main/java/com/cake/platform/controller/user/UserController.java
backend/src/main/java/com/cake/platform/dto/AuthRequests.java
backend/src/main/java/com/cake/platform/entity/UserAccount.java
backend/src/main/java/com/cake/platform/mapper/UserAuthMapper.java
backend/src/main/java/com/cake/platform/security/AuthErrorCode.java
backend/src/main/java/com/cake/platform/security/AuthErrorResponses.java
backend/src/main/java/com/cake/platform/security/AuthFailure.java
backend/src/main/java/com/cake/platform/security/AuthPrincipal.java
backend/src/main/java/com/cake/platform/security/JwtAuthenticationFilter.java
backend/src/main/java/com/cake/platform/security/JwtService.java
backend/src/main/java/com/cake/platform/service/auth/AuthenticationService.java
backend/src/main/java/com/cake/platform/vo/UserView.java
backend/src/test/java/com/cake/platform/security/AuthHttpMySqlTests.java
backend/src/test/java/com/cake/platform/security/AuthRequestsTests.java
backend/src/test/java/com/cake/platform/security/JwtAuthenticationFilterTests.java
backend/src/test/java/com/cake/platform/security/JwtServiceTests.java
frontend/src/api/auth.js
frontend/src/api/users.js
frontend/src/api/authentication.test.js
frontend/src/stores/auth.js
frontend/src/stores/auth.test.js
docs/security/AUTHENTICATION_DESIGN.md
docs/security/JWT_AND_PERMISSION.md
docs/security/PASSWORD_SECURITY.md
docs/security/AUTH_API_TESTS.md
reports/PHASE_3_REPORT.md
```

修改8个既有文件：README.md、PROJECT_STATUS.md、TODO.md、backend/pom.xml、backend/src/main/resources/application.yml、backend/src/test/resources/application-test.yml、backend/src/test/java/com/cake/platform/database/MySqlDatabaseTests.java、frontend/src/api/http.js。test配置排除安全自动配置仅用于保留无数据库的原基础回归，生产/dev/mysql-test认证实际启用，真实测试没有绕过安全链。

原62个文件中54个保持本轮起止SHA256相同，无删除；新增后共91个手写文件（排除node_modules/target/dist）。保护对象包括原DOCX、AGENTS、Phase0需求/追踪/架构/计划、Phase0～2报告、全部数据库SQL/工具/文档、公共响应/异常Java、原25项测试、前端package/lock及应用/Router/UI、Wrapper。仅旧28项数据库套件按必要原因修改ID表达，未删除测试。

需求原件SHA256仍为 `522D3CB741550FD88D8B73365A245F2AEDF98AA6CF6B1AF2A5A7A34DF56025E5`。前端package.json仍为 `ECDCF96036C84B98E8B66B0A8115C6469DC6F1E894F312B91A409D0EA5FB487A`，package-lock仍为 `43105B6A613314A1A1A9C929A94AEDB3F98D5EEA3B34D480492D5D1367F16DBA`。文件保护以本轮开始快照为准，没有用Git干净状态替代保护证据。

## 9 待确认、风险和验收结论

| 项目 | 结论与剩余边界 |
| --- | --- |
| 注册/登录/退出/本人/昵称/改密 | 本阶段API自动化验证通过 |
| BCrypt/JWT/锁定/版本/角色/禁用 | 自动化及真实MySQL/HTTP相关检查通过 |
| 原测试保护 | 原57项继续通过，28项旧夹具ID修复已说明 |
| 数据库与源文件保护 | 无DDL/种子/额外表字段，准确清理及起止审计通过 |
| 前端基础/测试/构建 | 通过；仅内存状态，无实际页面或刷新恢复持久化 |
| fieldErrors | **REQ-Q10未确认，公共字段定位未完成，整体必须PARTIAL** |
| Unicode密码长度 | 当前UTF-16计数；补充平面/组合字符口径待明确，不新增ASCII业务限制 |
| 初始化账号 | 参数未齐全，既有安全种子未执行，不编造真实账号 |
| 生产/页面/后续业务 | 未执行，不属于本轮交付或授权 |
| 阶段整体 | **PARTIAL，待审核；停止于Phase3** |

历史商家账号禁用来源、支付错误优先级、回补上限、热销店名快照、编号映射及修订附件疑点继续留在TODO，不通过本轮认证设计代替用户决定。生产密钥托管/证书TLS和真实页面联调还需后续独立验证。

README、PROJECT_STATUS、TODO和四份安全文档已同步本轮结果。等待用户审核及明确后续指令，**不进入Phase4，不执行种子或Git提交/推送**。
