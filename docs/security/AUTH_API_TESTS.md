# Phase 3 认证 API 测试说明

日期：2026-11-25（Asia/Shanghai）。范围为 USER-01～04、API-01/10～14，以及认证所需的输入保护、角色识别、DATA-09 绑定、并发和事务边界；Phase 3.1 补充已确认的参数错误公共契约。本文的用例矩阵描述检查目标；**设计条目不等于已执行通过**。Phase 3 历史执行证据见 [Phase 3 报告](../../reports/PHASE_3_REPORT.md)，本轮实际命令、套件数量、通过/失败/错误/跳过和构建结果单独记入 [Phase 3.1 报告](../../reports/PHASE_3_1_REPORT.md)。

## 请求与响应契约示例

下列手机号、昵称和时间只用于说明结构，不代表本机真实账号、已执行种子或可用凭据。密码在本机测试进程提供，不把真实应用密码放入文档、命令历史或日志。

注册请求 `POST /api/auth/register`：

```json
{"phone":"13800138000","nickname":"用户昵称","password":"示例密码仅展示结构"}
```

成功为 HTTP 201，响应业务 code 仍为 200，不含令牌：

```json
{"code":200,"msg":"success","data":{"id":"1","username":"13800138000","nickname":"用户昵称","phone":"13800138000","role":"CUSTOMER","status":1,"createTime":"2026-11-25T19:00:00.000+08:00"}}
```

登录请求 `POST /api/auth/login` 只含 username/password。成功 HTTP 200：

```json
{"code":200,"msg":"success","data":{"token":"<服务端签发的JWT>","tokenType":"Bearer","expiresAt":"2026-11-25T21:00:00.000+08:00","user":{"id":"1","username":"13800138000","nickname":"用户昵称","phone":"13800138000","role":"CUSTOMER","status":1,"createTime":"2026-11-25T19:00:00.000+08:00"}}}
```

保护请求携带 `Authorization: Bearer <token>`。`GET /api/users/me` 不接收参数；`PATCH /api/users/me` 只含 `{"nickname":"新昵称"}`；`PATCH /api/users/me/password` 只含 oldPassword/newPassword；`POST /api/auth/logout` 无请求体。退出与改密成功均为 HTTP 200、`{"code":200,"msg":"success","data":null}`，不签发替代令牌。

错误沿用 `code/msg/data`：40101 账号或密码错误；40102 登录已失效；40301 账号禁用；40302 角色不足/受保护字段；40901 重复账号；42301 临时锁定且 `data.lockedUntil` 为毫秒 +08:00；50001 固定安全提示及 requestId。Phase 3.1 不改变这些业务码、HTTP 状态及认证/业务异常结构。

已确认的普通参数错误为 HTTP 400、code=40001，能够定位真实字段时返回：

```json
{"code":40001,"msg":"参数错误","data":{"fieldErrors":{"phone":["手机号格式不正确"],"nickname":["昵称不能为空","昵称长度不能超过30个字符"]}}}
```

`data.fieldErrors` 必须为对象；key 为实际 API 的 lowerCamelCase 字段名，值为字符串数组，允许多个字段及同字段多消息。嵌套校验按 JSON 公开字段名映射，不暴露内部变量名或数据库列名。不能定位真实字段、只有未知字段、对象级或 JSON 语法错误等继续返回 `{"code":40001,"msg":"参数错误","data":null}`；禁止 `_global` 或虚拟字段。存在可定位错误时只收集真实字段，未知字段不进入 `fieldErrors`。`role/status/merchantId` 等受保护字段仍优先返回 HTTP 403/40302。

## 覆盖矩阵

