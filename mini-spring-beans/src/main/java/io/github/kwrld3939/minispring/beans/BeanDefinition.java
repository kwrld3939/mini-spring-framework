package io.github.kwrld3939.minispring.beans;

import io.github.kwrld3939.minispring.exception.BeanDefinitionException;

/**
 * Bean 的元信息（定义），不是 Bean 实例。
 *
 * <p>它把"类信息"和"对象实例"解耦：注册阶段产生定义，刷新阶段按定义创建对象。
 *
 * <p>注意：这里**没有** {@code lazyInit}。延时初始化在冻结模型下需要额外的并发保证，
 * 见 {@code docs/adr/0006-concurrency-and-lifecycle-freeze.md}——v0.1 明确不支持，
 * 因此不保留一个"看起来支持"的字段。
 */
public class BeanDefinition {

	public static final String SCOPE_SINGLETON = "singleton";
	public static final String SCOPE_PROTOTYPE = "prototype";

	private final String beanName;
	private final Class<?> beanClass;
	private final String scope;

	public BeanDefinition(String beanName, Class<?> beanClass) {
		this(beanName, beanClass, SCOPE_SINGLETON);
	}

	public BeanDefinition(String beanName, Class<?> beanClass, String scope) {
		if (beanName == null || beanName.isBlank()) {
			throw new BeanDefinitionException(beanName, "beanName 不能为空");
		}
		if (beanClass == null) {
			throw new BeanDefinitionException(beanName, "beanClass 不能为 null");
		}
		String normalizedScope = (scope == null || scope.isBlank()) ? SCOPE_SINGLETON : scope;
		if (!SCOPE_SINGLETON.equals(normalizedScope) && !SCOPE_PROTOTYPE.equals(normalizedScope)) {
			throw new BeanDefinitionException(beanName,
					"不支持的作用域 '" + normalizedScope + "'，目前只支持 " + SCOPE_SINGLETON + " 与 " + SCOPE_PROTOTYPE);
		}
		this.beanName = beanName;
		this.beanClass = beanClass;
		this.scope = normalizedScope;
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

	public boolean isSingleton() {
		return SCOPE_SINGLETON.equals(scope);
	}

	public boolean isPrototype() {
		return SCOPE_PROTOTYPE.equals(scope);
	}

	@Override
	public String toString() {
		return "BeanDefinition[" + beanName + " -> " + beanClass.getName() + ", scope=" + scope + "]";
	}
}
