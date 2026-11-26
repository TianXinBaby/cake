# Phase 3.1 统一参数错误响应契约提案

日期：2026-11-25（Asia/Shanghai）。项目：基于 Spring Boot + Vue 的蛋糕店订单管理系统。

**状态：方案已提出，待用户确认，未实施。** 本轮只新增本文件，未修改源码、数据库、SQL、依赖、需求原件、README、PROJECT_STATUS、TODO或历史报告，未增加接口，未执行Git初始化、提交或推送，不进入Phase4。Phase3仍保持PARTIAL，不能将本提案当作fieldErrors已交付或阶段已通过。

## 一 本轮结论

推荐在**能可靠定位请求字段的HTTP400／40001参数错误**中采用以下公共结构：

```json
{
  "code": 40001,
  "msg": "参数错误",
  "data": {
    "fieldErrors": {
      "phone": ["手机号格式不正确"],
      "nickname": ["昵称不能为空"]
    }
  }
}
```

建议类型为 `Map<String,List<String>>`，即“公开请求字段路径 → 非空错误消息数组”。顶层继续只有code、msg、data，不新增业务码、接口或数据库字段。该结构能复用现有内部错误提取器，避免丢失同一字段的多条消息。

对于JSON语法错误、整个请求体缺失、无法可靠定位字段的参数错误，**推荐继续data=null与原通用提示**；不把Java方法名、解析器位置或虚拟字段当成表单字段。不新增 `_global`、`globalErrors` 或第二种字段消息格式。此项属于待确认的兜底设计，需求书没有明确规定。

以上所有JSON均为**拟议响应示例**。当前后端参数错误仍是HTTP400／40001、data=null，本轮没有改变任何实际响应。

## 二 原始需求与设计建议的边界

直接读取原始V1.1 DOCX的相关正文和表格，依据如下。B编号按word/document.xml正文直接子块的零起始索引定位，便于复查，不冒充Word页码。

| 来源 | 原始规定 | 未规定的细节 |
| --- | --- | --- |
| 第3.7节，B097参数40001行 | 必填缺失、格式错误、金额或数量越界，返回fieldErrors定位字段；不写入数据 | fieldErrors是对象还是数组、每字段一条还是多条消息 |
| 第5.1节，B139/B141 | JSON字段lowerCamelCase；失败保持code/msg/data；失败data可含fieldErrors、requestId或当前状态，不返回内部异常 | 字段路径语法、消息排序、无法定位时的data形状 |
| 第5.1节，B149 | 普通未知参数或字段返回400；非法role、status等受保护字段按权限错误拒绝 | 未知字段在fieldErrors中的键和消息格式 |
| 第6.2节，B435 | 字段错误就地提示，后端fieldErrors映射回对应表单 | 具体前端映射工具、一次展示几条消息、全局提示与字段提示的配合 |

**需求明确要求的是字段定位及表单映射；本提案推荐的Map、字符串数组、路径语法、无字段时data=null、去重规则和提示文案均是设计建议，尚未获确认。** 不将历史内部实现或测试夹具当成用户已批准的公共契约。

## 三 当前实现分析

| 检查对象 | 当前事实 | 对实施的影响 |
| --- | --- | --- |
| `backend/.../common/ApiResponse.java` | 泛型record，只定义code/msg/data，data已能承载任意类型 | 无需修改顶层对象 |
| `backend/.../exception/GlobalExceptionHandler.java` | Bean/方法输入/ConstraintViolation/JSON及其他400统一返回40001、data=null；业务异常只保留错误描述 | 需要在现有处理器中集中填充可定位的字段信息 |
| `backend/.../exception/ValidationErrorExtractor.java` | 已内部返回不可变Map<String,List<String>>，同字段多条消息，不带rejectedValue；现在主要用于统计字段数 | 可以复用字段聚合模型；尚非公共输出；全局ObjectError目前未处理 |
| `backend/.../exception/BusinessException.java` | 仅携带ErrorCode，不携带字段位置 | 不能仅靠40001猜出是phone、password还是newPassword |
| `backend/.../dto/AuthRequests.java` | JsonNode白名单与手工校验；invalid()抛普通BusinessException，字段信息丢失；通常遇首个错误即停止 | 认证路径也必须改为带字段错误并支持多字段聚合，单改Bean Validation处理器不够 |
| AuthController/UserController | 直接绑定JsonNode，并有password/oldPassword及无参数/无请求体检查 | 需要传递真实公开字段名，保留现有规则和检查顺序 |
| AuthenticationService | 新旧密码相同抛40001，没有字段；原密码错误是40101 | 前者可明确定位newPassword；后者必须保留认证错误行为 |
| `frontend/src/api/http.js` | RequestError保留code、status、message及原始data；两种错误分支均透传，统一提示一次 | 已能接收推荐结构，最小方案不必修改Axios源码 |
| 前端auth/users API、Pinia | API不改造错误；Store只管理认证状态 | 无需把表单错误写进认证状态 |
| 当前Vue页面 | App是RouterView，Router只有技术空出口，没有实际登录/注册/个人中心表单 | 本轮不应创建页面来演示字段错误；真实UI映射须留待获授权的页面阶段 |

