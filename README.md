# Mini Spring IoC Container

[![CI](https://github.com/kwrld3939/mini-spring-framework/actions/workflows/ci.yml/badge.svg)](https://github.com/kwrld3939/mini-spring-framework/actions/workflows/ci.yml)

更新日期：2026-10-11

> **重构状态（2026-10-11 起）**
>
> 本项目正从"学习版演示"升级为**可被外部项目依赖的库**：已拆为 `mini-spring-beans` / `mini-spring-context` / `mini-spring-web` 三个库模块加两个样例模块，包名统一为 `io.github.kwrld3939.minispring.*`，并完成 `javax.servlet` → `jakarta.servlet` 迁移。
>
> 架构决策见 [docs/adr/](docs/adr/README.md)（**11 篇**；编号 0008 为"代理实现选择"预留，尚未撰写）。**已落地**：ADR-0001 依赖解析与代理暴露、ADR-0002 循环依赖范围、ADR-0003 类路径扫描（ASM，不加载用户的类）、ADR-0004 模块划分与坐标、ADR-0005 异常层级、ADR-0006 并发与生命周期冻结、ADR-0009 类型命名、ADR-0010 静态分析与架构守卫、ADR-0011 覆盖率门槛落点、ADR-0012 构造器选择，以及 ADR-0007 的 jakarta 迁移部分。**尚未落地**：ADR-0007 的 Web 能力（路由 method/模板、`@MiniPathVariable`/`@MiniRequestBody`、404/405/400、`@MiniExceptionHandler`）、ADR-0008 代理实现、`FailureAnalyzer` 结构化启动失败报告——推进计划与退出条件见 [docs/roadmap.md](docs/roadmap.md)。
>
> 四类质量门禁已全部接入并在 CI 上运行：enforcer（禁依赖）、Checkstyle（禁 `System.out`、禁 `javax.servlet` 导入）、ArchUnit（依赖方向）、JaCoCo（库模块行 85% / 分支 75%）；CI 矩阵为 JDK 21/25 × Linux/Windows。
>
> 本文以下内容中与 ADR 冲突的部分以 ADR 为准；"简易 AOP 实现"一节已按新语义重写，其余章节会随阶段 1 的推进继续重写。

## 更新记录（2026-08-04）

- 新增 setter 注入示例，补上循环依赖的学习场景
- 简易 AOP 升级为 JDK 动态代理 + CGLIB 回退
- RequestMapping 增加请求参数绑定和基础类型转换
- 新增对应单元测试，验证新能力是否可回归

## 后续拓展（2026-08-04 起）

- `@PathVariable` 和路径模板解析
- `@RequestBody` 与 JSON 入参绑定
- 统一异常处理与返回体封装
- 事件机制和监听器
- `BeanFactory` / `ApplicationContext` 分层

这是一个用于学习 Spring 核心原理的 Mini Spring 项目。项目不依赖 Spring 容器本身，而是基于 Java 原生反射、注解和 ClassLoader 实现一个轻量级 IoC 容器，覆盖组件扫描、BeanDefinition 注册、依赖注入、Bean 生命周期、作用域、配置注入、BeanPostProcessor 扩展点、简易 AOP 以及 RequestMapping 映射等核心流程。

项目目标不是替代 Spring，而是通过手写简化版本，理解 Spring IoC、DI、AOP 和 MVC 映射机制背后的设计思想。

## 技术栈

- Java 21
- Maven
- JUnit 5
- Java Reflection
- Java Annotation
- Java ClassLoader
- JDK Dynamic Proxy
- Spring CGLIB
- javax.servlet-api

## 核心功能

| 功能 | 说明 |
| --- | --- |
| 组件扫描 | 使用 Java 原生 ClassLoader 递归扫描指定根包下的 class 文件 |
| 自定义注解 | 实现 `@MiniComponent`、`@MiniAutowired`、`@MiniQualifier`、`@MiniValue`、`@MiniRequestParam` 等注解 |
| BeanDefinition | 保存 Bean 的元信息，包括 beanName、beanClass、scope、lazyInit |
| IoC 容器 | 由 `MiniApplicationContext` 统一创建、管理和获取 Bean |
| 构造器注入 | 根据构造器参数类型和 `@MiniQualifier` 从容器中解析依赖 |
| 字段注入 | 通过反射给 `@MiniAutowired` 字段注入依赖 |
| setter 注入 | 支持在方法上使用 `@MiniAutowired`，用于演示 setter 循环依赖 |
| 配置注入 | 通过 `@MiniValue` 从 `application.properties` 注入配置值 |
| 作用域 | 支持 singleton 和 prototype |
| 生命周期 | 支持 `@MiniPostConstruct` 初始化回调和 `@MiniPreDestroy` 销毁回调 |
| BeanPostProcessor | 支持 Bean 初始化前后扩展点 |
| 简易 AOP | 基于 BeanPostProcessor 实现 `@MiniLog` 日志增强，支持 JDK 动态代理和 CGLIB 回退 |
| RequestMapping | 扫描 `@MiniRequestMapping`，建立 URL 到方法的映射关系，并支持基础请求参数绑定 |
| 单元测试 | 使用 JUnit 5 验证容器核心功能 |
| 报错指南 | `SpringBoot报错指南.md` 记录常见开发、调试错误和解决方案 |

## 项目结构

```text
mini-spring-parent/            （聚合 POM，groupId io.github.kwrld3939，version 0.1.0-SNAPSHOT）
├── mini-spring-beans          BeanDefinition、BeanPostProcessor、注解、类型转换（零第三方运行时依赖）
│   └── src/main/java/io/github/kwrld3939/minispring/
│       ├── annotation/        MiniComponent、MiniAutowired、MiniQualifier、MiniValue、MiniScope、
│       │                      MiniPostConstruct、MiniPreDestroy
│       ├── beans/             BeanDefinition、BeanPostProcessor
│       └── util/              SimpleTypeConverter
├── mini-spring-context        组件扫描、配置、依赖注入、生命周期、BeanPostProcessor 链
│   └── .../minispring/context/MiniApplicationContext.java
├── mini-spring-web            HandlerMapping、参数解析、DispatcherServlet 适配（jakarta.servlet 6.1）
│   └── .../minispring/web/    MiniDispatcherServlet、MiniHandlerMapping、RequestArgumentResolver
│       └── annotation/        MiniRequestMapping、MiniRequestParam
└── samples/
    ├── spring-boot-reference  原生 Spring Boot 对照样例（Boot 4.1.0，自己继承 Boot parent）
    │   └── .../minispring/samples/boot/
    │       ├── SpringbootDemoApplication.java
    │       └── HelloController.java
    └── mini-spring-standalone  只用 mini-spring 运行的示例应用
        └── .../minispring/samples/standalone/
            ├── MainApp.java
            ├── controller/    PayController、UserController
            ├── service/       AlphaService、BetaService、AliPayService、WechatPayService、
            │                  PayService、OrderService、ReportService、UserService
            ├── processor/     LogAopBeanPostProcessor、LogBeanPostProcessor
            └── annotation/    MiniLog
```

容器测试分两层：框架级测试在 `mini-spring-context/src/test/`，端到端用例在 `samples/mini-spring-standalone/src/test/`（详见下方"测试覆盖"）。

## 启动流程

项目入口是：

```java
io.github.kwrld3939.minispring.samples.standalone.MainApp
```

核心启动流程如下：

```text
MainApp.main()
-> new MiniApplicationContext(MainApp.class.getPackageName())
-> loadProperties()
-> scanBeanClasses()
-> 注册 BeanDefinition
-> createBeans()
-> 构造器注入
-> injectDependencies()
-> 字段注入 / setter 注入 / @MiniValue 配置注入
-> registerBeanPostProcessors()
-> initializeBeans()
-> BeanPostProcessor before
-> @MiniPostConstruct
-> BeanPostProcessor after
-> AOP 代理增强
-> getBean() 使用 Bean
-> MiniHandlerMapping 建立 URL 映射
-> context.close()
-> @MiniPreDestroy
```

概括：

```text
扫描包 -> 解析注解 -> 注册 BeanDefinition -> 创建 Bean -> 依赖注入 -> 初始化扩展 -> 生命周期回调 -> AOP 代理 -> 单例池管理 -> getBean 使用
```

启动流程图：

```text
MainApp 启动 -> 创建 MiniApplicationContext -> ClassLoader 扫描根包 -> 解析 @MiniComponent -> 注册 BeanDefinition -> 实例化 Bean -> 构造器/字段/setter/@Value 注入 -> 执行 BeanPostProcessor -> 生命周期初始化 -> 初始化后生成 AOP 代理 -> 替换/更新单例池中的原始 Bean -> getBean 获取使用 -> close 触发销毁
```

## IoC 实现原理

IoC 的核心是把对象的创建权从业务代码转移给容器。

在本项目中，`MiniApplicationContext` 是容器核心。启动时传入根包名，例如：

```java
MiniApplicationContext context = new MiniApplicationContext("io.github.kwrld3939.minispring.samples.standalone");
```

容器会使用 ClassLoader 将包名：

```text
io.github.kwrld3939.minispring.samples.standalone
```

转换为路径：

```text
io/github/kwrld3939/minispring/samples/standalone
```

然后递归扫描该目录下的 `.class` 文件，加载类对象，并判断类上是否存在：

```java
@MiniComponent
```

如果存在，就将该类注册为一个 BeanDefinition。

## BeanDefinition

`BeanDefinition` 是 Bean 的元信息，不是 Bean 实例。

当前保存的信息包括：

```java
private final String beanName;
private final Class<?> beanClass;
private final String scope;
private final boolean lazyInit;
```

它的作用是连接“类信息”和“对象实例”：

```text
扫描阶段：Class -> BeanDefinition
创建阶段：BeanDefinition -> Object
```

这也是 Spring 中非常重要的设计思想：容器不会只保存对象，还会先保存对象的定义信息。

## DI 实现原理

DI 是 IoC 的具体实现方式，也就是容器创建对象后，帮对象装配依赖。

本项目支持三种注入方式。

### 构造器注入

示例：

```java
public PayController(@MiniQualifier("aliPayService") PayService payService) {
    this.payService = payService;
}
```

容器会解析构造器参数，根据参数类型和 `@MiniQualifier` 找到对应 Bean，再通过反射调用构造器创建对象。

**构造器的选择规则（ADR-0012）**：只有一个构造器时直接使用（含编译器生成的隐式无参构造器，不需要注解）；
存在多个构造器时，必须**恰好有一个**标注 `@MiniAutowired`，否则启动期失败并列出全部候选：

```text
Bean 定义不合法（beanName='orderService'）：存在多个构造器，但没有任何一个标注 @MiniAutowired；容器不会替你猜该用哪一个
  候选:
    OrderService()
    OrderService(com.demo.PayService)
  建议: 在需要容器使用的构造器上加 @MiniAutowired。
```

注意容器**不做**隐式回退（不像 Spring 那样在"多构造器且无注解"时退回无参构造器）：那会把"忘了标注"
这个配置错误变成运行期的行为差异，而选错构造器是不会报错的。

### 字段注入

示例：

```java
@MiniAutowired
private UserService userService;
```

容器会遍历 Bean 的字段，如果字段上存在 `@MiniAutowired`，则从容器中按字段名或类型查找依赖，并通过 `field.set()` 完成注入。

### setter 注入

示例：

```java
@MiniAutowired
public void setBetaService(BetaService betaService) {
    this.betaService = betaService;
}
```

容器会遍历 Bean 的方法，如果方法上存在 `@MiniAutowired`，且方法只有一个参数，就从容器中解析该参数对应的依赖，并通过反射调用该方法完成注入。当前项目使用 `AlphaService` 和 `BetaService` 演示 setter 方式下的循环依赖。

## 配置注入

项目支持从 `application.properties` 中读取配置：

```properties
app.name=mini-spring
```

然后通过 `@MiniValue` 注入：

```java
@MiniValue("app.name")
private String appName;
```

容器会读取配置值，并根据字段类型进行简单类型转换，目前支持：

```text
String
int / Integer
long / Long
boolean / Boolean
double / Double
```

## Scope 作用域

项目支持两种作用域：

| Scope | 说明 |
| --- | --- |
| singleton | 容器中只保存一个实例，后续 `getBean()` 返回同一个对象 |
| prototype | 每次 `getBean()` 都创建一个新对象，不放入单例池 |

示例：

```java
@MiniScope("prototype")
@MiniComponent
public class OrderService {
}
```

运行结果示例：

```text
UserService singleton same instance: true
OrderService prototype same instance: false
```

## Bean 生命周期

当前 Bean 生命周期如下：

```text
实例化
-> 依赖注入
-> BeanPostProcessor before
-> @MiniPostConstruct
-> BeanPostProcessor after
-> 使用 Bean
-> @MiniPreDestroy
```

示例（组件用 SLF4J 记录生命周期——库与样例都不写 `System.out`，输出格式由使用方的日志后端决定）：

```java
@MiniPostConstruct
public void init() {
    LOGGER.info("UserService 初始化完成, appName={}", appName);
}

@MiniPreDestroy
public void destroy() {
    LOGGER.info("UserService 已销毁");
}
```

容器关闭时：

```java
context.close();
```

会触发 `@MiniPreDestroy` 标记的方法。

## BeanPostProcessor

`BeanPostProcessor` 是容器提供的扩展点，允许在 Bean 初始化前后插入自定义逻辑。

接口定义：

```java
public interface BeanPostProcessor {

    Object postProcessBeforeInitialization(String beanName, Object bean);

    Object postProcessAfterInitialization(String beanName, Object bean);
}
```

项目中的 `LogBeanPostProcessor` 会在两个阶段输出日志。这类"每个 Bean 一条"的明细刻意放在 **DEBUG** 级别：
默认（INFO）不打印，需要排查时把它调到 DEBUG 即可。

```text
DEBUG LogBeanPostProcessor - BeanPostProcessor before: userService
INFO  UserService - UserService 初始化完成, appName=mini-spring
DEBUG LogBeanPostProcessor - BeanPostProcessor after: userService
```

（输出格式由使用方的日志后端决定；样例用的是 `samples/mini-spring-standalone/src/main/resources/logback.xml`。）

这个机制是后续实现 AOP 的基础。

## 简易 AOP 实现

项目通过 `@MiniLog`、`BeanPostProcessor`、JDK 动态代理和 CGLIB 实现了一个简易 AOP。

示例：

```java
@Override
@MiniLog
public String pay() {
    return "ali pay";
}
```

`LogAopBeanPostProcessor` 会在 Bean 初始化后判断：

```text
1. 当前 Bean 是否存在带 @MiniLog 的方法
2. 如果 Bean 实现了接口，使用 JDK 动态代理
3. 如果 Bean 没有实现接口，尝试使用 CGLIB 创建子类代理
```

如果目标 Bean 实现了接口，就通过：

```java
Proxy.newProxyInstance(...)
```

创建代理对象，并替换单例池中的原始 Bean。

如果目标 Bean 没有实现接口，例如 `ReportService`，则通过 CGLIB 创建代理：

```java
Enhancer enhancer = new Enhancer();
enhancer.setSuperclass(beanClass);
```

运行效果（`@MiniLog` 方法的调用会记录耗时，异常也会被记录后继续抛出）：

```text
PayService proxy class: jdk.proxy2.$Proxy7
14:22:33.123 INFO  LogAopBeanPostProcessor - AOP before: aliPayService.pay
14:22:33.124 INFO  LogAopBeanPostProcessor - AOP after: aliPayService.pay 耗时 1 ms
Invoke payService.pay(): ali pay
```

当前 AOP 的限制：

```text
1. CGLIB 不能代理 final 类，也不能增强 final 方法
2. 环上的反向引用拿到的是"早期引用"（未代理对象），与 Spring 的已知行为一致（ADR-0002 的例外条款）
3. 只实现了日志增强，没有实现切点表达式、通知顺序、异常通知等完整 AOP 能力
```

**"构造器注入拿到未代理对象"已经不再是限制。** 容器的 `getBean` 是唯一漏斗：一次调用内完成
"实例化 → 属性注入 → 后置处理 before → `@MiniPostConstruct` → 后置处理 after（代理在此产生）→ 写入单例池"，
而依赖解析递归调用同一个漏斗。因此**任何注入点拿到的都是终态对象，包括代理**。

下面两处调用现在都会带增强：

```java
handlerMapping.invoke("/pay");                            // 经由 PayController 构造器注入的依赖
((PayService) context.getBean("aliPayService")).pay();    // 直接按名称取 Bean
```

输出：

```text
14:22:33.123 INFO  LogAopBeanPostProcessor - AOP before: aliPayService.pay
14:22:33.124 INFO  LogAopBeanPostProcessor - AOP after: aliPayService.pay 耗时 1 ms
ali pay
```

这条不变式有专门的回归测试 `injectsTheAopProxyIntoConstructorDependencies`：它断言 `PayController`
构造器注入到的对象**就是**单例池里的那个代理。重构前的实现在这条断言上必然失败——当时代理是在"第二趟初始化"
里回填单例池的，注入者持有的是原始对象，`invoke("/pay")` 会静默地不带增强。

## RequestMapping 映射机制

项目实现了简化版的请求映射机制。

示例：

```java
@MiniRequestMapping(path = "/user/hello")
public String hello() {
    return userService.hello();
}
```

带请求参数的示例：

```java
@MiniRequestMapping(path = "/user/inspect")
public String inspect(HttpServletRequest request, HttpServletResponse response,
        @MiniRequestParam("name") String name,
        @MiniRequestParam("count") int count) {
    return userService.hello() + " | " + name + ":" + count;
}
```

`MiniHandlerMapping` 会遍历容器中的 Bean，扫描方法上的 `@MiniRequestMapping`，并建立映射表：

```text
/user/hello -> UserController#hello
/user/inspect -> UserController#inspect
/pay -> PayController#pay
```

调用时：

```java
handlerMapping.invoke("/user/hello");
```

本质是：

```text
根据 path 查找 Method
-> 使用 RequestArgumentResolver 解析 request / response / @MiniRequestParam
-> 通过 method.invoke(bean, args) 反射调用
-> 返回方法结果
```

这对应 Spring MVC 中 HandlerMapping 的核心思想。

## 与 Spring 的对应关系

| 本项目 | Spring |
| --- | --- |
| `@MiniComponent` | `@Component` |
| `@MiniAutowired` | `@Autowired` |
| `@MiniQualifier` | `@Qualifier` |
| `@MiniValue` | `@Value` |
| `@MiniScope` | `@Scope` |
| `@MiniPostConstruct` | `@PostConstruct` |
| `@MiniPreDestroy` | `@PreDestroy` |
| `@MiniRequestMapping` | `@RequestMapping` |
| `@MiniRequestParam` | `@RequestParam` |
| `@MiniLog` | 自定义切面注解 |
| `BeanDefinition` | Spring BeanDefinition |
| `BeanPostProcessor` | Spring BeanPostProcessor |
| `MiniApplicationContext` | Spring ApplicationContext |
| `MiniHandlerMapping` | Spring HandlerMapping |
| `MiniDispatcherServlet` | Spring DispatcherServlet |
| `RequestArgumentResolver` | Spring MVC HandlerMethodArgumentResolver |

## 运行方式

这是多模块聚合工程，所有命令都在仓库根目录执行。

构建并跑全部测试（推荐的第一步）：

```powershell
.\mvnw.cmd verify
```

运行 Mini Spring 示例应用（`samples/mini-spring-standalone`）：

```powershell
# 1) 把框架模块安装到本地仓库（示例模块按坐标解析它们）
.\mvnw.cmd install -DskipTests
# 2) 生成示例模块的运行时 classpath
.\mvnw.cmd -pl samples/mini-spring-standalone dependency:build-classpath "-Dmdep.outputFile=target/cp.txt"
# 3) 运行
java -cp "samples/mini-spring-standalone/target/classes;$(Get-Content samples/mini-spring-standalone/target/cp.txt -Raw)" io.github.kwrld3939.minispring.samples.standalone.MainApp
```

也可以直接用 `.vscode/launch.json` 里的两个配置：`Run Mini Spring (samples/mini-spring-standalone)` 与 `Run Spring Boot (samples/spring-boot-reference)`。

只跑容器回归测试（会自动带上它依赖的框架模块）：

```powershell
.\mvnw.cmd -pl samples/mini-spring-standalone -am test
```

运行测试：

```bash
./mvnw test
```

Windows PowerShell：

```powershell
.\mvnw.cmd test
```

如果本机 Maven 仓库放在 D 盘，可以指定本地仓库运行：

```powershell
.\mvnw.cmd "-Dmaven.repo.local=D:/maven-repository" test
```

常见开发和调试错误可以参考：

```text
SpringBoot报错指南.md
```

## 测试覆盖

全仓 **124 个测试**，按模块与关注点分层：

| 位置 | 数量 | 覆盖内容 |
| --- | --- | --- |
| `mini-spring-context/.../fixture/ConstructorSelectionTests.java` | 5 | 构造器选择（ADR-0012）：唯一构造器免注解、多个构造器必须恰好一个 `@MiniAutowired`、含糊即失败并列出候选 |
| `mini-spring-context/.../support/ClassPathScannerTests.java` | 7 | ASM 扫描：不加载类、不触发静态初始化、只在注册组件时加载、jar 扫描、嵌套 jar 拒绝、结果确定性 |
| `mini-spring-context/.../support/ClassPathScanningFailureTests.java` | 3 | 扫描/注册失败：组件标注在接口上、损坏的 class 文件、类无法加载（缺依赖）——都必须给出可执行的错误 |
| `mini-spring-context/.../fixture/DefaultListableBeanFactoryTests.java` | 12 | 构造器环/prototype 环失败语义与消息、候选列表、冻结后注册被拒、后置处理器依赖约束、环+代理的例外（含 WARN 日志断言）、销毁失败聚合、被代理 Bean 的销毁回调 |
| `mini-spring-context/.../fixture/BeanFactoryFailureTests.java` | 15 | 失败语义与诊断 API：限定符未命中/类型不匹配、setter 参数个数、生命周期方法带参、构造器抛异常、`@MiniValue` 空 key、后置处理器返回 null 或抛异常、销毁幂等、冻结前快照只读、按类型取 Bean、依赖图 |
| `mini-spring-context/.../fixture/MiniApplicationContextTests.java` | 10 | 组件扫描、构造器/字段注入、配置注入、字段环与自注入、单例/prototype、注册顺序确定性、销毁逆序、close 幂等 |
| `mini-spring-context/.../fixture/MiniApplicationContextEdgeCaseTests.java` | 3 | 空包启动、非法根包、对外暴露的装配信息 |
| `mini-spring-context/.../fixture/ConcurrencyTests.java` | 2 | 16 线程 × 320 次并发 `getBean`：单例同一实例、prototype 实例互不相同 |
| `mini-spring-context/.../env/EnvironmentTests.java` | 6 | 配置来源优先级（外部覆盖内部）、默认值、缺失资源容忍、快照只读 |
| `samples/mini-spring-standalone/.../MiniApplicationContextTests.java` | 12 | 端到端：扫描、注入、Qualifier、作用域、RequestMapping、AOP（JDK 代理与 CGLIB 回退）、setter 循环依赖、请求参数解析、**构造器注入拿到代理的不变式（ADR-0001）** |
| `mini-spring-beans/.../beans/BeanDefinitionTests.java` | 6 | Bean 定义的校验（空名称、null 类、未知作用域）、作用域默认值与 `toString` |
| `mini-spring-beans/.../exception/ExceptionHierarchyTests.java` | 8 | 异常层级可被基类捕获、消息含候选与链路、原因为 suppressed 保留、配置异常工厂的建议文案 |
| `mini-spring-beans/.../exception/ExceptionMessageRobustnessTests.java` | 6 | 消息拼装在"上下文缺失"（null 注入点/cause/链路明细）时不得抛 NPE——否则真实失败信息会被吞掉 |
| `mini-spring-beans/.../util/SimpleTypeConverterTests.java` | 4 | 支持的类型转换、`parseBoolean` 语义、不支持类型报出类型名、数字格式失败传播 |
| `mini-spring-web/.../RequestArgumentResolverTests.java` | 5 | 请求/响应注入、`@MiniRequestParam` 绑定与转换、无注解时按参数名兜底、必填缺失与转换失败 |
| `mini-spring-web/.../MiniHandlerMappingTests.java` | 5 | 从容器建立映射表、路径归一化、缺失处理器、**重复映射启动期失败**、只读视图与 `describe` |
| `mini-spring-web/.../MiniDispatcherServletTests.java` | 8 | 三条装配路径（构造器 / ServletContext 属性 / `rootPackage` 参数）、无容器时明确报错、404、contextPath 剥离、UTF-8 响应、已提交响应不写、处理器异常包装 |
| `mini-spring-web/.../arch/ArchitectureTests.java` | 6 | 架构守卫（ADR-0004 / ADR-0010）：四层依赖方向、库不得依赖 Spring/Jackson 与 `javax.servlet`，外加"非空守卫"和"反向对照组"证明规则不是在空集合上空过 |
| `samples/spring-boot-reference/.../SpringbootDemoApplicationTests.java` | 1 | 原生 Boot 对照样例能正常启动上下文 |

覆盖率（JaCoCo，`mvn verify` 后见各模块 `target/site/jacoco/`）：库模块统一门槛 **行 85% / 分支 75%**，低于即构建失败；当前实测 `mini-spring-beans` 97.8% / 87.2%、`mini-spring-context` 87.2% / 78.4%、`mini-spring-web` 96.1% / 90.3%。门槛只配在父 POM 一处，样例模块跳过（它是应用，`MainApp` 是命令行报告程序）——归属原则见 [`docs/adr/0011`](docs/adr/0011-coverage-gate-placement.md)。

## 质量门禁

一条 `mvn verify` 会跑完下面全部检查，任何一条不过就构建失败。CI（GitHub Actions，JDK 21/25 × Linux/Windows 四个组合）跑的是**同一条命令**，所以本地绿就等于 CI 绿。规则的选择理由与"有意不做哪些"记在 [`docs/adr/0010`](docs/adr/0010-static-analysis-and-architecture-guards.md)。

| 门禁 | 拦什么 | 怎么证明它真的会拦 |
| --- | --- | --- |
| `maven-enforcer-plugin` | JDK/Maven 版本；`beans`/`context` 依赖 Spring/Servlet/Jackson/CGLIB | 往 `mini-spring-beans` 注入 `spring-core` 后 `mvn validate` 失败，随后按哈希校验确认已还原 |
| Checkstyle（2 条自定义规则） | 库代码直接写 `System.out`/`System.err`；导入 `javax.servlet` | 临时加入一个含 `System.out.println` 的类，检查失败并指出文件与行号（验证后删除） |
| ArchUnit（4 条规则） | 破坏库内依赖方向；库依赖 Spring/Jackson/`javax.servlet` | 保留一条**反向对照组**："`web` 不得依赖 `jakarta.servlet`"对现有代码必然报错——证明规则在真的评估类集合，而不是空过；另有 `size() > 20` 非空守卫 |
| JaCoCo `check` | `mini-spring-context` 覆盖率低于 0.85 / 0.75 | 门槛设在阶段目标值而非实测值，回退即失败 |

> 原则：**不会拦的门禁等于没有门禁**。上面每条都做过变异检验或留有反向对照组，而不是"配置里写了就算"。


## 当前不足

```text
1. 扫描支持目录与 jar，但 jar 需要目录条目（jar 工具/Maven 生成的 jar 默认都有）；其他协议（http 等）不支持；嵌套 jar 明确报错而不是漏扫
2. 只有单例池 + 早期引用，不是完整的三级缓存；明确不支持 lazyInit（ADR-0006）
3. 构造器注入参与的环、涉及 prototype 的环不支持（有意为之，见 ADR-0002）
4. 未实现 BeanPostProcessor / 切面的执行顺序控制（@MiniOrder）
5. 未实现条件装配与自动配置
6. Web 层语义仍是雏形：不区分 HTTP method、无路径模板、无 @MiniRequestBody、必填参数缺失与类型转换失败目前返回 500（按 ADR-0007 应为 400）、无统一异常处理
7. 静态分析只启用了两条 Checkstyle 规则 + enforcer + ArchUnit；Spotless / ErrorProne / SpotBugs 按 ADR-0010 有意暂缓
```

## 后续优化方向

完整的阶段划分与退出条件见 [`docs/roadmap.md`](docs/roadmap.md)，近期优先级：

```text
1. 异常层级收尾：结构化启动失败报告（FailureAnalyzer），把"启动失败"从读栈变成读报告
2. Web 能力（ADR-0007）：路由 method/模板、@MiniPathVariable、@MiniRequestBody、404/405/400 语义、@MiniExceptionHandler、两个 sample 的真实 HTTP 集成测试
3. 代理实现选择（ADR-0008）：去掉样例对 spring-core 中 CGLIB 的借用
4. 实现 @MiniPrimary 与集合注入（List<T> / Map<String,T>）、@MiniOrder 执行顺序
5. 条件装配与自动配置的最小版本
6. 事件机制与监听器
7. 工程化收尾（阶段 3）：japicmp 二进制兼容门禁、BOM、sources/javadoc、GPG 与发布、JMH 基准
```
