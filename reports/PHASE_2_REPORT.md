# Phase 2 数据库设计与初始化报告

日期：2026-11-25（Asia/Shanghai）。工作区：`D:\study\蛋糕店订单管理系统`。

**结论：数据库结构、真实 MySQL 完整性测试、后端集成和打包检查通过；Phase 2 整体为 PARTIAL，待用户审核，不标记整体 PASS。** 用户明确选择“先完成建表和实测，保留参数化种子待执行”，因此本轮没有初始化应用账号、店铺或演示商品。完成后停止，Phase 3 未授权、未开始。

## 1 授权、依据与开始检查

本轮依据用户粘贴的完整 Phase 2 要求执行，唯一业务来源仍为根目录 V1.1 DOCX，实际修订日期2026-11-24。用户明确说明Phase0已完成、Phase1有条件通过，本轮授权数据库设计与初始化；该授权覆盖AGENTS、历史计划中的“仅Phase0/未授权Phase2”等旧状态。

开始前已检查AGENTS、README、PROJECT_STATUS、TODO、需求基线、追踪矩阵、架构、阶段计划、Phase0/1报告，以及现有pom、application.yml、application-test.yml、Wrapper、后端包及测试、前端package和锁文件。沿用现有工程，没有重新生成。旧计划允许六Entity/Mapper，而本轮明确禁止，按最新要求不创建；fieldErrors继续待确认。

只读ZIP/OOXML解析当前DOCX，正文body共463块，其中首块为空；对应462非空来源块，完整逐段、逐表单元格对照Phase0转录，仅B041运行环境行存在已知历史差异：当前JDK18.0.2.1/Node24.20.0，旧转录JDK17/Node18。第四章全部字段和规则相同。未修改或重新排版原件，未重新渲染页码，以4.3～4.5及表名作为可靠定位。

`git status --short` 开始及结束均返回 `fatal: not a git repository`。没有执行init、commit、push、分支、reset、rebase或清理；不能把非仓库说成工作树干净。

## 2 环境、命名与安全配置

| 项目 | 本轮实际结果 |
| --- | --- |
| JDK | `D:\study\jdk18\bin\java.exe -version` 为18.0.2.1；编译release18 |
| Spring Boot | 沿用3.5.16，未升级 |
| Maven / Wrapper | 沿用3.9.11 / 官方3.3.4；Wrapper脚本和发行校验配置原样 |
| Node / npm | `node --version` v24.20.0；`npm.cmd --version` 11.19.0 |
| MySQL | CLI二进制8.0.19；**实际JDBC/SQL服务端VERSION()为8.0.19**，Community Server GPL |
| MyBatis-Plus | 精确固定Boot3 starter3.5.18，沿用架构候选并实际构建 |
| Connector/J | 精确固定8.4.0，实际连接8.0.19成功 |
| 数据库名 | 未发现此前已确认名称，按本轮要求采用cake_order_system；独立测试库cake_order_system_phase2_test |
| collation | 用户本轮明确选择utf8mb4_0900_as_cs，区分大小写与重音，原待确认项关闭 |
| 表与时间 | InnoDB、utf8mb4、DATETIME(3)，JDBC会话强制+08:00 |

初次只读探测两个目标库均不存在，随后仅创建这两个新库。服务器原全局 `time_zone=SYSTEM`、`collation_server=utf8mb4_0900_ai_ci` 在结束时保持原值；没有为了新项目修改全局变量或其他库。

用户授权的本地连接凭据只用于交互/本轮进程环境，不写入项目、SQL、README、报告、JDBC URL或Java源码。临时受Windows用户DPAPI保护的凭据缓存位于系统TEMP，结束时清理；没有保存明文配置或设置持久环境变量。源码及target文本日志/测试XML的实际密码字面量扫描未发现命中；不在报告重复该字面量。

