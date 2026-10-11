package io.github.kwrld3939.minispring.exception;

/**
 * 配置读取或类型转换失败：属性缺失、资源无法读取、值无法转换为目标类型。
 *
 * <p>消息必须包含配置 key、期望类型与原始值（若可得），让使用者不必读源码就能定位。
 */
public class ConfigException extends MiniSpringException {

	private static final long serialVersionUID = 1L;

	public ConfigException(String message) {
		super(message);
	}

	public ConfigException(String message, Throwable cause) {
		super(message, cause);
	}

	public static ConfigException missingProperty(String key, String targetDescription) {
		return new ConfigException("找不到配置项 '" + key + "'，无法注入到 " + targetDescription
				+ "\n  建议: 在 application.properties 中补充 " + key + "=... ，或改用 @MiniValue(\"" + key
				+ "\") 之外的注入方式。");
	}

	public static ConfigException conversionFailed(String key, String value, Class<?> targetType, Throwable cause) {
		return new ConfigException("配置项 '" + key + "' 的值 '" + value + "' 无法转换为 "
				+ targetType.getName() + "\n  建议: 检查该配置项的取值格式。", cause);
	}
}
