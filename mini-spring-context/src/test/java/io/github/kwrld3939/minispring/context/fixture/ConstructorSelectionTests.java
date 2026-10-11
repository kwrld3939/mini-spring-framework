package io.github.kwrld3939.minispring.context.fixture;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.github.kwrld3939.minispring.beans.BeanDefinition;
import io.github.kwrld3939.minispring.context.support.DefaultListableBeanFactory;
import io.github.kwrld3939.minispring.env.StandardEnvironment;
import io.github.kwrld3939.minispring.exception.BeanDefinitionException;

/**
 * 构造器选择（ADR-0012）：容器不做猜测。
 *
 * <p>这件事看起来是细节，但"选错构造器"会静默地注入错误的依赖集合——不报错、不打日志，
 * 只是行为不对。所以策略是：只有一个构造器就直接用，多个就必须显式指定，含糊即失败。
 */
class ConstructorSelectionTests {

	private DefaultListableBeanFactory factory;

	@BeforeEach
	void setUp() {
		factory = new DefaultListableBeanFactory(new StandardEnvironment(), getClass().getClassLoader());
		factory.registerBeanDefinition(new BeanDefinition("simpleComponent", SimpleComponent.class));
	}

	@Test
	void usesTheOnlyConstructorWithoutAnyAnnotation() {
		factory.registerBeanDefinition(new BeanDefinition("consumerComponent", ConsumerComponent.class));

		assertSame(factory.getBean("simpleComponent"), factory.getBean(ConsumerComponent.class).simple());
	}

	@Test
	void acceptsSingleAnnotatedConstructor() {
		factory.registerBeanDefinition(new BeanDefinition("singleAnnotatedConstructor", SingleAnnotatedConstructor.class));

		SingleAnnotatedConstructor bean = (SingleAnnotatedConstructor) factory.getBean("singleAnnotatedConstructor");

		assertSame(factory.getBean("simpleComponent"), bean.simple());
	}

	@Test
	void usesTheAnnotatedConstructorWhenSeveralExist() {
		factory.registerBeanDefinition(
				new BeanDefinition("twoConstructorsOneAnnotated", TwoConstructorsOneAnnotated.class));

		TwoConstructorsOneAnnotated bean = (TwoConstructorsOneAnnotated) factory.getBean("twoConstructorsOneAnnotated");

		assertSame(factory.getBean("simpleComponent"), bean.viaConstructor(),
				"必须使用标注 @MiniAutowired 的那个构造器，而不是参数最多的那个");
	}

	@Test
	void rejectsSeveralConstructorsWithoutAnnotation() {
		factory.registerBeanDefinition(
				new BeanDefinition("twoConstructorsNoneAnnotated", TwoConstructorsNoneAnnotated.class));

		BeanDefinitionException ex = assertThrows(BeanDefinitionException.class,
				() -> factory.getBean("twoConstructorsNoneAnnotated"));

		assertTrue(ex.getMessage().contains("不会替你猜"), ex.getMessage());
		assertTrue(ex.getMessage().contains("候选"), ex.getMessage());
		assertTrue(ex.getMessage().contains("TwoConstructorsNoneAnnotated()"), ex.getMessage());
		assertTrue(ex.getMessage().contains("建议"), ex.getMessage());
	}

	@Test
	void rejectsMultipleAnnotatedConstructors() {
		factory.registerBeanDefinition(
				new BeanDefinition("twoConstructorsBothAnnotated", TwoConstructorsBothAnnotated.class));

		BeanDefinitionException ex = assertThrows(BeanDefinitionException.class,
				() -> factory.getBean("twoConstructorsBothAnnotated"));

		assertTrue(ex.getMessage().contains("多个构造器标注了 @MiniAutowired"), ex.getMessage());
		assertTrue(ex.getMessage().contains("[@MiniAutowired]"), ex.getMessage());
	}
}
