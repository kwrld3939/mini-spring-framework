package com.demo.mini.beans;

public class BeanDefinition {

	private final String beanName;
	private final Class<?> beanClass;
	private final String scope;
	private final boolean lazyInit;

	public BeanDefinition(String beanName, Class<?> beanClass) {
		this(beanName, beanClass, "singleton", false);
	}

	public BeanDefinition(String beanName, Class<?> beanClass, String scope, boolean lazyInit) {
		this.beanName = beanName;
		this.beanClass = beanClass;
		this.scope = scope;
		this.lazyInit = lazyInit;
	}

	public String getBeanName() {
		return beanName;
	}

	public Class<?> getBeanClass() {
		return beanClass;
	}

	public String getScope() {
		return scope;
	}

	public boolean isLazyInit() {
		return lazyInit;
	}

	public boolean isSingleton() {
		return "singleton".equals(scope);
	}

	public boolean isPrototype() {
		return "prototype".equals(scope);
	}
}
