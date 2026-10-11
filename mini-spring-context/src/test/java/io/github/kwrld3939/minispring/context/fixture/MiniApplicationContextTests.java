package io.github.kwrld3939.minispring.context.fixture;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.github.kwrld3939.minispring.context.MiniApplicationContext;
import io.github.kwrld3939.minispring.exception.NoSuchBeanException;

/**
 * 容器集成测试：fixture 与测试同包，便于直接断言包私有组件的内部状态。
 *
 * <p>容器会扫描本包，因此测试类也会被加载，但没有 {@code @MiniComponent} 就不会被注册为 Bean。
 */
class MiniApplicationContextTests {

	private static final String FIXTURE_PACKAGE = MiniApplicationContextTests.class.getPackageName();

	@BeforeEach
	void resetRecorder() {
		LifecycleRecorder.clear();
	}

	@Test
	void scansAnnotatedComponentsOnly() {
		try (MiniApplicationContext context = new MiniApplicationContext(FIXTURE_PACKAGE)) {
			assertTrue(context.containsBean("simpleComponent"));
			assertTrue(context.containsBean("fieldCycleA"));
			assertTrue(context.containsBean("prototypeComponent"));
			assertTrue(context.containsBean("configuredComponent"));
			assertFalse(context.containsBean("constructorCycleA"), "未标注 @MiniComponent 的类不应进入容器");
			assertFalse(context.containsBean("failingDestroyComponent"));
		}
	}

	@Test
	void injectsConstructorDependencies() {
		try (MiniApplicationContext context = new MiniApplicationContext(FIXTURE_PACKAGE)) {
			ConsumerComponent consumer = context.getBean(ConsumerComponent.class);

			assertSame(context.getBean(SimpleComponent.class), consumer.simple());
		}
	}

	@Test
	void injectsConfigurationValues() {
		try (MiniApplicationContext context = new MiniApplicationContext(FIXTURE_PACKAGE)) {
			assertEquals("mini-spring-context-test", context.getBean(ConfiguredComponent.class).name());
		}
	}

	@Test
	void resolvesFieldInjectionCycleThroughEarlyReference() {
		try (MiniApplicationContext context = new MiniApplicationContext(FIXTURE_PACKAGE)) {
			FieldCycleA a = context.getBean(FieldCycleA.class);
			FieldCycleB b = context.getBean(FieldCycleB.class);

			assertSame(b, a.other());
			assertSame(a, b.other());
			assertTrue(context.getBeanFactory().getEarlyReferencesExposed().contains("fieldCycleA"));
			assertTrue(context.getBeanFactory().getDiagnostics().isEmpty(),
					"环上没有代理时不应产生诊断信息：" + context.getBeanFactory().getDiagnostics());
		}
	}

	@Test
	void supportsSelfInjection() {
		try (MiniApplicationContext context = new MiniApplicationContext(FIXTURE_PACKAGE)) {
			SelfInjectingComponent component = context.getBean(SelfInjectingComponent.class);

			assertSame(component, component.self());
		}
	}

	@Test
	void supportsSingletonAndPrototypeScopes() {
		try (MiniApplicationContext context = new MiniApplicationContext(FIXTURE_PACKAGE)) {
			assertSame(context.getBean(SingletonComponent.class), context.getBean(SingletonComponent.class));
			assertNotSame(context.getBean(PrototypeComponent.class), context.getBean(PrototypeComponent.class));
		}
	}

	@Test
	void registrationOrderIsDeterministicAcrossContexts() {
		try (MiniApplicationContext first = new MiniApplicationContext(FIXTURE_PACKAGE);
				MiniApplicationContext second = new MiniApplicationContext(FIXTURE_PACKAGE)) {
			assertArrayEquals(first.getBeanDefinitionNames(), second.getBeanDefinitionNames());
		}
	}

	@Test
	void destroysDependentsBeforeTheirDependencies() {
		MiniApplicationContext context = new MiniApplicationContext(FIXTURE_PACKAGE);
		LifecycleRecorder.clear();

		context.close();

		assertEquals(List.of("A", "B"), LifecycleRecorder.events(),
				"DestroyOrderA 依赖 DestroyOrderB，因此 A 必须先销毁");
	}

	@Test
	void closeIsIdempotent() {
		MiniApplicationContext context = new MiniApplicationContext(FIXTURE_PACKAGE);
		context.close();
		LifecycleRecorder.clear();

		context.close();

		assertTrue(LifecycleRecorder.events().isEmpty(), "重复 close 不应再次触发销毁回调");
	}

	@Test
	void throwsWhenBeanDoesNotExist() {
		try (MiniApplicationContext context = new MiniApplicationContext(FIXTURE_PACKAGE)) {
			assertThrows(NoSuchBeanException.class, () -> context.getBean("notRegistered"));
		}
	}
}
