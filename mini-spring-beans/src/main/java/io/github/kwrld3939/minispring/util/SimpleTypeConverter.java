package io.github.kwrld3939.minispring.util;

public final class SimpleTypeConverter {

	private SimpleTypeConverter() {
	}

	public static Object convert(String value, Class<?> targetType) {
		if (targetType == String.class) {
			return value;
		}
		if (targetType == int.class || targetType == Integer.class) {
			return Integer.parseInt(value);
		}
		if (targetType == long.class || targetType == Long.class) {
			return Long.parseLong(value);
		}
		if (targetType == boolean.class || targetType == Boolean.class) {
			return Boolean.parseBoolean(value);
		}
		if (targetType == double.class || targetType == Double.class) {
			return Double.parseDouble(value);
		}
		throw new IllegalArgumentException("Unsupported target type: " + targetType.getName());
	}
}
