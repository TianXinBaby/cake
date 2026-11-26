# Phase 1 前后端基础架构阶段报告

日期：2026-11-24（Asia/Shanghai）。工作区：`D:\study\蛋糕店订单管理系统`。

**结论：Phase 1 为 PARTIAL，待用户审核，不标记整体 PASS。** 后端25项测试、前端28项测试、两端构建、独立启动与真实开发代理检查均通过。用户明确暂不确认 `data.fieldErrors` 的公共结构，因此公共字段级参数错误定位响应尚未交付。本轮停止于Phase1，Phase2未授权、未开始。

## 1 阶段目标与授权范围

依据用户本轮《Phase 1：蛋糕店订单管理系统——前后端基础架构搭建》完整要求，创建Spring Boot与Vue基础工程、官方Maven Wrapper、公共响应/异常、Axios/代理、测试和文档。此前只有需求原件及九份Phase0 Markdown，无已有工程可复用，不覆盖或删除用户文件。

本轮明确覆盖历史计划中的三点：ENV-02采用JDK18.0.2.1；ENV-03使用Maven Wrapper而非全局Maven；本阶段暂不引入MyBatis-Plus、MySQL驱动、Spring Security、JWT、BCrypt、Knife4j/OpenAPI或ECharts。ENV-01继续采用Node24.20.0。不修改原始DOCX、AGENTS、需求基线、追踪、架构、阶段计划和Phase0报告。

没有创建数据库、连接数据库、SQL、迁移、业务Controller/API、Entity、Mapper、Service、认证、权限、业务页面或业务Store。根路由只有技术空出口，不计入UI-01～17。没有初始化Git、创建分支、提交或推送。

## 2 环境检查结果

修改前检查了工作区绝对路径、九份Phase0文档、根目录及父目录规范、现有文件与Git状态。`D:\AGENTS.md`与`D:\study\AGENTS.md`均不存在，未发现其他需继承的上级文件。

| 检查 | 实际结果 | 本轮处理 |
| --- | --- | --- |
| Windows / PowerShell | Windows11专业工作站版10.0.26200 / PowerShell7.6.9 | 使用PowerShell原生命令、UTF-8、npm.cmd |
| `java -version` | 默认Oracle javapath无可用版本输出 | 未修复启动器；不以此认定JDK版本 |
| `where.exe java` | Oracle javapath优先，其次D:\study\jdk18\bin\java.exe | Wrapper通过JAVA_HOME直接使用指定JDK |
| 指定JDK绝对路径 `java.exe -version` / `javac.exe -version` | 均18.0.2.1，成功 | 不安装、不切换JDK17 |
| 进程JAVA_HOME | D:\study\jdk18 | 实际Wrapper确认runtime同路径；未改系统/用户环境 |
| `node -v` / `npm.cmd -v` | v24.20.0 / 11.19.0 | 直接使用现有环境 |
| `where.exe mvn` | 未找到全局命令 | 不依赖全局Maven，不推断整机绝无其他安装 |
| `mvnw.cmd -version` | Maven3.9.11；Java18.0.2.1；runtime D:\study\jdk18 | 通过 |
| MySQL二进制 / 服务 | mysqld8.0.19 / MySQL80 Running | 只读版本和服务状态，不连接数据库或使用凭据 |
| Git | 2.55.0.windows.5；工作区非仓库 | 分支/远程/未提交状态不适用，不能称工作树干净 |

未修改系统或用户的JAVA_HOME、PATH、NODE_OPTIONS和PowerShell ExecutionPolicy。开始/结束对这些设置的哈希及策略快照一致。项目依赖安装、官方Maven下载/用户缓存及构建产物是本轮授权内操作，不是全局软件安装。

## 3 技术及精确版本

