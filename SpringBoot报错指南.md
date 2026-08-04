# Spring Boot 入门报错总结

更新日期：2026-08-04

这份文档从 Spring Boot 入门者的视角整理常见问题记录。重点不是记住所有异常名，而是看到报错以后能先判断方向：是 Maven 问题、启动问题、端口问题、Controller 问题，还是这个项目里 Mini Spring 容器自身的问题。

## 1. Maven 下载依赖失败

常见现象：

```text
Non-resolvable parent POM
Could not transfer artifact
Permission denied: getsockopt
```

这类问题容易被误判为 `pom.xml` 写错。实际原因通常是 Maven 没有读到正确的本地仓库，或者当前环境不能联网下载依赖。

解决办法：

```powershell
.\mvnw.cmd "-Dmaven.repo.local=D:/maven-repository" test
```

如果仓库已经迁移到 D 盘，就要确认 Maven 用的是 `D:/maven-repository`，不要还去默认的 `C:\Users\用户名\.m2\repository` 找。

## 2. Maven 参数写法不对

常见现象：

```text
No plugin found for prefix '.repo.local=D'
```

这个报错表面上像是 Maven 找不到插件。实际原因可能只是 Windows PowerShell 里参数没有被正确识别。

解决办法：

```powershell
.\mvnw.cmd "-Dmaven.repo.local=D:/maven-repository" test
```

注意把 `-Dmaven.repo.local=...` 整体用英文双引号包起来，并且路径使用 `/` 兼容性更好。

## 3. 端口被占用

常见现象：

```text
Port 8080 was already in use
Web server failed to start
```

这个项目默认端口在 `src/main/resources/application.properties` 里：

```properties
server.port=8080
```

解决办法：

1. 先确认是不是已经启动过一次项目。
2. 改端口，例如：

```properties
server.port=8081
```

3. 或者停掉占用 8080 的进程。

## 4. 启动类包路径不对

常见现象：

```text
No qualifying bean of type
404 Not Found
Controller 没有生效
```

Spring Boot 默认会从启动类所在包往下扫描。这个项目的标准 Boot 启动类是：

```java
com.example.demo.SpringbootDemoApplication
```

如果新建 Controller 放到了 `com.example.demo` 之外，Spring Boot 可能扫描不到。

解决办法：

- 普通 Spring Boot Controller 建议放在 `com.example.demo` 或它的子包下面。
- Mini Spring 的学习代码则放在 `com.demo` 下，两套不要混着判断。

## 5. Controller 地址访问不到

常见现象：

```text
404 Not Found
```

建议先检查三个点：

1. 方法上有没有 `@GetMapping` 或 `@RequestMapping`。
2. 请求路径有没有写错。
3. 应用是否已经启动成功。

比如这个项目里的标准 Boot 接口是：

```java
@GetMapping("/")
public Map<String, Object> hello()
```

访问路径就是：

```text
http://localhost:8080/
```

## 6. `application.properties` 配置没有生效

常见现象：

```text
配置值还是默认值
@MyValue 找不到 key
No property found for key
```

这个项目里 Mini Spring 容器会读取：

```text
src/main/resources/application.properties
```

如果使用 `@MyValue("app.name")`，就必须有：

```properties
app.name=mini-spring
```

解决办法：

- 检查 key 是否完全一致。
- 检查文件是否在 `src/main/resources` 下。
- 改完配置后重新编译或重新启动。

## 7. 多个 Bean 实现同一个接口

常见现象：

```text
Multiple beans found for type
```

这个项目里 `PayService` 有两个实现：

```text
AliPayService
WechatPayService
```

如果只按类型获取：

```java
context.getBean(PayService.class)
```

容器不知道该选哪一个。

解决办法：

使用 `@MyQualifier` 指定具体 Bean：

```java
public PayController(@MyQualifier("aliPayService") PayService payService) {
    this.payService = payService;
}
```

## 8. 循环依赖报错

常见现象：

```text
Circular constructor dependency detected
```

构造器循环依赖比较麻烦，因为两个对象都要先等对方创建完才能创建自己。

容易出问题的写法：

```java
public A(B b) {
}

public B(A a) {
}
```

解决办法：

