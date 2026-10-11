package io.github.kwrld3939.minispring.exception;

import java.util.Collection;

/**
 * 按名称/类型/限定符找不到 Bean。
 *
 * <p>消息中会列出当前容器里可用的 bean 名称，因为"拼错名字"是这类失败最常见的原因。
 */
public class NoSuchBeanException extends BeansException {

	private static final long serialVersionUID = 1L;

	private final String requested;

	public NoSuchBeanException(String requested, Collection<String> availableBeanNames, String hint) {
		super(buildMessage(requested, availableBeanNames, hint));
		this.requested = requested;
	}

	public String getRequested() {
		return requested;
	}

	private static String buildMessage(String requested, Collection<String> availableBeanNames, String hint) {
		StringBuilder message = new StringBuilder("找不到 Bean：" + requested);
		if (availableBeanNames == null || availableBeanNames.isEmpty()) {
			message.append("\n  容器中没有注册任何 Bean。");
		} else {
			message.append("\n  可用的 Bean（").append(availableBeanNames.size()).append(" 个）: ")
					.append(String.join(", ", availableBeanNames));
		}
		if (hint != null && !hint.isBlank()) {
			message.append("\n  建议: ").append(hint);
		}
		return message.toString();
	}
}