Phase1原测试中，`ValidationErrorExtractorTests`已证明同字段多消息、不可变和不含rejected secret。`GlobalExceptionHandlerTests`仍有参数错误data为空的断言。Phase3的`AuthHttpMySqlTests`对普通未知字段明确断言data=null，后续必须精确更新该参数错误预期。

前端`http.test.js`使用过 `fieldErrors: {phone: "格式错误"}`。该测试只断言data对象原样透传，没有解析消息或绑定表单，**不能据此认定Map<String,String>已获批准**。

## 四 结构方案比较

| 候选 | 优点 | 成本与风险 | 建议 |
| --- | --- | --- | --- |
| 字段→单字符串 | JSON最短，单条表单提示直接取用 | 与内部多消息模型不同，需要丢弃、拼接或选择消息，产生额外规则 | 不作为首选 |
| 字段→字符串数组 | 直接沿用内部模型，保留同字段多条消息，按字段查询简单 | 前端显示时需取一条或逐条展示 | **推荐，待确认** |
| `[{field,message}]` | 便于顺序遍历、允许重复字段 | 新增元素字段约定，表单绑定需再次归组；当前无此公共实现 | 不作为首选 |

不引入字段级错误码、Java约束类型、rejectedValue、额外timestamp或新的顶层字段。此处的“最小”指复用有效实现并减少转换，不代表结构已实施。

## 五 推荐公共契约及JSON示例

### 5.1 适用范围

建议仅对HTTP400且业务码40001的**请求参数错误**提供fieldErrors。可以可靠定位时，data为只含fieldErrors的对象；fieldErrors为非空对象，键为字段路径，值为非空字符串数组，消息不得为null、空白或内部诊断。

同一字段多条不同消息可以保留；建议去重。字段对象键的顺序没有业务意义，前端不得依赖JSON键顺序。消息是给人看的提示，不是供程序解析的错误码。优先抑制同一根因的重复错误，例如字段缺失时不再同时报告长度和格式。

### 5.2 单字段错误

```json
{
  "code": 40001,
  "msg": "参数错误",
  "data": {
    "fieldErrors": {
      "nickname": ["昵称不能为空"]
    }
  }
}
```

### 5.3 多字段同时失败

拟议注册参数检查应在没有受保护字段且JSON能成功解析的前提下，收集独立的phone、nickname、password错误，再统一拒绝，不进入写库或签发令牌流程。

```json
{
  "code": 40001,
  "msg": "参数错误",
  "data": {
    "fieldErrors": {
      "phone": ["手机号格式不正确"],
      "nickname": ["昵称不能为空"],
      "password": ["密码长度不符合要求"]
    }
  }
}
```

### 5.4 一个字段存在多条独立错误

```json
{
  "code": 40001,
  "msg": "参数错误",
  "data": {
    "fieldErrors": {
      "password": ["密码长度不符合要求", "密码不能包含空白字符"]
    }
  }
}
```

数组使多条独立消息有明确表达，不要求所有校验都产生多条消息。建议使用明确的校验顺序并保留数组顺序，但不承诺Validation引擎的默认遍历顺序。当前界面未来可先显示第一条；服务端不为此丢掉其他消息。

### 5.5 无法定位字段的请求错误

例如整个JSON无法解析，或者旧的code-only BusinessException没有字段信息，推荐保留：

```json
{
  "code": 40001,
  "msg": "参数错误",
  "data": null
}
```

这是一条明确的兜底分支：**可定位则提供统一Map，不能定位则不伪造位置**。如果ObjectError包含跨字段规则但没有明确字段来源，不猜测其属于某个输入框；已有可信字段错误可继续返回，没有任何可信字段时使用本分支。

