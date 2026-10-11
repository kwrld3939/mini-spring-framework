# ADR-0002：循环依赖的支持范围与失败语义

- 状态：Accepted（2026-10-11 确认；阶段 1 据此实施）
- 实施：已落地（2026-10-11）。环检测基于"当前创建路径"（天然线程安全，prototype 并发创建不会误报），`CircularDependencyException` 携带完整链路与每个节点的注入点明细；测试覆盖构造器环、prototype 环、字段注入环与自注入。
- 待补：本 ADR 要求的**启动期 WARN** 目前记录在 `DefaultListableBeanFactory#getDiagnostics()`（已有测试断言），日志输出随 SLF4J 接入一起完成，见 `docs/roadmap.md`。
- 日期：2026-10-11
- 决策者：项目维护者
- 影响范围：`mini-spring-context`、`mini-spring-beans`

## 背景

> 本节描述的是**重构前的基线**（写这篇 ADR 时的实现），其中的类与方法已在阶段 1 被拆解。保留原文是为了记录这个决策当时要解决的问题，不要按它去理解现在的代码。

当前实现中，循环依赖的行为是"碰巧成立"而不是被设计出来的：

- `getOrCreateBean` 用一个 `creating` 集合检测构造器环并抛 `IllegalStateException("Circular constructor dependency detected")`（`MiniApplicationContext.java:191-213`）；
- 字段/setter 注入路径却传入 `new HashSet<>()`（`MiniApplicationContext.java:277`），等于把环检测关掉；
- setter 环之所以能跑通，是因为"先建全部再统一注入"（`MiniApplicationContext.java:242-246`）——两个对象都先构造出来，再互相 set。这是两趟创建的副作用，不是有意设计。

结果：`AlphaService`/`BetaService` 的 setter 环能通过测试，但没有任何文档说明"哪些环支持、哪些不支持、为什么"。一旦引入 ADR-0001 的递归创建，这条"副作用"路径会立刻断掉，必须在重构前先把语义定死。

## 决策

**支持范围（显式承诺）：**

| 场景 | 行为 |
| --- | --- |
| 单例之间的字段注入环（A 字段注入 B，B 字段注入 A） | 支持，通过早期引用解开 |
| 单例之间的 setter 注入环 | 支持，同上 |
| 自注入（A 注入自身） | 支持，通过早期引用 |
| 构造器注入参与的环（含构造器 + setter 混合且构造成环） | **不支持**，快速失败 |
| 涉及 prototype 的环 | **不支持**，快速失败 |

**失败语义：** 抛出 `CircularDependencyException`，消息必须包含

1. 完整依赖链，形如 `alphaService -> betaService -> alphaService`；
2. 链上每个节点的注入点（构造器参数 / 字段 / setter 方法签名）；
3. 首次检测到环的位置（正在创建中的 beanName）。

**例外条款（必须写进 javadoc 与文档）：** 若环中某个 Bean 需要 AOP 代理，环上的反向引用拿到的是**早期引用**，即未代理对象——这与 Spring 的已知行为一致。容器在启动期对这类 Bean 输出 WARN 并列出 beanName，使用者可据此判断"我的环上增强为什么没生效"。

**不支持 lazyInit 参与的环**（与 ADR-0006 一致：v0.1 不支持 lazyInit）。

## 后果

正面：

- 使用者可以凭文档预测行为，不需要跑起来试；
- 构造器环的失败信息直接指出链路，不需要逐个断点排查；
- 与 Spring 的语义对齐，学习者的既有知识可以迁移。

负面：

- 环上反向引用的代理例外必须一直维护和解释，这是复杂度，不是缺陷；
- 早期引用缓存的生命周期管理必须严谨，否则会引入内存驻留或错误复用（由 ADR-0001 的"环解开即清除"约束）。

## 被否决的替代方案

1. **完全不支持循环依赖（一律快速失败）**：语义最干净，但会让现有示例和大量真实写法（setter 往返引用）无法工作，且偏离 Spring，学习者会困惑。
2. **用 `@Lazy` 代理打破一切环**：需要字节码生成注入点代理，工程量与风险都远超当前阶段收益。

## 验收标准

每个场景一个测试：

1. setter 环成功，且两侧方法调用链正确（保留现有 `AlphaService`/`BetaService` 测试）；
2. 字段注入环成功；
3. 自注入成功；
4. 构造器环失败，断言消息包含完整链（`a -> b -> a`）与注入点；
5. prototype 参与的环失败，异常类型为 `CircularDependencyException`；
6. 环 + 代理场景断言启动期 WARN 出现，且反向引用确实为原始对象（记录为已知语义，而非 bug）。
