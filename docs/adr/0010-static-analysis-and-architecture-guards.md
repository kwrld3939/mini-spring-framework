# ADR-0010：静态分析与架构守卫的落地范围

- 状态：Accepted（2026-10-11 确认并已实施）
- 日期：2026-10-11
- 决策者：项目维护者
- 影响范围：构建配置、全部库模块、`config/checkstyle/`

## 背景

"接入静态分析"听起来是个纯加法，实际不是：多数工具默认规则集会把项目重排一遍——格式化、命名、Javadoc、复杂度统统报错。在**重构进行中**引入这种全量规则，会让 diff 里混进成百上千行与语义无关的改动，把真正重要的变更（依赖方向、失败语义）淹没掉。这类"看起来更专业了"的改动，实际降低的是可审查性。

但反过来，有几条规则对应的不是风格偏好，而是**已经在别处踩过的真实问题**：

1. 库代码直接写 `System.out` → 使用者无法把它接进自己的日志体系。第一阶段我们刚把 core 里的 26 处清成 0 处，需要防止回退。
2. 导入 `javax.servlet` → 与 Boot 4 / Tomcat 11 的 `jakarta.servlet` 无法共存（ADR-0007 的整个动机）。
3. 模块依赖方向被破坏 → `enforcer` 只能约束 Maven 坐标，管不到"字节码里到底引用了谁"；而且 ADR-0004 写的方向此前**没有任何自动断言**。

## 决策

**分层引入，只启用"能说清防的是什么问题"的规则。**

### 现在启用的三类

| 手段 | 规则 | 作用范围 |
| --- | --- | --- |
| `maven-enforcer-plugin` | JDK ≥ 21、Maven ≥ 3.9；`beans`/`context` 禁止依赖 `org.springframework:*`、`javax.servlet:*`、`jakarta.servlet:*`、`com.fasterxml.jackson.core:*`、`cglib:*`、`net.sf.cglib:*` | Maven 依赖坐标 |
| Checkstyle（自定义规则集） | `RegexpSinglelineJava` 禁 `System.out/System.err`；`IllegalImport` 禁 `javax.servlet` | `src/main`（`includeTestSourceDirectory=false`） |
| ArchUnit | ① `beans`/`annotation`/`util`/`exception` 不得依赖 `context`/`env`/`web`；② `context`/`env` 不得依赖 `web` 或 `jakarta.servlet`；③ 库不得依赖 `org.springframework`/`com.fasterxml.jackson`；④ 库不得依赖 `javax.servlet` | 字节码层面的实际引用 |

配套约束：

- Checkstyle 规则集**刻意只有两条**，写在 `config/checkstyle/checkstyle.xml`，注释里说明每条防的是什么；
- 样例模块 `samples/mini-spring-standalone` 显式跳过 Checkstyle（它的 `MainApp` 是命令行报告程序，写标准输出是它的职责），「跳过」写在它的 pom 里而不是靠全局豁免；
- 架构守卫放在 `mini-spring-web`：只有它的测试类路径同时包含三个库模块的类。

### 现在明确**不做**的

| 手段 | 为什么暂不做 |
| --- | --- |
| 全量 Checkstyle 规则集（命名/Javadoc/复杂度等） | 与本次目标无关，会制造大量无意义改动；要引入应先单独评估每条规则的价值 |
| Spotless / google-java-format 全量格式化 | 会重排所有源文件（当前是 Tab 缩进），一次改动覆盖整个仓库，后续 code review 无法定位真实变更 |
| ErrorProne | 需要 `-XDcompilePolicy`、`--add-exports` 等编译器与 JVM 参数，配置脆弱；收益主要在新代码，留到 CI 阶段单独评估 |
| SpotBugs | 需要分级与基线（首轮必然报一批既有问题），在缺少"何时修"的决策前引入只会制造噪音 |
| japicmp（二进制兼容门禁） | 只有在有上一个发布版本之后才有意义，属于阶段 3 |

## 后果

正面：

- 每条规则都能回答"它防的是什么问题"，违反时的错误信息直接指向原因与 ADR；
- 依赖方向第一次有了自动断言，且**与 enforcer 互补**：坐标层面与字节码层面各管一段；
- 重构期间的 diff 保持可审查：只有语义变更，没有格式化噪音。

负面：

- 规则少，意味着命名、Javadoc、复杂度等仍然靠评审——这被记在路线图的待完成项里，而不是假装已经覆盖；
- 维护自定义 Checkstyle 规则集是额外成本（很小，但存在）；
- JaCoCo/Checkstyle/ArchUnit 都要引入第三方依赖与插件，构建时间略有增加。

## 被否决的替代方案

1. **一次性引入全量规则集 + 全量格式化**：把重构变成"重排仓库"，短期看起来更规范，长期代价是历史被一次性抹平、真实变更难以追溯。
2. **只靠 enforcer**：管不到字节码引用（例如有人把 jar 直接塞进类路径），也表达不了"层与层之间的方向"。
3. **只靠评审约定**：ADR-0004 的方向约定此前就是这么存在的——写了半年，一个断言都没有。这一次的教训是：**写在文档里的约束，如果没有自动检查，就等于没有**。
4. **写自定义 Maven 插件做检查**：成本远高于 Checkstyle/ArchUnit，收益相同。

## 验收标准

每条规则都必须被证明"违反时真的会拦"，而不是只存在于配置里：

1. **enforcer**：向 `mini-spring-beans` 注入 `spring-core` 后 `mvn validate` 失败（`BannedDependencies failed`），随后按哈希校验确认 pom 已还原——已完成；
2. **Checkstyle**：临时加入一个含 `System.out.println` 的类后 `mvn -pl mini-spring-beans verify` 失败，并指出文件名与行号；验证后删除该文件——已完成；
3. **ArchUnit**：保留一条**反向对照组**——"`web` 不得依赖 `jakarta.servlet`"这条规则对现有代码必然报错（web 就是 Servlet 适配层），断言它确实失败。没有这个对照组，一旦 `ClassFileImporter` 导入了空集合，四条规则会全部"通过"而实际什么都没检查——已完成，且该对照组随构建长期运行；
4. 另有一条断言 `libraryClasses.size() > 20` 作为非空守卫。
