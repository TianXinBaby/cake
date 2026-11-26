# Phase 3 用户认证设计

日期：2026-11-25（Asia/Shanghai）。依据为根目录《蛋糕店订单管理系统需求说明书_V1.1.docx》的 USER-01～04、API-01/10～14、DATA-08/09、5.1 和 5.4，以及用户明确授权的 Phase 3 和 Phase 3.1 要求。原始需求文件、六表结构和 Phase 0～3 历史报告保持原样。本文描述当前代码的职责与契约；Phase 3 历史证据见 [Phase 3 报告](../../reports/PHASE_3_REPORT.md)，本次补充实施的实际数量、结果及未执行项以 [Phase 3.1 报告](../../reports/PHASE_3_1_REPORT.md) 为准。

## 范围与已有成果

沿用 Spring Boot 3.5.16、JDK 18.0.2.1、Maven Wrapper、MyBatis-Plus 3.5.18、Connector/J 8.4.0、MySQL 8.0.19 和现有 Vue/Pinia/Axios 工程。仅增加用户认证、本人资料和前端认证基础能力；不创建登录、注册、个人中心页面，不进入 Phase 4，不添加数据库表、字段、索引或 CHECK。

原有统一响应仍为 `code/msg/data`。成功业务码始终为 200；注册的 HTTP 状态为 201，其余认证成功为 HTTP 200。Phase 3 曾因 `data.fieldErrors` 公共结构待确认而为 **PARTIAL**。用户已在 Phase 3.1 正式确认字段名到字符串数组的对象结构；本轮实施该契约，最终验收仍以 Phase 3.1 报告中的实际验证结果为准，完成后状态为待审核。

## Phase 3.1 参数错误契约

能够定位真实请求字段的参数错误统一为 HTTP 400、业务码 40001 和“参数错误”：

```json
{"code":40001,"msg":"参数错误","data":{"fieldErrors":{"phone":["手机号格式不正确"],"nickname":["昵称不能为空","昵称长度不能超过30个字符"]}}}
```

`fieldErrors` 为对象，对应 Java 类型 `Map<String, List<String>>`；每个值始终为字符串数组，一次收集多个字段及同字段的多条消息，不用后来的消息覆盖先前消息。key 使用 API 实际公开的 lowerCamelCase 字段名，包括 `oldPassword/newPassword`，不暴露 Java 内部字段名或数据库 snake_case 名。Bean Validation 的嵌套路径按 Jackson 对外 JSON 属性映射，各级属性使用实际公开字段名。

JSON 语法错误、缺少或非对象请求体、对象级错误等无法定位真实字段的参数错误继续为 `{"code":40001,"msg":"参数错误","data":null}`。未知字段本身不是公开 API 字段；只有未知字段、没有可定位字段错误时也返回 `data=null`。若同次请求存在可定位字段错误，则仅收集真实字段，不给未知字段或全局错误合成位置。禁止 `_global` 或其他虚拟字段。

受保护字段 `role/status/merchantId` 及既有逐接口保护字段仍优先返回 HTTP 403/40302，不转换为参数错误。40101、40102、40301、42301、50001 及其他已有业务异常的 HTTP 状态、业务码和结构保持原约定；系统异常仍只给安全提示及 requestId，不返回堆栈、SQL 或数据库信息。

## 代码职责

| 位置 | 职责 |
| --- | --- |
| `controller/auth/AuthController` | 注册、登录、退出；逐接口输入白名单，不直接绑定数据库记录 |
| `controller/user/UserController` | 从认证上下文确定本人，读取资料、改昵称、改密码 |
| `dto/AuthRequests` | 字段分类、字符串类型、手机号、昵称、密码格式校验 |
| `exception/ParameterErrorData`、`FieldValidationException`、`ValidationErrorExtractor` | 真实字段错误聚合、字符串数组契约、JSON 公开字段名映射和安全消息转换 |
| `service/auth/AuthenticationService` | BCrypt 验证、事务、失败锁定、令牌版本、数据库当前身份与绑定校验 |
| `mapper/UserAuthMapper` | 仅访问现有 sys_user/cake_merchant；参数绑定、行锁及数据库当前时间 |
| `entity/UserAccount` | 内部账号记录，包含安全控制字段，不直接返回给客户端 |
| `vo/UserView` | 公开 User 白名单；所有 ID 转为正十进制字符串 |
| `security` / `config/AuthenticationConfiguration` | JWT、SecurityContext、无状态过滤链和统一安全错误 |

## 六个接口

| 编号与接口 | 请求 | 成功 `data` | 主要异常 |
| --- | --- | --- | --- |
| API-10 `POST /api/auth/register` | `phone/nickname/password`，匿名 | HTTP 201，User，不自动登录 | 40001、40302、40901 |
| API-01 `POST /api/auth/login` | `username/password`，匿名 | `token/tokenType/expiresAt/user` | 40001、40101、40301、40302、42301 |
| API-11 `POST /api/auth/logout` | 已登录，无请求体和 Query 参数 | `null` | 40102；账号禁用时 40301 |
| API-12 `GET /api/users/me` | 已登录，无参数，不接收用户 ID | 当前账号 User | 40102、40301；非法输入 40001/40302 |
| API-13 `PATCH /api/users/me` | 已登录，只有 `nickname` | 更新后的 User | 40001、40302；认证类错误 |
| API-14 `PATCH /api/users/me/password` | 已登录，`oldPassword/newPassword` | `null`，不签发新 JWT | 40101、40001；认证类错误 |

