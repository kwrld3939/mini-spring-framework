package io.github.kwrld3939.minispring.beans;

import java.util.Map;

/**
 * Bean 容器的读取契约：只负责"按名称/类型取出可用的 Bean"。
 *
 * <p>这是本框架最小的对外抽象，`mini-spring-context` 在其上叠加定义注册、扫描与生命周期。
 * 依赖注入的解析全部通过本接口进行，因此"注入点拿到的一定是终态对象（含 AOP 代理）"这条
 * 不变式由实现方在这一层保证，见 {@code docs/adr/0001-dependency-resolution-and-proxy-exposure.md}。
 */
public interface BeanFactory {

	/**
	 * 按名称取 Bean。
	 *
	 * @throws io.github.kwrld3939.minispring.exception.NoSuchBeanException 名称不存在
	 */
	Object getBean(String beanName);

	/**
	 * 按类型取 Bean，要求该类型在容器中唯一。
	 *
	 * @throws io.github.kwrld3939.minispring.exception.NoSuchBeanException   没有该类型的 Bean
	 * @throws io.github.kwrld3939.minispring.exception.NoUniqueBeanException 存在多个候选
	 */
	<T> T getBean(Class<T> beanType);

	/** 名称是否已注册（不触发实例化）。 */
	boolean containsBean(String beanName);

	/** 已注册的全部 bean 名称，顺序稳定（与扫描/注册顺序一致）。 */
	String[] getBeanDefinitionNames();

	/** 按类型取出所有匹配的 Bean（可能触发 prototype 的实例化）。 */
	<T> Map<String, T> getBeansOfType(Class<T> beanType);
}
