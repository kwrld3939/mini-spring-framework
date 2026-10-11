# 架构决策记录（ADR）

本目录记录**不可从代码直接读出的决策**：为什么这样设计、否决了哪些方案、以及如何验收。代码能说明"是什么"，ADR 负责说明"为什么"和"边界在哪"。

## 索引

| 编号 | 标题 | 状态 | 一句话结论 |
| --- | --- | --- | --- |
| [0001](0001-dependency-resolution-and-proxy-exposure.md) | 依赖解析与代理暴露语义 | Accepted | `getBean` 是唯一漏斗，一次调用走完生命周期并返回终态对象，构造器注入拿到的就是代理 |
| [0002](0002-circular-dependency-scope.md) | 循环依赖的支持范围与失败语义 | Accepted | 字段/setter 环与自注入支持；构造器环与 prototype 环快速失败并报出完整链 |
| [0003](0003-classpath-scanning.md) | 类路径扫描策略 | Accepted | 扫描用 ASM 读元数据不加载类；支持 file/jar；结果按类名排序保证可复现 |
| [0004](0004-module-layout-and-coordinates.md) | 模块划分、坐标与依赖方向 | Accepted | 拆 `beans/context/aop/web/test` + samples；库不继承 Boot parent；web 切 jakarta 6.1 |
| [0005](0005-exception-hierarchy-and-diagnostics.md) | 异常层级与可诊断失败 | Accepted | 类型即语义的异常层级 + 统一消息模板 + 结构化启动失败报告 |
| [0006](0006-concurrency-and-lifecycle-freeze.md) | 并发模型与生命周期冻结 | Accepted | `refresh()` 后冻结，读路径无锁；v0.1 不支持 lazyInit；销毁按依赖逆序 |
| [0007](0007-web-integration-jakarta.md) | Web 集成与 jakarta.servlet 迁移 | Accepted | web 只依赖 jakarta 6.1；补齐 404/405/400 语义；两个 sample 分别验证独立与嵌入 |
| [0009](0009-public-api-type-naming.md) | public API 的类型命名 | Accepted | 去掉 `My` 前缀，统一 `Mini*`（`MiniApplicationContext`、`@MiniComponent`），与包名对齐且不与 Spring 同名 |
| [0010](0010-static-analysis-and-architecture-guards.md) | 静态分析与架构守卫的落地范围 | Accepted | 分层引入：enforcer 管坐标、Checkstyle 管两条真实规则、ArchUnit 管依赖方向；明确不做全量格式化与 ErrorProne/SpotBugs |
| [0011](0011-coverage-gate-placement.md) | 覆盖率门槛的落点与多模块归属 | Accepted | 门槛只在父 POM 配一处（行 85% / 分支 75%）；报告按模块归属但不限制覆盖来源；样例是应用，显式跳过 |
| [0012](0012-constructor-selection.md) | 构造器选择策略 | Accepted | 唯一构造器直接用；多个构造器必须恰好一个 `@MiniAutowired`，否则启动失败并列出候选；不做 Spring 式的无参回退 |

> 编号 0008（代理实现选择）为 `docs/roadmap.md` 中的待决议题预留。

## 状态含义

| 状态 | 含义 |
| --- | --- |
| Proposed | 已写出方案，等待维护者确认；确认前不据此写代码 |
| Accepted | 已确认，代码必须符合；变更需新 ADR 或修订本 ADR |
| Deprecated | 不再适用，但保留历史原因 |
| Superseded by NNNN | 被新 ADR 取代，需在新 ADR 中说明取代理由 |

## 新增 ADR 的流程

1. 复制下方模板，编号取当前最大编号 + 1，文件名 `NNNN-kebab-case-title.md`；
2. 必须写明**背景（含现状证据）**、**决策**、**后果（正面与负面）**、**被否决的替代方案**、**验收标准**；
3. 负面后果与被否决方案不可省略——只写优点的 ADR 等于没有记录决策；
4. 决策一旦 Accepted，代码与测试必须与之一致；发现不一致时，先改 ADR 再改代码（或在同一提交内说明）。

## 模板

```markdown
# ADR-NNNN：标题

- 状态：Proposed
- 日期：YYYY-MM-DD
- 决策者：项目维护者
- 影响范围：涉及的模块

## 背景

现状是什么，证据在哪（文件与行号），为什么现在必须决定。

## 决策

做什么，边界在哪，不变式是什么。

## 后果

正面：...
负面：...

## 被否决的替代方案

1. 方案：被否决的理由。

## 验收标准

可执行、可断言的标准，而不是"实现良好"。
```