User 为 `id/username/nickname/phone/role/status/createTime`；MERCHANT 另含只读 `merchantId`，其他角色不包含该字段。不返回密码散列、tokenVersion、失败次数、锁定控制字段或完整内部记录。时间响应使用带 `+08:00` 的 ISO 8601 毫秒字符串，例如 `2026-11-25T19:00:00.000+08:00`。

## 注册与输入保护

手机号必须匹配 `^1[3-9][0-9]{9}$`。昵称去首尾空白后为 1～30 个字符。密码为 6～20 位可见非空白字符，具体实现和 Unicode 长度边界见 [密码安全](PASSWORD_SECURITY.md)。

后端固定 `username=phone`、`role=CUSTOMER`、`status=1`、`token_version=0`、`login_fail_count=0`、`locked_until=NULL`。`create_time/update_time` 使用数据库默认 `CURRENT_TIMESTAMP(3)`。插入后读取公开 User；BCrypt 散列只进入数据库，不签发令牌。

注册拒绝 `role/status/merchantId/tokenVersion`；本人资料拒绝修改账号、手机号、角色、状态、绑定及用户 ID 等只读字段。普通未知字段和 Query 参数为 40001，受保护字段为 40302。请求体采用逐接口白名单；同时存在受保护字段和普通未知字段时，优先拒绝受保护字段，不依赖 JSON 字段顺序。唯一索引是重复账号/手机号的最终约束，冲突统一转为 40901，不向客户端显示数据库信息。

## 登录与失败锁定

登录在数据库事务中按 username 锁定账号行；禁用账号返回 40301。不存在账号执行一次虚拟 BCrypt 校验后返回与错误密码相同的 40101 和“账号或密码错误”，不区分账号是否存在。临时锁定不等于账号禁用。

以数据库 `CURRENT_TIMESTAMP(3)` 判定锁定时间：锁定期间返回 42301 和 `data.lockedUntil`；到期后下一次尝试开始新的失败计数周期。第 1～4 次密码失败写入相应次数并返回 40101；第 5 次写入 5、锁定至数据库当前时间加十分钟并返回 42301。成功清零次数、清空锁定时间后签发 JWT。

失败结果先正常退出事务使计数和锁定提交，再向调用方抛出对应业务异常。不能把失败异常直接抛出事务而回滚失败计数。相同账号行锁串行处理登录、退出和密码变更，不同账号各自计数。数据库错误或死锁按 50001 安全返回，不伪装成功。

## 本人资料、退出与修改密码

三个角色均可访问本人接口。本人 ID 从服务端认证上下文获取，不采信路径、Query 或请求体中的其他账号 ID；昵称更新不修改账号、手机号、角色、绑定和历史订单快照。

写操作进入事务后重新锁定本人账号，检查启用状态和当前 token_version，再执行修改。退出通过 SQL `token_version=token_version+1` 使该账号全部已有 JWT 失效，不删除账号，不新增令牌表。修改密码在同一事务中完成旧密码验证、新旧密码不同校验、新 BCrypt 散列和版本原子加一；成功返回 `data=null`，之后必须重新登录。

## 三角色和商家绑定

ADMIN、MERCHANT、CUSTOMER 分别映射为 `ROLE_ADMIN/ROLE_MERCHANT/ROLE_CUSTOMER`。数据库当前角色为权限依据；JWT 的角色声明和前端展示均不能覆盖它。SecurityContext 的身份包含 `userId/role/tokenVersion`，MERCHANT 额外包含从 `cake_merchant.user_id` 关联查询取得的 `merchantId`。

MERCHANT 必须存在且只存在一条店铺绑定；非 MERCHANT 不应存在商家绑定。违反 DATA-09 时记录安全的数据完整性事件，按 50001 返回 requestId，不签发正常商家身份令牌，也不自动修复历史数据。店铺 status=0 不等于账号禁用，启用商家仍可登录，后续处理已支付订单；本阶段不实现经营接口。

## 前端基础集成

复用现有单例 Axios 和 Pinia，API 方法为 `login/register/logout/getCurrentUser/updateNickname/updatePassword`。成功响应继续返回原始 data；失败响应由既有 `RequestError.data` 原样透传 `data.fieldErrors`，包含多个字段及同字段多消息。现有 Axios 核心已兼容确认结构，本轮保留其实现、统一提示和认证失效行为，仅补充兼容回归测试，不创建表单页面。

Phase 0 未明确 localStorage/sessionStorage 持久化方案，本阶段采用 **仅内存保存**：Pinia 管理 `token/tokenType/expiresAt/user` 和 `isAuthenticated`；不引入额外存储框架，不写 localStorage、sessionStorage、Cookie，不保存密码。页面刷新后需要重新登录；这是本阶段基础状态能力的边界，未创建登录页或新增浏览器路由。

受保护请求统一注入 Bearer；登录、注册不注入旧令牌。40102 清理对应的认证状态；退出成功、退出收到 401 和修改密码成功均清理。异步请求保存发出时的令牌快照，旧请求延迟响应不会清除或覆盖后来建立的新会话。客户端角色仅作显示，最终校验始终由后端执行。

## 初始化和验证边界

保留 Phase 2 参数化初始化器；当前没有完整安全参数时不创建未知凭据的 ADMIN/MERCHANT，不写固定弱密码，不编造真实手机号，不为登录测试改写开发库历史数据。自动化夹具仅写独立测试库，结束清理。

对应测试、隔离方式、复现命令及未完成项见 [认证 API 测试](AUTH_API_TESTS.md)。文档设计、普通单测、真实 MySQL 并发/回滚及用户审核是不同证据，不互相替代。本轮完成后停止于 Phase 3.1，等待审核，不自动进入 Phase 4。