| 后端技术 | 精确版本 |
| --- | --- |
| JDK运行时 / 编译release | 18.0.2.1 / 18（class major62） |
| Maven / Wrapper插件及发行脚本 | 3.9.11 / 3.3.4 only-script |
| Spring Boot及三个直接starter | 3.5.16 |
| Spring Framework | 6.2.19 |
| Tomcat | 10.1.55 |
| Jackson | 2.21.4；jackson-annotations为2.21 |
| Hibernate Validator / Jakarta Validation API | 8.0.3.Final / 3.0.2 |
| Logback / SLF4J | 1.5.34 / 2.0.18 |
| JUnit Jupiter / JUnit Platform | 5.12.2 / 1.12.2 |
| Mockito / AssertJ | 5.17.0 / 3.27.7 |

后端依赖由精确的Boot3.5.16父POM/BOM管理，没有动态版本或独立混装框架版本。完整解析依赖树记录在本报告附录及 `backend/target/dependency-tree.txt`，可按第13节命令复现。

| 前端技术 | 精确版本 |
| --- | --- |
| Node.js / npm | 24.20.0 / 11.19.0 |
| Vue | 3.5.46 |
| Vue Router | 4.6.5 |
| Pinia | 3.0.4 |
| Element Plus | 2.14.8 |
| Axios | 1.21.1 |
| Vite / @vitejs/plugin-vue | 7.3.8 / 6.0.11 |
| Vitest / jsdom | 4.1.12 / 31.0.1 |

直接版本在package.json精确固定，无^/~；package-lock.json固定解析版本和完整性值。engines精确固定Node/npm，packageManager为npm@11.19.0；项目.npmrc启用engine-strict与save-exact。实际安装通过；另机器扫描锁文件97个Node engines声明，当前24.20.0不兼容项为0。`npm.cmd ls --depth=0`与上述清单一致。