| 分组 | 必测行为 | 证据类型 |
| --- | --- | --- |
| 注册正向 | username=phone、CUSTOMER/启用/版本0/失败0/NULL锁定、数据库时间、真实 BCrypt、HTTP201、无 JWT | API 与数据库查询 |
| 注册拒绝 | 重复手机号/账号、手机号格式、昵称空或超长、密码过短/过长/空白、role/status/merchantId/tokenVersion、普通未知字段 | HTTP/业务码及无写入断言 |
| 注册并发 | 同号并发仅一条 CUSTOMER 成功，唯一冲突40901 | 真实 InnoDB 事务/唯一键 |
| 登录 | 正确密码、错误密码、不存在账号、禁用账号、输出字段白名单、JWT签名/精确两小时 | API、JWT解码、数据库 |
| 失败周期 | 前四次40101，第五次42301/10分钟，期间不增加，到期新周期，成功清零 | 数据库时间与控制字段 |
| 登录并发 | 同号失败不丢计数/不超过5，异号互不影响 | 多线程真实MySQL |
| JWT异常 | 缺失/格式/重复头/签名篡改/过期/非法claim/版本失效/用户不存在 | 保护接口与错误码 |
| 当前权限 | 三角色上下文、数据库当前角色优先、禁用立即生效、角色入口限制 | SecurityContext/接口 |
| 商家绑定 | 正确merchantId、MERCHANT无店、非MERCHANT绑定、绑定移除 | DATA09异常及无正常令牌 |
| 本人资料 | 三角色只返回本人，Query不可指定他人，公开字段屏蔽内部记录 | API与字段集合 |
| 昵称 | 去首尾空白、合法更新、空/超长拒绝、只读/受保护字段拒绝、其他账号不变 | API与前后数据库快照 |
| 退出 | 版本加一、多个旧JWT均失效、再次请求40102、无删用户/新JWT | API与数据库 |
| 改密 | 旧密码错误、新密码格式、相同密码、真实新BCrypt、版本加一、全部旧JWT失效、无新JWT | API与数据库 |
| 改密事务/并发 | 密码与版本回滚完整，同旧身份并发只允许有效转换 | 故障注入与真实事务 |
| 前端 | Store保存/清理、Bearer注入、40102、退出401、改密后清理、旧响应不覆盖新会话、无浏览器持久化 | Vitest与Axios适配器 |
| Phase 3.1 参数契约 | 单字段、多字段、同字段多消息、字符串数组、lowerCamelCase/JSON映射、无法定位时null、无_global、40302优先、成功及其他错误不变、系统信息不泄露 | 后端异常转换及认证输入回归 |
| Phase 3.1 前端兼容 | 2xx业务失败及HTTP400透传fieldErrors、数组不丢弃、40001保留会话、40102清理、无字段仍null | 原Axios实现与Vitest适配器 |
| 原有回归 | Phase1原25项及Phase2新增4基础+28真实项保留；前端原28项保留 | 完整套件及XML |

Phase 2 的后端 **57 项总数已包含 Phase 1 的 25 项**，不能计算为 25+57。参数化展开后的数量和新增套件数量以实际 Surefire/Vitest 汇总为准，不把同一测试重复运行累计为更多不同用例。

## 真实 MySQL 隔离

优先复用已初始化的 `cake_order_system_phase2_test`，开发库仍为 `cake_order_system`。显式设置 `CAKE_MYSQL_TEST_ENABLED=true` 才执行真实套件；DB_TEST_NAME 必须以 `_test` 结尾且不同于 DB_NAME。写夹具前应验证实际 `SELECT DATABASE()` 等于测试库、版本为 MySQL 8.0.x（Phase 5 起断言已放宽，历史基线 8.0.19），并核对既有六表。

认证请求和并发服务事务可能提交，不能依靠外层测试 `@Transactional` 自动清理。测试生成专属合成账号和真实 BCrypt，跟踪注册手机号及生成 ID；结束时按依赖关系只删除本套件创建的店铺/账号，最后比较六表计数。原有数据库完整性套件继续事务回滚。任何测试失败均须先检查本套件清理结果，不对开发库试错，不清空已有数据，不关闭外键检查。

本阶段不执行 init、seed、ALTER、DROP、TRUNCATE 或新增触发器；回滚测试使用测试专属事务/Mapper故障注入，不通过修改已交付 CHECK 模拟失败。自增分配不随回滚恢复，测试库可能存在 ID 空号，不能把空号说成数据残留或通过修改 AUTO_INCREMENT 掩盖它。

## 本机复现命令

以下是复现操作说明，是否本轮已执行须查看报告。沿用当前 PowerShell 和指定 JDK，不修改系统环境、ExecutionPolicy 或依赖版本。

普通后端回归与构建，在 backend 目录：

