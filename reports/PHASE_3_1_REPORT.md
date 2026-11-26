# Phase 3.1 统一参数错误响应契约实施报告

日期：2026-11-25（Asia/Shanghai）。项目：蛋糕店订单管理系统。

**结论：本阶段实施、完整回归、构建和本机JAR验证通过，状态为待审核。立即停止于Phase3.1，不进入Phase4。** 原Phase3的fieldErrors遗留已补齐，历史PARTIAL报告保持原样，不回写当时的验收结果。

## 1 授权范围和检查依据

唯一需求基线仍为根目录《蛋糕店订单管理系统需求说明书_V1.1.docx》。本轮只读核对原文JSON命名、统一响应、40001字段定位、未知参数和敏感字段分类、认证及系统错误规则；未修改原始DOCX。读取PHASE_3_1_PROPOSAL及Phase1、2、3报告，并检查实际ApiResponse、异常处理、Validation提取、认证手工校验、业务异常、Axios/API/Store及现有测试。

以本轮用户明确批准和实施指令覆盖AGENTS及历史文档的旧阶段限制。仅实施data.fieldErrors，不增加接口、业务功能或页面，不改变数据库、SQL、依赖、JWT机制、BCrypt配置、登录锁定及tokenVersion。环境实测沿用JDK18.0.2.1、Node24.20.0、npm11.19.0、官方Maven Wrapper、MySQL8.0.19，未安装软件或依赖，未改变系统环境、PATH或ExecutionPolicy。

起止`git status --short`均返回非Git仓库；未git init、commit、push、reset、rebase或分支操作。原有文件以本轮开始SHA256清单保护，不声称Git工作区干净。

## 2 最终公共响应结构

```json
{
  "code": 40001,
  "msg": "参数错误",
  "data": {
    "fieldErrors": {
      "phone": ["手机号格式不正确"],
      "nickname": ["昵称不能为空", "昵称长度不能超过30个字符"]
    }
  }
}
```

以上为结构示例，具体消息由实际触发的校验产生，不表示昵称的所有规则必然同时触发。顶层只有code/msg/data；参数错误保持HTTP400、40001和“参数错误”。fieldErrors为非空对象，实际API字段对应非空字符串数组，可以同时有多字段及同字段多消息，不承诺Bean Validation遍历顺序。去除同字段重复消息，保留不同约束的受控消息；不包含拒绝值、内部约束对象、堆栈或解析信息。

字段使用对外lowerCamelCase名称；如Java内部名称不同，通过Jackson元数据映射。嵌套DTO/列表使用公开路径，例如`items[0].displayName`，其中每段属性仍为公开lowerCamelCase。本轮没有增加订单DTO或业务接口，此路径使用测试夹具验证。

无法定位实际请求字段时继续HTTP400／40001、data=null：包括JSON语法错误、整个请求体缺失、非对象认证请求、只有ObjectError、空ConstraintViolation、无字段BusinessException以及只有未知字段的输入。不产生_global、globalErrors或虚构body字段。有可信字段和无位置错误同时存在时，只返回可信字段部分。

本轮用户的“实际API字段”强制约定优先于提案中普通未知名称安全回显的建议：普通未知字段仍拒绝，但不回显unexpected或将token_version转换为tokenVersion。只有未知字段时data=null；同时有已知字段错误时可以返回已知部分，不会接受未知输入。

## 3 后端实施

| 位置 | 最终行为 |
| --- | --- |
| ApiResponse | 原样保留三顶层字段和成功工厂，无顶层结构变更 |
| FieldValidationException | 固定INVALID_PARAMETER的异常子类，携带字段→消息数组；不扩展其他BusinessException的数据能力 |
| ParameterErrorData | 只承载fieldErrors，拷贝Map/List并保持深层不可变，过滤无效位置/空消息 |
| ValidationErrorExtractor | 通过Jackson公开元数据映射Bean字段及嵌套路径，按字段归集；ObjectError不猜字段；绑定错误和未知模板使用受控提示 |
| Bean和方法参数Validation | 收集全部可定位字段，包括方法校验中的Bean错误与Query错误，不只返回第一条 |
| ConstraintViolation | 读取结构化属性/参数节点，避免直接导出Java方法前缀；没有可信位置则null |
| JSON类型错误 | 只用结构化Jackson引用路径与请求DTO公开属性生成位置，不回传异常原文；未知属性不伪装成API字段 |
| 缺失Query和类型不匹配 | 使用公开参数名，保持40001，值不回显 |
| AuthRequests | 按注册、登录、昵称、改密请求归集错误，缺失/类型根因只停止该字段，其他字段继续验证；保留原规则和保护字段优先级 |
| 两个Controller | 复用已验证的操作输入，不再用连续抛首错的手工组合；URL、HTTP方法及权限检查顺序保持 |
| AuthenticationService | 仅将新旧密码相同的40001附加newPassword位置；其他事务/密码/版本逻辑不改 |
| AuthErrorResponses | 只更新过时的fieldErrors未确认注释，认证响应实现无变化 |

