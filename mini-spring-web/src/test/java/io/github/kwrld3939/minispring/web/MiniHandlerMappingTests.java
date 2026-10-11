package io.github.kwrld3939.minispring.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import io.github.kwrld3939.minispring.context.MiniApplicationContext;
import io.github.kwrld3939.minispring.web.MiniHandlerMapping.HandlerMethod;

/** 映射表的建立与查找：路径归一化、重复映射的快速失败、以及不可变视图。 */
class MiniHandlerMappingTests {

	private static final String FIXTURE_PACKAGE = "io.github.kwrld3939.minispring.web.fixture";
	private static final String DUPLICATE_PACKAGE = "io.github.kwrld3939.minispring.web.duplicate";

	@Test
	void buildsMappingsFromContextBeans() {
		try (MiniApplicationContext context = new MiniApplicationContext(FIXTURE_PACKAGE)) {
			MiniHandlerMapping mapping = new MiniHandlerMapping(context);

			assertTrue(mapping.getMappings().containsKey("/user/hello"), mapping.getMappings().keySet().toString());
			assertTrue(mapping.getMappings().containsKey("/user/inspect"));
			assertTrue(mapping.getMappings().containsKey("/boom"));
			assertTrue(mapping.getMappings().containsKey("/"), "空路径应归一为根路径");
			assertEquals("hello", mapping.invoke("/user/hello"));
			assertEquals("hello", mapping.invoke("/user/hello/"), "末尾斜杠不影响匹配");
		}
	}

	@Test
	void normalizesPaths() {
		assertEquals("/", MiniHandlerMapping.normalizePath(null));
		assertEquals("/", MiniHandlerMapping.normalizePath("   "));
		assertEquals("/", MiniHandlerMapping.normalizePath("/"));
		assertEquals("/user/hello", MiniHandlerMapping.normalizePath("user/hello"));
		assertEquals("/user/hello", MiniHandlerMapping.normalizePath("/user/hello/"));
	}

	@Test
	void reportsMissingHandlerWithTheRequestedPath() {
		try (MiniApplicationContext context = new MiniApplicationContext(FIXTURE_PACKAGE)) {
			MiniHandlerMapping mapping = new MiniHandlerMapping(context);

			IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> mapping.invoke("/missing"));

			assertTrue(ex.getMessage().contains("No handler for /missing"), ex.getMessage());
		}
	}

	@Test
	void rejectsDuplicateMappingsAtStartup() {
		try (MiniApplicationContext context = new MiniApplicationContext(DUPLICATE_PACKAGE)) {
			IllegalStateException ex = assertThrows(IllegalStateException.class,
					() -> new MiniHandlerMapping(context));

			assertTrue(ex.getMessage().contains("Duplicate request mapping"), ex.getMessage());
			assertTrue(ex.getMessage().contains("/duplicate"), ex.getMessage());
		}
	}

	@Test
	void exposesReadOnlyMappingsAndDescribesHandlers() {
		try (MiniApplicationContext context = new MiniApplicationContext(FIXTURE_PACKAGE)) {
			MiniHandlerMapping mapping = new MiniHandlerMapping(context);
			HandlerMethod handler = mapping.getMappings().get("/user/hello");

			assertTrue(handler.describe().contains("WebFixtureController#hello"), handler.describe());
			assertEquals("hello", handler.invoke());
			assertThrows(UnsupportedOperationException.class, () -> mapping.getMappings().clear());
		}
	}
}
