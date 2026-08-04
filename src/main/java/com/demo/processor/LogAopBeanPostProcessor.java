package com.demo.processor;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.lang.reflect.Modifier;

import com.demo.mini.annotation.MyComponent;
import com.demo.mini.annotation.MyLog;
import com.demo.mini.beans.BeanPostProcessor;

import org.springframework.cglib.proxy.Enhancer;
import org.springframework.cglib.proxy.MethodInterceptor;
import org.springframework.cglib.proxy.MethodProxy;

@MyComponent
public class LogAopBeanPostProcessor implements BeanPostProcessor {

	@Override
	public Object postProcessBeforeInitialization(String beanName, Object bean) {
		return bean;
	}

	@Override
	public Object postProcessAfterInitialization(String beanName, Object bean) {
		Class<?> beanClass = bean.getClass();
		if (!hasLogMethod(beanClass)) {
			return bean;
		}

		Class<?>[] interfaces = beanClass.getInterfaces();
		if (interfaces.length > 0) {
			return createJdkProxy(beanName, bean, beanClass, interfaces);
		}
		return createCglibProxy(beanName, bean, beanClass);
	}

	private Object createJdkProxy(String beanName, Object bean, Class<?> beanClass, Class<?>[] interfaces) {
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

	private Object createCglibProxy(String beanName, Object bean, Class<?> beanClass) {
		if (Modifier.isFinal(beanClass.getModifiers())) {
			return bean;
		}

		Enhancer enhancer = new Enhancer();
		enhancer.setSuperclass(beanClass);
		enhancer.setCallback((MethodInterceptor) (proxy, method, args, methodProxy) -> {
			Method targetMethod = beanClass.getMethod(method.getName(), method.getParameterTypes());
			if (!targetMethod.isAnnotationPresent(MyLog.class)) {
				return methodProxy.invoke(bean, args);
			}

			System.out.println("[AOP before] " + beanName + "." + method.getName());
			Object result = methodProxy.invoke(bean, args);
			System.out.println("[AOP after] " + beanName + "." + method.getName());
			return result;
		});
		return enhancer.create();
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