已审查的固定中文提示可以原样输出；标准约束按约束类型映射不同的安全提示，避免将同字段NotBlank/Size都变成同一句话后丢失独立错误。未审查自定义模板回退通用提示，不输出validatedValue等模板内容。绑定失败不回传默认类型转换详情。

登录password及改密oldPassword仍保留原1～100长度界限；注册/新密码仍按原6～20及字符规则校验，昵称仍按原首尾空白处理、代码点长度与控制字符规则。未借本轮改变Unicode口径或其他业务约束。

| 已有错误 | 保持的HTTP状态 | 保护结论 |
| --- | --- | --- |
| 40101 账号或密码错误 | 401 | 包含原密码不匹配，未变为字段参数错误 |
| 40102 认证失效 | 401 | 原认证失效结构和前端清理继续工作 |
| 40301 账号禁用 | 403 | 原结构不变 |
| 40302 权限不足 | 403 | role/status/merchantId及其他既有保护字段保持权限优先 |
| 42301 登录锁定 | 423 | 原lockedUntil、失败计数和锁定规则不变 |
| 50001 系统故障 | 500 | 原安全提示/requestId，不泄露Java、SQL、数据库或内部异常 |

成功、40901等其他已存在业务异常、服务器返回值校验失败的50001行为均由完整旧测试继续验证，未修改错误码定义或HTTP映射。

## 4 前端兼容

现有http.js的两条失败分支均将body.data原样保存到RequestError.data；调用方可读取error.data.fieldErrors，Map和消息数组不被转换、覆盖或丢弃。40001继续原业务错误提示并保留会话，40102继续清理认证状态，无fieldErrors时继续原处理。

没有真实兼容问题，因此Axios核心、auth/users API、Pinia、Vue页面、Router和样式全部保持原样。仅将旧测试的单字符串示例改为字符串数组，并新增5项兼容回归。没有创建表单、字段组件或未使用的映射工具；真实表单就地提示需在后续获授权页面阶段验收。

## 5 文件清单

新增4个手写文件：

- backend/src/main/java/com/cake/platform/exception/FieldValidationException.java
- backend/src/main/java/com/cake/platform/exception/ParameterErrorData.java
- backend/src/test/java/com/cake/platform/exception/ParameterContractTests.java
- reports/PHASE_3_1_REPORT.md

修改19个既有文件：

- README.md
- PROJECT_STATUS.md
- TODO.md
- backend/src/main/java/com/cake/platform/exception/GlobalExceptionHandler.java
- backend/src/main/java/com/cake/platform/exception/ValidationErrorExtractor.java
- backend/src/main/java/com/cake/platform/dto/AuthRequests.java
- backend/src/main/java/com/cake/platform/controller/auth/AuthController.java
- backend/src/main/java/com/cake/platform/controller/user/UserController.java
- backend/src/main/java/com/cake/platform/service/auth/AuthenticationService.java
- backend/src/main/java/com/cake/platform/security/AuthErrorResponses.java（仅注释）
- backend/src/test/java/com/cake/platform/exception/GlobalExceptionHandlerTests.java
- backend/src/test/java/com/cake/platform/exception/ValidationErrorExtractorTests.java
- backend/src/test/java/com/cake/platform/security/AuthRequestsTests.java
- backend/src/test/java/com/cake/platform/security/AuthHttpMySqlTests.java
- frontend/src/api/http.test.js
- docs/security/AUTHENTICATION_DESIGN.md
- docs/security/AUTH_API_TESTS.md
- docs/security/JWT_AND_PERMISSION.md
- docs/security/PASSWORD_SECURITY.md

实际新增异常名称为FieldValidationException，与提案暂列名称不同，职责一致；其余修改按当前源码确定。没有删除原测试。原GlobalExceptionHandler测试只定向更新已确认的字段响应预期，原ValidationErrorExtractor测试用真实公开字段类型替代Object夹具，以验证可信字段映射，保留多消息、不可变和拒绝值保护断言。AuthHTTP原普通未知字段data=null断言保留，并对所有响应强化公共结构检查。

