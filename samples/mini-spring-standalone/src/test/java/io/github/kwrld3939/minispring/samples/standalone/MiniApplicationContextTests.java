package io.github.kwrld3939.minispring.samples.standalone;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import io.github.kwrld3939.minispring.samples.standalone.controller.UserController;
import io.github.kwrld3939.minispring.web.RequestArgumentResolver;
import io.github.kwrld3939.minispring.samples.standalone.service.AlphaService;
import io.github.kwrld3939.minispring.samples.standalone.service.BetaService;
import org.junit.jupiter.api.Test;

import io.github.kwrld3939.minispring.samples.standalone.controller.PayController;
import io.github.kwrld3939.minispring.context.MiniApplicationContext;
import io.github.kwrld3939.minispring.exception.NoUniqueBeanException;
import io.github.kwrld3939.minispring.web.MiniHandlerMapping;
import io.github.kwrld3939.minispring.samples.standalone.service.OrderService;
import io.github.kwrld3939.minispring.samples.standalone.service.PayService;
import io.github.kwrld3939.minispring.samples.standalone.service.ReportService;
import io.github.kwrld3939.minispring.samples.standalone.service.UserService;

class MiniApplicationContextTests {

	@Test
	void scansComponentsAndCreatesSingletonBeans() {
		try (MiniApplicationContext context = new MiniApplicationContext(MainApp.class.getPackageName())) {
			assertEquals(MainApp.class.getPackageName(), context.getRootPackage());
			assertTrue(context.getBeans().containsKey("userService"));
			assertTrue(context.getBeans().containsKey("userController"));
			assertTrue(context.getBeans().containsKey("payController"));
			assertTrue(context.getBeans().containsKey("aliPayService"));
			assertTrue(context.getBeans().containsKey("wechatPayService"));
		}
	}

	@Test
	void injectsConstructorDependenciesAndPropertyValues() {
		try (MiniApplicationContext context = new MiniApplicationContext(MainApp.class.getPackageName())) {
			UserController userController = context.getBean(UserController.class);

			assertNotNull(userController);
			assertEquals("hello mini ioc from mini-spring", userController.hello());
		}
	}

	@Test
	void usesQualifierWhenMultipleImplementationsExist() {
		try (MiniApplicationContext context = new MiniApplicationContext(MainApp.class.getPackageName())) {
			PayController payController = context.getBean(PayController.class);

			assertNotNull(payController);
			assertEquals("ali pay", payController.pay());
		}
	}

	@Test
	void supportsSingletonAndPrototypeScopes() {
		try (MiniApplicationContext context = new MiniApplicationContext(MainApp.class.getPackageName())) {
			UserService userService1 = context.getBean(UserService.class);
			UserService userService2 = context.getBean(UserService.class);
			OrderService orderService1 = context.getBean(OrderService.class);
			OrderService orderService2 = context.getBean(OrderService.class);

			assertSame(userService1, userService2);
			assertNotSame(orderService1, orderService2);
		}
	}

	@Test
	void buildsRequestMappingAndInvokesHandlers() {
		try (MiniApplicationContext context = new MiniApplicationContext(MainApp.class.getPackageName())) {
			MiniHandlerMapping handlerMapping = new MiniHandlerMapping(context);

			assertTrue(handlerMapping.getMappings().containsKey("/user/hello"));
			assertTrue(handlerMapping.getMappings().containsKey("/pay"));
			assertEquals("hello mini ioc from mini-spring", handlerMapping.invoke("/user/hello"));
			assertEquals("ali pay", handlerMapping.invoke("/pay"));
		}
	}

	@Test
	void createsAopProxyByBeanPostProcessor() {
		try (MiniApplicationContext context = new MiniApplicationContext(MainApp.class.getPackageName())) {
			PayService payService = (PayService) context.getBean("aliPayService");

			assertTrue(Proxy.isProxyClass(payService.getClass()));
			assertEquals("ali pay", payService.pay());
		}
	}

	/**
	 * ADR-0001 的核心不变式：构造器注入拿到的必须与容器里的终态对象是同一个（也就是 AOP 代理）。
	 *
	 * <p>重构前这里必然失败：代理是在"第二趟初始化"里生成并回填单例池的，构造器注入者持有的是原始对象，
	 * 于是 {@code invoke("/pay")} 静默地不带增强。这条断言就是那个缺陷的回归防线。
	 */
	@Test
	void injectsTheAopProxyIntoConstructorDependencies() {
		try (MiniApplicationContext context = new MiniApplicationContext(MainApp.class.getPackageName())) {
			PayController payController = context.getBean(PayController.class);
			Object injected = payController.payService();

			assertSame(context.getBean("aliPayService"), injected);
			assertTrue(Proxy.isProxyClass(injected.getClass()));
			assertEquals("ali pay", payController.pay());
		}
	}

