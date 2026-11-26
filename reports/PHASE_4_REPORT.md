# Phase 4 平台商家与用户管理实施报告

日期：2026-11-26（Asia/Shanghai）。项目：基于 Spring Boot + Vue 的蛋糕店订单管理系统。

**结论：本轮限定范围已实施，后端363项、前端85项测试通过，两端构建成功；打包JAR及Chrome/Edge两页双分辨率检查通过。当前为Phase 4待审核，停止开发，不进入Phase 5。**

## 1 授权范围与需求核对

本轮用户确认Phase 0完成、Phase 1有条件通过、Phase 2通过、Phase 3遗留解决、Phase 3.1通过，明确授权MERCH-01、USER-05、API-35～40及UI-14/15。该授权覆盖磁盘AGENTS的旧Phase 0限制及历史计划的页面分期。本轮不实现API-33/34、登录页、商品分类、订单支付、统计或其他阶段功能，不增加商家账号禁用入口，不执行正式种子或Git操作。

编码前完整读取用户指定的21份项目文档：根目录AGENTS、README、PROJECT_STATUS、TODO；需求基线和追踪矩阵；架构与阶段计划；数据库设计、数据字典、索引约束、初始化指南；认证设计、JWT权限、密码安全、认证测试；Phase 0、1、2、3、3.1报告。历史报告保留原结论，不回写成当时已通过。

原始DOCX只读核对全部正文和表格，计463个正文块、462个非空来源块、25张表、346行、1675个单元格。462块与Phase 0转录逐项核对，仅已知B041环境行有历史差异：现原件为JDK18.0.2.1/Node24.20.0，旧转录为17/18；本轮不修改原件或历史基线。追踪矩阵180条API明细文本及46条TC原文与当前DOCX一致。业务契约重点核对第3.2、3.7、4.3、5.1、5.3、6.1及8.2节。

`git status --short`起止均返回非Git仓库，未执行git init、commit、push、reset或rebase。修改前保存96个手写文件的SHA256清单，排除node_modules、target、dist；本轮按文件哈希核对保护状态。

## 2 编码前技术检查

