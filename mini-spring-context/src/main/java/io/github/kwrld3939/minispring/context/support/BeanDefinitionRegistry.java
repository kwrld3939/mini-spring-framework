package io.github.kwrld3939.minispring.context.support;

import io.github.kwrld3939.minispring.beans.BeanDefinition;
import io.github.kwrld3939.minispring.exception.BeanDefinitionException;

/**
 * Bean 定义的注册表：只管"定义"，不负责实例化。
 *
 * <p>把它从容器实现中独立出来，是为了让扫描器（{@code ClassPathBeanDefinitionScanner}）
 * 只依赖一个窄接口，而不是依赖整个容器——这样两者可以分别测试与替换。
 */
public interface BeanDefinitionRegistry {

	/**
	 * 注册一个定义。
	 *
	 * @throws BeanDefinitionException 名称重复，或容器已冻结
	 */
	void registerBeanDefinition(BeanDefinition beanDefinition);

	/** 按名称取定义，不存在返回 {@code null}。 */
	BeanDefinition getBeanDefinition(String beanName);

	boolean containsBeanDefinition(String beanName);

	/** 已注册的定义名称，顺序稳定。 */
	String[] getBeanDefinitionNames();

	int getBeanDefinitionCount();
}
