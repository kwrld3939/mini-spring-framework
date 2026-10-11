package io.github.kwrld3939.minispring.samples.standalone.processor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.github.kwrld3939.minispring.annotation.MiniComponent;
import io.github.kwrld3939.minispring.beans.BeanPostProcessor;

/** 最小示例：在后置处理的两个阶段打印日志，用于观察 Bean 的生命周期顺序。 */
@MiniComponent
public class LogBeanPostProcessor implements BeanPostProcessor {

	private static final Logger LOGGER = LoggerFactory.getLogger(LogBeanPostProcessor.class);

	@Override
	public Object postProcessBeforeInitialization(String beanName, Object bean) {
		LOGGER.debug("BeanPostProcessor before: {}", beanName);
		return bean;
	}

	@Override
	public Object postProcessAfterInitialization(String beanName, Object bean) {
		LOGGER.debug("BeanPostProcessor after: {}", beanName);
		return bean;
	}
}
