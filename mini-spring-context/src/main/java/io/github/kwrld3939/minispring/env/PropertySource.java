package io.github.kwrld3939.minispring.env;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;

/**
 * 一个命名的配置来源（不可变）。
 *
 * <p>多个来源按加入顺序决定优先级：先加入者优先。这样"外部覆盖内部"（例如系统属性覆盖文件）
 * 只需要把外部来源 addFirst 即可，无需改动读取逻辑。
 */
public final class PropertySource {

	private final String name;
	private final Map<String, String> properties;

	public PropertySource(String name, Map<String, String> properties) {
		if (name == null || name.isBlank()) {
			throw new IllegalArgumentException("PropertySource name must not be blank");
		}
		this.name = name;
		this.properties = Collections.unmodifiableMap(new LinkedHashMap<>(properties));
	}

	public static PropertySource fromProperties(String name, Properties properties) {
		Map<String, String> values = new LinkedHashMap<>();
		for (String key : properties.stringPropertyNames()) {
			values.put(key, properties.getProperty(key));
		}
		return new PropertySource(name, values);
	}

	public String getName() {
		return name;
	}

	public String getProperty(String key) {
		return properties.get(key);
	}

	public boolean containsProperty(String key) {
		return properties.containsKey(key);
	}

	public Map<String, String> asMap() {
		return properties;
	}

	public int size() {
		return properties.size();
	}

	@Override
	public String toString() {
		return "PropertySource[" + name + ", " + properties.size() + " properties]";
	}
}
