package io.github.kwrld3939.minispring.exception;

/**
 * Bean 定义本身不合法：名称为空、类为 {@code null}、名称重复、作用域未知等。
 *
 * <p>这类失败发生在容器刷新期，属于配置错误；使用者的应对方式是修正注解或注册代码。
 */
public class BeanDefinitionException extends BeansException {

	private static final long serialVersionUID = 1L;

	private final String beanName;

	public BeanDefinitionException(String beanName, String problem) {
		super("Bean 定义不合法" + (beanName == null ? "" : "（beanName='" + beanName + "'）") + "：" + problem);
		this.beanName = beanName;
	}

	public BeanDefinitionException(String beanName, String problem, Throwable cause) {
		super("Bean 定义不合法" + (beanName == null ? "" : "（beanName='" + beanName + "'）") + "：" + problem, cause);
		this.beanName = beanName;
	}

	public String getBeanName() {
		return beanName;
	}
}
