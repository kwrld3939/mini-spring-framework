# ADR-0007：Web 集成与 jakarta.servlet 迁移

- 状态：Accepted（2026-10-11 确认；jakarta 迁移已在阶段 1 骨架中完成，其余 Web 能力在阶段 2 实施）
- 日期：2026-10-11
- 决策者：项目维护者
- 影响范围：`mini-spring-web`、`samples/*`

## 背景

工程里有一个完整的 `MiniDispatcherServlet`（`MiniDispatcherServlet.java`）、`MiniHandlerMapping`、`RequestArgumentResolver`，但：

1. **它永远不会被注册。** 全仓库没有任何 `web.xml`、`ServletRegistrationBean` 或 `@Bean` 注册它；`com.example.demo` 的 Boot 应用用的是自己的 DispatcherServlet。当前它是"编译得过、跑不起来"的示例代码。
2. **它注定注册不进去。** 它编译在 `javax.servlet-api:4.0.1`（`pom.xml:42-46`、`MiniDispatcherServlet.java:14`），而 Boot 4.1.0 运行时是 Tomcat 11.0.22 + `jakarta.servlet-api:6.1.0`（本机仓库已确认）。包名不同，同一 JVM 内不可能挂进 Boot 的容器。这不是配置问题，是构建结构造成的死结，必须由 ADR-0004 的模块拆分来解决。
3. **路由语义过于简陋。** 只按 path 匹配（`MiniHandlerMapping.java:44-53`），不区分 HTTP method；找不到 handler 一律 404（`MiniDispatcherServlet.java:56-58`），没有 405；`normalizePath` 会把 `//`、结尾 `/` 归一，但没有路径模板（`@PathVariable`）能力。
4. **错误语义不安全。** 处理失败抛 `ServletException`（`:68-70`），参数缺失抛 `IllegalArgumentException`（`RequestArgumentResolver.java:37`）——最终都会变成 500，而参数缺失应当是 400。异常信息还会直接暴露内部类名。

## 决策

### 1. 依赖与迁移

`mini-spring-web` 只依赖 `jakarta.servlet-api:6.1.0`（`provided` + `optional`）。**全仓库移除 `javax.servlet-api` 依赖与 `javax.servlet` 导入**，由 ADR-0004 的验收标准（禁止导入检查）守住。core（`beans`/`context`/`aop`）对 servlet 零依赖。

### 2. 路由语义显式化

- 匹配维度：path + HTTP method（`GET`/`POST`/... + `*`），支持 path 模板变量；
- 路径模板在**注册期**编译为匹配器（不允许每请求重复解析正则），支持 Ant 风格 `*` / `**` 与 `{name}` 变量；
- 二义性处理：同一 path+method 注册两次 → `BeanDefinitionException`（注册期失败，而不是运行期覆盖）；模板与字面量同时匹配时字面量优先（更具体优先）；
- 状态码：无 path 匹配 → 404；path 匹配但 method 不匹配 → 405 且返回 `Allow` 头；参数绑定失败 → 400；处理器内部抛异常 → 由异常处理器决定（默认 500）。

### 3. 参数绑定

- `@MiniRequestParam`：增加 `required`（默认 true）与 `defaultValue`；缺失必填 → 400；类型转换失败 → 400（不是 500），异常为 `RequestBindingException`（见 ADR-0005），消息包含参数名、原始值、期望类型；
- `@MiniPathVariable`：从模板变量绑定并做类型转换；
- `@MiniRequestBody`：由 Jackson 反序列化；Jackson 为 **optional** 依赖，缺失时抛出可读异常（说明需要引入哪个坐标），绝不让 `NoClassDefFoundError` 泄漏给使用者；
- `HttpServletRequest`/`HttpServletResponse` 注入保持现状（`RequestArgumentResolver.java:20-27`）。

### 4. 响应与错误模型

- 统一错误响应体（结构化字段：`status`、`error`、`message`、`path`），默认**不泄露**内部类名与堆栈；
- `@MiniExceptionHandler` 按异常类型解析处理器，匹配规则：精确类型 → 父类 → 兜底；解析期建立索引，运行期不做线性反射扫描；
- 内容协商：默认 `application/json`，无 Jackson 时降级为 `text/plain` 并在启动日志 WARN 说明。

### 5. 两种集成方式都必须验证

| 示例模块 | 目的 | 验收 |
| --- | --- | --- |
| `samples/mini-spring-standalone` | 嵌入式 Tomcat 独立启动，证明框架自足、不依赖 Spring | 真实 HTTP 请求返回 JSON；404/405/400 各有断言 |
| `samples/spring-boot-reference` | 用 `ServletRegistrationBean` 把 mini Servlet 挂进 Boot 应用，与原生 `HelloController` 共存 | 同一端口下 `/`（原生）与 mini 路由（`/mini/...`）都能访问，证明可共存、可对照 |

`MiniDispatcherServlet` 保留名称（延续学习者认知），但实现降级为**薄适配器**：解析请求 → 交给 `HandlerMapping` → 写响应。业务语义（绑定、异常、状态码）全部在框架内部，不在 Servlet 类里。

## 后果

正面：

- jakarta 死结解开，Web 部分第一次真正可运行；
- 404/405/400/500 语义正确，这是"能被真实客户端使用"的最低要求；
- 两个 sample 覆盖"独立部署"与"嵌入既有应用"两条真实路径，并顺带成为 ADR-0004 依赖方向的活体验证；
- Web 与 core 解耦，使用者可以只用 IoC/AOP 而不引入 servlet。

负面：

- 需要维护自己的路径匹配器与参数解析器，边界情况（编码、`+` 号、数组参数、multipart）需要逐步补齐；
- Jackson 作为 optional 依赖意味着需要维护"有/无 Jackson"两条路径的测试；
- 从 `javax` 迁移会破坏任何按旧 API 编写的使用者代码，必须在 CHANGELOG 与迁移文档中显著标注。

## 被否决的替代方案

1. **保留 `javax.servlet`，把 Boot 降级到 3.x 以匹配**：为了一个手写 Servlet 而回退整个应用的框架版本，因果倒置。
2. **放弃 Servlet，改用自研 HTTP 服务器（Netty 等）**：破坏"能挂进既有容器"的核心价值，且工程量远超收益。
3. **把 Web 部分并入 `context` 模块**：会让所有只用 IoC 的使用者被迫带上 servlet 依赖，违反 ADR-0004 的依赖方向。

## 验收标准

1. 全仓库无 `javax.servlet` 导入（静态检查强制）；
2. 两个 sample 都能通过真实 HTTP 集成测试（随机端口 + HTTP 客户端）；
3. 404（无路由）、405（method 不匹配且带 `Allow` 头）、400（参数缺失/类型错误）各有断言；
4. 无 Jackson 时启动 WARN 且异常信息可读，有测试覆盖；
5. 注册重复路由在启动期失败，有测试；
6. 默认错误响应不包含内部类名（有测试断言响应体不含 `com.demo` / `minispring` 包名）。
