# 数据库脚本

本目录是 Phase 2 的首次初始化材料。目标为 MySQL 8.0.x（历史基线 8.0.19），精确六表74字段、InnoDB、utf8mb4、DATETIME(3)、北京时间。排序规则已由用户于2026-11-25确认 `utf8mb4_0900_as_cs`；Phase 5 起测试断言与 DatabaseTool 已放宽为 8.0.x。

| 路径 | 顺序与用途 |
| --- | --- |
| schema/01_create_tables.sql | 1：表、6主键、9唯一键 |
| indexes/02_create_indexes.sql | 2：14个需求普通索引 |
| constraints/03_data_constraints.sql | 3：8外键RESTRICT、29 CHECK；InnoDB另生成1个必要复合FK支持索引 |
| seed/04_initial_data.sql | 4，可选：具名参数模板，PreparedStatement绑定，不可直接SOURCE |
| tools/DatabaseTool.java | 本机交互连接/空库检查/初始化/种子事务；不打包进业务应用 |
| demo/ | 本地演示数据（奶油星球、8商品）与清理脚本；非正式种子、不提交 |

从项目根目录运行工具，步骤与环境变量见 [初始化指南](../docs/database/INITIALIZATION_GUIDE.md)。DDL中的collation占位由工具验证后替换，SQL不包含数据库凭据、删库或清空。已有表/视图时拒绝初始化；DDL隐式提交，失败后不得假定回滚或直接重跑。

开发库 `cake_order_system` 与专用测试库 `cake_order_system_phase2_test` 分开。真实测试在后端mysql-test配置中通过数据源运行，普通test保持无数据库依赖。正式演示账号资料尚未准备，正式种子仍待执行；`demo/` 只提供本地开发/答辩用的临时商家账号与商品数据，不提交、不作为正式种子，使用方式见 [本地演示说明](demo/README.md)。

设计与完整字典见 [数据库设计](../docs/database/DATABASE_DESIGN.md)、[数据字典](../docs/database/DATA_DICTIONARY.md)、[索引约束](../docs/database/INDEX_AND_CONSTRAINTS.md)；本轮实测和验收边界见 [Phase2报告](../reports/PHASE_2_REPORT.md)。