```powershell
Set-Location -LiteralPath 'D:\study\蛋糕店订单管理系统\backend'
$env:JAVA_HOME = 'D:\study\jdk18'
.\mvnw.cmd -B test
.\mvnw.cmd -B package
```

未启用真实开关时，数据库套件显示 skip，不能称为 MySQL/事务/并发通过。真实测试应在本机隐藏交互注入已授权凭据，环境只在当前进程和子进程存活：

```powershell
$env:DB_NAME = 'cake_order_system'
$env:DB_TEST_NAME = 'cake_order_system_phase2_test'
$env:DB_USERNAME = Read-Host '本地数据库用户名'
$taskPassword = Read-Host '本地数据库密码' -AsSecureString
$env:DB_PASSWORD = [Net.NetworkCredential]::new('', $taskPassword).Password
$env:CAKE_MYSQL_TEST_ENABLED = 'true'
$taskKeyBytes = [byte[]]::new(32)
$taskRng = [Security.Cryptography.RandomNumberGenerator]::Create()
try { $taskRng.GetBytes($taskKeyBytes) }
finally { $taskRng.Dispose() }
$env:JWT_SECRET_BASE64 = [Convert]::ToBase64String($taskKeyBytes)
try { .\mvnw.cmd -B test }
finally {
    Remove-Item Env:DB_PASSWORD,Env:CAKE_MYSQL_TEST_ENABLED,Env:JWT_SECRET_BASE64 -ErrorAction SilentlyContinue
    Remove-Variable taskPassword -ErrorAction SilentlyContinue
    [Array]::Clear($taskKeyBytes, 0, $taskKeyBytes.Length)
    Remove-Variable taskKeyBytes,taskRng -ErrorAction SilentlyContinue
}
```

真实测试签名密钥在测试进程运行期生成，不需要把 JWT 密钥写入测试 yml。生产/dev 启动仍须通过安全外置配置提供 JWT_SECRET_BASE64；普通数据库套件如随完整应用加载认证配置，应使用测试专属临时随机密钥，不引入固定默认。

前端在已有依赖的 frontend 目录执行，不重复安装：

```powershell
Set-Location -LiteralPath 'D:\study\蛋糕店订单管理系统\frontend'
npm.cmd run test
npm.cmd run build
```

## 实际结果与待审核

**Phase 3 历史证据：**当时真实 MySQL 开启的完整后端 package 为255项通过、失败0、错误0、跳过0；其中原57项、新增198项（认证HTTP101、输入55、过滤器17、JWT25）。当时前端54项通过，包含原28与新增26，后端package和前端build均成功。这些数字仅描述 Phase 3 报告中的历史运行，不代表本轮已经重新执行。旧数据库测试ID/绑定与方法权限错误处理的失败修复、JAR运行、数据库清理及保护证据见该历史报告，不把多轮运行累计为不同用例。

**Phase 3.1 本轮证据：**已启用真实MySQL套件执行 `mvnw.cmd -B test` 和 `mvnw.cmd -B package`，最终均298项通过、失败0、错误0、跳过0，包含原255项及新增43项；原25项与Phase2累计57项包含在内，不重复相加。新增为公共契约17项、AuthRequests增加20项、认证HTTP增加6项。JAR实际启动、11项HTTP检查通过，验证进程已停止，两库结构与数据起止摘要一致。已执行 `npm.cmd test`，4个文件共59项通过，包含原54项及新增5项；`npm.cmd run build`成功，仍有超过500 kB的chunk体积提示。Axios核心实现保持原样，原字符串错误示例改为字符串数组。完整明细及生成物位置见Phase3.1报告。

Phase 1 原25项、Phase 2 原57项及 Phase 3 完整前后端测试要求均保留，不删除、跳过或降低验收要求。fieldErrors契约已经确认、实施并完成本轮必需回归，阶段状态为待审核。密码 Unicode 长度口径见 [密码安全](PASSWORD_SECURITY.md)。参数化初始化账号仍待外部安全资料，不因测试夹具可登录而宣称真实管理员或演示商家已建立。当前不进行 UI 页面/真实浏览器业务验收、生产部署或后续阶段业务测试。完成 Phase 3.1 后立即停止，等待用户审核，不进入 Phase 4。
