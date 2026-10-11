package io.github.kwrld3939.minispring.env;

/**
 * 配置访问抽象：容器只依赖本接口读取配置，具体来源（properties 文件、系统属性、环境变量）由实现决定。
 */
public interface Environment {

	/** 读取配置值，不存在返回 {@code null}。 */
	String getProperty(String key);

	/** 读取配置值，不存在时返回默认值。 */
	String getProperty(String key, String defaultValue);

	boolean containsProperty(String key);
}
