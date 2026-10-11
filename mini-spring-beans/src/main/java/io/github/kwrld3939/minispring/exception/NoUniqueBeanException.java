package io.github.kwrld3939.minispring.exception;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 按类型查找时找到多个候选 Bean，容器无法判断该注入哪一个。
 *
 * <p>消息必须列出**全部**候选（不截断）以及每个候选的实现类，并给出可执行的建议，
 * 因为这是使用者最容易卡住的一类失败，见 {@code docs/adr/0005-exception-hierarchy-and-diagnostics.md}。
 */
public class NoUniqueBeanException extends BeansException {

	private static final long serialVersionUID = 1L;

	private final Class<?> beanType;
	private final Map<String, Class<?>> candidates;

	public NoUniqueBeanException(Class<?> beanType, Map<String, Class<?>> candidates, String requestedBy) {
		super(buildMessage(beanType, candidates, requestedBy));
		this.beanType = beanType;
		this.candidates = new LinkedHashMap<>(candidates);
	}

	public Class<?> getBeanType() {
		return beanType;
	}

	/** 候选 Bean：beanName -> 实现类。 */
	public Map<String, Class<?>> getCandidates() {
		return new LinkedHashMap<>(candidates);
	}

	private static String buildMessage(Class<?> beanType, Map<String, Class<?>> candidates, String requestedBy) {
		StringBuilder message = new StringBuilder();
		message.append("解析 ").append(requestedBy == null ? "依赖" : requestedBy)
				.append(" 时，类型 ").append(beanType.getName())
				.append(" 找到 ").append(candidates.size()).append(" 个候选，无法确定注入哪一个。");
		message.append("\n  候选:");
		for (Map.Entry<String, Class<?>> entry : candidates.entrySet()) {
			message.append("\n    ").append(entry.getKey()).append(" (").append(entry.getValue().getName()).append(")");
		}
		String firstCandidate = candidates.keySet().iterator().next();
		message.append("\n  建议: 在注入点加 @MiniQualifier(\"").append(firstCandidate)
				.append("\") 指定 bean 名称，或改用按 bean 名称注入。");
		return message.toString();
	}
}
