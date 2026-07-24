package com.demo.processor;

import com.demo.mini.annotation.MyComponent;
import com.demo.mini.beans.BeanPostProcessor;

@MyComponent
public class LogBeanPostProcessor implements BeanPostProcessor {

	@Override
	public Object postProcessBeforeInitialization(String beanName, Object bean) {
		System.out.println("[BeanPostProcessor before] " + beanName);
		return bean;
	}

	@Override
	public Object postProcessAfterInitialization(String beanName, Object bean) {
		System.out.println("[BeanPostProcessor after] " + beanName);
		return bean;
	}
}