## 6 测试数量和结果

最终源码完整后端test与package均实测通过；真实MySQL开关显式启用，不以skip代替通过。

| 后端套件 | 原Phase3执行数 | 本轮最终执行数 | 最终结果 |
| --- | --- | --- | --- |
| CakeOrderPlatformApplicationTests | 1 | 1 | 通过 |
| ApiResponseTests | 6 | 6 | 通过 |
| GlobalExceptionHandlerTests | 17 | 17 | 通过 |
| ValidationErrorExtractorTests | 1 | 1 | 通过 |
| DatabaseInfrastructureTests | 4 | 4 | 通过 |
| MySqlDatabaseTests | 28 | 28 | 通过 |
| AuthRequestsTests | 55 | 75 | 通过 |
| AuthHttpMySqlTests | 101 | 107 | 通过 |
| JwtAuthenticationFilterTests | 17 | 17 | 通过 |
| JwtServiceTests | 25 | 25 | 通过 |
| ParameterContractTests | 0 | 17 | 通过 |
| 合计 | 255 | 298 | 失败0／错误0／跳过0 |

Phase1原25项包含于Phase2累计57项，57项又包含于Phase3原255项，不能25+57+255重复相加。本轮新增43项＝公共契约17＋AuthRequests20＋认证HTTP6。前端4个测试文件共59项通过，失败0、跳过0，包含原54项和新增5项。相同套件被test和package重复运行，不累计成更多不同用例。

新增覆盖包括单字段、多字段、同字段多消息、全部消息为字符串、lowerCamelCase、JsonProperty重命名、嵌套列表、显式Query名称、方法校验同时包含Bean/Query错误、默认注解独立消息、JSON类型/语法、全局错误和未知字段null兜底、无_global、不可变和不泄露拒绝值。认证HTTP新增覆盖注册多错误且不写库、登录username定位、改密双字段和newPassword位置；原有认证、权限、并发锁定、退出/改密事务及系统错误回归全部保留。

| 实际命令或检查 | 结果 |
| --- | --- |
| 指定JDK java -version、node --version、npm.cmd --version | 18.0.2.1／24.20.0／11.19.0 |
| mvnw.cmd -B -DskipTests compile | 仅前期编译检查成功；不是完整测试证据 |
| mvnw.cmd -B -Dtest=GlobalExceptionHandlerTests,ValidationErrorExtractorTests,ParameterContractTests test | 初轮33项通过，之后公共契约再补2项并定向17项通过 |
| mvnw.cmd -Dtest=AuthRequestsTests test | 75项通过 |
| mvnw.cmd -B test（真实MySQL开关开启） | 首轮296项通过；补2项后最终298项全过，耗时20.562秒 |
| mvnw.cmd -B package（同一真实MySQL配置，无跳测） | 再次298项全过并打包成功，耗时21.307秒 |
| npm.cmd test | 4文件59项通过 |
| npm.cmd run build | 生产构建成功；原chunk超过500kB告警保留，JS约1041.65kB，gzip344.20kB |
| 两库只读JDBC起止审计 | MySQL8.0.19／会话+08:00，六表数据及规范化结构摘要一致 |
| 打包JAR真实启动及HTTP检查 | 11项通过，loopback临时端口，持有的验证进程已停止 |
| 文件保护、Markdown/JSON/链接检查 | 见第8节 |

后端环境只在验证PowerShell及其子进程设置JAVA_HOME、DB_USERNAME、DB_PASSWORD、DB_NAME、DB_TEST_NAME、CAKE_MYSQL_TEST_ENABLED=true和本轮随机JWT_SECRET_BASE64。真实密码与密钥未写入项目、报告或JVM命令行。复现时按docs/security/AUTH_API_TESTS.md隐藏交互注入，不提供明文示例。

## 7 打包运行和数据库保护

本轮JAR为backend/target/cake-order-platform-0.0.1-SNAPSHOT.jar，使用现有dev配置连接开发库，SQL自动初始化保持never。通过隐藏的无窗口子进程启动，秘密从环境继承，仅绑定127.0.0.1的临时空闲端口。

11项实际HTTP检查：匿名本人资料40102；注册缺字段；注册多字段及password双消息；登录username/password定位；注册携带role、status、merchantId、tokenVersion分别40302；畸形JSON、普通未知字段及非对象请求data=null。所有检查同时校验三顶层字段、HTTP与业务码，字段消息为字符串数组。只使用被参数或权限检查拒绝的请求，未在开发库创建账号或触发已有账号失败计数。

