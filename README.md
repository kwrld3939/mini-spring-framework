# Mini Spring IoC Container

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
- javax.servlet-api

## 核心功能

| 功能 | 说明 |
| --- | --- |
| 组件扫描 | 使用 Java 原生 ClassLoader 递归扫描指定根包下的 class 文件 |
| 自定义注解 | 实现 `@MyComponent`、`@MyAutowired`、`@MyQualifier`、`@MyValue` 等注解 |
| BeanDefinition | 保存 Bean 的元信息，包括 beanName、beanClass、scope、lazyInit |
| IoC 容器 | 由 `MyApplicationContext` 统一创建、管理和获取 Bean |
| 构造器注入 | 根据构造器参数类型和 `@MyQualifier` 从容器中解析依赖 |
| 字段注入 | 通过反射给 `@MyAutowired` 字段注入依赖 |
| 配置注入 | 通过 `@MyValue` 从 `application.properties` 注入配置值 |
| 作用域 | 支持 singleton 和 prototype |
| 生命周期 | 支持 `@MyPostConstruct` 初始化回调和 `@MyPreDestroy` 销毁回调 |
| BeanPostProcessor | 支持 Bean 初始化前后扩展点 |
| 简易 AOP | 基于 BeanPostProcessor 和 JDK 动态代理实现 `@MyLog` 日志增强 |
| RequestMapping | 扫描 `@MyRequestMapping`，建立 URL 到方法的映射关系 |
| 单元测试 | 使用 JUnit 5 验证容器核心功能 |

## 项目结构

```text
src/main/java/com/demo
├── MainApp.java
├── controller
│   ├── PayController.java
│   └── UserController.java
├── service
│   ├── AliPayService.java
│   ├── OrderService.java
│   ├── PayService.java
│   ├── UserService.java
│   └── WechatPayService.java
├── processor
│   ├── LogAopBeanPostProcessor.java
│   └── LogBeanPostProcessor.java
└── mini
    ├── annotation
    │   ├── MyAutowired.java
    │   ├── MyComponent.java
    │   ├── MyLog.java
    │   ├── MyPostConstruct.java
    │   ├── MyPreDestroy.java
    │   ├── MyQualifier.java
    │   ├── MyRequestMapping.java
    │   ├── MyScope.java
    │   └── MyValue.java
    ├── beans
    │   ├── BeanDefinition.java
    │   └── BeanPostProcessor.java
    ├── context
    │   └── MyApplicationContext.java
    └── web
        ├── MyDispatcherServlet.java
        └── MyHandlerMapping.java
```

## 启动流程

项目入口是：

```java
com.demo.MainApp
```

核心启动流程如下：

```text
MainApp.main()
-> new MyApplicationContext("com.demo")
-> loadProperties()
-> scanBeanClasses()
-> 注册 BeanDefinition
-> createBeans()
-> 构造器注入
-> injectFields()
-> 字段注入 / @MyValue 配置注入
-> registerBeanPostProcessors()
-> initializeBeans()
-> BeanPostProcessor before
-> @MyPostConstruct
-> BeanPostProcessor after
-> AOP 代理增强
-> getBean() 使用 Bean
-> MyHandlerMapping 建立 URL 映射
-> context.close()
-> @MyPreDestroy
```

一句话概括：

```text
扫描包 -> 解析注解 -> 注册 BeanDefinition -> 创建 Bean -> 依赖注入 -> 初始化扩展 -> 生命周期回调 -> AOP 代理 -> 单例池管理 -> getBean 使用
```

一句话版启动流程图：

```text
MainApp 启动 -> 创建 MyApplicationContext -> ClassLoader 扫描根包 -> 解析 @MyComponent -> 注册 BeanDefinition -> 实例化 Bean -> 构造器/字段/@Value 注入 -> 执行 BeanPostProcessor -> 生命周期初始化 -> 初始化后生成 AOP 代理 -> 替换/更新单例池中的原始 Bean -> getBean 获取使用 -> close 触发销毁
```

## IoC 实现原理

IoC 的核心是把对象的创建权从业务代码转移给容器。

在本项目中，`MyApplicationContext` 是容器核心。启动时传入根包名，例如：

