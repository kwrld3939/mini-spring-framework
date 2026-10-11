# ADR-0004：模块划分、坐标与依赖方向

- 状态：Accepted（2026-10-11 确认；模块骨架已按本 ADR 落地）
- 实施：已落地（2026-10-11）。骨架、坐标、包重命名与 jakarta 迁移均已完成。验收标准 2（core 依赖树约束）由 `maven-enforcer-plugin` 强制；验收标准 5（依赖方向）由 `mini-spring-web` 中的 ArchUnit 测试断言——两者都做过变异检验或反向对照，见 ADR-0010。`mini-spring-aop` 与 `mini-spring-test` 尚未创建：按本 ADR 的原则"模块在它有内容时出现"，等 AOP 代理实现从样例中抽出后再建，避免留下空模块。
- 日期：2026-10-11
- 决策者：项目维护者
- 影响范围：构建结构、全部模块

## 背景

当前是单模块 Maven 工程，两个互不相干的入口挤在同一棵 `src` 树里：

- 真实 Spring Boot 应用：`com.example.demo`（`SpringbootDemoApplication`、`HelloController`、`@SpringBootTest`）；
- 手写容器与演示：`com.demo` / `com.demo.mini`（`MainApp`、示例 service/controller/processor）。

三个具体问题：

1. **servlet API 版本冲突是死结。** `pom.xml:42-46` 声明 `javax.servlet:javax.servlet-api:4.0.1`，而工程继承的 Spring Boot 4.1.0 在运行时带来 Tomcat 11.0.22 + `jakarta.servlet-api:6.1.0`（已在本机仓库确认）。`MiniDispatcherServlet` 继承 `javax.servlet.http.HttpServlet`（`MiniDispatcherServlet.java:14`），**在同一个 JVM 里不可能注册进 Boot 的 Tomcat**，且全仓库没有任何地方注册它。它目前是永远无法运行的死代码。
2. **库继承应用父 POM。** `pom.xml:5-10` 继承 `spring-boot-starter-parent`。库不应被某个应用的依赖管理与构建约定绑架，否则使用者会被迫接受 Boot 的版本决策。
3. **命名空间是占位符。** `com.demo.mini` 不具备任何组织标识，也无法发布；且 public API 与示例代码、内部实现混在同一包空间。

## 决策

### 1. 模块划分与依赖方向

依赖方向严格单向，禁止反向依赖与循环依赖（由 ArchUnit 在阶段 3 强制）：

```
mini-spring-beans      BeanDefinition、BeanFactory 接口、注册表、异常层级、类型转换
        ↑
mini-spring-context    扫描、Environment/PropertySource、DI、生命周期、BeanPostProcessor 链
        ↑
mini-spring-aop        代理实现（JDK 动态代理 + CGLIB）、切点与通知、advisor 排序
        ↑
mini-spring-web        jakarta.servlet 适配、HandlerMapping、参数解析、异常处理、JSON（可选）
        ↑
mini-spring-test       测试支持（容器启动、断言工具）

samples/mini-spring-standalone    嵌入式 Tomcat 独立启动（证明框架自足）
samples/spring-boot-reference     原生 Spring Boot 写法对照 + 通过 ServletRegistrationBean 挂载 mini Servlet
```

关键约束：

- `beans` 与 `context` 对 servlet、Spring、Jackson **零依赖**；唯一第三方依赖是 SLF4J API 与 ASM（后者仅 `context`）。
- `web` 对 `jakarta.servlet-api` 使用 `provided` + `optional`，不污染使用者。
- `aop` 不依赖 `web`。当前位于 `com.demo.processor` 的 `LogAopBeanPostProcessor` 属于**示例**，不进框架核心。

### 2. 父 POM 策略

库的父 POM **不继承** `spring-boot-starter-parent`，自建 aggregator parent，用 `dependencyManagement` 统一版本。`samples/spring-boot-reference` 需要 Boot 时，由它自己继承 Boot parent，两棵继承树互不影响。

### 3. 坐标与命名空间

- `artifactId`：各模块统一 `mini-spring-*`；aggregator 为 `mini-spring-parent`。
- `groupId`：**`io.github.kwrld3939`**（2026-10-11 确认）。取自维护者 GitHub 账号 `kwrld3939`，可发布到 Maven Central 且不需要持有域名。
- `version`：从 `0.1.0-SNAPSHOT` 起步；`1.0.0` 表示 public API 稳定并开始受 japicmp 兼容性门禁约束。
- 包命名：public API 位于 `io.github.kwrld3939.minispring.*`；内部实现位于 `....internal`，**不在文档中承诺稳定**，可在次版本变更。（类型名是否去掉 `My` 前缀见 `docs/roadmap.md` 待决事项 ADR-0009。）
- 包重命名与模块拆分**同一批完成**，避免二次 churn 与两次全量回归。

### 4. Java 基线

`release=21`。理由：与本机 Temurin 21.0.10 一致、是当前采用面最广的 LTS、且足够覆盖 Boot 4 生态。不使用预览特性。CI 增加 JDK 25 作为第二编译/测试目标，但**不改变字节码基线**——库的目标是让使用者用得上的最低版本。

### 5. API 稳定性

- public API 全部标 `@since`；
- `0.x` 阶段允许次版本破坏性变更，`1.0.0` 之后破坏性变更只允许在主版本；
- 二进制兼容由 japicmp 在 `verify` 阶段强制（阶段 3 接入）。

## 后果

正面：

- jakarta 死结被解开（core 无 servlet 依赖，web 只在 jakarta 上）；
- 使用者可只依赖需要的模块（例如只要 IoC 就只引 `beans` + `context`）；
- 库与应用彻底分离，各自的构建约定不再互相牵制；
- 依赖方向成为可被自动检查的架构规则，而不是口头约定。

负面：

- 构建复杂度上升（多模块、两棵父 POM、聚合与发布顺序）；
- 包重命名会让现有使用者的 import 失效，需在 CHANGELOG 与迁移文档中明确说明；
- 拆分初期需要一次性搬运大量文件，回归风险集中在阶段 1。

## 被否决的替代方案

1. **保持单模块，只用 Maven profile 区分库与示例**：无法解决依赖污染（core 仍会带上 servlet 与 Spring），也无法表达依赖方向。
2. **所有模块都继承 Boot parent**：省事，但把库的生命周期绑在一个应用的版本节奏上，与本次目标（可被外部项目依赖）直接冲突。

## 验收标准

1. 父工程一次 `mvn verify` 构建全部模块（含 samples）。
2. `mini-spring-beans` 与 `mini-spring-context` 的依赖树中不含 `spring-*`、`servlet`、`jackson`、`cglib`（用 `maven-enforcer-plugin` 的 bannedDependencies 断言并纳入 CI）。
3. 全仓库不再出现 `javax.servlet` 的编译引用（用禁止导入的静态检查守住）。
4. 每个模块的 `pom.xml` 中 `artifactId` 与目录名一致，版本由父 POM 统一管理。
5. ArchUnit 规则：`beans` 不依赖 `context`/`aop`/`web`，`context` 不依赖 `aop`/`web`，`aop` 不依赖 `web`。
