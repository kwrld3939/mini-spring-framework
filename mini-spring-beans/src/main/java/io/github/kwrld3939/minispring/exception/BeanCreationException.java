package io.github.kwrld3939.minispring.exception;

/**
 * Bean 创建过程中的失败（实例化、注入、初始化回调、后置处理）。
 *
 * <p>仅用于"创建流程"本身；定义不合法用 {@link BeanDefinitionException}，
 * 找不到依赖用 {@link NoSuchBeanException}。原始异常始终保留在 {@code cause} 中，不允许丢弃。
 */
public class BeanCreationException extends BeansException {

	private static final long serialVersionUID = 1L;

	private final String beanName;
	private final Class<?> beanClass;
	private final String injectionPoint;

	public BeanCreationException(String beanName, Class<?> beanClass, String injectionPoint, String problem,
			Throwable cause) {
		super(buildMessage(beanName, beanClass, injectionPoint, problem, cause), cause);
		this.beanName = beanName;
		this.beanClass = beanClass;
		this.injectionPoint = injectionPoint;
	}

	public String getBeanName() {
		return beanName;
	}

	public Class<?> getBeanClass() {
		return beanClass;
	}

	/** 出错的注入点描述（构造器参数 / 字段 / setter 方法），可能为 {@code null}。 */
	public String getInjectionPoint() {
		return injectionPoint;
	}

	private static String buildMessage(String beanName, Class<?> beanClass, String injectionPoint, String problem,
			Throwable cause) {
		StringBuilder message = new StringBuilder("创建 Bean '").append(beanName).append("'");
		if (beanClass != null) {
			message.append(" (").append(beanClass.getName()).append(")");
		}
		message.append(" 失败");
		if (injectionPoint != null && !injectionPoint.isBlank()) {
			message.append("\n  注入点: ").append(injectionPoint);
		}
		if (problem != null && !problem.isBlank()) {
			message.append("\n  问题: ").append(problem);
		}
		if (cause != null) {
			message.append("\n  原因: ").append(cause.getClass().getSimpleName());
			if (cause.getMessage() != null) {
				message.append(": ").append(cause.getMessage());
			}
		}
		return message.toString();
	}
}