```java
MyApplicationContext context = new MyApplicationContext("com.demo");
```

容器会使用 ClassLoader 将包名：

```text
com.demo
```

转换为路径：

```text
com/demo
```

然后递归扫描该目录下的 `.class` 文件，加载类对象，并判断类上是否存在：

```java
@MyComponent
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

本项目支持两种注入方式。

### 构造器注入

示例：

```java
public PayController(@MyQualifier("aliPayService") PayService payService) {
    this.payService = payService;
}
```

容器会解析构造器参数，根据参数类型和 `@MyQualifier` 找到对应 Bean，再通过反射调用构造器创建对象。

### 字段注入

示例：

```java
@MyAutowired
private UserService userService;
```

容器会遍历 Bean 的字段，如果字段上存在 `@MyAutowired`，则从容器中按字段名或类型查找依赖，并通过 `field.set()` 完成注入。

## 配置注入

项目支持从 `application.properties` 中读取配置：

```properties
app.name=mini-spring
```

然后通过 `@MyValue` 注入：

```java
@MyValue("app.name")
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
@MyScope("prototype")
@MyComponent
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
-> @MyPostConstruct
-> BeanPostProcessor after
-> 使用 Bean
-> @MyPreDestroy
```

示例：

```java
@MyPostConstruct
public void init() {
    System.out.println("[lifecycle] UserService init");
}

@MyPreDestroy
public void destroy() {
    System.out.println("[lifecycle] UserService destroy");
}
```

容器关闭时：

```java
context.close();
```

会触发 `@MyPreDestroy` 标记的方法。

## BeanPostProcessor

`BeanPostProcessor` 是容器提供的扩展点，允许在 Bean 初始化前后插入自定义逻辑。

接口定义：

```java
public interface BeanPostProcessor {

    Object postProcessBeforeInitialization(String beanName, Object bean);

