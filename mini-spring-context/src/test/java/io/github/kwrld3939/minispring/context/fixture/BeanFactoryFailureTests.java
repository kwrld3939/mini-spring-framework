package io.github.kwrld3939.minispring.context.fixture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.github.kwrld3939.minispring.beans.BeanDefinition;
import io.github.kwrld3939.minispring.context.support.DefaultListableBeanFactory;
import io.github.kwrld3939.minispring.env.StandardEnvironment;
import io.github.kwrld3939.minispring.exception.BeanCreationException;
import io.github.kwrld3939.minispring.exception.BeansException;
import io.github.kwrld3939.minispring.exception.ConfigException;
import io.github.kwrld3939.minispring.exception.NoSuchBeanException;

/**
 * 失败语义与诊断 API 的固化。
 *
 * <p>约定：注入点上的依赖解析失败统一表现为 {@link BeansException}（消息里必须点名**哪个 Bean 的哪个注入点**），
 * 能给出更精确类型的场景（找不到 / 多候选）保留在 `cause` 里；循环依赖异常则原样抛出、不被包装，
 * 因为它自身已经携带完整链路。
 */
class BeanFactoryFailureTests {

	private DefaultListableBeanFactory factory;

	@BeforeEach
	void setUp() {
		LifecycleRecorder.clear();
		factory = new DefaultListableBeanFactory(
				StandardEnvironment.loadFromClasspath(getClass().getClassLoader(), "application.properties"));
	}

	@Test
	void exposesRegistryMetadataAndDiagnostics() {
		factory.registerBeanDefinition(new BeanDefinition("simpleComponent", SimpleComponent.class));
		factory.registerBeanDefinition(new BeanDefinition("manualServiceA", ManualServiceA.class));

		assertTrue(factory.containsBean("simpleComponent"));
		assertFalse(factory.containsBean("missing"));
		assertTrue(factory.containsBeanDefinition("manualServiceA"));
		assertEquals(2, factory.getBeanDefinitionCount());
		assertEquals(2, factory.getBeanDefinitionNames().length);
		assertSame(SimpleComponent.class, factory.getBeanDefinition("simpleComponent").getBeanClass());
		assertNotNull(factory.getEnvironment());
		assertNotNull(factory.getClassLoader());
		assertFalse(factory.isFrozen());
		assertTrue(factory.getEarlyReferencesExposed().isEmpty());

		factory.getBean("simpleComponent");
		factory.freeze();

		assertTrue(factory.isFrozen());
		assertTrue(factory.getSingletonBeans().containsKey("simpleComponent"));
	}

	@Test
	void recordsDependencyEdgesForDiagnostics() {
		factory.registerBeanDefinition(new BeanDefinition("simpleComponent", SimpleComponent.class));
		factory.registerBeanDefinition(new BeanDefinition("consumerComponent", ConsumerComponent.class));

		factory.getBean("consumerComponent");

		assertEquals(Set.of("simpleComponent"), factory.getDependencies().get("consumerComponent"));
	}

	@Test
	void returnsEveryBeanMatchingAType() {
		factory.registerBeanDefinition(new BeanDefinition("manualServiceA", ManualServiceA.class));
		factory.registerBeanDefinition(new BeanDefinition("manualServiceB", ManualServiceB.class));
		factory.registerBeanDefinition(new BeanDefinition("simpleComponent", SimpleComponent.class));

		Map<String, ManualService> services = factory.getBeansOfType(ManualService.class);

		assertEquals(Set.of("manualServiceA", "manualServiceB"), services.keySet());
		assertTrue(factory.getBeansOfType(Runnable.class).isEmpty());
	}

	@Test
	void rejectsNullTypeLookupWithActionableMessage() {
		NoSuchBeanException ex = assertThrows(NoSuchBeanException.class, () -> factory.getBean((Class<Object>) null));

		assertTrue(ex.getMessage().contains("null"), ex.getMessage());
	}

	@Test
	void reportsQualifierPointingToUnknownBean() {
		factory.registerBeanDefinition(new BeanDefinition("qualifierMissesComponent", QualifierMissesComponent.class));

		BeansException ex = assertThrows(BeansException.class, () -> factory.getBean("qualifierMissesComponent"));

		assertInstanceOf(NoSuchBeanException.class, ex.getCause());
		assertTrue(ex.getCause().getMessage().contains("@MiniQualifier"), ex.getCause().getMessage());
		assertTrue(ex.getMessage().contains("qualifierMissesComponent"), ex.getMessage());
	}

