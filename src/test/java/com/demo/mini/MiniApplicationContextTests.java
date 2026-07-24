package com.demo.mini;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Proxy;

import org.junit.jupiter.api.Test;

import com.demo.controller.PayController;
import com.demo.controller.UserController;
import com.demo.mini.context.MyApplicationContext;
import com.demo.mini.web.MyHandlerMapping;
import com.demo.service.OrderService;
import com.demo.service.PayService;
import com.demo.service.UserService;

class MiniApplicationContextTests {

	@Test
	void scansComponentsAndCreatesSingletonBeans() {
		try (MyApplicationContext context = new MyApplicationContext("com.demo")) {
			assertEquals("com.demo", context.getRootPackage());
			assertTrue(context.getBeans().containsKey("userService"));
			assertTrue(context.getBeans().containsKey("userController"));
			assertTrue(context.getBeans().containsKey("payController"));
			assertTrue(context.getBeans().containsKey("aliPayService"));
			assertTrue(context.getBeans().containsKey("wechatPayService"));
		}
	}

	@Test
	void injectsConstructorDependenciesAndPropertyValues() {
		try (MyApplicationContext context = new MyApplicationContext("com.demo")) {
			UserController userController = context.getBean(UserController.class);

			assertNotNull(userController);
			assertEquals("hello mini ioc from mini-spring", userController.hello());
		}
	}

	@Test
	void usesQualifierWhenMultipleImplementationsExist() {
		try (MyApplicationContext context = new MyApplicationContext("com.demo")) {
			PayController payController = context.getBean(PayController.class);

			assertNotNull(payController);
			assertEquals("ali pay", payController.pay());
		}
	}

	@Test
	void supportsSingletonAndPrototypeScopes() {
		try (MyApplicationContext context = new MyApplicationContext("com.demo")) {
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
		try (MyApplicationContext context = new MyApplicationContext("com.demo")) {
			MyHandlerMapping handlerMapping = new MyHandlerMapping(context);

			assertTrue(handlerMapping.getMappings().containsKey("/user/hello"));
			assertTrue(handlerMapping.getMappings().containsKey("/pay"));
			assertEquals("hello mini ioc from mini-spring", handlerMapping.invoke("/user/hello"));
			assertEquals("ali pay", handlerMapping.invoke("/pay"));
		}
	}

	@Test
	void createsAopProxyByBeanPostProcessor() {
		try (MyApplicationContext context = new MyApplicationContext("com.demo")) {
			PayService payService = (PayService) context.getBean("aliPayService");

			assertTrue(Proxy.isProxyClass(payService.getClass()));
			assertEquals("ali pay", payService.pay());
		}
	}

	@Test
	void throwsWhenMultipleBeansFoundByType() {
		try (MyApplicationContext context = new MyApplicationContext("com.demo")) {
			IllegalStateException ex = assertThrows(IllegalStateException.class,
					() -> context.getBean(PayService.class));

			assertTrue(ex.getMessage().contains("Multiple beans found for type"));
		}
	}

	@Test
	void throwsWhenRequestMappingNotFound() {
		try (MyApplicationContext context = new MyApplicationContext("com.demo")) {
			MyHandlerMapping handlerMapping = new MyHandlerMapping(context);

			IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
					() -> handlerMapping.invoke("/missing"));

			assertTrue(ex.getMessage().contains("No handler"));
		}
	}
}
