package com.demo.processor;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

import com.demo.mini.annotation.MyComponent;
import com.demo.mini.annotation.MyLog;
import com.demo.mini.beans.BeanPostProcessor;

@MyComponent
public class LogAopBeanPostProcessor implements BeanPostProcessor {

	@Override
	public Object postProcessBeforeInitialization(String beanName, Object bean) {
		return bean;
	}

	@Override
	public Object postProcessAfterInitialization(String beanName, Object bean) {
		Class<?> beanClass = bean.getClass();
		Class<?>[] interfaces = beanClass.getInterfaces();
		if (interfaces.length == 0 || !hasLogMethod(beanClass)) {
			return bean;
		}

		return Proxy.newProxyInstance(
				beanClass.getClassLoader(),
				interfaces,
				(proxy, method, args) -> {
					Method targetMethod = beanClass.getMethod(method.getName(), method.getParameterTypes());
					if (!targetMethod.isAnnotationPresent(MyLog.class)) {
						return method.invoke(bean, args);
					}

					System.out.println("[AOP before] " + beanName + "." + method.getName());
					Object result = method.invoke(bean, args);
					System.out.println("[AOP after] " + beanName + "." + method.getName());
					return result;
				});
	}

	private boolean hasLogMethod(Class<?> beanClass) {
		for (Method method : beanClass.getMethods()) {
			if (method.isAnnotationPresent(MyLog.class)) {
				return true;
			}
		}
		return false;
	}
}
