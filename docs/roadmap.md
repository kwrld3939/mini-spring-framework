# 路线图：从学习版到真实可用的库

- 更新日期：2026-10-11
- 当前阶段：**阶段 1（架构重构）已完成**（2026-10-11）——模块骨架与包重命名、jakarta 迁移、容器内核拆分、ADR-0001/0002/0003/0005/0006/0009/0010/0011/0012 全部落地，SLF4J 与四类门禁（enforcer / Checkstyle / ArchUnit / JaCoCo）已接入，**124 个测试全绿**
- 目标定位：把 Mini Spring 容器做成**可被外部项目依赖的库**（路线 A），原 `com.example.demo` 已迁入 `samples/` 作为原生 Spring 对照示例

## "真实水准"的验收定义

不追 Spring 的功能对等，只追这五条可验收的指标：

| # | 指标 | 验收方式 |
| --- | --- | --- |
| 1 | 不看源码也能用 | javadoc 完整，public API 有 `@since` 与稳定性标注，reference docs 覆盖接入路径 |
| 2 | 语义可预测 | 循环依赖、代理暴露、并发、扫描顺序均有文档 + 断言测试，不存在"碰巧能跑" |
| 3 | 失败可诊断 | 异常带 bean 名、类名、成员、依赖链与建议，有结构化启动失败报告 |
| 4 | 演进可控 | 多模块、语义化版本、japicmp 兼容门禁、CI 全绿、覆盖率门槛 |
| 5 | 有人真的在用 | 一个只依赖本框架的真实服务跑起来，并公开其不足 |

## 阶段 0 · 定契约（已完成）

- [x] `docs/adr/0001` ~ `0007`：七条决定架构走向的决策，状态均为 Accepted；
- [x] `docs/adr/README.md`：ADR 流程与模板；
- [x] `docs/roadmap.md`：本文件；
- [x] `groupId` 定为 `io.github.kwrld3939`（GitHub 账号 kwrld3939，仓库 mini-spring-ioc-container）；
- [x] 库的父 POM 不继承 `spring-boot-starter-parent`（ADR-0004）。

## 阶段 1 · 架构重构（已完成，2026-10-11）

顺序不能与阶段 2 调换：先补功能再重构，只会把上帝类养得更胖。

### 已完成