依赖兼容核对采用官方资料：[MP Boot3安装坐标](https://baomidou.com/getting-started/install/)、[3.5.18 Boot3 starter编译配置](https://github.com/baomidou/mybatis-plus/blob/v3.5.18/spring-boot-starter/mybatis-plus-spring-boot3-starter/build.gradle)、[Connector/J8.4.0支持MySQL8.0及以上](https://dev.mysql.com/doc/relnotes/connector-j/en/news-8-4-0.html)、[JDBC连接时区设置](https://dev.mysql.com/doc/connector-j/en/connector-j-connp-props-datetime-types-processing.html)。这些静态依据结合本轮实际依赖解析、连接和构建，不代替真实证据。

最终依赖树确认MyBatis3.5.19、mybatis-spring3.0.6、HikariCP6.3.3，无重复Boot2 starter。排除MP starter传递的spring-cloud-commons及三个未使用的mybatis-thymeleaf/velocity/freemarker，最终树及JAR均未包含它们。没有Redis、消息队列或微服务组件。

## 3 真实初始化与结构结果

使用独立 `database/tools/DatabaseTool.java` 以Connector/J连接，先检查MySQL精确版本、会话时区、目标库为空及字符设置，再按01建表/PK/UK → 02普通索引 → 03外键/CHECK执行。开发库、专用测试库各完整执行一遍，所有SQL成功。

| 表 | 原文字段数 | 两个库实际字段数 | 字典匹配 |
| --- | ---: | ---: | --- |
| sys_user | 12 | 各12 | 名称、顺序、类型/长度、UNSIGNED、NULL、默认、自增一致 |
| cake_merchant | 9 | 各9 | 一致 |
| cake_category | 7 | 各7 | 一致 |
| cake_product | 12 | 各12 | 一致 |
| cake_order | 25 | 各25 | 一致 |
| cake_order_item | 9 | 各9 | 一致 |
| 合计 | **74** | **各74** | **6表，无额外字段** |

独立预期文件 `backend/src/test/resources/database/expected-columns.tsv` 从原DOCX字典生成，另经原文字典逐项复核，不是从DDL反向生成的自证。真实测试查询information_schema逐项匹配；结束时再比较开发库与测试库的列/默认/顺序、索引、键和CHECK表达式，完全一致。因此实测测试库的结构与实际交付开发库相同。

| 结构 | 每库实际数量 | 说明 |
| --- | ---: | --- |
| 主键 | 6 | 各表id，自增BIGINT UNSIGNED |
| 唯一键 | 9 | 包括账号/手机号、一账号一店、分类店内名称、复合引用、订单号/请求幂等、明细同商品唯一 |
| 需求普通索引 | 14 | 全部列顺序与4.4一致 |
| 必要FK支持索引 | 1 | InnoDB为商品(category_id,merchant_id)自动生成，不是新增业务查询索引 |
| 物理索引总数 | 30 | 6PK+9UK+14普通+1FK支持 |
| 外键 | 8 | 全部ON DELETE RESTRICT、ON UPDATE RESTRICT，无CASCADE |
| CHECK | 29 | 全部实际存在且ENFORCED=YES |

商品复合外键保证分类同店。明细与订单同店、账号角色归属、明细数量与主明细金额等不增加字段强行实现，留给后续Service事务。update_time保留CURRENT_TIMESTAMP(3)初值，无ON UPDATE；expire_time无默认，CHECK要求恰为create_time加30分钟。

重复执行开发库init的保护检查实际返回退出1，在DDL前拒绝已有表，随后结构对照无差异。本目录脚本明确只用于首次初始化，不宣称幂等升级。没有DROP DATABASE、清空或删除用户已有数据。DDL隐式提交，未来中途失败必须人工核查，不宣称能整批回滚。

## 4 真实完整性测试

真实套件 `MySqlDatabaseTests` 使用Spring Boot数据源、MyBatis-Plus工厂及JdbcTemplate连接专用MySQL8.0.19库，要求显式开关 `CAKE_MYSQL_TEST_ENABLED=true`；DB_TEST_NAME必须以_test结尾且与开发库不同。每例在事务中准备最小夹具，结束回滚，再以新事务外查询核对六表行数与开始一致。

| 用户要求的17类测试 | 实际结果 |
| --- | --- |
| 重复username | 拒绝，MySQL1062 |
| 重复phone | 拒绝，1062 |
| 商家重复绑定账号 | 拒绝，1062 |
| 同商家重复分类名 | 拒绝，1062 |
| 不同商家同分类名 | 允许，两条归属不同 |
| 商品引用他店分类 | 拒绝，复合FK1452 |
| 不存在商家引用 | 拒绝，1452 |
| 不存在用户引用 | 拒绝，1452 |
| 商品单价0或负数 | 拒绝，CHECK3819 |
| 商品负库存 | 拒绝，3819 |
| 非法角色 | 拒绝，3819 |
| 非法订单状态 | 拒绝，3819，状态枚举与事件矩阵共同覆盖 |
| 非法订单类型 | 拒绝，3819，类型枚举与地址分支共同覆盖 |
| 订单内重复商品明细 | 拒绝，1062 |
| 删除被引用记录 | 拒绝，1451；另验证更新被引用主键亦拒绝 |
| DECIMAL(10,2)精度 | 99999999.99精确往返；100000000.00拒绝1264；1.239实际变1.24，应用层需预拒绝 |
| DATETIME(3)精度 | .123毫秒经JDBC保存/读取一致 |

其他真实验证包含：

- 74列全量元数据、全部索引/外键列顺序及RESTRICT、29CHECK定义与ENFORCED。
- 26个CHECK按违规返回的约束名实际确认；另3类重叠违规（非法status、非法order_type、price/subtotal同为0）实际返回3819。**不宣称29项均在孤立条件下单独触发。**
- 两履约分支的全部八状态合法事件矩阵；已付款/接单后取消保留时间；履约可以晚于付款截止；时间逆序、地址分支和截止错误拒绝。
- 账号未指定role/status/token_version/login_fail_count时实际采用CUSTOMER/1/0/0，locked_until为NULL，时间由数据库默认生成。
- as_cs实证同店Cake/cake/cafe/café可分别保存，精确重复仍拒绝。
- 大unsigned值以JDBC BigInteger参数CAST往返验证；**没有Entity/Mapper持久化映射或高ID插入验证**，避免改变测试库自增上限。

真实测试夹具是事务内合成数据，密码栏为BCrypt格式测试占位值，不是可用账号种子或密码验证证据。测试结束实际查询两库，六表均0行。InnoDB自增分配不随回滚恢复，专用测试库可能存在ID空号，这是结构序列元信息，不是测试行残留；开发库没有写入测试行。

## 5 后端集成与连接故障

仅新增基础配置与两个非业务Java类。`DatabaseConnectionConfiguration` 建立Hikari池及包装数据源，启动时实际取连接并校验，连接失败用固定安全提示替换vendor错误且不保留敏感cause；未修改公共异常处理。开发SQL日志 `SqlEventLog` 只记录PREPARE/WARN/ERROR固定事件，省略SQL正文、参数、结果和异常内容，生产与测试不记录SQL；Hikari/Connector日志关闭。

普通test排除数据源/事务/MP自动配置保持无数据库依赖；mysql-test单独使用隔离库。dev默认本机数据源，prod使用VERIFY_IDENTITY并要求部署证书信任配置，生产TLS尚未实测。所有profile均不自动执行DDL或种子。

实际启动生产JAR，默认dev配置连接开发库，Tomcat端口57697，日志显示2.585秒启动；启动连接校验后进程继续存活，GET `/api/products` 为404，GET测试专用 `/__database_fixture/unreachable` 亦404，证明未把测试夹具/业务API带进正式应用。测试后停止本轮PID32684，未停止MySQL服务。

另使用已确认关闭的本机临时端口启动同一JAR，模拟连接不可达，不关闭用户MySQL。进程退出1，日志含“数据库连接失败，请检查本机数据库服务、网络和环境变量配置。”，不含真实密码/JDBC URL。HTTP层故障测试也经真实驱动连接关闭端口，原有异常处理返回HTTP500/50001及requestId，日志脱敏断言通过。没有添加生产健康或数据库探测接口。

## 6 测试与构建命令结果

所有Maven命令在backend目录、当前进程JAVA_HOME指向现有JDK18执行；真实测试凭据通过进程环境注入，未放-D参数。

| 实际命令/动作 | 结果 |
| --- | --- |
| `node --version`、`npm.cmd --version`、直接java/mysql版本 | 符合指定版本 |
| `mvnw.cmd -B dependency:build-classpath '-Dmdep.outputFile=target/database-classpath.txt'` | 成功；初次未引号含点-D参数被PowerShell拆分，改完整引号后成功 |
| `java -cp <项目解析类路径> database/tools/DatabaseTool.java check` | 实际连接8.0.19、会话+08:00 |
| 同运行器init，分别指定两个新库 | 各6表74字段，01/02/03全部成功 |
| `mvnw.cmd -B clean test`（初轮未开真实开关） | 29项基础测试通过；当时26项真实测试明确skip，未当成实测证据 |
| `mvnw.cmd -B test`（首轮开真实开关） | 当时55项、1断言失败：非法类型先报告地址CHECK；修正对约束先后顺序假设，DDL未改 |
| `mvnw.cmd -B package`（最终真实开关开启） | **57项全过，失败0/错误0/跳过0，BUILD SUCCESS，19.004秒** |
| `mvnw.cmd -B dependency:tree '-DoutputFile=target/phase2-dependency-tree.txt'` | 成功，最终精确依赖与排除项已核对 |
| JAR真实dev启动、闭端口启动 | 分别正常存活与固定安全错误退出1 |
| 重复init保护、JAR内容检查、两库结构对照 | 全部符合保护及范围 |

最终Surefire XML：

| 套件 | 测试 | 失败 | 错误 | 跳过 |
| --- | ---: | ---: | ---: | ---: |
| CakeOrderPlatformApplicationTests | 1 | 0 | 0 | 0 |
| ApiResponseTests | 6 | 0 | 0 | 0 |
| GlobalExceptionHandlerTests | 17 | 0 | 0 | 0 |
| ValidationErrorExtractorTests | 1 | 0 | 0 | 0 |
| DatabaseInfrastructureTests | 4 | 0 | 0 | 0 |
| MySqlDatabaseTests | 28 | 0 | 0 | 0 |
| 合计 | **57** | **0** | **0** | **0** |

原25项+新增4项基础+新增28项真实测试，不将多次运行累加为不同用例。JAR为 `backend/target/cake-order-platform-0.0.1-SNAPSHOT.jar`，28,843,897字节。最终ZIP检查无Tests/Fixture、测试yml、字典预期、独立DatabaseTool或被排除模块入包。

证据生成物位于target：surefire-reports、phase2-dependency-tree.txt、phase2-metadata.txt、phase2-structural-metadata.txt、phase2-startup.log/err、phase2-offline.log、JAR。它们不作为手写业务源码，clean可移除。MyBatis无Mapper扫描告警符合本轮禁止Mapper的范围，不增加空Mapper来压制告警。

前端原文件、锁文件及Wrapper均未修改，本轮未重复npm安装、前端测试/构建或浏览器视觉验收。未执行业务权限/归属/订单并发、45API、17页面、TC01～46或生产部署，原因是本阶段无相应业务实现；不把数据库结构测试当成这些验收通过。

## 7 种子与未完成事项

种子模板使用具名绑定参数，由独立运行器校验BCrypt格式/工作因子至少10、手机号、金额及库存后以PreparedStatement执行。仅在六表全空时允许，整事务提交/回滚；没有默认密码、手机号、店铺或小组人员资料。最小输出为ADMIN与MERCHANT两账号、一店、一分类、一商品，不创建订单。

用户已明确选择暂不执行。仍需准备CAKE_SEED_*参数中的手机号/昵称、安全BCrypt散列、商家店铺地址/电话、分类及商品资料，之后由用户决定执行。当前没有可用应用账号，这一项不标PASS，也不使用本地数据库登录密码替代应用账号密码。

## 8 新增及修改文件

原有43个源文件/文档中，仅以下6个被授权修改：

1. README.md
2. PROJECT_STATUS.md
3. TODO.md
4. backend/pom.xml
5. backend/src/main/resources/application.yml
6. backend/src/test/resources/application-test.yml

新增19个文件：

```text
database/README.md
database/schema/01_create_tables.sql
database/indexes/02_create_indexes.sql
database/constraints/03_data_constraints.sql
database/seed/04_initial_data.sql
database/tools/DatabaseTool.java
docs/database/DATABASE_DESIGN.md
docs/database/DATA_DICTIONARY.md
docs/database/INDEX_AND_CONSTRAINTS.md
docs/database/INITIALIZATION_GUIDE.md
backend/src/main/resources/application-dev.yml
backend/src/main/resources/application-prod.yml
backend/src/main/java/com/cake/platform/config/DatabaseConnectionConfiguration.java
backend/src/main/java/com/cake/platform/config/SqlEventLog.java
backend/src/test/resources/application-mysql-test.yml
backend/src/test/resources/database/expected-columns.tsv
backend/src/test/java/com/cake/platform/database/DatabaseInfrastructureTests.java
backend/src/test/java/com/cake/platform/database/MySqlDatabaseTests.java
reports/PHASE_2_REPORT.md
```

排除node_modules/target/dist后合计62文件。其余37个原文件SHA256起止一致，无删除。构建生成物及Maven缓存不计为新增业务文件。除上述源文件，本轮外部持久变化仅为两个明确用途的新数据库及其六表结构；测试行已清理，种子未执行。

## 9 原件与已有成果保护

| 保护对象 | 本轮起止一致的SHA256 |
| --- | --- |
| 蛋糕店订单管理系统需求说明书_V1.1.docx | 522D3CB741550FD88D8B73365A245F2AEDF98AA6CF6B1AF2A5A7A34DF56025E5 |
| AGENTS.md | 0A8BED2F69F2E0A58557F5FAEE7C96BEAF6B81C736581C0D01FED49A6AC90202 |
| REQUIREMENTS_BASELINE.md | 3E24B6A7EE41A9FB69EBFE7204A3AA54731DC8BC6DD29B2475BAC9DDA25D5C5D |
| TRACEABILITY_MATRIX.md | B466C660D080B1F613D35C9BF75C2C48AB40BD78179F37F41269D7C109B8B8B9 |
| ARCHITECTURE_PLAN.md | 42886C7B1019616BC669AB299BA02817D140AF188D80B847AD2D17F4D5792453 |
| PHASE_PLAN.md | 94281E081ED229392CFA29214432CC4C8F834844AAF998E11F4E2F3011753ED0 |
| PHASE_0_REPORT.md | 467601F100F45027DAA323F5EE79B71927280C601B01112D56E26D9D40FE7A93 |
| PHASE_1_REPORT.md | 2043FB4C81F726410E880C4DB1367EA490B72BB081FA18FDB267A921C265CA17 |

全部原Java源码、原有四套25项测试、前端13文件及Wrapper均在37个未变文件内；公共响应/异常处理没有覆盖。保护以本轮实际起始哈希为准，不冒用Phase0的旧DOCX哈希。

## 10 风险及验收结论

| 验收项目 | 本轮结论 |
| --- | --- |
| MySQL8.0.19真实连接 | 通过 |
| 恰好六表、74字段逐项字典 | 通过，两库一致 |
| 主键/唯一/普通索引 | 通过；FK物理支持索引单列解释 |
| 外键/RESTRICT删除更新保护 | 通过 |
| CHECK | 通过，29元数据+实际拒绝案例，重叠触发边界已说明 |
| DECIMAL/DATETIME精度 | 数据库存储边界通过；多余小数入参拒绝留应用层，未冒充TC-38 |
| 数据源与MP初始化 | 通过，真实连接和元数据 |
| 启动及不可达提示 | 通过，本地dev/闭端口验证 |
| 凭据与源文件保护 | 通过，本轮扫描及起止哈希 |
| 相关测试及后端构建 | 通过，57/57，JAR成功 |
| 数据库文档 | 四份设计文档、目录说明、状态与报告完成 |
| 正式账号种子 | **未执行，用户明确暂缓，资料待准备** |
| Phase2整体 | **PARTIAL，待审核；不标整体PASS** |
| Phase3 | **未授权，未开始，立即停止** |

REQ-Q10、商家账号禁用来源、付款错误优先级、库存回补上限、热销商家名称快照及原编号/附件疑点继续保留TODO；collation已由用户确认关闭。没有通过新增表、字段或业务规则绕过这些问题。生产证书/TLS、应用层权限与跨表事务、状态迁移与快照不可变尚须未来获授权阶段实现和验证。

本轮交付供用户审核；审核通过也不自动构成Phase3、种子执行或Git操作授权。