原文没有要求每个40001的data必须为对象。若用户希望所有40001都强制包含fieldErrors，需要另外确认使用空对象还是 `_global` 等保留键。空对象不能定位字段；保留键需要前端增加请求级提示约定。两者均不是原始需求，本提案推荐保留null以减少额外约定。

## 六 字段命名及错误归集规则

### 6.1 公开字段名称

字段键使用当前接口公开JSON/Query字段名，不使用Java类名、方法名、参数索引arg0、数据库列名或内部前缀。已知字段遵循lowerCamelCase。

| 场景 | 建议字段键 | 注意事项 |
| --- | --- | --- |
| 注册手机号 | phone | 不返回用户输入的手机号值 |
| 登录账号格式 | username | 虽复用手机号规则，也不能误标phone |
| 注册/登录密码格式 | password | 不返回原始密码 |
| 修改昵称 | nickname | 不改变既有去空白和长度规则 |
| 旧密码缺失或类型/长度错误 | oldPassword | 旧密码校验不匹配仍为40101，不增加fieldErrors |
| 新密码格式或新旧相同 | newPassword | 不返回两次输入内容 |
| 公开Query参数格式错误 | 公开参数名，如count | count仅为现有测试夹具示例，不新增业务Query |
| 嵌套字段/数组 | 如items[0].quantity | 建议点号+零起始下标；当前无订单实现，仅预留路径约定 |

不增加 `body.`、`query.` 前缀。当前六接口没有同名且歧义的多来源参数；将来出现这种接口时须单独评审路径映射，不提前设计新业务。

### 6.2 多字段聚合及权限优先级

建议在同一成功解析的输入集合内，先保留当前受保护字段检查，再收集普通未知字段和合法字段的必填、类型、格式错误。必填/类型错误应短路该字段的后续格式检查，但不阻断其他独立字段的检查。

原本的认证过滤器、权限检查及各接口检查顺序保持；不为收集更多字段信息而改变401/403的优先级，也不提前验证真实账号密码或执行BCrypt/写库。无法解析JSON时不得通过搜索原始文本中的role字样改判权限错误。

### 6.3 普通未知字段

原文已规定普通未知字段返回400；建议在其名称能安全表示时，返回原输入字段名和固定文案：

```json
{
  "code": 40001,
  "msg": "参数错误",
  "data": {
    "fieldErrors": {
      "unexpected": ["不支持的字段"]
    }
  }
}
```

不静默忽略或赋值未知字段。未知名称不是已知公开字段，不能自动把token_version转成tokenVersion或借此改变现有错误分类。注册中role/status/merchantId/tokenVersion仍是40302；注册中其他普通未知字段保持原40001分类。

**建议的输出安全约定，待确认：**只在未知名称为简单标识符 `[A-Za-z_][A-Za-z0-9_]*`、不超过128字符且不是 `__proto__`、`prototype`、`constructor` 时回显该名称。包含点号/方括号、控制字符、超长或无法无歧义表示的未知名称，不回显，采用第五节无字段兜底；若同时有其他可信字段错误，则只输出可信部分。此限制仅控制错误位置输出，不使未知输入被接受，也不改变40001。

### 6.4 受保护字段

受保护字段仍使用现有HTTP403／40302、“无权执行此操作”和现有data行为，不填充fieldErrors，不把权限错误改成40001。字段错误契约不改变各接口已存在的保护字段集合。

## 七 后端异常转换方案

建议增加一个固定40001的字段参数异常及一个data对象模型。异常可继承现有BusinessException，携带不可变的字段→消息数组；不修改基础BusinessException，使认证/权限/业务异常不会顺带获得任意响应数据能力。ApiResponse无需改动。