- [x] **父 POM 与模块骨架**：`mini-spring-parent` 聚合 `mini-spring-beans`、`mini-spring-context`、`mini-spring-web` 与两个样例；Java 基线 `release=21`；`-parameters` 保留（`RequestArgumentResolver` 依赖参数名兜底）。
- [x] **包重命名与代码搬运**：`com.demo.mini.*` → `io.github.kwrld3939.minispring.*`，`com.example.demo` → `samples/spring-boot-reference`，演示代码 → `samples/mini-spring-standalone`；34 个 java 文件全部通过"声明包 vs 目录"一致性校验。
- [x] **jakarta 迁移**：全仓库移除 `javax.servlet`（ADR-0007 的前置条件，提前到骨架阶段完成，避免二次改动）。
- [x] **配置拆分**：容器样例的 `app.*` 与 Boot 样例的 `spring.application.name`/`server.port` 分离到各自模块。
- [x] **回归基线**：`mvn clean verify` 六个模块全绿，12 个测试（11 容器 + 1 Boot）通过，行为与重构前一致。
- [x] **类型命名统一为 `Mini*`**（ADR-0009）：`MyApplicationContext` → `MiniApplicationContext` 等 13 个类型与同名文件（`git mv` 保留历史）一次性改名，java 引用、README、报错指南、`docs/` 全部同步；大小写敏感检查无 `My*` 残留。
- [x] **拆分容器内核**：446 行的 `MiniApplicationContext` 降为装配层，职责拆到 `MiniApplicationContext`（装配）、`DefaultListableBeanFactory`（创建/注入/后置处理/生命周期/销毁）、`BeanDefinitionRegistry`、`ClassPathScanner`、`ClassPathBeanDefinitionScanner`、`DependencyDescriptor`、`Environment`/`PropertySource`/`StandardEnvironment`，以及 `BeanFactory` 接口与 9 个异常类型。
- [x] **落地 ADR-0001（依赖解析与代理暴露）**：`getBean` 成为唯一漏斗，实例化后登记早期引用。**行为已变并且可验证**：`handlerMapping.invoke("/pay")` 现在会经过构造器注入的代理并打印 AOP 日志（重构前静默无增强）；回归断言 `injectsTheAopProxyIntoConstructorDependencies`。
- [x] **落地 ADR-0002（循环依赖范围）**：环检测基于创建路径（线程安全），构造器环与 prototype 环快速失败并给出完整链路 + 每个节点的注入点；字段环、setter 环、自注入通过早期引用解开；环上代理的例外记入 `getDiagnostics()`。
- [x] **落地 ADR-0005（异常层级）**：`MiniSpringException` → `BeansException` → `BeanDefinitionException`/`NoSuchBeanException`/`NoUniqueBeanException`/`BeanCreationException`/`BeanCurrentlyInCreationException`/`CircularDependencyException`/`LifecycleException`，另有 `ConfigException`；每个类型都在测试中断言消息内容（候选列表、依赖链、配置 key、建议）。
- [x] **落地 ADR-0006 的冻结与销毁逆序**：`refresh()` 后冻结（读路径无锁 + volatile 安全发布），冻结后注册直接报错，`lazyInit` 字段已移除，销毁按依赖逆序且单个回调失败不中断其余（聚合为 `LifecycleException`）。
- [x] **框架模块自测**：`mini-spring-context` 新增 23 个测试（`MiniApplicationContextTests` 10 + `DefaultListableBeanFactoryTests` 11 + `ConcurrencyTests` 2），含 16 线程 × 320 次并发 `getBean` 的安全性断言；整体从 12 个测试增至 36 个。
- [x] **三处隐性缺陷顺手修掉**：①销毁回调原先按实例的运行时类反射，被代理的 Bean 会**静默跳过** `@MiniPreDestroy`（JDK 代理上甚至没有该方法）——现在按定义类反射、在**目标对象**上调用，并有回归测试，且用变异检验（把修复还原后测试确实失败）确认了这条测试有效；②构造器选择原先依赖 `getDeclaredConstructors()` 的返回顺序，同参数个数时结果不可复现——现在按签名排序定序；③扫描路径原先用 `URLDecoder` 解码，会把目录名里的 `+` 误解成空格——现在改用 `URI`。
- [x] **落地 ADR-0003（ASM 扫描）**：扫描阶段改用 ASM 读字节码元数据，**不加载用户的类**；组件类以 `Class.forName(..., initialize=false)` 加载，静态初始化块推迟到实例化；支持 `file:` 与 `jar:`，嵌套 jar 明确报错；结果按类名排序；加载失败不再静默跳过而是抛出可读异常。
  - 验证手法：每个扫描测试用**全新的 ClassLoader**（否则静态块在一个 JVM 里只会执行一次，断言不可靠），并用"显式初始化必须能被探测到"的对照组证明探针本身有效；另有 `loadsOnlyComponentClasses` 断言非组件类从未被加载。
  - 已知限制：jar 需要目录条目（`jar` 工具与 Maven 生成的 jar 都有；手工拼装的 jar 缺目录条目会表现为"扫不到"），已写入 `ClassPathScanner` 的 javadoc。
- [x] **接入 SLF4J**：`mini-spring-context` 依赖 SLF4J API（不绑定实现）；启动/关闭打 INFO，ADR-0002 的"环上代理"例外打 **WARN**，并有测试用 logback `ListAppender` 断言这条 WARN 真的输出；core 模块 `System.out` 数量为 **0**（样例组件也已改为 SLF4J，仅 `MainApp` 的命令行报告保留 stdout 输出）。
- [x] **依赖约束由构建强制**（原本只是 ADR 里的一句话）：`maven-enforcer-plugin` 在 `mini-spring-beans`/`mini-spring-context` 上禁止 `org.springframework:*`、`javax.servlet:*`、`jakarta.servlet:*`、`com.fasterxml.jackson.core:*`、`cglib:*`、`net.sf.cglib:*`；并有 JDK/Maven 版本断言。
  - 验证手法：**变异检验**——往 `mini-spring-beans` 注入一个 `spring-core` 依赖后 `mvn validate` 失败（`BannedDependencies failed`），随后按哈希校验确认 pom 已还原。