已核对官方包元数据及兼容说明。Boot3.5的Java支持范围包含18；Vite7和所选测试工具的Node范围包含24.20.0，最终以实际测试/构建为验证证据。[Spring Boot系统要求](https://docs.spring.io/spring-boot/3.5/system-requirements.html)、[Vite7迁移与Node要求](https://v7.vite.dev/guide/migration)、[npm官方Registry](https://registry.npmjs.org/)。本轮没有为了追求更新主版本引入Vite8或额外架构。

## 4 后端目录结构

```text
backend/
├── .mvn/wrapper/maven-wrapper.properties
├── mvnw
├── mvnw.cmd
├── pom.xml
└── src/
    ├── main/
    │   ├── java/com/cake/platform/
    │   │   ├── CakeOrderPlatformApplication.java
    │   │   ├── common/ApiResponse.java
    │   │   ├── config/JacksonConfiguration.java
    │   │   ├── config/RequestTraceFilter.java
    │   │   └── exception/
    │   │       ├── BusinessException.java
    │   │       ├── CommonErrorCode.java
    │   │       ├── ErrorCode.java
    │   │       ├── GlobalExceptionHandler.java
    │   │       └── ValidationErrorExtractor.java
    │   └── resources/application.yml
    └── test/
        ├── java/com/cake/platform/
        │   ├── CakeOrderPlatformApplicationTests.java
        │   ├── common/ApiResponseTests.java
        │   ├── exception/GlobalExceptionHandlerTests.java
        │   └── exception/ValidationErrorExtractorTests.java
        └── resources/application-test.yml
```

包名com.cake.platform是本轮沿用架构唯一候选的技术选定；Phase0原文说“候选、由Phase1确认”，不冒称此前已正式确认。未创建无用空目录或空类。

## 5 前端目录结构

```text
frontend/
├── .npmrc
├── index.html
├── package.json
├── package-lock.json
├── vite.config.js
└── src/
    ├── App.vue
    ├── application.js
    ├── application.test.js
    ├── main.js
    ├── api/http.js
    ├── api/http.test.js
    ├── router/index.js
    └── styles/base.css
```

应用工厂真实注册Element Plus中文配置、Pinia和Router。main.js引入基础及Element Plus CSS并挂载。App.vue只有RouterView，根路径渲染空出口，无导航、仪表盘、登录表单或其他业务UI。不为目录占位创建views、stores空文件。

## 6 Maven Wrapper 配置

通过Apache官方 `maven-wrapper-plugin:3.3.4:wrapper` 生成only-script发行；bootstrap使用TEMP中的Maven3.9.11，先核对官方SHA512，不安装全局Maven。官方only-script类型无需maven-wrapper.jar，缺少该JAR不是Wrapper不完整。[Apache Maven Wrapper说明](https://maven.apache.org/tools/wrapper/)、[官方插件用法](https://maven.apache.org/tools/wrapper/maven-wrapper-plugin/usage.html)。

配置：

```properties
wrapperVersion=3.3.4
distributionType=only-script
distributionUrl=https://repo.maven.apache.org/maven2/org/apache/maven/apache-maven/3.9.11/apache-maven-3.9.11-bin.zip
distributionSha256Sum=0d7125e8c91097b36edb990ea5934e6c68b4440eef4ea96510a0f6815e7eeadb
```

两脚本与官方3.3.4发行ZIP逐字节哈希相同，没有手写或伪造脚本：

```text
mvnw      CAE96CEF89EBEA3531221F4AE17C23CF8EDF67D00EAE8306D4186AE1BBED4D02
mvnw.cmd  46EEDB8419BD14FE70D5BB2916D7B6F51806E51B39D5B76A42610384CA929C1C
```

实际 `mvnw.cmd -version` 确认Maven3.9.11、JDK18.0.2.1及D:\study\jdk18。运行JDK与编译目标分别记录，release18，不把JDK17写成用户安装要求。

## 7 统一响应实现

`ApiResponse<T>`为泛型record，仅code、msg、data三字段，无success、timestamp等新顶层字段。成功固定200/success，data支持泛型和null；失败使用需求业务码。测试同时覆盖HTTP200和201包装，201夹具只在test源码中，不实现任何创建业务。

```json
{"code":200,"msg":"success","data":null}
```

```json
{"code":40001,"msg":"参数错误","data":null}
```

```json
{"code":50001,"msg":"操作失败，请稍后重试","data":{"requestId":"服务端生成的UUID"}}
```

字段命名为lowerCamelCase。未实现业务的标准框架404等响应不被称为已实现业务响应，相关边界见第18节。

## 8 异常处理与基础配置

GlobalExceptionHandler覆盖Bean Validation、方法输入校验、JSON解析/类型/未知字段、业务异常基础类型及未知系统异常。参数HTTP400/40001；未知系统HTTP500/50001及原文固定提示。方法返回值校验失败属于服务端错误，映射500，不误报客户端参数错。ErrorCode接口为后续需求内业务码保留扩展能力，本轮只定义40001/50001。

请求过滤器生成服务端UUID，只放请求属性和系统错误data.requestId，不增加响应顶层字段。日志记录请求号、HTTP方法、路由模板和结果，不记录正文、查询值、路径变量值、调用者伪造请求号、密码、完整JWT、SQL或异常message。测试核对响应与日志均不泄露这些测试敏感内容。当前没有真实操作者或业务资源，不虚构用户身份。

**REQ-Q10：**完整原文只规定fieldErrors字段名和字段定位目的，没有对象/数组结构。用户本轮明确选择“暂不确认，报告中保留此待确认项”。ValidationErrorExtractor仅内部收集字段名及校验消息，不携带rejectedValue；参数响应当前data=null。该内部能力测试通过，不等于公共字段级定位交付；此项验收保留未通过/待确认。

application.yml配置UTF-8、Asia/Shanghai、lowerCamelCase、null保留、关闭时间戳输出、拒绝未知普通JSON字段、隐藏框架异常详情和基础日志。Jackson自定义LocalDateTime与OffsetDateTime序列化为北京时间ISO8601毫秒+08:00，测试真实断言示例与空事件null。没有宣称已完成后续ID/金额/数据库时间映射。application-test.yml放测试资源，端口0、关闭banner和降低日志，不进入生产JAR。

## 9 Axios 配置

唯一实例位于src/api/http.js：baseURL=/api、timeout=10000。成功2xx且code=200返回原始data，包括null；错误通过同一ElMessage.error入口提示并reject。前端本地RequestError保留code、data、HTTP status；后端data内的fieldErrors/requestId不解释、不加工，不定义其公共形状。

非法响应体、网络失败、超时及未知异常统一安全提示；50001固定“操作失败，请稍后重试”。没有重复实例、组件硬编码后端地址、JWT注入、登录态/角色判断、重试或缓存，仅注释保留未来统一请求头扩展位置。

## 10 Vite 代理配置

开发服务器127.0.0.1:5173，strictPort=true；`/api`统一代理至http://127.0.0.1:8080，changeOrigin=true，无rewrite。后端默认绑定127.0.0.1:8080，可通过SERVER_PORT/SERVER_ADDRESS现有配置占位覆盖，不需要秘密。

代理目标只在vite.config.js配置，没有开放全部CORS。preview使用127.0.0.1:4173。开发代理不等于生产部署方案，未部署站点。

## 11 前后端独立启动与真实联通

后端使用JDK绝对路径独立启动生产JAR，日志显示Java18.0.2.1、Boot3.5.16、Tomcat10.1.55，2026-11-24 20:55:57 +08:00启动完成，耗时1.652秒，启动stderr为空。前端 `npm.cmd run dev` 成功，Vite启动约380ms。

| 实际HTTP检查 | 结果 |
| --- | --- |
| GET http://127.0.0.1:5173/ | 200、text/html，含#app和真实main.js入口 |
| GET http://127.0.0.1:5173/src/main.js | 200、text/javascript |
| GET http://127.0.0.1:5173/src/App.vue | 200、text/javascript，真实SFC转换成功 |
| 直连8080 GET /api/products?merchantId=1 | 404、application/problem+json; charset=UTF-8 |
| 经5173 GET相同/api/products?merchantId=1 | 同404、同Content-Type、UTF-8正文逐字相同 |
| 后端GET /api/hello、/api/test、/api/health | 全部404，未保留探测接口 |
| 后端GET /__fixture/success | 404，正式包无测试路由 |

代理两侧实际正文相同：

```json
{"type":"about:blank","title":"Not Found","status":404,"detail":"No static resource api/products.","instance":"/api/products"}
```

使用PowerShell Invoke-WebRequest -SkipHttpErrorCheck -TimeoutSec10；对application/problem+json返回的byte[]先UTF-8解码，再比较状态和正文；不把byte[]对象比较当作文本一致。此检查证明代理保持原路径且到达真实后端，**不证明商品API或业务成功链路已实现，也不是统一业务响应的通过证据**。没有为联通新增或临时保留生产接口。测试夹具只在src/test中。

验证结束后停止本轮持有的后端PID30116及Vite PID26704，按命令行与程序路径确认归属，不影响MySQL服务。未进行真实浏览器业务交互或视觉验收。

## 12 后端测试结果

| Surefire套件 | 测试 | 失败 | 错误 | 跳过 |
| --- | ---: | ---: | ---: | ---: |
| CakeOrderPlatformApplicationTests | 1 | 0 | 0 | 0 |
| ApiResponseTests | 6 | 0 | 0 | 0 |
| GlobalExceptionHandlerTests | 17 | 0 | 0 | 0 |
| ValidationErrorExtractorTests | 1 | 0 | 0 | 0 |
| 合计 | 25 | 0 | 0 | 0 |

覆盖上下文、泛型/失败/null/精确三字段、HTTP200/201/400/500、Bean与方法校验、JSON非法/缺失/类型/未知字段、字段内部提取、业务异常、未知异常、UUID唯一与日志关联、响应/日志无内部信息、返回值校验500、未实现路径保留404及时间格式。全部无数据库依赖。

真实执行 `mvnw.cmd -B clean test`，BUILD SUCCESS，约5.899秒；随后package再次执行相同25项全过，不累计成50个不同测试。最终XML可在backend/target/surefire-reports复核。

## 13 前端测试与两端构建结果

| 实际命令及目录 | 结果 |
| --- | --- |
| backend: .\mvnw.cmd -version | Maven3.9.11 / Java18.0.2.1 |
| backend: .\mvnw.cmd -B clean test | 成功，25/25 |
| backend: .\mvnw.cmd -B package | BUILD SUCCESS，约6.165秒，25项再次通过 |
| backend: .\mvnw.cmd -B dependency:tree -DoutputFile=target/dependency-tree.txt | 成功，约3.964秒，完整版本树已记录 |
| frontend: npm.cmd install | exit0；新增152包、审计153包，0漏洞 |
| frontend: npm.cmd run test | exit0；2文件，28/28通过 |
| frontend: npm.cmd run build | exit0；Vite7.3.8，处理1627模块 |
| frontend: npm.cmd ls --depth=0 | 精确直接版本与清单一致 |
| frontend: 锁文件engines机器核验 | 97个声明，incompatible=[] |

前端测试覆盖真实应用挂载、Element Plus/Pinia/Router真实注册、状态与实例隔离、路由初始化、Axios配置和默认非2xx拒绝、200/201/202及null、业务失败、无效体、非2xx、系统错误脱敏、网络/两类超时/未知异常、安全提示与不重试。这里只是基础设施测试，不计为TC-01～46业务验收。

## 14 后端构建产物

`backend/target/cake-order-platform-0.0.1-SNAPSHOT.jar`，22,678,189字节。Manifest确认Build-Jdk-Spec18、Boot3.5.16、Start-Class=com.cake.platform.CakeOrderPlatformApplication；javap class major62。

ZIP/JAR检查：应用class共11个（含序列化匿名类），没有Tests/Fixture类、application-test.yml或JUnit/Mockito/BootTest测试运行库；没有生产业务Controller。另有原始未repackage JAR、测试报告、依赖树、启动日志等target生成物。它们可由clean移除并复现，不是业务源码。

## 15 前端构建产物与告警

| 产物 | 实际大小 | gzip显示值 |
| --- | ---: | ---: |
| dist/index.html | 约0.41kB | 构建输出为准 |
| dist/assets/index-xRwi_nNV.css | 363,190字节 | 48.55kB |
| dist/assets/index-CcixO_x2.js | 1,041,651字节 | 344.20kB |

Element Plus全量注册使主JS超过Vite默认500kB chunk提示线，构建成功，体积告警保留；未提高阈值或加入拆包插件掩盖提示。npm11.19另提示esbuild0.28.2的postinstall未登记allowScripts，未擅自批准或修改环境，实际测试和生产构建均成功。安装时0漏洞是该次npm审计结果，不代表整套系统已做全面安全审计。

## 16 新增文件清单

新增源代码/配置/文档共33个：后端19、前端13、报告1。准确文件名已在第4、5节树中逐项列出，另新增本文件 `reports/PHASE_1_REPORT.md`。这些目录之前不存在，因此无覆盖既有工程。

另有授权安装与验证生成目录backend/target、frontend/node_modules、frontend/dist，以及Maven用户缓存/TEMP bootstrap；不计为新增业务源码，不将成千上万依赖文件误报为手写代码。无临时生产测试路由或测试工具代码残留。

## 17 修改文件及源文件保护

只修改原有三份可更新文档：README.md、PROJECT_STATUS.md、TODO.md。README补实际启动/测试/构建与环境；状态记录Phase1/PARTIAL/后续未授权；TODO关闭用户已确认环境项并保留需求歧义。没有覆盖历史需求/架构/计划/报告。

完整只读解析当前原件462块（437正文、25表、346行、1675单元格），与Phase0转录462个唯一SRC标记逐段/表核对，只有2.3/B041运行环境行不同：当前为JDK18.0.2.1、Node24.20.0，旧转录为JDK17、Node18；其他正文和表格行一致。脚注/尾注无业务文字，页脚只有页码。

当前原件119,964字节，Phase0历史报告记录114,393字节及618DAE...哈希；该差异在本轮修改前已经存在，不能伪称当前仍匹配历史哈希，也不能推断其编辑来源。本轮以本次起始值证明保护。以下7份文件结束SHA256与本轮开始值全部相同：

| 保护对象 | 本轮起止一致的SHA256 |
| --- | --- |
| 蛋糕店订单管理系统需求说明书_V1.1.docx | 522D3CB741550FD88D8B73365A245F2AEDF98AA6CF6B1AF2A5A7A34DF56025E5 |
| AGENTS.md | 0A8BED2F69F2E0A58557F5FAEE7C96BEAF6B81C736581C0D01FED49A6AC90202 |
| docs/requirements/REQUIREMENTS_BASELINE.md | 3E24B6A7EE41A9FB69EBFE7204A3AA54731DC8BC6DD29B2475BAC9DDA25D5C5D |
| docs/requirements/TRACEABILITY_MATRIX.md | B466C660D080B1F613D35C9BF75C2C48AB40BD78179F37F41269D7C109B8B8B9 |
| docs/architecture/ARCHITECTURE_PLAN.md | 42886C7B1019616BC669AB299BA02817D140AF188D80B847AD2D17F4D5792453 |
| docs/plan/PHASE_PLAN.md | 94281E081ED229392CFA29214432CC4C8F834844AAF998E11F4E2F3011753ED0 |
| reports/PHASE_0_REPORT.md | 467601F100F45027DAA323F5EE79B71927280C601B01112D56E26D9D40FE7A93 |

结束再次执行Git检查，仍报not a git repository；不init/add/commit/push，不声称已取得Git diff或分支。文档与工程范围通过实际文件集合、源码检索和哈希复核，不依赖不存在的仓库。

最终补充核验：排除node_modules/target/dist生成物后共43个源文件及文档，等于原有10个加新增33个；四份本轮文档本地断链0、Markdown表格列数异常0；七份保护对象起止哈希一致；持久环境及ExecutionPolicy快照一致；8080/5173监听数0，两个验证进程均已退出。独立只读复核再次确认文件计数、Surefire XML、JAR排除测试代码和文档PARTIAL结论一致。

## 18 遗留问题

1. **REQ-Q10 / 未通过待确认：**公共fieldErrors结构未定，用户明确暂缓，当前参数data=null；不能将内部字段提取测试当成对外定位功能完成。
2. **框架错误范围：**无需求定义业务码的404/405/415等保留Spring默认处理，可能为ProblemDetail；本轮未擅增码表。已实现的公共参数/业务基础/未知系统异常才使用已验证三字段封装。未来如需统一所有框架状态，应先确认对应业务码，不以本轮404联通检查代替契约验收。
3. **构建体积：**主JS约1041.65kB，当前基础工程可构建；后续页面阶段再根据实际使用评估按需注册/加载，不提前改变本阶段范围。
4. **默认Java启动器：**Oracle javapath仍异常；Wrapper实际使用指定JDK已通过，用户系统配置保持不变。
5. **历史文档：**原件环境行与Phase0旧转录存在已核对差异，旧文档仍含Phase0/JDK17等历史状态；本轮执行以README/当前状态/本报告中的最新授权为准，不覆盖旧基线。

## 19 风险及待确认事项

除REQ-Q10外，Phase0原有collation、商家账号禁用入口、付款超时与店铺不可用优先级、库存回补上限、热销店名快照、编号映射和缺失修订附件等仍在TODO保留。本轮未处理或擅自决定这些业务规则，它们应在受影响阶段获授权后确认。

当前没有业务实现，因此权限/归属/事务/并发/数据库故障/性能测试和TC-01～46未执行；无生产部署、真实浏览器业务操作或UI视觉验收。构建成功、依赖审计和本轮53个不同基础测试不替代这些后续证据。没有索取或使用密码、数据库凭据或真实令牌。

## 20 阶段验收结论

| 验收项 | 结论与证据 |
| --- | --- |
| 后端工程 | 通过：JAR独立启动 |
| Maven Wrapper | 通过：官方生成、固定版本/校验、实际命令成功 |
| JDK | 通过：18.0.2.1，runtime路径、release18及major62 |
| 前端工程 | 通过：真实Vue初始化测试与Vite启动、入口HTTP200 |
| Node.js / npm | 通过：24.20.0 / 11.19.0 |
| 统一响应 | 已实现公共能力通过：code/msg/data、成功200、HTTP201包装、null |
| 异常处理 | 已实现映射通过：40001/50001、安全提示/UUID/日志；未定义框架状态边界已记录 |
| 字段级公共错误响应 | **未通过/待确认：用户暂不确认fieldErrors结构** |
| Axios | 通过：单例、统一配置、响应/异常与统一提示 |
| Vite代理 | 通过：真实两端相同404状态与正文，无新增探测API |
| 后端测试 | 通过：25项，失败/错误/跳过均0 |
| 前端测试 | 通过：28项 |
| 后端构建 | 通过：生产JAR可运行，无测试代码入包 |
| 前端构建 | 通过：dist生成；体积和npm脚本提示已记录 |
| 文档及保护 | 四份本轮可更新/新增文档完成；七份保护对象未变 |
| 功能范围 | 通过：无业务、无DB、无额外API/UI/架构 |
| Phase1整体 | **PARTIAL，待审核，不是PASS** |
| 下一阶段 | **Phase2未授权、未开始；立即停止** |

阶段审核与fieldErrors确认均由用户决定。报告列出的待办不构成自动继续开发授权。

## 附录 后端完整解析依赖树

以下为本轮真实 dependency:tree 输出，记录compile/test的精确版本；不是候选清单。
```text
com.cake.platform:cake-order-platform:jar:0.0.1-SNAPSHOT
+- org.springframework.boot:spring-boot-starter-web:jar:3.5.16:compile
|  +- org.springframework.boot:spring-boot-starter:jar:3.5.16:compile
|  |  +- org.springframework.boot:spring-boot:jar:3.5.16:compile
|  |  +- org.springframework.boot:spring-boot-autoconfigure:jar:3.5.16:compile
|  |  +- org.springframework.boot:spring-boot-starter-logging:jar:3.5.16:compile
|  |  |  +- ch.qos.logback:logback-classic:jar:1.5.34:compile
|  |  |  |  \- ch.qos.logback:logback-core:jar:1.5.34:compile
|  |  |  +- org.apache.logging.log4j:log4j-to-slf4j:jar:2.24.3:compile
|  |  |  |  \- org.apache.logging.log4j:log4j-api:jar:2.24.3:compile
|  |  |  \- org.slf4j:jul-to-slf4j:jar:2.0.18:compile
|  |  +- jakarta.annotation:jakarta.annotation-api:jar:2.1.1:compile
|  |  \- org.yaml:snakeyaml:jar:2.4:compile
|  +- org.springframework.boot:spring-boot-starter-json:jar:3.5.16:compile
|  |  +- com.fasterxml.jackson.core:jackson-databind:jar:2.21.4:compile
|  |  |  +- com.fasterxml.jackson.core:jackson-annotations:jar:2.21:compile
|  |  |  \- com.fasterxml.jackson.core:jackson-core:jar:2.21.4:compile
|  |  +- com.fasterxml.jackson.datatype:jackson-datatype-jdk8:jar:2.21.4:compile
|  |  +- com.fasterxml.jackson.datatype:jackson-datatype-jsr310:jar:2.21.4:compile
|  |  \- com.fasterxml.jackson.module:jackson-module-parameter-names:jar:2.21.4:compile
|  +- org.springframework.boot:spring-boot-starter-tomcat:jar:3.5.16:compile
|  |  +- org.apache.tomcat.embed:tomcat-embed-core:jar:10.1.55:compile
|  |  \- org.apache.tomcat.embed:tomcat-embed-websocket:jar:10.1.55:compile
|  +- org.springframework:spring-web:jar:6.2.19:compile
|  |  +- org.springframework:spring-beans:jar:6.2.19:compile
|  |  \- io.micrometer:micrometer-observation:jar:1.15.12:compile
|  |     \- io.micrometer:micrometer-commons:jar:1.15.12:compile
|  \- org.springframework:spring-webmvc:jar:6.2.19:compile
|     +- org.springframework:spring-aop:jar:6.2.19:compile
|     +- org.springframework:spring-context:jar:6.2.19:compile
|     \- org.springframework:spring-expression:jar:6.2.19:compile
+- org.springframework.boot:spring-boot-starter-validation:jar:3.5.16:compile
|  +- org.apache.tomcat.embed:tomcat-embed-el:jar:10.1.55:compile
|  \- org.hibernate.validator:hibernate-validator:jar:8.0.3.Final:compile
|     +- jakarta.validation:jakarta.validation-api:jar:3.0.2:compile
|     +- org.jboss.logging:jboss-logging:jar:3.6.3.Final:compile
|     \- com.fasterxml:classmate:jar:1.7.3:compile
\- org.springframework.boot:spring-boot-starter-test:jar:3.5.16:test
   +- org.springframework.boot:spring-boot-test:jar:3.5.16:test
   +- org.springframework.boot:spring-boot-test-autoconfigure:jar:3.5.16:test
   +- com.jayway.jsonpath:json-path:jar:2.9.0:test
   |  \- org.slf4j:slf4j-api:jar:2.0.18:compile
   +- jakarta.xml.bind:jakarta.xml.bind-api:jar:4.0.5:test
   |  \- jakarta.activation:jakarta.activation-api:jar:2.1.4:test
   +- net.minidev:json-smart:jar:2.5.2:test
   |  \- net.minidev:accessors-smart:jar:2.5.2:test
   |     \- org.ow2.asm:asm:jar:9.7.1:test
   +- org.assertj:assertj-core:jar:3.27.7:test
   |  \- net.bytebuddy:byte-buddy:jar:1.17.8:test
   +- org.awaitility:awaitility:jar:4.2.2:test
   +- org.hamcrest:hamcrest:jar:3.0:test
   +- org.junit.jupiter:junit-jupiter:jar:5.12.2:test
   |  +- org.junit.jupiter:junit-jupiter-api:jar:5.12.2:test
   |  |  +- org.opentest4j:opentest4j:jar:1.3.0:test
   |  |  +- org.junit.platform:junit-platform-commons:jar:1.12.2:test
   |  |  \- org.apiguardian:apiguardian-api:jar:1.1.2:test
   |  +- org.junit.jupiter:junit-jupiter-params:jar:5.12.2:test
   |  \- org.junit.jupiter:junit-jupiter-engine:jar:5.12.2:test
   |     \- org.junit.platform:junit-platform-engine:jar:1.12.2:test
   +- org.mockito:mockito-core:jar:5.17.0:test
   |  +- net.bytebuddy:byte-buddy-agent:jar:1.17.8:test
   |  \- org.objenesis:objenesis:jar:3.3:test
   +- org.mockito:mockito-junit-jupiter:jar:5.17.0:test
   +- org.skyscreamer:jsonassert:jar:1.5.3:test
   |  \- com.vaadin.external.google:android-json:jar:0.0.20131108.vaadin1:test
   +- org.springframework:spring-core:jar:6.2.19:compile
   |  \- org.springframework:spring-jcl:jar:6.2.19:compile
   +- org.springframework:spring-test:jar:6.2.19:test
   \- org.xmlunit:xmlunit-core:jar:2.10.4:test
```
