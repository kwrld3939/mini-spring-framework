package io.github.kwrld3939.minispring.exception;

/**
 * 框架所有异常的根类型（unchecked）。
 *
 * <p>约定：本框架不使用错误码字符串表达语义，异常类型本身就携带语义，
 * 调用方可以按类型捕获并降级处理；同时每条消息都必须包含足够定位问题的信息
 * （bean 名称、类名、注入点、依赖链与修复建议），见 {@code docs/adr/0005-exception-hierarchy-and-diagnostics.md}。
 */
public class MiniSpringException extends RuntimeException {

	private static final long serialVersionUID = 1L;

	public MiniSpringException(String message) {
		super(message);
	}

	public MiniSpringException(String message, Throwable cause) {
		super(message, cause);
	}
}
