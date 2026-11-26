# Codex 项目开发规则

## 需求与阶段边界

1. 本项目为多商家平台，唯一需求依据为根目录《蛋糕店订单管理系统需求说明书_V1.1.docx》，实际修订日期为 2026-11-24。正文、表格、数据字典、接口、页面和验收场景均须完整核对；摘要不替代原文。
   用户于 2026-11-24 明确补充“Node.js 使用 24.20.0”，记为 ENV-01：后续执行采用 Node.js 24.20.0，覆盖原文 Node.js 18 要求。原始 DOCX 保持原样，文档须区分原文与已确认补充。
2. 开发依照 `docs/plan/PHASE_PLAN.md` 中 Phase 0 至 Phase 13 顺序推进。每次仅执行用户明确授权的当前阶段，完成后停止并提交 Markdown 阶段报告供审核；不得自行进入下一阶段。
3. 当前仅授权 Phase 0。只允许创建或完善本轮指定的九份 Markdown 文档及其父目录；禁止业务代码、工程脚手架、SQL/迁移脚本、数据库创建、API、Vue 页面、依赖安装及环境变更。
4. 需求歧义、冲突和实现风险须单列待确认；不得以设计方案代替用户决定，不得擅改原始 DOCX、数据库、API 路径/字段、角色权限或业务规则。
5. 核心表固定为 sys_user、cake_merchant、cake_category、cake_product、cake_order、cake_order_item；不得擅增 SKU、真实支付、退款、库存流水等排除范围。

## 修改与验证

6. 修改前检查相关文件、调用关系、已有规范和 Git 状态；已有实现必须先分析复用，不得生成重复工程。
7. 不得修改当前阶段允许目录之外的文件。保护已有文件和未提交修改，不覆盖、不删除、不清理；已有有效文档优先更新。
8. 新增功能必须配套有意义的测试，覆盖正向、异常、权限、归属及并发边界。不得将设计完成写成实现完成。
9. 每阶段执行与其范围相关的测试和构建，并记录命令、结果及未执行原因。Phase 0 只做文档完整性、追踪一致性、源文件保护及环境检查；没有工程时不安装依赖或伪称构建通过。
10. 每阶段在 reports/ 生成 Markdown 报告，记录范围、证据、变更文件、验证结果、风险、待确认项和验收结论。

## 安全与权限

11. 不提交明文密码、JWT 密钥、数据库凭据或其他敏感信息；后续仅提供占位配置，通过本地环境或受控配置注入真实秘密。
12. 后端必须独立校验 JWT、数据库当前账号角色、启停状态、令牌版本及资源归属；前端守卫不替代后端权限校验。
13. ADMIN 只读订单、不代商家接单或维护商品；MERCHANT 仅本店；CUSTOMER 仅本人。公开注册固定 CUSTOMER，商家由平台创建。

## Git 与 Windows 命令

14. 禁止 git reset、git rebase、强制推送等破坏性 Git 操作。未经用户明确授权不得自动 commit 或 push。
15. 当前勘察发现工作区不在 Git 仓库中；不得自行 git init，须记录并等待用户确认。后续工作前再次检查实际状态。
16. 使用项目既有 Windows / PowerShell 命令规范；优先 rg 搜索，npm 优先使用 npm.cmd。不要依赖修改 ExecutionPolicy 运行脚本。
17. 不修改系统环境变量、PowerShell ExecutionPolicy，不擅自安装软件或依赖。版本不一致时只记录，等待用户决定。
18. 文件操作使用明确路径及 PowerShell 原生命令；中文文件使用 UTF-8。文档中未来命令均为计划，不表示已执行。

## 本轮允许输出

- AGENTS.md
- README.md
- PROJECT_STATUS.md
- TODO.md
- docs/requirements/REQUIREMENTS_BASELINE.md
- docs/requirements/TRACEABILITY_MATRIX.md
- docs/architecture/ARCHITECTURE_PLAN.md
- docs/plan/PHASE_PLAN.md
- reports/PHASE_0_REPORT.md

原始需求 DOCX 保持原样。未经用户审核本轮文档并明确授权 Phase 1，不得开始基础工程建设。
