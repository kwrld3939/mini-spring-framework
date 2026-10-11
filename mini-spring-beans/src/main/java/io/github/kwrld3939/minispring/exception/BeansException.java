package io.github.kwrld3939.minispring.exception;

/**
 * Bean 容器相关失败的基类：Bean 定义、创建、注入、查找与生命周期都归入此层。
 */
public class BeansException extends MiniSpringException {

	private static final long serialVersionUID = 1L;

	public BeansException(String message) {
		super(message);
	}

	public BeansException(String message, Throwable cause) {
		super(message, cause);
	}
}
