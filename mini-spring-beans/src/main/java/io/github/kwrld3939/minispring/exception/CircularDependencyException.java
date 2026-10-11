package io.github.kwrld3939.minispring.exception;

/**
 * 检测到容器无法解开的循环依赖。
 *
 * <p>消息必须包含完整依赖链（形如 {@code a -> b -> a}）与注入点，并说明支持范围与修复方向，
 * 见 {@code docs/adr/0002-circular-dependency-scope.md}。
 *
 * <p>支持范围：字段注入环、setter 注入环、自注入（通过早期引用解开）。
 * 不支持：构造器注入参与的环、涉及 prototype 的环——它们会抛出本异常而不是无限递归。
 */
public class CircularDependencyException extends BeanCurrentlyInCreationException {

	private static final long serialVersionUID = 1L;

	private final String dependencyChain;

	public CircularDependencyException(String beanName, Class<?> beanClass, String injectionPoint, String dependencyChain) {
		this(beanName, beanClass, injectionPoint, dependencyChain, null);
	}

	public CircularDependencyException(String beanName, Class<?> beanClass, String injectionPoint,
			String dependencyChain, String chainDetail) {
		super(beanName, beanClass, injectionPoint, buildProblem(dependencyChain, chainDetail));
		this.dependencyChain = dependencyChain;
	}

	/** 完整依赖链，形如 {@code a -> b -> a}。 */
	public String getDependencyChain() {
		return dependencyChain;
	}

	private static String buildProblem(String dependencyChain, String chainDetail) {
		StringBuilder problem = new StringBuilder("检测到循环依赖：").append(dependencyChain);
		if (chainDetail != null && !chainDetail.isBlank()) {
			problem.append("\n  链路细节:").append(chainDetail);
		}
		problem.append("\n  支持范围: 字段注入环、setter 注入环与自注入可以通过早期引用解开；")
				.append("构造器注入参与的环、涉及 prototype 的环不支持。")
				.append("\n  建议: 把环上某一处构造器注入改为字段或 setter 注入；或引入第三方 Bean 打破环。");
		return problem.toString();
	}
}
