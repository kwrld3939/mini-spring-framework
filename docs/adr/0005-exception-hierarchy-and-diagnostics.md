# ADR-0005：异常层级与可诊断失败

- 状态：Accepted（2026-10-11 确认；阶段 1 据此实施）
- 实施：已落地（2026-10-11）。九个异常类型 + `ConfigException` 全部就位，消息模板统一为"做什么失败 + 哪个 Bean/类/成员 + 依赖链 + 建议"。
- 约定（由测试固化）：注入点上的依赖解析失败统一表现为 `BeansException`，消息里点名**哪个 Bean 的哪个注入点**；能给出更精确类型的场景（`NoSuchBeanException`/`NoUniqueBeanException`）保留在 `cause` 中；**循环依赖异常原样抛出、不被包装**，因为它自身已携带完整链路。这条不对称是有意的，写在 `BeanFactoryFailureTests` 的类注释里。
- 待补：结构化启动失败报告（`FailureAnalyzer`/`FailureAnalysis` 扩展点）在阶段 2 交付，见 `docs/roadmap.md`。
- 日期：2026-10-11
- 决策者：项目维护者
- 影响范围：全部模块

## 背景

当前所有失败路径都用 JDK 通用异常加字符串拼接表达，例如：

- `throw new IllegalStateException("Duplicate bean name: " + beanName)`（`MiniApplicationContext.java:105`）
- `throw new IllegalStateException("Multiple beans found for type: " + dependencyType.getName())`（`:343`、`:390`）
- `throw new IllegalStateException("Failed to instantiate bean: " + clazz.getName(), ex)`（`:227`）
- `throw new IllegalStateException("No property found for key: " + propertyKey)`（`:292`）

问题有三个：

1. **调用方无法按语义处理**。使用者在框架里捕获到 `IllegalStateException`，既无法区分"没有这个 bean"和"有两个候选"，也无法在不解析字符串的前提下做降级处理。
2. **信息不足以定位**。`Failed to instantiate bean: com.demo.X` 不给出依赖链、不给出注入点、不给出建议；真实项目里这类报错要在启动日志里反复排查。
3. **原始异常在转换时可能被弱化**。例如 `injectValue` 的 `IllegalAccessException` 分支（`:299-301`）重新包装时上下文信息有限，排查时容易丢失线索。

对一个库来说，"失败信息质量"就是产品力：使用者第一次遇到错误时能不能自己修好，决定了这个库是否可用。

## 决策

**建立 unchecked 异常层级，类型即语义。**

```
MiniSpringException (RuntimeException)
├── BeansException
│   ├── BeanDefinitionException         定义非法：重复 beanName、空类、非法 scope
│   ├── NoSuchBeanException             按名称/类型/限定符找不到
│   ├── NoUniqueBeanException           多个候选（消息必须列出全部候选与来源）
│   ├── BeanCreationException           创建失败：beanName + beanClass + 注入点 + cause
│   ├── BeanCurrentlyInCreationException 环检测中间态（由 ADR-0002 细化）
│   ├── CircularDependencyException     消息必须含完整依赖链
│   └── LifecycleException              初始化/销毁回调失败（可聚合多个）
├── ConfigException                     配置读取/类型转换失败（含 key、期望类型、原始值）
└── (web) RequestBindingException       请求参数绑定失败（含参数名、期望类型、原始值）
```

**统一消息模板：** `做什么失败 + 哪个 bean/类/成员 + 依赖链 + 下一步建议`。

示例（目标形态，而非现状）：

```
NoUniqueBeanException: 解析 PayController 的构造器参数 PayService 时找到 2 个候选。
  候选: aliPayService (com.demo.service.AliPayService)
        wechatPayService (com.demo.service.WechatPayService)
  建议: 在参数上加 @MiniQualifier("aliPayService")，或给其中一个实现加 @MiniPrimary。
  依赖链: payController -> PayService
```

**规则：**

1. 转换异常时必须保留 `cause`，禁止 `throw new X(msg)` 丢弃原始异常；
2. 异常类型必须可由调用方程序化判断，禁止用错误码字符串表达语义；
3. 每个 public 异常在 javadoc 中写明**触发条件**与**使用者的应对方式**；
4. 容器启动失败时输出**结构化失败报告**（对齐 Spring Boot 的 FailureAnalyzer 思路）：根因异常 + 依赖链 + 相关 BeanDefinition 摘要 + 建议。本 ADR 固化接口（`FailureAnalyzer` / `FailureAnalysis` 扩展点），完整报告在阶段 2 交付。

## 后果

正面：

- 使用者可按类型捕获并降级，也可以只依赖消息定位问题；
- 多候选、缺 bean、配置缺失这三类最高频错误变得可以自助解决；
- 结构化失败报告让"启动失败"从猜谜变成阅读，这是库的可用性分水岭；
- 为后续 IDE 提示与文档生成提供稳定的锚点（异常类型即文档索引）。

负面：

- 异常类型增多，需要持续的文档维护成本；
- 存在"过度细分"的风险，需要克制：不为每一种内部错误造类型，只为核心语义建类型；
- 现有测试断言的字符串需同步更新。

## 被否决的替代方案

1. **只用 `BeansException` 一个类型 + 消息区分**：调用方仍需解析字符串，没解决第一个问题。
2. **用错误码枚举**：不符合 Java 生态惯例，且丢失栈与类型信息，与"可诊断"目标相反。

## 验收标准

1. 每个异常类型至少一个测试，断言消息包含关键字段（beanName / 候选列表 / 依赖链 / 配置 key / 注入点）；
2. 存在测试断言"多候选异常消息包含全部候选名"，且候选超过 2 个时不截断；
3. `cause` 链在包装后仍可通过 `getCause()` 到达根因（针对创建失败的测试）；
4. 启动失败报告有快照测试（文本快照，避免无意改动）；
5. javadoc 中每个 public 异常都写明触发条件与应对方式（由 javadoc 检查插件在阶段 3 强制）。
