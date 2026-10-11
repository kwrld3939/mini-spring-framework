# ADR-0009：public API 的类型命名

- 状态：Accepted（2026-10-11 确认并已实施）
- 日期：2026-10-11
- 决策者：项目维护者
- 影响范围：全部模块的 public API、文档与示例

## 背景

容器最初是给自己看的单模块学习项目，类型统一用 `My` 前缀：`MyApplicationContext`、`@MyComponent`、`@MyAutowired`、`MyHandlerMapping` 等。在这个语境下它不造成问题，但要把项目做成可被外部依赖的库，它有两个具体代价：

1. **与坐标/包名不一致**：库坐标是 `io.github.kwrld3939:mini-spring-*`，包名是 `...minispring`，而类型却叫 `MyApplicationContext`。使用者第一次看到会以为是别名、兼容层或未清理的残留。
2. **`My` 不携带语义**：它只是占位式的所有格，却会出现在每一份 javadoc、每一行 import、每一段示例里。真实库的 public API 不会这样命名。

同时存在一个**约束**：本仓库刻意保留 `samples/spring-boot-reference`，且 ADR-0007 计划用 `ServletRegistrationBean` 把 mini 的 Servlet 挂进这个 Boot 应用——也就是 mini 的注解与 Spring 的注解会在**同一个工程里共存**。因此新名字必须与 Spring 明确区分，不能直接叫 `@Component` / `@Autowired`。

## 决策

**public API 类型统一使用 `Mini` 前缀**，与包名 `minispring` 对齐，并在阶段 1 的重命名中一次性完成（不留二次改名）。

| 旧名 | 新名 |
| --- | --- |
| `MyApplicationContext` | `MiniApplicationContext` |
| `MyHandlerMapping` | `MiniHandlerMapping` |
| `MyDispatcherServlet` | `MiniDispatcherServlet` |
| `MyComponent` | `MiniComponent` |
| `MyAutowired` | `MiniAutowired` |
| `MyQualifier` | `MiniQualifier` |
| `MyValue` | `MiniValue` |
| `MyScope` | `MiniScope` |
| `MyPostConstruct` | `MiniPostConstruct` |
| `MyPreDestroy` | `MiniPreDestroy` |
| `MyRequestMapping` | `MiniRequestMapping` |
| `MyRequestParam` | `MiniRequestParam` |
| `MyLog` | `MiniLog` |

执行范围：13 个类型（含同名文件，用 `git mv` 保留历史）、全部 java 引用与字符串、README、`SpringBoot报错指南.md`、`docs/` 下全部文档。与 Spring 的对照关系（例如 `@MiniComponent` ↔ `@Component`）继续由 README 的对照表承担，避免使用者找不到对应概念。

## 后果

正面：

- 包名、artifactId、类型名三者一致，`io.github.kwrld3939.minispring.MiniApplicationContext` 是可解释的；
- 与 Spring 注解同名的冲突风险被排除，样例中两套注解共存时不会误导入；
- 0.1.0 之前没有外部使用者，破坏性更名零迁移成本——这是最便宜的时机，错过就要等主版本。

负面：

- 更名本身是破坏性的，`1.0.0` 之后若再改必须走主版本；
- 文档中既有的旧名引用需同步（本次已全量替换，含各 ADR 背景章节里引用的旧类名）；
- `Mini` 比 `My` 长，示例代码略啰嗦——相比与 Spring 冲突的风险，这个代价可接受。

## 被否决的替代方案

1. **完全去掉前缀**（`ApplicationContext`、`@Component`、`@Autowired`）：API 最干净，但与 Spring 同名；ADR-0007 计划让两者在同一应用共存，IDE 自动导入与阅读都会出错。
2. **保持 `My*`**：零改动，但库名与类型名不一致，观感停留在练习代码；且以后要改就是第二次全量重命名。
3. **改成 `Ms*` 之类更短的前缀**：字数更省，但可读性与可搜索性下降（`MsComponent` 需要额外解释），不如 `Mini` 直白。

## 验收标准

1. 全仓库（java 与文档）在大小写敏感检查下不存在 `My*` 类型引用；
2. 34 个 java 文件的声明包与目录一致（脚本校验通过）；
3. `mvn clean verify` 六个模块全绿、12 个测试通过；
4. README 的"与 Spring 的对应关系"表使用新名，且 ADR-0001 ~ 0007 中引用的类名已同步更新。
