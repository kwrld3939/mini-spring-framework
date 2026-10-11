# ADR-0001：依赖解析与代理暴露语义

- 状态：Accepted（2026-10-11 确认；阶段 1 据此实施）
- 实施：已落地（2026-10-11）。`DefaultListableBeanFactory` 成为唯一漏斗，实例化后登记早期引用。回归断言 `injectsTheAopProxyIntoConstructorDependencies`（构造器注入到的对象 `==` 单例池中的代理），另有 `resolvesFieldInjectionCycleThroughEarlyReference` 与 `recordsDiagnosticWhenEarlyReferenceIsReplacedByProxy` 覆盖早期引用路径。
- 日期：2026-10-11
- 决策者：项目维护者
- 影响范围：`mini-spring-beans`、`mini-spring-context`、`mini-spring-aop`

## 背景

> 本节描述的是**重构前的基线**（写这篇 ADR 时的实现），其中的类与方法已在阶段 1 被拆解。保留原文是为了记录这个决策当时要解决的问题，不要按它去理解现在的代码。

当前容器把 Bean 的创建拆成两趟：`createBeans()` 先实例化并注入（`MiniApplicationContext.java:129-136`、`:242-246`），`initializeBeans()` 再统一跑 BeanPostProcessor 并把单例池里的对象**替换**成 AOP 代理（`MiniApplicationContext.java:417-441`）。

由此产生一个结构性后果：构造器注入发生在第一趟，注入者拿到的是原始对象；代理在第二趟才产生，且不会回头修改已注入的字段。所以 `PayController` 里的 `payService` 永远是原始 `AliPayService`，`handlerMapping.invoke("/pay")` 不会打印 AOP 日志，只有从容器重新 `getBean("aliPayService")` 才触发增强。

这不是实现细节上的疏忽，而是**语义缺失**：框架没有回答"注入点拿到的到底是哪一个对象"。当前只能靠 README 的一段文字解释（`README.md:405-433`），使用者必须读源码才能预测行为。这是把学习版升级为真实库时最先必须解决的问题——静默的不一致比明确的限制更有破坏性。

## 决策

**`getBean` 是唯一漏斗：一次调用内完成完整生命周期，并返回终态对象。**

```
getBean(name)
  → 实例化
  → 属性注入（字段 / setter，依赖通过递归调用同一个漏斗解析）
  → BeanPostProcessor.postProcessBeforeInitialization
  → @MiniPostConstruct
  → BeanPostProcessor.postProcessAfterInitialization（AOP 代理在此产生）
  → 写入单例池
  → 返回终态对象
```

依赖解析不再"自己 new 一个再等第二趟初始化"，而是**递归调用这个漏斗**。因此构造器注入拿到的必然是完成态对象，包含代理。

为在支持循环依赖（见 ADR-0002）的同时维持该不变式，引入早期引用：

- 实例化完成后、属性注入之前，把该 beanName 的**早期引用工厂**登记到早期单例缓存；
- 若解析依赖时目标 bean 正处于创建中，则从早期缓存取引用，并对其应用 `getEarlyBeanReference(beanName, bean)` 扩展点；默认实现返回原始对象，AOP 后置处理器可覆盖它；
- 单例池中**只允许**存放终态对象；早期引用只用于打破环，环解开后即清除。

**核心不变式（必须由测试守住）：**

> 除 ADR-0002 定义的环上反向引用外，任意注入点持有的对象 == 该 beanName 在单例池中的终态对象（`==` 同一引用）。

## 后果

正面：

- `PayController` 构造器注入的 `payService` 就是 JDK 代理，AOP 对构造器注入这种主流写法真正生效；
- 语义可被单条断言表达，也可以写进 javadoc，使用者不需要读源码；
- 为后续 advisor 排序、`@MiniTransactional` 式切面、代理暴露策略留出统一入口。

负面：

- 需要引入早期单例缓存与 `getEarlyBeanReference`，容器内部复杂度上升；
- 启动路径从"两趟"变成"递归一趟"，异常栈更深——由 ADR-0005 的链式失败报告承担可读性；
- 创建顺序的语义变得更加重要，必须配合 ADR-0003 的确定性顺序才能复现问题。

## 被否决的替代方案

1. **注入点改为 `ObjectFactory`/`Provider`**：语义正确，但要求使用者把字段类型从 `PayService` 改成 `Provider<PayService>`，等于让框架的缺陷泄漏到业务代码，且偏离学习者的 Spring 直觉。
2. **保留两趟 + 启动时检测并报错**：诚实，但结论是"AOP 对构造器注入不可用"，框架作为 AOP 容器的价值基本被抹掉；只能作为 v0.1 之前的临时兜底，不作为目标语义。
3. **只增强从容器直接获取的对象，文档说明**：即现状。被否决，理由是它把不确定性留给使用者。

## 验收标准

1. `PayController` 的构造器注入对象 `==` 单例池中的代理对象（`Proxy.isProxyClass(...)` 为真）。
2. `handlerMapping.invoke("/pay")` 打印 AOP before/after 日志。
3. 环上反向引用为原始对象这一例外，有专门测试；且该情况在启动期以 WARN 记录涉及的 beanName。
4. README 中"构造器注入拿到未代理对象"的说明被删除，替换为对本 ADR 语义的描述。