| 异常/校验来源 | 后续拟议转换 | 必须保留的边界 |
| --- | --- | --- |
| MethodArgumentNotValidException | 从BindingResult提取公开字段，组成data.fieldErrors | 不返回rejectedValue或绑定原始异常 |
| HandlerMethodValidationException输入 | 根据公开参数名/对象字段映射 | `isForReturnValue()`仍为50001及requestId |
| ConstraintViolationException输入 | 从结构化节点映射公开字段 | 不直接输出含方法名的propertyPath；空集合/不可信来源采用无字段兜底 |
| JSON值类型不匹配/映射失败 | 仅从结构化引用路径提取可信字段 | 不返回Java目标类型或原始pathReference文本 |
| JSON未知普通属性 | 安全名称对应“不支持的字段” | 适用第六节未知字段规则 |
| 缺失Query参数/Query类型不匹配 | 定位公开参数名 | 不回显非法参数值 |
| JSON语法错误/整体请求体缺失/非对象 | data=null通用参数错误 | 不伪造body字段，不返回行列/解析文本 |
| 接口不接受参数或请求体 | 能安全指出违规名称则定位，否则data=null | 仍保留受保护字段403分类 |
| AuthRequests手动校验 | 抛带字段位置的参数异常，并按请求聚合 | 保留现有规则，不能只改全局处理器 |
| 明确字段的业务40001 | 如新旧密码相同，标记newPassword | 不改变旧密码错误40101 |
| 普通BusinessException仅有40001 | 无字段信息则data=null | 不从错误码或msg猜字段 |
| ObjectError/其他无法定位的400 | 保留通用参数提示；有可信字段时可同时返回该部分 | 不凭空增加类名或虚拟字段 |
| 服务器返回值校验/未知系统异常 | 按既有系统错误原则50001及requestId | 不冒充用户输入字段错误；未涉及的分类不扩大改动 |

不能直接将任意 `exception.getMessage()`、Jackson解析信息、Binding类型转换默认文案或自定义模板原样输出。即便已有提取器不携带rejectedValue，message本身也可能通过模板或默认绑定信息泄露输入。建议使用代码可审查的固定/受控文案，未知约束安全回退“参数错误”，不包含Java包名、异常栈、SQL、密码、JWT或validatedValue。

对未来发生的服务端ConstraintViolation，需先确认其是输入还是返回值来源；不得将返回值失败字段化，或为本次响应形状改动擅自重分配现有业务错误码。

## 八 Axios接收与未来表单映射

当前Axios已经在业务错误和非2xx HTTP错误中构造RequestError，并保留 `error.data`。批准后的接收位置是 `error.data.fieldErrors`，不是 `error.response.data.fieldErrors`，也不是响应顶层fieldErrors。API模块不吞掉该异常。

以下仅为报告中的接收示意，不写入项目源码：

```javascript
try {
  await register(input)
} catch (error) {
  const fieldErrors = error.code === 40001 ? error.data?.fieldErrors : undefined
  // 未来实际表单只映射自身白名单字段；没有有效字段则沿用统一提示。
}
```

最小实施建议：**不修改frontend/src/api/http.js、auth.js、users.js和Pinia源码，仅补充Axios契约测试。** 无需为了接收fieldErrors增加解析框架、持久化、路由或Vue页面。

未来实际表单获授权后，映射规则建议如下，当前不交付UI：

1. 仅处理40001的普通对象fieldErrors；Map值必须为字符串数组，不把单字符串转换成第二种正式公共格式。
2. 按表单已知字段白名单精确匹配，使用Object.hasOwn/Object.entries读取自有属性；不动态遍历点号路径写入表单model，不做递归合并。
3. 使用Map或无原型对象保存提示，排除原型相关键。未知字段无法映射输入框时保留现有全局提示。
4. 每字段默认显示第一条有效消息，可按页面设计逐条显示；完整数组仍保留，不按消息文本决定业务行为。
5. 文案按Vue普通文本或组件error属性展示，不使用v-html或HTML解释；不记录密码、JWT或输入值。
6. Axios已经统一提示一次，表单层只设置字段提示，不再重复toast。data=null、缺少fieldErrors或非法形状时自然回退现有通用提示，保留用户输入。

第6.2节真实表单映射的视觉/交互验收须在对应页面阶段完成，不能把Axios透传测试写成已经实现表单就地提示。

## 九 待批准后的文件修改清单

以下是**拟议清单，当前均未实施**。代码包前缀为 `backend/src/main/java/com/cake/platform/`。

### 9.1 Java源码

| 文件 | 拟议动作 | 目的 |
| --- | --- | --- |
| `exception/ParameterValidationException.java` | 新增 | 固定40001，携带不可变字段错误，支持单字段/聚合；不携带输入值 |
| `exception/ParameterErrorData.java` | 新增 | 只定义fieldErrors的data模型，不改变顶层ApiResponse |
| `exception/ValidationErrorExtractor.java` | 修改 | 复用Map/List，规范公开路径、去重和受控文案；明确ObjectError/不可信位置兜底 |
| `exception/GlobalExceptionHandler.java` | 修改 | 汇聚各种请求参数来源，包装data；保留无位置兜底及全部非400错误 |
| `dto/AuthRequests.java` | 修改 | 校验带字段名，支持独立字段聚合和未知字段定位；保留保护字段规则 |
| `controller/auth/AuthController.java` | 修改 | 登录username与注册phone正确区分，密码错误定位password；请求级校验提供上下文 |
| `controller/user/UserController.java` | 修改 | nickname、oldPassword、newPassword定位及无参数/请求体边界 |
| `service/auth/AuthenticationService.java` | 修改 | 仅将新旧密码相同的40001附加newPassword位置，不改事务/BCrypt/版本机制 |

