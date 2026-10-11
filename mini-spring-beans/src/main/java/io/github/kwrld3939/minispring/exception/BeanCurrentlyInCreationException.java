package io.github.kwrld3939.minispring.exception;

/**
 * 请求的 Bean 正处于创建过程中，且当前拿不到可用引用。
 *
 * <p>这是循环依赖的语义父类：{@link CircularDependencyException} 是它在"已确认成环"时的具体化。
 * 调用方如果只想表达"创建中，请稍后"可以捕获本类，想精确定位环则捕获子类。
 */
public class BeanCurrentlyInCreationException extends BeanCreationException {

	private static final long serialVersionUID = 1L;

	public BeanCurrentlyInCreationException(String beanName, Class<?> beanClass, String injectionPoint, String problem) {
		super(beanName, beanClass, injectionPoint, problem, null);
	}
}
