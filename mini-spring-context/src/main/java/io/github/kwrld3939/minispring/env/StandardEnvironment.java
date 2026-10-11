package io.github.kwrld3939.minispring.env;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Properties;

import io.github.kwrld3939.minispring.exception.ConfigException;

/**
 * 默认的 {@link Environment} 实现：持有一组有序的 {@link PropertySource}，先加入者优先。
 *
 * <p>当前只从类路径加载 {@code .properties} 文件；profile、YAML、环境变量与系统属性属于后续扩展，
 * 但都已经可以表达为额外的 PropertySource，不需要改动读取逻辑。
 */
public class StandardEnvironment implements Environment {

	private final List<PropertySource> propertySources = new ArrayList<>();

	public StandardEnvironment addFirst(PropertySource propertySource) {
		propertySources.add(0, propertySource);
		return this;
	}

	public StandardEnvironment addLast(PropertySource propertySource) {
		propertySources.add(propertySource);
		return this;
	}

	public List<PropertySource> getPropertySources() {
		return Collections.unmodifiableList(propertySources);
	}

	@Override
	public String getProperty(String key) {
		for (PropertySource propertySource : propertySources) {
			String value = propertySource.getProperty(key);
			if (value != null) {
				return value;
			}
		}
		return null;
	}

	@Override
	public String getProperty(String key, String defaultValue) {
		String value = getProperty(key);
		return value != null ? value : defaultValue;
	}

	@Override
	public boolean containsProperty(String key) {
		return getProperty(key) != null;
	}

	/**
	 * 从类路径加载若干 properties 文件（后加入者优先级更低，即先加载者优先）。
	 *
	 * <p>文件不存在时**跳过**而不是失败：配置是可选的；但读取过程中的 IO 错误会抛 {@link ConfigException}。
	 */
	public static StandardEnvironment loadFromClasspath(ClassLoader classLoader, String... locations) {
		StandardEnvironment environment = new StandardEnvironment();
		for (String location : locations) {
			if (location == null || location.isBlank()) {
				continue;
			}
			try (InputStream inputStream = classLoader.getResourceAsStream(location)) {
				if (inputStream == null) {
					continue;
				}
				Properties properties = new Properties();
				properties.load(inputStream);
				environment.addLast(PropertySource.fromProperties(location, properties));
			} catch (IOException ex) {
				throw new ConfigException("读取配置资源 '" + location + "' 失败", ex);
			}
		}
		return environment;
	}

	@Override
	public String toString() {
		return "StandardEnvironment" + propertySources;
	}
}