拟议新增2个Java文件、修改6个Java文件。参数聚合优先放在现有校验工具内，不重写整套DTO、引入重复异常体系或额外依赖。

无需修改：ApiResponse.java、BusinessException.java、CommonErrorCode.java、ErrorCode.java、AuthFailure.java、AuthErrorResponses.java、AuthExceptionHandler.java、JWT过滤器/配置、Mapper、Entity、数据库/SQL或依赖文件。若实施时发现额外必要修改，应先在清单中说明，不能静默扩大范围。

### 9.2 Vue和JavaScript

| 文件/范围 | 拟议动作 |
| --- | --- |
| frontend/src/api/http.js | 不改，现有RequestError.data已经透传 |
| frontend/src/api/auth.js、users.js | 不改，继续传播请求错误 |
| frontend/src/stores/auth.js | 不改，表单错误不属于登录状态 |
| 现有.vue、Router、App/application、样式 | 不改；不存在可接入的真实表单，不新增页面 |
| frontend/package.json、package-lock.json | 不改，不安装依赖 |
| frontend/src/api/http.test.js | 补充已确认数组Map在HTTP400和2xx业务失败分支的透传、null兜底及安全行为断言 |

不提前创建未使用的字段错误组件或映射工具。实际表单文件尚不存在，不能虚列为本轮需要修改的Vue文件。

### 9.3 接口与项目文档

需要在获准实施时更新现有认证接口说明和响应示例：`docs/security/AUTHENTICATION_DESIGN.md`、`AUTH_API_TESTS.md`；必要处同步`JWT_AND_PERMISSION.md`和`PASSWORD_SECURITY.md`中“参数错误data=null/fieldErrors未确认”的当前说明。README、PROJECT_STATUS、TODO也须记录已批准契约及真实验证结果，并新增实施报告。

目前没有已集成的OpenAPI/Knife4j规范文件需要同步；若实施时发现规范已新增，应同步已存在文件，不因本提案安装文档依赖或创建新接口。原DOCX、Phase0需求/追踪/架构/计划及Phase0～3历史报告保持原样，历史待确认记录不回写成当时已经通过。

## 十 拟议测试清单

本轮只阅读测试，没有新增、修改、运行或跳过任何测试。下列是契约确认后的实施验证计划，不是本轮通过结果。

| 套件 | 拟议新增/修改的有意义覆盖 |
| --- | --- |
| ApiResponseTests | 保留三顶层字段/成功null/时间用例，新增嵌套ParameterErrorData序列化；不能把通用failure(null)工厂禁止掉 |
| ValidationErrorExtractorTests | 保留多消息、不可变、无rejected secret；补充去重、受控文案、嵌套路径、ObjectError及不可信位置兜底 |
| GlobalExceptionHandlerTests | Bean/方法输入/ConstraintViolation、缺失和类型Query、JSON绑定/未知字段、畸形JSON/缺体/无位置BusinessException；精确更新参数错误data=null预期；返回值500及requestId不变 |
| 新ParameterValidationExceptionTests | 单字段/多字段、深层不可变、空或非法payload处理、无输入值/内部异常泄露 |
| AuthRequestsTests | 字段名、聚合多个独立错误、空值/类型顺序、未知字段安全名称、受保护字段优先；不改变密码/手机号/昵称业务边界 |
| AuthHttpMySqlTests | 注册phone/nickname/password多错误、登录username定位、改密oldPassword/newPassword及新旧相同定位、普通未知字段；响应变化前后无写入；保留现有成功与所有非400错误断言 |
| JwtServiceTests/JwtAuthenticationFilterTests及原数据库测试 | 作为回归保留，不改密钥/令牌/角色/锁定/事务/表结构；验证新增错误处理未影响安全路径 |
| frontend/src/api/http.test.js | 400与2xx业务失败的数组Map透传、data=null兜底、一次toast、不误清登录状态、成功和非400错误不变；原字符串Map夹具可保留为不透明data兼容测试 |
| 前端其余现有测试 | 保留认证状态、40102/退出401清理和旧响应竞争；不编写虚假表单页面测试 |