    Object postProcessAfterInitialization(String beanName, Object bean);
}
```

项目中的 `LogBeanPostProcessor` 会在 Bean 初始化前后输出日志：

```text
[BeanPostProcessor before] userService
[lifecycle] UserService init, appName=mini-spring
[BeanPostProcessor after] userService
```

这个机制是后续实现 AOP 的基础。

## 简易 AOP 实现

项目通过 `@MyLog`、`BeanPostProcessor` 和 JDK 动态代理实现了一个简易 AOP。

示例：

```java
@Override
@MyLog
public String pay() {
    return "ali pay";
}
```

`LogAopBeanPostProcessor` 会在 Bean 初始化后判断：

```text
1. 当前 Bean 是否实现了接口
2. 当前 Bean 是否存在带 @MyLog 的方法
```

如果满足条件，就通过：

```java
Proxy.newProxyInstance(...)
```

创建代理对象，并替换单例池中的原始 Bean。

运行效果：

```text
PayService proxy class: jdk.proxy2.$Proxy7
[AOP before] aliPayService.pay
[AOP after] aliPayService.pay
Invoke payService.pay(): ali pay
```

当前 AOP 的限制：

```text
1. 只支持 JDK 动态代理，因此目标类必须实现接口
2. 不支持 CGLIB 方式代理普通类
3. 构造器注入时，依赖对象可能仍然是原始对象，不一定是代理对象
4. 只实现了日志增强，没有实现完整切点表达式
```

更具体地说，当前版本的 AOP 是学习版实现，代理对象是在 `postProcessAfterInitialization` 阶段生成并放回单例池的。因此，如果某个 Bean 在代理生成之前已经通过构造器注入拿到了原始对象，那么它内部持有的仍然可能是未代理对象。

例如 `PayController` 通过构造器注入 `aliPayService` 时，拿到的是当时已经创建好的原始 `AliPayService`。后续 `LogAopBeanPostProcessor` 会把单例池中的 `aliPayService` 替换成 JDK 代理对象，但不会自动回头修改 `PayController` 里已经保存的字段。因此：

```text
handlerMapping.invoke("/pay")
```

可能只返回业务结果：

```text
ali pay
```

而不会输出 AOP 日志。直接从容器重新获取代理对象再调用，则可以触发 AOP：

```java
PayService payService = (PayService) context.getBean("aliPayService");
payService.pay();
```

输出示例：

```text
[AOP before] aliPayService.pay
[AOP after] aliPayService.pay
```

这个现象体现了当前实现和真实 Spring 的差距。真实 Spring 会在更完整的 Bean 创建流程中处理代理暴露、依赖注入和生命周期顺序问题，并且可以结合 JDK 动态代理和 CGLIB 处理不同类型的 Bean。

## RequestMapping 映射机制

项目实现了简化版的请求映射机制。

示例：

```java
@MyRequestMapping(path = "/user/hello")
public String hello() {
    return userService.hello();
}
```

`MyHandlerMapping` 会遍历容器中的 Bean，扫描方法上的 `@MyRequestMapping`，并建立映射表：

```text
/user/hello -> UserController#hello
/pay -> PayController#pay
```

调用时：

```java
handlerMapping.invoke("/user/hello");
```

本质是：

```text
根据 path 查找 Method
-> 通过 method.invoke(bean) 反射调用
-> 返回方法结果
```

这对应 Spring MVC 中 HandlerMapping 的核心思想。

## 与 Spring 的对应关系

| 本项目 | Spring |
| --- | --- |
| `@MyComponent` | `@Component` |
| `@MyAutowired` | `@Autowired` |
| `@MyQualifier` | `@Qualifier` |
| `@MyValue` | `@Value` |
| `@MyScope` | `@Scope` |
| `@MyPostConstruct` | `@PostConstruct` |
| `@MyPreDestroy` | `@PreDestroy` |
| `@MyRequestMapping` | `@RequestMapping` |
| `@MyLog` | 自定义切面注解 |
| `BeanDefinition` | Spring BeanDefinition |
| `BeanPostProcessor` | Spring BeanPostProcessor |
| `MyApplicationContext` | Spring ApplicationContext |
| `MyHandlerMapping` | Spring HandlerMapping |
| `MyDispatcherServlet` | Spring DispatcherServlet |

## 运行方式

运行主程序：

```bash
./mvnw compile
java -cp target/classes com.demo.MainApp
```

Windows PowerShell 下可以使用：

```powershell
.\mvnw.cmd compile
java -cp target\classes com.demo.MainApp
```

运行测试：

```bash
./mvnw test
```

Windows PowerShell：

```powershell
.\mvnw.cmd test
```

## 测试覆盖

测试文件：

```text
src/test/java/com/demo/mini/MiniApplicationContextTests.java
```

当前测试覆盖：

```text
1. 组件扫描和 singleton Bean 创建
2. 构造器注入和配置值注入
3. @MyQualifier 多实现选择
4. singleton / prototype 作用域
5. RequestMapping 映射和调用
6. BeanPostProcessor 生成 AOP 代理
7. 多实现按类型获取时抛出明确异常
8. RequestMapping 路径不存在时抛出明确异常
```

## 当前不足

这个项目是学习版 Mini Spring，和真实 Spring 相比仍有明显简化：

```text
1. 包扫描只支持 file 协议，不支持 jar 包扫描
2. Bean 创建流程没有实现完整三级缓存
3. 对循环依赖的处理不完整
4. AOP 只支持 JDK 动态代理，不支持 CGLIB
5. RequestMapping 不支持请求参数绑定
6. DispatcherServlet 仅为简化实现，没有完整 MVC 能力
7. 没有实现 BeanFactory 和 ApplicationContext 的分层设计
8. 没有实现复杂的 BeanPostProcessor 顺序控制
9. 没有实现条件装配、自动配置等 Spring Boot 能力
```

## 后续优化方向

后续可以继续扩展：

```text
1. 实现 BeanFactory 和 ApplicationContext 分层
2. 实现 @MyPrimary，优化多实现注入选择
3. 实现 BeanPostProcessor 执行顺序控制
4. 支持 CGLIB 代理没有接口的类
5. 优化 AOP，使 Controller 注入的依赖也能拿到代理对象
6. 增加异常场景测试，例如重复 beanName、缺少依赖、多实现无 Qualifier
7. 扩展 RequestMapping，支持请求参数绑定
8. 补充项目流程图和设计说明
```
