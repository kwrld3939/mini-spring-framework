package io.github.kwrld3939.minispring.env;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;

import org.junit.jupiter.api.Test;

/**
 * 配置层的契约：来源优先级、默认值、缺失资源的容忍度。
 *
 * <p>这些看起来是"小 API"，但它们决定了外部化配置能否覆盖内部默认值——用错了会在生产环境里
 * 表现为"配置明明改了却不生效"，因此值得逐条固化。
 */
class EnvironmentTests {

	@Test
	void propertySourceExposesItsContentAsReadOnlySnapshot() {
		Map<String, String> values = new LinkedHashMap<>();
		values.put("app.name", "mini-spring");
		PropertySource source = new PropertySource("test-source", values);

		assertEquals("test-source", source.getName());
		assertEquals("mini-spring", source.getProperty("app.name"));
		assertTrue(source.containsProperty("app.name"));
		assertFalse(source.containsProperty("missing"));
		assertEquals(1, source.size());
		assertEquals("mini-spring", source.asMap().get("app.name"));
		assertTrue(source.toString().contains("test-source"));

		values.put("app.name", "changed-after-construction");
		assertEquals("mini-spring", source.getProperty("app.name"), "构造时应做快照，之后外部改动不得影响它");
		assertThrows(UnsupportedOperationException.class, () -> source.asMap().put("x", "y"));
	}

	@Test
	void propertySourceRejectsBlankName() {
		assertThrows(IllegalArgumentException.class, () -> new PropertySource("  ", Map.of()));
	}

	@Test
	void propertySourceCopiesFromProperties() {
		Properties properties = new Properties();
		properties.setProperty("app.name", "from-properties");

		PropertySource source = PropertySource.fromProperties("loaded", properties);

		assertEquals("from-properties", source.getProperty("app.name"));
		assertEquals(1, source.size());
	}

	@Test
	void environmentGivesPriorityToTheFirstAddedSource() {
		StandardEnvironment environment = new StandardEnvironment();
		environment.addLast(new PropertySource("file", Map.of("app.name", "from-file", "only.file", "x")));
		environment.addFirst(new PropertySource("system", Map.of("app.name", "from-system")));

		assertEquals("from-system", environment.getProperty("app.name"),
				"先加入者优先——这是实现「外部配置覆盖内部默认值」的机制");
		assertEquals("x", environment.getProperty("only.file"));
		assertNull(environment.getProperty("missing"));
		assertEquals("fallback", environment.getProperty("missing", "fallback"));
		assertEquals("from-system", environment.getProperty("app.name", "fallback"),
				"默认值只在完全读不到时才生效");
		assertTrue(environment.containsProperty("app.name"));
		assertFalse(environment.containsProperty("missing"));
		assertEquals(2, environment.getPropertySources().size());
		assertTrue(environment.toString().contains("file"));
		assertThrows(UnsupportedOperationException.class, () -> environment.getPropertySources().clear());
	}

	@Test
	void loadFromClasspathSkipsMissingOrBlankLocations() {
		StandardEnvironment environment = StandardEnvironment.loadFromClasspath(getClass().getClassLoader(),
				"does-not-exist.properties", "application.properties", null, "   ");

		assertEquals(1, environment.getPropertySources().size(), "只应加载真实存在的那一个资源");
		assertEquals("mini-spring-context-test", environment.getProperty("fixture.name"));
	}

	@Test
	void loadFromClasspathWithoutLocationsProducesEmptyEnvironment() {
		StandardEnvironment environment = StandardEnvironment.loadFromClasspath(getClass().getClassLoader());

		assertTrue(environment.getPropertySources().isEmpty());
		assertFalse(environment.containsProperty("anything"));
		assertNull(environment.getProperty("anything"));
	}
}
