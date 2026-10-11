package io.github.kwrld3939.minispring.samples.standalone.processor;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.lang.reflect.Modifier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.github.kwrld3939.minispring.annotation.MiniComponent;
import io.github.kwrld3939.minispring.samples.standalone.annotation.MiniLog;
import io.github.kwrld3939.minispring.beans.BeanPostProcessor;

import org.springframework.cglib.proxy.Enhancer;
import org.springframework.cglib.proxy.MethodInterceptor;
import org.springframework.cglib.proxy.MethodProxy;

/**
 * 示例切面：给带 {@code @MiniLog} 的方法加日志增强。
 *
 * <p>它演示框架的扩展点（BeanPostProcessor）如何实现 AOP：有接口走 JDK 动态代理，没有接口走 CGLIB 回退。
 * 日志走 SLF4J —— 库与样例都不直接写 {@code System.out}，输出格式由使用方的日志后端决定。
 */
@MiniComponent
public class LogAopBeanPostProcessor implements BeanPostProcessor {

	private static final Logger LOGGER = LoggerFactory.getLogger(LogAopBeanPostProcessor.class);

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
					if (!targetMethod.isAnnotationPresent(MiniLog.class)) {
						return method.invoke(bean, args);
					}

					LOGGER.info("AOP before: {}.{}", beanName, method.getName());
					long startedAt = System.nanoTime();
					try {
						Object result = method.invoke(bean, args);
						LOGGER.info("AOP after: {}.{} 耗时 {} ms", beanName, method.getName(),
								elapsedMillis(startedAt));
						return result;
					} catch (InvocationTargetException ex) {
						LOGGER.warn("AOP 异常: {}.{} 抛出 {}", beanName, method.getName(), ex.getTargetException());
						throw ex;
					}
				});
	}

	private Object createCglibProxy(String beanName, Object bean, Class<?> beanClass) {
		if (Modifier.isFinal(beanClass.getModifiers())) {
			LOGGER.warn("无法为 final 类 {} 创建 CGLIB 代理，将返回原始对象（方法 {}）",
					beanClass.getName(), beanName);
			return bean;
		}

		Enhancer enhancer = new Enhancer();
		enhancer.setSuperclass(beanClass);
		enhancer.setCallback((MethodInterceptor) (proxy, method, args, methodProxy) -> {
			Method targetMethod = beanClass.getMethod(method.getName(), method.getParameterTypes());
			if (!targetMethod.isAnnotationPresent(MiniLog.class)) {
				return methodProxy.invoke(bean, args);
			}

			LOGGER.info("AOP before: {}.{}", beanName, method.getName());
			long startedAt = System.nanoTime();
			try {
				Object result = methodProxy.invoke(bean, args);
				LOGGER.info("AOP after: {}.{} 耗时 {} ms", beanName, method.getName(), elapsedMillis(startedAt));
				return result;
			} catch (InvocationTargetException ex) {
				LOGGER.warn("AOP 异常: {}.{} 抛出 {}", beanName, method.getName(), ex.getTargetException());
				throw ex;
			}
		});
		return enhancer.create();
	}

	private long elapsedMillis(long startedAt) {
		return (System.nanoTime() - startedAt) / 1_000_000L;
	}

	private boolean hasLogMethod(Class<?> beanClass) {
		for (Method method : beanClass.getMethods()) {
			if (method.isAnnotationPresent(MiniLog.class)) {
				return true;
			}
		}
		return false;
	}
}
