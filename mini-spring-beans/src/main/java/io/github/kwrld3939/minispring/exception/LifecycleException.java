package io.github.kwrld3939.minispring.exception;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 生命周期回调（{@code @MiniPostConstruct} / {@code @MiniPreDestroy}）执行失败。
 *
 * <p>销毁阶段的约定：单个 Bean 的销毁回调失败**不会中断**其余 Bean 的销毁，
 * 全部失败会被收集到本异常中（每个失败作为 suppressed 异常保留原始栈）。
 */
public class LifecycleException extends BeansException {

	private static final long serialVersionUID = 1L;

	private final List<String> failedBeanNames;

	public LifecycleException(String phase, List<String> failedBeanNames, List<Throwable> causes) {
		super(buildMessage(phase, failedBeanNames, causes));
		this.failedBeanNames = Collections.unmodifiableList(new ArrayList<>(failedBeanNames));
		if (causes != null) {
			for (Throwable cause : causes) {
				addSuppressed(cause);
			}
		}
	}

	public List<String> getFailedBeanNames() {
		return failedBeanNames;
	}

	private static String buildMessage(String phase, List<String> failedBeanNames, List<Throwable> causes) {
		StringBuilder message = new StringBuilder(phase).append(" 阶段有 ")
				.append(failedBeanNames.size()).append(" 个 Bean 的回调失败，其余 Bean 已正常处理：");
		for (int i = 0; i < failedBeanNames.size(); i++) {
			message.append("\n  ").append(failedBeanNames.get(i)).append(": ");
			Throwable cause = causes == null || i >= causes.size() ? null : causes.get(i);
			message.append(cause == null ? "(无原因)" : cause.toString());
		}
		return message.toString();
	}
}