	@Test
	void fallsBackToCglibForClassBasedAopTargets() {
		try (MiniApplicationContext context = new MiniApplicationContext(MainApp.class.getPackageName())) {
			ReportService reportService = context.getBean(ReportService.class);

			assertNotNull(reportService);
			assertFalse(Proxy.isProxyClass(reportService.getClass()));
			assertTrue(reportService.getClass().getName().contains("$$"));
			assertEquals("report ready", reportService.buildReport());
		}
	}

	@Test
	void supportsSetterBasedCircularDependencies() {
		try (MiniApplicationContext context = new MiniApplicationContext(MainApp.class.getPackageName())) {
			AlphaService alphaService = context.getBean(AlphaService.class);
			BetaService betaService = context.getBean(BetaService.class);

			assertNotNull(alphaService);
			assertNotNull(betaService);
			assertEquals("alpha -> beta", alphaService.chain());
			assertEquals("beta -> alpha", betaService.chain());
		}
	}

	@Test
	void resolvesRequestParametersForMappedHandlers() throws Exception {
		try (MiniApplicationContext context = new MiniApplicationContext(MainApp.class.getPackageName())) {
			MiniHandlerMapping handlerMapping = new MiniHandlerMapping(context);
			Method method = UserController.class.getMethod("inspect",
					HttpServletRequest.class, HttpServletResponse.class, String.class, int.class);

			Map<String, String> parameters = new HashMap<>();
			parameters.put("name", "Alice");
			parameters.put("count", "3");

			HttpServletRequest request = (HttpServletRequest) Proxy.newProxyInstance(
					HttpServletRequest.class.getClassLoader(),
					new Class<?>[] { HttpServletRequest.class },
					new RequestInvocationHandler(parameters));
			HttpServletResponse response = (HttpServletResponse) Proxy.newProxyInstance(
					HttpServletResponse.class.getClassLoader(),
					new Class<?>[] { HttpServletResponse.class },
					(proxy, invokedMethod, ignoredArgs) -> null);

			RequestArgumentResolver resolver = new RequestArgumentResolver();
			Object[] resolvedArgs = resolver.resolve(method, request, response);

			assertSame(request, resolvedArgs[0]);
			assertSame(response, resolvedArgs[1]);
			assertEquals("Alice", resolvedArgs[2]);
			assertEquals(3, resolvedArgs[3]);
			assertEquals("hello mini ioc from mini-spring | Alice:3",
					handlerMapping.getMappings().get("/user/inspect").invoke(resolvedArgs));
		}
	}

	/**
	 * ADR-0005：多候选必须抛出可程序化判断的 NoUniqueBeanException，且消息要列出**全部**候选与修复建议，
	 * 而不是一句 "Multiple beans found for type"。
	 */
	@Test
	void throwsNoUniqueBeanExceptionListingAllCandidates() {
		try (MiniApplicationContext context = new MiniApplicationContext(MainApp.class.getPackageName())) {
			NoUniqueBeanException ex = assertThrows(NoUniqueBeanException.class,
					() -> context.getBean(PayService.class));

			assertEquals(2, ex.getCandidates().size());
			assertTrue(ex.getMessage().contains("aliPayService"), ex.getMessage());
			assertTrue(ex.getMessage().contains("wechatPayService"), ex.getMessage());
			assertTrue(ex.getMessage().contains("@MiniQualifier"), ex.getMessage());
		}
	}

	@Test
	void throwsWhenRequestMappingNotFound() {
		try (MiniApplicationContext context = new MiniApplicationContext(MainApp.class.getPackageName())) {
			MiniHandlerMapping handlerMapping = new MiniHandlerMapping(context);

			IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
					() -> handlerMapping.invoke("/missing"));

			assertTrue(ex.getMessage().contains("No handler"));
		}
	}

	private static final class RequestInvocationHandler implements InvocationHandler {

		private final Map<String, String> parameters;

		private RequestInvocationHandler(Map<String, String> parameters) {
			this.parameters = parameters;
		}

		@Override
		public Object invoke(Object proxy, Method method, Object[] args) {
			if ("getParameter".equals(method.getName())) {
				return parameters.get(String.valueOf(args[0]));
			}
			if ("getMethod".equals(method.getName())) {
				return "GET";
			}
			return null;
		}
	}
}