首次JAR检查的临时PowerShell助手因集合比较表达式括号问题误报字段集合不匹配；修正助手后重新启动同一JAR，11项全部通过，未为此修改应用源码。两次启动的自持验证进程均已停止，不停止MySQL服务或其他进程。

数据库回归复用cake_order_system_phase2_test，保留已有_test校验、与开发库不同的限制及实际catalog检查。认证套件只按本轮合成账号/生成ID清理自身夹具，逐例核对六表计数；旧数据库套件事务回滚。没有清空全表、重置数据库、修改结构、执行init/seed或无关数据清理。

| 数据库 | 本轮起止六表行数 | 本轮起止规范化结构SHA256 |
| --- | --- | --- |
| cake_order_system | 六表均0，起止一致 | ee3bebe42cae1a8e7381932029b6f0e2e8cd272d644c4ff25f4d2cb290db5f87 |
| cake_order_system_phase2_test | 六表均0，起止一致 | 15cda01419f260c563f5f78386f371eed939283ce5ec01a2a5bacf5599fbbc75 |

两库数据摘要起止均为e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855。审计通过SELECT与SHOW CREATE TABLE完成，规范化结构仅忽略AUTO_INCREMENT当前数字，另记录序列元信息；不重置自增序列，不用事务回滚推断序列已恢复。核心六表、74字段、索引、外键、CHECK和collation均未变化，原SQL文件起止SHA256一致。

可重建验证生成物位于backend/target/phase31-full-tests.log、phase31-package.log、phase31-common-tests.log、phase31-contract-tests.log、phase31-database-before.log、phase31-database-after.log、phase31-jar-checks.json、phase31-jar-stdout.log、phase31-jar-stderr.log和surefire-reports。临时只读审计及启动助手在本轮专用TEMP目录，不加入项目源码。

## 8 源文件和文档保护

开始92个既有手写文件，排除node_modules、target、dist；最终96个，其中新增4、修改19、原有73个SHA256一致、删除0。原DOCX、AGENTS、Phase0需求/追踪/架构/计划、Phase0～3历史报告、PHASE_3_1_PROPOSAL、整个database目录、pom/Wrapper、前端package/lock、应用配置、Axios核心、JWT/BCrypt/锁定/版本实现相关未授权路径均保持原样。AuthenticationService的授权改动仅为同密码40001补字段，AuthErrorResponses仅改注释。

原DOCX SHA256仍为522D3CB741550FD88D8B73365A245F2AEDF98AA6CF6B1AF2A5A7A34DF56025E5。无SQL、数据库表字段、API路径、依赖版本、页面或排除范围功能变化。README、PROJECT_STATUS、TODO及四份安全文档已区分Phase3历史证据与本轮结果。Markdown代码围栏、报告示例JSON、本轮文档本地链接、契约锚点及文件清单均核对；本轮数据库密码在项目和验证日志文本中的持久化检查为0处。源文件保护以起止清单为准，核对后无残留匹配JAR进程。

## 9 未解决问题及验收结论

| 通过条件 | 本轮结论 |
| --- | --- |
| 确认的fieldErrors对象与字符串数组 | 通过 |
| 单字段、多字段、同字段多消息 | 通过 |
| 实际API字段命名及嵌套JSON映射 | 通过 |
| 无法定位data=null，无_global | 通过 |
| 40302及其他既有错误码/HTTP状态 | 完整原回归及强化契约断言通过 |
| Axios接收/透传及认证处理 | 通过，核心未改 |
| Phase1、2、3及新增完整测试 | 最终后端298、前端59，全过，无跳过 |
| 后端package及前端build | 成功 |
| JAR启动和响应检查 | 11项通过，进程已停止 |
| 数据库结构与已有数据保护 | 起止审计及源文件保护通过 |
| 阶段状态 | 待审核，停止于Phase3.1 |

本阶段没有未完成的必需验证。既有边界继续保留：密码补充平面/组合字符长度口径待明确；真实ADMIN/MERCHANT参数化种子暂缓；真实表单就地提示、生产TLS/密钥托管、浏览器业务联调、商品订单归属及完整TC01～46尚未验收。它们不由本次公共参数契约代替用户决定，也不提前开发。

当前剩余构建提示为既有前端大chunk告警，未为消除提示擅改打包方案。停止开发，等待用户审核Phase3.1；未经新的明确授权，不进入Phase4，不执行种子、Git初始化、提交或推送。