| 项目 | 实查结果及本轮复用 |
| --- | --- |
| 统一响应及异常 | ApiResponse维持code/msg/data；GlobalExceptionHandler提供固定安全错误与requestId；认证异常使用原AuthExceptionHandler |
| fieldErrors | Phase3.1的实际字段到字符串数组对象，支持多字段和同字段多消息；未知字段不伪造位置，不增加_global |
| 后端安全 | SecurityFilterChain限制/api/admin/**为ADMIN；JWT每请求检查数据库当前账号、角色、启用状态及版本；Controller另有方法级鉴权 |
| 密码和失效 | 现有BCrypt10、HS256/2小时JWT、tokenVersion；创建复用PasswordEncoder，用户启停复用原有用户行锁机制 |
| 数据访问与事务 | 原UserAuthMapper注解SQL及BigInteger实体；原AuthenticationService使用TransactionTemplate；本轮继续同一风格 |
| 分页 | 当前没有MyBatis-Plus分页拦截器；本轮绑定COUNT与LIMIT/OFFSET，不为分页新增依赖 |
| 前端 | 原Router仅根出口；Pinia只存内存会话，Axios单例处理Bearer/40102并保留RequestError.data；没有后台布局、管理员守卫或业务表格组件 |
| 既有测试 | 后端298项、前端59项基线场景保留；仅原application测试的“只有根路由”断言随新授权更新，其余测试保留 |

## 3 数据库与环境检查

实连MySQL 8.0.19，JDBC会话+08:00。开发库cake_order_system、隔离测试库cake_order_system_phase2_test均复用现配置；开始时六表各0行。只读核对sys_user的12字段及cake_merchant的9字段、类型、NULL、默认值和实际建表语句。

- sys_user.username独立唯一键uk_user_username，phone独立唯一键uk_user_phone。
- cake_merchant.user_id唯一键uk_merchant_user，外键fk_merchant_user关联sys_user.id，双向RESTRICT。
- ID及关联ID为BIGINT UNSIGNED，Java BigInteger；新增Mapper显式jdbcType=DECIMAL，测试高ID参数转BigDecimal/DECIMAL，接口ID保持十进制字符串。
- 当前错误角色绑定和重复绑定计数均0。现有FK/UK保证账号存在且至多一店，不能证明MERCHANT角色；创建服务固定该角色并同事务保存。
- 测试库cake_category原自增值已到9223372036854775807，本轮保留该状态，不清空或重置序列；历史数据保护夹具已按unsigned范围处理。

| 环境 | 本轮结果 |
| --- | --- |
| JDK | D:\study\jdk18，18.0.2.1 |
| Node.js / npm | 24.20.0 / 11.19.0，Windows使用npm.cmd |
| Maven | 原官方Wrapper3.3.4 / Maven3.9.11 |
| Spring Boot / MyBatis-Plus | 原3.5.16 / 3.5.18 |
| MySQL / Connector | 8.0.19 / 原8.4.0 |
| Vue / Axios / Pinia / Vite | 沿用原package及lock版本，未安装或升级 |

未修改系统或用户环境变量、PATH、PowerShell ExecutionPolicy。后端验证仅在当前子进程设置JDK、现有数据库凭据、测试库开关和临时随机JWT密钥；秘密未放入命令参数、源码或报告。验证器所需PATH/PATHEXT仅在其子进程补齐，不持久化。

## 4 六接口交付

| 编号 | 方法和路径 | 本轮行为 |
| --- | --- | --- |
| API-35 | GET /api/admin/merchants | page/size/keyword/status可选；含启用及停用店铺、绑定userId/username；Merchant分页 |
| API-36 | POST /api/admin/merchants | accountPhone/nickname/password/name/phone/address必填，description可空；HTTP201，账号和店铺同事务创建 |
| API-37 | PUT /api/admin/merchants/{id} | 仅name/phone/address/description；不换绑、不改账号，不回写历史订单快照 |
| API-38 | PATCH /api/admin/merchants/{id}/status | 整数status 0/1；同值返回当前Merchant；不改账号、商品、订单或库存 |
| API-39 | GET /api/admin/users | 固定role=CUSTOMER；昵称或手机号字面搜索、状态筛选及分页；不接收role筛选 |
| API-40 | PATCH /api/admin/users/{id}/status | 仅CUSTOMER；实际变更status、token_version+1、update_time同SQL同事务；非CUSTOMER及不存在统一40405 |

创建账号固定username和phone为accountPhone、MERCHANT、启用、版本0；店铺启用且唯一绑定该新账号。店铺电话与账号手机号分别处理。重复账号返回40901，不转换已有用户角色。任一步失败全部回滚，密码只保存BCrypt散列，不返回散列或明文。

写事务锁定管理员本人并再次核实启用状态、版本和ADMIN角色；用户状态接口再锁目标并核实CUSTOMER。状态更新与既有退出/改密使用同一用户行锁，防止版本增量丢失。禁用时旧JWT返回40301，恢复后旧JWT返回40102，新登录才可继续。

所有新请求使用独立白名单，不接收实体。原fieldErrors结构不变，accountPhone等错误映射到实际公开字段；受保护字段优先40302，无法定位的普通未知字段仍40001/data=null。Merchant只返回原文十个字段，User复用原公开对象；控制字段不外泄。

分页默认page1/size10，size上限100；超末页返回空数组。超大offset使用BigInteger计算后安全返回空页，不发生数值回绕。LIKE固定ESCAPE '!'并转义!、%、_，反斜杠按字面，全部用户值参数绑定。API-35按店名搜索、两个列表按id降序为原文未细化部分的实现选择。API-40同状态不重复改版本或时间也是实现的幂等选择，未冒称原文逐字规定。

## 5 页面交付与浏览器验证

UI-14 `/admin/merchants`包含店铺筛选分页、绑定账号展示、创建账号店铺、编辑店铺及启停确认。UI-15 `/admin/users`仅包含CUSTOMER筛选分页与启停确认。新增最小后台布局、公共分页、字段错误数组和确认弹窗；无删除、角色编辑、商品经营或订单操作入口。

管理员守卫每次进入管理路由读取现有本人接口，核实服务器当前角色及启用状态；后端独立校验。前端复用Axios、Pinia及退出接口，不保存密码或浏览器持久令牌。表单必填和字段错误就地反馈；提交期间阻止重复操作；失败保留输入；取消不发写请求；输入按纯文本渲染，多行简介可正常提交。

使用打包后的JAR、真实隔离MySQL、现有认证API及真实headless浏览器进行检查。由于UI-01未授权，自动化通过现有登录API建立Pinia内存会话，再进入页面；未新增产品调试入口或登录页。这不代表完整正式登录页面流程通过。

| 浏览器 | 分辨率 | 已执行内容 |
| --- | --- | --- |
| Chrome158.0.8127.38 | 1440×1000、390×844 | 匿名守卫；真实ADMIN认证；商家创建/必填/编辑；启停取消与确认；多行简介及HTML纯文本；顾客启停及旧JWT 40301→40102；两页和窄屏表单 |
| Edge158.0.4370.33 | 1440×1000、390×844 | 匿名守卫与真实ADMIN认证；两管理页列表、表单打开/取消；用户启停取消；两页布局及桌面创建表单 |

截图人工检查未发现遮挡或文档横向溢出。窄屏表格在卡片内横向滚动，创建弹窗在视口内纵向滚动。十张PNG位于backend/target/phase4-browser/，检查摘要位于backend/target/phase4-evidence/browser-checks.json，均为可重建验证产物，不含真实用户资料。浏览器使用本轮临时合成账号，完成后精确清理。

本机8080端口已有其他服务，未停止或改动。验证JAR仅绑定127.0.0.1的临时空闲端口，使用TEMP中的Vite配置覆盖代理；项目vite.config.js及application配置保持原样。自持JAR、Vite和headless浏览器均已停止。

## 6 测试与构建

| 实际命令/检查 | 结果 |
| --- | --- |
| 指定JDK java -version、node --version、npm.cmd --version、mysql.exe --version | 版本与用户固定环境一致 |
| mvnw.cmd -B -Dtest=AdminRequestsTests test | 新增参数测试27项通过 |
| mvnw.cmd -B -Dtest=AdminRequestsTests,AdminHttpMySqlTests test，真实MySQL开关开启 | 新增65项通过，失败0/错误0/跳过0 |
| mvnw.cmd -B test，同一真实MySQL配置 | 完整363项通过，失败0/错误0/跳过0，25.935秒 |
| mvnw.cmd -B -DskipTests package | 打包成功，2.404秒；已在前一步完成全部测试，本命令明确跳过重复测试 |
| npm.cmd test | 6文件85项通过，原59项+新增26项 |
| npm.cmd run build | 成功；原有单chunk超过500kB提示保留，主JS约1.13MB/gzip375.70kB |
| JAR、Chrome/Edge、数据库起止审计 | 见第5、7节 |

新增后端38项真实MySQL/HTTP测试和27项参数测试。覆盖三角色六入口、匿名、敏感字段、精确公开投影、字面搜索、分页及overflow、三角色重复账号、并发唯一创建、创建/状态写入故障回滚、绑定和账号不变、同值幂等、不同旧JWT失效、非CUSTOMER40405、unsigned最大ID不存在路径、版本上限500安全回滚、并发禁用/退出、订单明细快照及库存保持。MySQL测试只按本轮准确ID清理；两个旧JWT使用不同iat并断言不同且事前有效。

前端新增26项覆盖真实应用组件与路由、服务器当前角色刷新、401/403、字段多消息、必填阻断、创建/状态防重复、失败保留输入、取消、编辑白名单、多行简介、纯文本XSS、筛选分页、旧列表延迟响应保护、退出成功/失败，以及原始JSON和URL中的unsigned大ID字符串。

验证辅助流程中曾出现Wrapper子进程缺少命令解析环境、旧数据库classpath未含BCrypt库、8080占用及浏览器助手超时。分别通过补齐验证子进程环境、引用已有crypto JAR、临时端口和重新建立隔离浏览器处理；超时后按已记录准确ID清理夹具再继续。没有为这些辅助问题修改业务实现、安装依赖、改变系统设置或停止他人进程。最终应用测试与验证均通过。

## 7 数据库、凭据与源文件保护

| 数据库 | 起止六表数据 | 起止规范化结构SHA256 |
| --- | --- | --- |
| cake_order_system | 六表均0行，数据摘要一致 | ee3bebe42cae1a8e7381932029b6f0e2e8cd272d644c4ff25f4d2cb290db5f87 |
| cake_order_system_phase2_test | 六表均0行，数据摘要一致 | 15cda01419f260c563f5f78386f371eed939283ce5ec01a2a5bacf5599fbbc75 |

两库数据摘要起止均为e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855。结构规范化仅忽略AUTO_INCREMENT当前数值，序列另行记录；测试可能推进自增值，不重置，不用事务回滚推断序列恢复。开发库没有写入；未执行DDL、init、正式seed、清表、关闭外键或无关清理。

起始96个手写文件；最终119个，新增23、修改7、原有89个SHA256一致、删除0。后端既有源码和全部原测试保持哈希不变。原DOCX、AGENTS、需求基线、架构、阶段计划、四份数据库文档、四份安全文档、历史报告、database目录、pom/Wrapper、应用配置、前端package/lock、Axios和认证Store/API保持原样。追踪矩阵仅调整历史状态提示并追加本轮实施记录，原文API/TC追踪保留。

原DOCX SHA256仍为522D3CB741550FD88D8B73365A245F2AEDF98AA6CF6B1AF2A5A7A34DF56025E5。本轮按实际数据库密码及临时密钥检查手写源码和验证文本，未发现持久化；不在报告重复秘密。运行日志只记录安全事件，不记录请求密码、完整JWT或JDBC密码。

可重建证据：backend/target/phase4-evidence/中的admin-tests.log、full-tests.log、package.log、database-before.log、database-metadata.log、database-after-tests.log、database-after.log、jar-stdout.log、jar-stderr.log、files-summary.json和browser-checks.json，以及target/surefire-reports/和target/phase4-browser/。辅助脚本只在本轮TEMP目录，不加入业务JAR。

## 8 变更文件

新增后端生产8文件：controller/admin/AdminController、dto/AdminRequests、entity/MerchantRecord、exception/AdminErrorCode、mapper/AdminMapper、service/admin/AdminService、vo/MerchantView、vo/PageView，均位于backend/src/main/java/com/cake/platform/。新增backend/src/test/java/com/cake/platform/admin/下AdminRequestsTests和AdminHttpMySqlTests。

前端新增11文件：src/api/admin.js及admin.test.js；src/admin/useAdminList.js、validation.js、management.test.js；src/components/admin/AdminPagination.vue、FieldErrors.vue、StatusConfirm.vue；src/layouts/AdminLayout.vue；src/views/admin/MerchantsView.vue、UsersView.vue。修改src/router/index.js、src/styles/base.css和src/application.test.js，均在本轮页面范围。

文档新增docs/admin/PLATFORM_MANAGEMENT.md及本报告；更新README.md、PROJECT_STATUS.md、TODO.md、docs/requirements/TRACEABILITY_MATRIX.md。完整新增/修改/保护清单见files-summary.json。

## 9 验收结论与后续边界

MERCH-01、USER-05、六接口及两管理页在本轮范围内通过实现、测试、构建、JAR和浏览器检查，等待用户审核。TC-39/40的本阶段权限、事务和页面操作已有证据；TC-29仅验证管理修改不回写既有历史，TC-35仅验证本轮平台权限/搜索/纯文本，TC-36仅验证两页相关浏览器部分。未将完整TC-29/35/36/37/45、全部46场景或全部17页标为通过，API-33/34和TC-41本轮未实现。

仍保留：UI-01正式登录页面和刷新重登流程未交付；正式ADMIN/MERCHANT参数化种子暂缓；生产TLS/密钥托管、Knife4j手工验收、未来下单/支付/履约消费店铺状态的联调未执行。商家账号禁用来源、支付超时/停店优先级、库存回补溢出、排行商家快照、密码Unicode长度口径等历史问题继续由TODO登记，不在本轮擅定。

**停止于Phase 4待审核。未经新授权，不进入Phase 5，不补做公开商家接口，不执行正式种子、Git初始化、提交或推送。**
