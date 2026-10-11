package io.github.kwrld3939.minispring.beans;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import io.github.kwrld3939.minispring.exception.BeanDefinitionException;

/** Bean 定义是"类信息"与"对象实例"之间的契约，它的校验必须发生在注册期而不是实例化期。 */
class BeanDefinitionTests {

	@Test
	void defaultsToSingletonScope() {
		BeanDefinition definition = new BeanDefinition("simple", String.class);

		assertEquals("simple", definition.getBeanName());
		assertSame(String.class, definition.getBeanClass());
		assertEquals(BeanDefinition.SCOPE_SINGLETON, definition.getScope());
		assertTrue(definition.isSingleton());
		assertFalse(definition.isPrototype());
		assertTrue(definition.toString().contains("simple"), definition.toString());
		assertTrue(definition.toString().contains("singleton"), definition.toString());
	}

	@Test
	void acceptsPrototypeScope() {
		BeanDefinition definition = new BeanDefinition("prototype", String.class, BeanDefinition.SCOPE_PROTOTYPE);

		assertTrue(definition.isPrototype());
		assertFalse(definition.isSingleton());
	}

	@Test
	void treatsBlankScopeAsSingleton() {
		assertTrue(new BeanDefinition("blankScope", String.class, "   ").isSingleton());
	}

	@Test
	void rejectsBlankName() {
		BeanDefinitionException ex = assertThrows(BeanDefinitionException.class,
				() -> new BeanDefinition("  ", String.class));

		assertEquals("  ", ex.getBeanName());
		assertTrue(ex.getMessage().contains("beanName"), ex.getMessage());
	}

	@Test
	void rejectsNullBeanClass() {
		BeanDefinitionException ex = assertThrows(BeanDefinitionException.class,
				() -> new BeanDefinition("noClass", null));

		assertTrue(ex.getMessage().contains("beanClass"), ex.getMessage());
	}

	@Test
	void rejectsUnknownScopeAndListsSupportedOnes() {
		BeanDefinitionException ex = assertThrows(BeanDefinitionException.class,
				() -> new BeanDefinition("badScope", String.class, "session"));

		assertTrue(ex.getMessage().contains("session"), ex.getMessage());
		assertTrue(ex.getMessage().contains(BeanDefinition.SCOPE_SINGLETON), ex.getMessage());
		assertTrue(ex.getMessage().contains(BeanDefinition.SCOPE_PROTOTYPE), ex.getMessage());
	}
}