旧测试必须按已批准契约**定向更新**，不能删除测试、直接跳过或只断言40001而取消结构检查。退出/改密成功的data=null、401/403的原data、42301的lockedUntil及50001的requestId不得被批量替换。

未来实施时执行完整后端与前端测试及构建，真实认证回归复用独立_test库并准确清理，不修改数据库定义。历史Phase3报告记录后端255项和前端54项通过；这是既有证据，本轮未复跑，后续数量以当次实际报告为准，不能预填新的通过数。

## 十一 兼容性影响与风险

| 层面 | 影响及控制 |
| --- | --- |
| URL/方法/请求字段/角色/业务规则 | 均不改变，不增加API或数据库字段 |
| HTTP/业务码/顶层字段 | 400／40001及code/msg/data保持；msg仍为“参数错误” |
| 可定位的参数错误data | **由null改为对象，是可见的响应契约变化**；依赖data恒为null的消费者/测试需更新，不能称为完全无影响 |
| 无法定位的参数错误 | 推荐继续null，客户端必须允许本分支，不能无条件读取fieldErrors |
| 单字符串Map消费者 | 当前源码未发现正式解析器，只有透传测试；若外部客户端已自行按字符串解析，需一起迁移，不能悄悄兼容两种正式格式 |
| 成功、401/403/409/423/500 | 保持现有响应，错误处理只对请求参数40001选择字段payload |
| 消息安全 | 不回显值、原始绑定/解析异常或任意模板结果；未知名称也需受控 |
| 错误聚合 | 只聚合独立参数问题；不改变受保护字段优先级、不执行额外账号验证或写库 |
| 表单映射 | 当前没有实际页面，只能确认接收契约；未来UI仍需真实交互与视觉验收 |
| 其他遗留问题 | 密码Unicode长度、初始化账号、后续业务疑点不因fieldErrors结构确认自动关闭 |

## 十二 待用户确认的完整建议

| 编号 | 推荐，均为设计而非原文细则 | 可选择的差异 |
| --- | --- | --- |
| FE-01 | fieldErrors使用“字段路径→字符串数组”，不使用单字符串或元素对象数组 | 改用单字符串需另定多消息选择规则；改用数组需另定元素字段 |
| FE-02 | 仅能可靠定位时输出非空Map；无法定位仍data=null，不引入_global | 如要求所有40001强制data对象，须明确空Map或保留键及其前端含义 |
| FE-03 | 已知字段用公开lowerCamelCase；嵌套点号/零起始数组路径；普通未知名称按安全标识/128字符/原型键规则处理 | 路径格式和安全输出边界可调整，不能冒用Java内部路径 |
| FE-04 | 允许同字段多条受控消息、去重、明确顺序；聚合独立字段，缺失/类型根因优先 | 可限定每字段显示一条，但不必改变公共数组模型 |
| FE-05 | 保留现有Axios源码和一次全局提示，仅加契约测试；实际表单映射留到授权页面阶段 | 当前不新增页面或未使用组件；若另需映射工具须明确授权 |

用户确认公共结构后，仍须获得明确实施指令再修改上述源码与测试。本报告是具体可审阅的方案，不代替用户确认，也不自动将Phase3标为PASS。

## 十三 本轮执行与保护验证

实际动作仅为：读取原DOCX相关段落/表格、阅读上述Java/JavaScript及现有测试、搜索调用点、检查Git状态、创建本提案、校验Markdown和JSON示例并比对文件保护快照。`git status --short --branch`仍显示非Git仓库，没有执行Git写操作。

不运行Maven/npm测试或构建，不安装依赖，不启动应用，不连接数据库，不执行SQL。原因是本阶段只提出待确认方案，运行会写测试数据或生成物的验证不属于本轮必要范围；没有将历史测试数量当成本轮执行证据。

开始快照为91个既有手写源文件/文档，排除node_modules、target、dist。最终快照已核对：共92个文件，仅新增本提案，原91个文件SHA256全部一致，无修改或删除。报告内6个JSON示例均可解析，代码围栏和Markdown表格检查通过。原始需求DOCX SHA256为 `522D3CB741550FD88D8B73365A245F2AEDF98AA6CF6B1AF2A5A7A34DF56025E5`。

**至此停止：待确认fieldErrors结构及上述必要细节后，再按明确授权实施；本轮不写业务代码，不进入Phase4。**
