package io.github.kwrld3939.minispring.samples.standalone;

import java.util.Map;

import io.github.kwrld3939.minispring.samples.standalone.controller.UserController;
import io.github.kwrld3939.minispring.context.MiniApplicationContext;
import io.github.kwrld3939.minispring.web.MiniHandlerMapping;
import io.github.kwrld3939.minispring.samples.standalone.service.OrderService;
import io.github.kwrld3939.minispring.samples.standalone.service.PayService;
import io.github.kwrld3939.minispring.samples.standalone.service.UserService;

public class MainApp {

	public static void main(String[] args) {
		String rootPackage = MainApp.class.getPackageName();
		MiniApplicationContext context = new MiniApplicationContext(rootPackage);

		try {
			System.out.println("\n1. IoC: scan root package");
			System.out.println("Mini IoC root package: " + context.getRootPackage());
			System.out.println("Bean count: " + context.getBeans().size());
			for (Map.Entry<String, Object> entry : context.getBeans().entrySet()) {
				System.out.println(entry.getKey() + " -> " + entry.getValue().getClass().getName());
			}

			System.out.println("\n2. DI: constructor injection");
			UserController userController = context.getBean(UserController.class);
			System.out.println("UserController.hello(): " + userController.hello());

			System.out.println("\n3. RequestMapping: URL to method");
			MiniHandlerMapping handlerMapping = new MiniHandlerMapping(context);
			for (Map.Entry<String, MiniHandlerMapping.HandlerMethod> entry : handlerMapping.getMappings().entrySet()) {
				System.out.println(entry.getKey() + " -> " + entry.getValue().describe());
			}
			System.out.println("Invoke /user/hello: " + handlerMapping.invoke("/user/hello"));
			System.out.println("Invoke /pay: " + handlerMapping.invoke("/pay"));

			System.out.println("\n4. Scope: singleton or prototype");
			UserService userService1 = context.getBean(UserService.class);
			UserService userService2 = context.getBean(UserService.class);
			System.out.println("UserService singleton same instance: " + (userService1 == userService2));

			OrderService orderService1 = context.getBean(OrderService.class);
			OrderService orderService2 = context.getBean(OrderService.class);
			System.out.println("OrderService prototype same instance: " + (orderService1 == orderService2));

			System.out.println("\n5. AOP: BeanPostProcessor proxy");
			PayService payService = (PayService) context.getBean("aliPayService");
			System.out.println("PayService proxy class: " + payService.getClass().getName());
			System.out.println("Invoke payService.pay(): " + payService.pay());
		} finally {
			System.out.println("\n6. Bean lifecycle: close context");
			context.close();
		}
	}
}
