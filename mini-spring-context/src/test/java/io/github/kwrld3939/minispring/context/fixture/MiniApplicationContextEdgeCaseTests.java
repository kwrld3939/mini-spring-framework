package io.github.kwrld3939.minispring.context.fixture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import io.github.kwrld3939.minispring.context.MiniApplicationContext;
import io.github.kwrld3939.minispring.exception.ConfigException;

/** 上下文的边界行为：空包、非法入参、以及对外暴露的装配信息。 */
class MiniApplicationContextEdgeCaseTests {

	private static final String FIXTURE_PACKAGE = MiniApplicationContextEdgeCaseTests.class.getPackageName();
	private static final String EMPTY_PACKAGE = "io.github.kwrld3939.minispring.context.nothinghere";

	@Test
	void startsWithZeroBeansWhenThePackageHasNoComponents() {
		try (MiniApplicationContext context = new MiniApplicationContext(EMPTY_PACKAGE)) {
			assertEquals(0, context.getBeanDefinitionCount());
			assertTrue(context.getBeans().isEmpty());
			assertTrue(context.getBeanFactory().isFrozen(), "即使没有组件，刷新结束后也必须是冻结态");
		}
	}

	@Test
	void rejectsBlankOrNullRootPackageWithActionableMessage() {
		ConfigException blank = assertThrows(ConfigException.class, () -> new MiniApplicationContext("   "));
		assertTrue(blank.getMessage().contains("rootPackage"), blank.getMessage());

		ConfigException nullPackage = assertThrows(ConfigException.class, () -> new MiniApplicationContext(null));
		assertTrue(nullPackage.getMessage().contains("rootPackage"), nullPackage.getMessage());
	}

	@Test
	void exposesAssembledComponentsAndEnvironment() {
		try (MiniApplicationContext context = new MiniApplicationContext(FIXTURE_PACKAGE)) {
			assertEquals(FIXTURE_PACKAGE, context.getRootPackage());
			assertTrue(context.containsBean("simpleComponent"));
			assertEquals(1, context.getBeansOfType(SimpleComponent.class).size());
			assertTrue(context.getBeansOfType(Runnable.class).isEmpty());
			assertTrue(context.getBeanDefinitionNames().length > 0);
			assertNotNull(context.getEnvironment());
			assertNotNull(context.getClassLoader());
			assertNotNull(context.getBeanFactory());
		}
	}
}