	@Test
	void reportsQualifierTypeMismatch() {
		factory.registerBeanDefinition(new BeanDefinition("simpleComponent", SimpleComponent.class));
		factory.registerBeanDefinition(new BeanDefinition("qualifierWrongTypeComponent",
				QualifierWrongTypeComponent.class));

		BeansException ex = assertThrows(BeansException.class,
				() -> factory.getBean("qualifierWrongTypeComponent"));

		assertTrue(ex.getMessage().contains("不是该类型"), ex.getMessage());
		assertTrue(ex.getMessage().contains("字段"), ex.getMessage());
	}

	@Test
	void rejectsAutowiredSetterWithWrongParameterCount() {
		factory.registerBeanDefinition(new BeanDefinition("twoParameterSetterComponent",
				TwoParameterSetterComponent.class));

		BeanCreationException ex = assertThrows(BeanCreationException.class,
				() -> factory.getBean("twoParameterSetterComponent"));

		assertTrue(ex.getMessage().contains("只能有一个参数"), ex.getMessage());
	}

	@Test
	void rejectsLifecycleMethodWithParameters() {
		factory.registerBeanDefinition(new BeanDefinition("lifecycleWithParameterComponent",
				LifecycleWithParameterComponent.class));

		BeanCreationException ex = assertThrows(BeanCreationException.class,
				() -> factory.getBean("lifecycleWithParameterComponent"));

		assertTrue(ex.getMessage().contains("必须无参数"), ex.getMessage());
	}

	@Test
	void wrapsConstructorFailureKeepingItsCause() {
		factory.registerBeanDefinition(new BeanDefinition("constructorThrowsComponent",
				ConstructorThrowsComponent.class));

		BeanCreationException ex = assertThrows(BeanCreationException.class,
				() -> factory.getBean("constructorThrowsComponent"));

		assertInstanceOf(IllegalStateException.class, ex.getCause());
		assertTrue(ex.getMessage().contains("构造器抛出异常"), ex.getMessage());
	}

	@Test
	void rejectsValueAnnotationWithBlankKey() {
		factory.registerBeanDefinition(new BeanDefinition("blankValueKeyComponent", BlankValueKeyComponent.class));

		ConfigException ex = assertThrows(ConfigException.class, () -> factory.getBean("blankValueKeyComponent"));

		assertTrue(ex.getMessage().contains("不能为空"), ex.getMessage());
	}

	@Test
	void rejectsBeanPostProcessorReturningNull() {
		factory.addBeanPostProcessor(new NullReturningBeanPostProcessor());
		factory.registerBeanDefinition(new BeanDefinition("simpleComponent", SimpleComponent.class));

		BeanCreationException ex = assertThrows(BeanCreationException.class, () -> factory.getBean("simpleComponent"));

		assertTrue(ex.getMessage().contains("返回了 null"), ex.getMessage());
		assertTrue(ex.getMessage().contains("NullReturningBeanPostProcessor"), ex.getMessage());
	}

	@Test
	void wrapsBeanPostProcessorFailure() {
		factory.addBeanPostProcessor(new ThrowingBeanPostProcessor());
		factory.registerBeanDefinition(new BeanDefinition("simpleComponent", SimpleComponent.class));

		BeanCreationException ex = assertThrows(BeanCreationException.class, () -> factory.getBean("simpleComponent"));

		assertTrue(ex.getMessage().contains("后置处理失败"), ex.getMessage());
		assertInstanceOf(IllegalStateException.class, ex.getCause());
	}

	@Test
	void destroysOnceEvenIfCalledTwice() {
		factory.registerBeanDefinition(new BeanDefinition("recordingDestroyComponent", RecordingDestroyComponent.class));
		factory.getBean("recordingDestroyComponent");
		factory.freeze();

		factory.destroySingletons();
		LifecycleRecorder.clear();
		factory.destroySingletons();

		assertTrue(LifecycleRecorder.events().isEmpty(), "重复销毁不应再次触发回调");
	}

	@Test
	void singletonSnapshotBeforeFreezeIsReadOnly() {
		factory.registerBeanDefinition(new BeanDefinition("simpleComponent", SimpleComponent.class));
		factory.getBean("simpleComponent");

		Map<String, Object> snapshot = factory.getSingletonBeans();

		assertTrue(snapshot.containsKey("simpleComponent"));
		assertThrows(UnsupportedOperationException.class, () -> snapshot.put("x", new Object()));
	}

	@Test
	void preInstantiateSingletonsSkipsPrototypes() {
		factory.registerBeanDefinition(new BeanDefinition("simpleComponent", SimpleComponent.class));
		factory.registerBeanDefinition(new BeanDefinition("prototypeComponent", PrototypeComponent.class,
				BeanDefinition.SCOPE_PROTOTYPE));

		factory.preInstantiateSingletons();

		assertTrue(factory.getSingletonBeans().containsKey("simpleComponent"));
		assertFalse(factory.getSingletonBeans().containsKey("prototypeComponent"));
	}
}