- [x] **JaCoCo 覆盖率达标**：先按"棘轮"接入（当时实测行 75.7% / 分支 67.9%，门槛设在 0.70 / 0.62 防止回退），随后补齐**失败语义**测试（限定符未命中/类型不匹配、setter 参数个数、生命周期方法带参、构造器抛异常、`@MiniValue` 空 key、后置处理器返回 null 或抛异常、损坏 class 文件、类无法加载、配置来源优先级等），实测提升到 **行 87.2%（524/601）、分支 78.4%（225/287）**，门槛随即上调为阶段 1 的目标值 **0.85 / 0.75**。
  - 说明：目标值不是先写数字再凑——第一轮就如实记录"未达标"，补齐后再上调；门槛设在目标值而不是实测值，避免"刚好卡在现状"的假门槛。
- [x] **静态分析与架构守卫（ADR-0010）**：Checkstyle 自定义规则集（禁 `System.out`/`System.err`、禁 `javax.servlet` 导入；只作用于 `src/main`，样例显式跳过）；ArchUnit 断言库内四层依赖方向与"库不得依赖 Spring/Jackson/`javax.servlet`"。
  - 三条规则都用**变异检验或反向对照组**证明"违反时真的会拦"：临时加一个含 `System.out` 的类被 Checkstyle 拦下并报出文件与行号（随后删除）；ArchUnit 保留一条"`web` 不得依赖 `jakarta.servlet`"的反向对照——它对现有代码必然报错，用来证明规则确实在评估这批类，而不是因为导入为空而全部"通过"。
  - 明确**不做**：全量 Checkstyle 规则集、Spotless 全量格式化、ErrorProne、SpotBugs（理由与被否决方案见 ADR-0010）。
- [x] **补齐 `beans`/`web` 自身测试并统一覆盖率门槛（ADR-0011）**：`mini-spring-beans` 新增 24 个测试（定义校验、类型转换、异常层级与消息健壮性），`mini-spring-web` 新增 18 个测试（参数解析、映射表、DispatcherServlet 的三条装配路径与 404/已提交响应/异常包装），全仓测试 77 → 119。
  - 门槛从"写在 context 模块的 pom 里"改为**只在父 POM 配一处**（行 85% / 分支 75%），消除三份配置漂移的隐患；实测 `beans` 97.8%/87.2%、`context` 87.2%/78.4%、`web` 96.1%/90.3%，样例模块显式跳过（它是应用，`MainApp` 的职责就是打印）。
  - 归属原则写进 ADR-0011：**报告按模块归属，但覆盖来源不受限制**——web 的类被样例端到端覆盖是正当的，不为"报告好看"重复测试。
- [x] **构造器选择策略（ADR-0012）**：只有一个构造器就直接使用；存在多个构造器时必须**恰好有一个**标注 `@MiniAutowired`，否则启动失败并列出全部候选（含已标注者）。`@MiniAutowired` 的 `@Target` 增加 `CONSTRUCTOR`。
  - **不做** Spring 式的隐式回退（多构造器无注解时退回无参构造器）：那会把"忘了标注"这个配置错误变成运行期的行为差异，而选错构造器不会报错。
  - 回归证据：改动前后**既有 119 个测试与两个样例一行未改即通过**，说明此前没有任何代码真的依赖旧的"参数最多优先"启发式——这次是纯语义收敛，不是行为迁移。

### 待完成

- （**阶段 1 已无欠账**）唯一延后项是 ADR-0010 明确决定**暂不引入**的静态分析工具：Spotless 全量格式化、ErrorProne、SpotBugs、以及更完整的 Checkstyle 规则集。这是有意的取舍而非遗漏——引入前需先单独评估"每条规则防的是什么问题"与既有问题基线，避免把重构 diff 变成格式化噪音。

### 退出条件

- 原有容器测试作为回归全部通过（必要时改写断言以匹配新异常类型）——**已达成**（124 个测试全绿）；
- 每个异常类型至少一个测试；环、代理暴露、并发、销毁顺序、扫描确定性均有断言——**已达成**；
- 覆盖率：三个库模块统一门槛 0.85 / 0.75，实测 97.8%/87.2%、87.2%/78.4%、96.1%/90.3%——**已达成**；
- core 模块依赖树中不含 spring/servlet/jackson/cglib——**已由 enforcer 强制**；
- **阶段 1 判定：已完成**（2026-10-11）。`FailureAnalyzer` 属于阶段 2 的交付项（已列在下方阶段 2 清单里），不再是阶段 1 的欠账。

