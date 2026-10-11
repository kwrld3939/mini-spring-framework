package io.github.kwrld3939.minispring.context.support;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;

import io.github.kwrld3939.minispring.annotation.MiniQualifier;

/**
 * 一个依赖注入点的描述：类型、可选名称、可选限定符，以及用于错误信息的人类可读描述。
 *
 * <p>存在的意义有两个：让依赖解析只有一条代码路径（构造器参数 / 字段 / setter 共用），
 * 以及让失败信息能准确指出"是哪一处注入坏了"，见 {@code docs/adr/0005-exception-hierarchy-and-diagnostics.md}。
 *
 * <p>{@code nameBasedFallback} 区分了历史行为：字段注入会先按字段名匹配同名 Bean（Spring 的常见约定），
 * 而构造器参数与 setter 参数只按类型/限定符匹配，避免参数名意外改变注入目标。
 */
public final class DependencyDescriptor {

	private final Class<?> type;
	private final String name;
	private final String qualifier;
	private final String description;
	private final boolean nameBasedFallback;

	private DependencyDescriptor(Class<?> type, String name, String qualifier, String description,
			boolean nameBasedFallback) {
		this.type = type;
		this.name = name;
		this.qualifier = qualifier;
		this.description = description;
		this.nameBasedFallback = nameBasedFallback;
	}

	public static DependencyDescriptor forField(Field field) {
		MiniQualifier qualifier = field.getAnnotation(MiniQualifier.class);
		String description = "字段 " + field.getDeclaringClass().getName() + "#" + field.getName();
		return new DependencyDescriptor(field.getType(), field.getName(), qualifierValue(qualifier), description, true);
	}

	public static DependencyDescriptor forConstructorParameter(Constructor<?> constructor, Parameter parameter, int index) {
		MiniQualifier qualifier = parameter.getAnnotation(MiniQualifier.class);
		String description = "构造器参数 " + index + " (" + parameter.getType().getName() + ") of "
				+ constructor.getDeclaringClass().getName();
		return new DependencyDescriptor(parameter.getType(), parameterName(parameter), qualifierValue(qualifier),
				description, false);
	}

	public static DependencyDescriptor forSetterParameter(Method method, Parameter parameter) {
		MiniQualifier qualifier = parameter.getAnnotation(MiniQualifier.class);
		String description = "setter 方法 " + method.getDeclaringClass().getName() + "#" + method.getName();
		return new DependencyDescriptor(parameter.getType(), parameterName(parameter), qualifierValue(qualifier),
				description, false);
	}

	private static String qualifierValue(MiniQualifier qualifier) {
		return qualifier == null ? null : qualifier.value();
	}

	private static String parameterName(Parameter parameter) {
		return parameter.isNamePresent() ? parameter.getName() : null;
	}

	public Class<?> getType() {
		return type;
	}

	/** 字段名或参数名，可能为 {@code null}（未使用 {@code -parameters} 编译时）。 */
	public String getName() {
		return name;
	}

	/** {@code @MiniQualifier} 指定的 bean 名称，未指定为 {@code null}。 */
	public String getQualifier() {
		return qualifier;
	}

	public String getDescription() {
		return description;
	}

	public boolean isNameBasedFallback() {
		return nameBasedFallback;
	}

	@Override
	public String toString() {
		return description;
	}
}