在这个学习项目里，可以用 setter 注入演示更容易理解的循环依赖：

```java
@MyAutowired
public void setBetaService(BetaService betaService) {
    this.betaService = betaService;
}
```

真实 Spring 里循环依赖还涉及三级缓存、提前暴露对象、代理对象等细节，这也是后续继续扩展的重点。

## 9. AOP 没有打印日志

常见现象：

```text
方法执行了，但是没有出现 [AOP before] / [AOP after]
```

这个项目的 AOP 是通过 `@MyLog` 标记方法，再由 `LogAopBeanPostProcessor` 创建代理。

排查顺序：

1. 方法上有没有 `@MyLog`。
2. 当前 Bean 有没有被 `@MyComponent` 扫描到。
3. 调用的是不是容器里拿到的代理对象。
4. 如果是构造器中较早注入的对象，可能拿到的是代理前的原始对象。

解决办法：

优先从容器重新获取 Bean 再调用：

```java
PayService payService = (PayService) context.getBean("aliPayService");
payService.pay();
```

## 10. CGLIB 代理失败

常见现象：

```text
Cannot subclass final class
```

CGLIB 的原理是生成目标类的子类，所以 `final` 类不能被代理，`final` 方法也不能被覆盖增强。

解决办法：

- 不要把需要 AOP 增强的类声明成 `final`。
- 不要把需要增强的方法声明成 `final`。
- 如果类实现了接口，也可以走 JDK 动态代理。

## 11. RequestMapping 参数绑定失败

常见现象：

```text
Missing @MyRequestParam
Missing required request parameter
Unsupported target type
```

这个项目里的简化 MVC 参数绑定支持：

```java
@MyRequestParam("name") String name
@MyRequestParam("count") int count
```

访问时必须带上参数：

```text
/user/inspect?name=Alice&count=3
```

如果少传 `count`，而方法参数是 `int` 这种基本类型，就会报错，因为基本类型不能是 `null`。

解决办法：

- 确认每个普通参数都写了 `@MyRequestParam`。
- 确认 URL 上传入了对应参数。
- 确认参数类型是当前转换器支持的类型：`String`、`int`、`long`、`boolean`、`double` 以及对应包装类。

## 12. 测试启动太慢或日志太多

常见现象：

```text
Spring Boot 自动配置日志过多
Condition Evaluation Report 内容较长
```

这是因为配置里有：

```properties
debug=true
```

解决办法：

如果只是跑普通测试，不想看太多自动配置日志，可以改成：

```properties
debug=false
```

这个项目现在就是 `debug=false`，更适合日常学习。

## 13. 测试通过，但命令行有 Mockito / ByteBuddy 警告

常见现象：

```text
Mockito is currently self-attaching
Dynamic loading of agents will be disallowed by default in a future release
```

这类通常是测试框架在 JDK 21 下的提醒，不一定代表测试失败。

判断标准：

```text
BUILD SUCCESS
Tests run: 12, Failures: 0, Errors: 0
```

只要最终是 `BUILD SUCCESS`，这类警告通常不影响当前测试结果。后续如果需要减少测试警告，可以再按 Mockito 文档配置 Java agent。

## 14. 改了代码但运行结果没变化

常见原因：

- 没有重新编译。
- 运行的是旧的 class 文件。
- IDE 里启动的是另一个项目。
- 把 RuoYi 项目和 `workspace/springboot-demo` 混在一起看了。

解决办法：

在这个项目目录下重新跑：

```powershell
.\mvnw.cmd "-Dmaven.repo.local=D:/maven-repository" test
```

或者至少先编译：

```powershell
.\mvnw.cmd "-Dmaven.repo.local=D:/maven-repository" compile
```

## 15. 建议的排错顺序

以后遇到报错，可以先按这个顺序检查：

1. 先看最后一段异常，不被前面的大量日志干扰。
2. 判断是 Maven、启动、请求、容器、AOP 还是测试问题。
3. 找到项目里的对应文件，例如 `pom.xml`、`application.properties`、Controller、`MyApplicationContext`。
4. 用一个最小测试或最小接口复现。
5. 改完后跑测试确认，不只依赖主观判断。

这套顺序比较基础，但适合入门阶段使用：先定位大方向，再处理具体异常。