## 阶段 2 · 语义补齐（让它真能用）

- 代理暴露语义按 ADR-0001 落地并测试；启动期 WARN 覆盖 ADR-0002 的例外条款；
- 新增 `@MiniConfiguration` / `@MiniBean`（注册你不拥有的类）、`@MiniPrimary`、集合注入（`List<T>` / `Map<String,T>`）、`@MiniOrder`（BeanPostProcessor 与切面排序）、条件装配的最小版本；
- `@MiniValue` 补齐：默认值语法 `${key:default}`、profile 与外部化配置覆盖、类型转换覆盖 enum/char/float/BigDecimal；
- 结构化启动失败报告（`FailureAnalyzer`，ADR-0005 已定接口）；
- 事件机制与监听器（`ApplicationEvent` / `@MiniEventListener`）；
- **Web**：按 ADR-0007 完成路由语义（method/模板/405）、参数绑定（`@PathVariable`/`@RequestBody`/`required`/`defaultValue`）、统一错误响应与 `@MiniExceptionHandler`、拦截器；
- 两个 sample 的真实 HTTP 集成测试；
- 清除样例对 `spring-core` 的 CGLIB 借用（见待决事项 ADR-0008）。

**退出条件：** `samples/mini-spring-standalone` 能用 HTTP 返回 JSON；404/405/400 语义有测试；`samples/spring-boot-reference` 中 mini 路由与原生路由共存可访问。

## 阶段 3 · 工程化交付（让它像真项目）

- **CI**：GitHub Actions 矩阵（JDK 21/25 × Linux/Windows），缓存本地仓库，`mvn verify` 全绿为合并前提；
- **静态分析**：Spotless + Checkstyle（含禁止 `javax.servlet` 导入与禁止 `System.out` 的规则）、SpotBugs、ErrorProne，作为 `verify` 的一部分；
- **架构守卫**：ArchUnit 断言 ADR-0004 的依赖方向；
- **兼容性门禁**：japicmp 对比上一个发布版本，破坏性变更必须显式批准；
- **覆盖率门禁**：JaCoCo 门槛纳入 CI，不达标即失败；
- **发布工程**：`maven-source-plugin` + `maven-javadoc-plugin`、`flatten-maven-plugin`、BOM（`mini-spring-bom`）、`Automatic-Module-Name`、可复现构建（`project.build.outputTimestamp`）、GPG 签名与 Central 发布插件；
- **基准**：JMH 测量启动耗时、扫描耗时、单例 `getBean` 吞吐，数字写入 README（真框架都给数字）；
- **文档**：CHANGELOG（keep-a-changelog）、CONTRIBUTING、SECURITY、CODE_OF_CONDUCT、issue/PR 模板、`docs/` 下的原理与迁移指南、ADR 状态更新。

**退出条件：** 一条 `mvn verify` 产出全部报告并全部通过；远端 CI 全绿；`v0.1.0` 可被另一个项目以 `dependency` 引入；JMH 数字公开。

## 阶段 4 · 真实使用验证

只用本框架 + HikariCP/JDBC 写一个小服务（短链或待办），Docker 化，做一轮负载测试，写一篇"我的框架在真实场景缺什么"的复盘。

**退出条件：** 服务可部署、可压测；复盘产出的 issue 清单进入下一个里程碑。这一步的证据比任何设计文档都硬。

## 待决事项（需要新的 ADR）

| 编号（预留） | 议题 | 现状与风险 |
| --- | --- | --- |
| ADR-0008 | **代理实现选择** | 样例的 `LogAopBeanPostProcessor` 借用 `spring-core` 中重打包的 CGLIB（`org.springframework.cglib.proxy.*`），因此"不依赖 Spring"的样例目前编译期仍需 `spring-core`（已在该模块 pom 中标注技术债）。需要决定：独立 CGLIB、ByteBuddy，还是仅 JDK 动态代理 + 显式不支持无接口类。 |

## 每个阶段的共同纪律

1. 阶段结束时打 tag，主干永远可 `mvn verify` 通过；
2. 语义变更必须同时更新 ADR、javadoc 与测试，三者不允许脱节；
3. 每个阶段都要删掉一些东西（死代码、假支持、重复实现）——只做加法的重构会退化。
