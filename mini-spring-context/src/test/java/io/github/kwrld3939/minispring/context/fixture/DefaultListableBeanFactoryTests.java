package io.github.kwrld3939.minispring.context.fixture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;

import io.github.kwrld3939.minispring.beans.BeanDefinition;
import io.github.kwrld3939.minispring.context.support.DefaultListableBeanFactory;
import io.github.kwrld3939.minispring.env.StandardEnvironment;
import io.github.kwrld3939.minispring.exception.BeanCreationException;
import io.github.kwrld3939.minispring.exception.BeanDefinitionException;
import io.github.kwrld3939.minispring.exception.BeansException;
import io.github.kwrld3939.minispring.exception.CircularDependencyException;
import io.github.kwrld3939.minispring.exception.ConfigException;
import io.github.kwrld3939.minispring.exception.LifecycleException;
import io.github.kwrld3939.minispring.exception.NoSuchBeanException;
import io.github.kwrld3939.minispring.exception.NoUniqueBeanException;

/**
 * 容器引擎的单元测试：只用手工注册的定义，不依赖组件扫描。
 *
 * <p>这里固化的是各 ADR 的失败语义——特别是"失败必须可诊断"：不仅断言异常类型，
 * 还断言消息里确实带着 bean 名、候选列表、依赖链与建议。
 */
class DefaultListableBeanFactoryTests {

	private DefaultListableBeanFactory factory;

	@BeforeEach
	void setUp() {
		LifecycleRecorder.clear();
		factory = new DefaultListableBeanFactory(
				StandardEnvironment.loadFromClasspath(getClass().getClassLoader(), "application.properties"));
	}

	@Test
	void failsFastOnConstructorInjectionCycleWithFullChain() {
		factory.registerBeanDefinition(new BeanDefinition("constructorCycleA", ConstructorCycleA.class));
		factory.registerBeanDefinition(new BeanDefinition("constructorCycleB", ConstructorCycleB.class));

		CircularDependencyException ex = assertThrows(CircularDependencyException.class,
				() -> factory.getBean("constructorCycleA"));

		assertEquals("constructorCycleA -> constructorCycleB -> constructorCycleA", ex.getDependencyChain());
		assertTrue(ex.getMessage().contains("链路细节"), ex.getMessage());
		assertTrue(ex.getMessage().contains("构造器参数"), ex.getMessage());
		assertTrue(ex.getMessage().contains("建议"), ex.getMessage());
	}

	@Test
	void failsFastOnPrototypeCycle() {
		factory.registerBeanDefinition(new BeanDefinition("prototypeCycleA", PrototypeCycleA.class,
				BeanDefinition.SCOPE_PROTOTYPE));
		factory.registerBeanDefinition(new BeanDefinition("prototypeCycleB", PrototypeCycleB.class,
				BeanDefinition.SCOPE_PROTOTYPE));

		assertThrows(CircularDependencyException.class, () -> factory.getBean("prototypeCycleA"));
	}

	@Test
	void rejectsDuplicateBeanNames() {
		factory.registerBeanDefinition(new BeanDefinition("duplicated", SimpleComponent.class));

		BeanDefinitionException ex = assertThrows(BeanDefinitionException.class,
				() -> factory.registerBeanDefinition(new BeanDefinition("duplicated", PrototypeComponent.class)));

		assertTrue(ex.getMessage().contains("duplicated"), ex.getMessage());
		assertTrue(ex.getMessage().contains(SimpleComponent.class.getName()), ex.getMessage());
		assertTrue(ex.getMessage().contains(PrototypeComponent.class.getName()), ex.getMessage());
	}

	@Test
	void rejectsRegistrationAfterFreeze() {
		factory.registerBeanDefinition(new BeanDefinition("simpleComponent", SimpleComponent.class));
		factory.freeze();

		assertThrows(BeansException.class,
				() -> factory.registerBeanDefinition(new BeanDefinition("late", SimpleComponent.class)));
		assertThrows(BeansException.class, () -> factory.addBeanPostProcessor(new NoOpBeanPostProcessor()));
	}

	@Test
	void reportsAllCandidatesWhenTypeIsAmbiguous() {
		factory.registerBeanDefinition(new BeanDefinition("manualServiceA", ManualServiceA.class));
		factory.registerBeanDefinition(new BeanDefinition("manualServiceB", ManualServiceB.class));

		NoUniqueBeanException ex = assertThrows(NoUniqueBeanException.class,
				() -> factory.getBean(ManualService.class));

		assertEquals(2, ex.getCandidates().size());
		assertTrue(ex.getMessage().contains("manualServiceA"), ex.getMessage());
		assertTrue(ex.getMessage().contains("manualServiceB"), ex.getMessage());
		assertTrue(ex.getMessage().contains("@MiniQualifier"), ex.getMessage());
	}

	@Test
	void reportsAvailableNamesWhenBeanIsMissing() {
		factory.registerBeanDefinition(new BeanDefinition("simpleComponent", SimpleComponent.class));

		NoSuchBeanException ex = assertThrows(NoSuchBeanException.class, () -> factory.getBean("doesNotExist"));

		assertTrue(ex.getMessage().contains("simpleComponent"), ex.getMessage());
	}

	@Test
	void reportsMissingPropertyAsConfigException() {
		factory.registerBeanDefinition(new BeanDefinition("missingValueComponent", MissingValueComponent.class));

		ConfigException ex = assertThrows(ConfigException.class, () -> factory.getBean("missingValueComponent"));

		assertTrue(ex.getMessage().contains("fixture.missing"), ex.getMessage());
	}

	@Test
	void reportsConversionFailureAsConfigException() {
		factory.registerBeanDefinition(new BeanDefinition("badValueComponent", BadValueComponent.class));

		ConfigException ex = assertThrows(ConfigException.class, () -> factory.getBean("badValueComponent"));

		assertTrue(ex.getMessage().contains("fixture.not-a-number"), ex.getMessage());
		assertTrue(ex.getMessage().contains("abc"), ex.getMessage());
	}

	@Test
	void rejectsBeanPostProcessorThatDependsOnRegularBean() {
		factory.registerBeanDefinition(new BeanDefinition("dependentBeanPostProcessor", DependentBeanPostProcessor.class));
		factory.registerBeanDefinition(new BeanDefinition("simpleComponent", SimpleComponent.class));

		BeanCreationException ex = assertThrows(BeanCreationException.class,
				() -> factory.registerBeanPostProcessors());

		assertTrue(ex.getMessage().contains("BeanPostProcessor 依赖了普通 Bean"), ex.getMessage());
	}

	@Test
	void recordsDiagnosticAndLogsWarningWhenEarlyReferenceIsReplacedByProxy() {
		Logger factoryLogger = (Logger) LoggerFactory.getLogger(DefaultListableBeanFactory.class);
		ListAppender<ILoggingEvent> appender = new ListAppender<>();
		appender.start();
		factoryLogger.addAppender(appender);
		try {
			factory.addBeanPostProcessor(new ReplacingBeanPostProcessor());
			factory.registerBeanDefinition(new BeanDefinition("cycleTarget", CycleTarget.class));
			factory.registerBeanDefinition(new BeanDefinition("cycleConsumer", CycleConsumer.class));

			CycleTarget replacement = (CycleTarget) factory.getBean("cycleTarget");

			assertEquals("replacement", replacement.value());
			assertTrue(factory.getEarlyReferencesExposed().contains("cycleTarget"));
			assertEquals(1, factory.getDiagnostics().size(), factory.getDiagnostics().toString());
			assertTrue(factory.getDiagnostics().get(0).contains("cycleTarget"), factory.getDiagnostics().toString());
			assertEquals("original", factory.getBean(CycleConsumer.class).target().value(),
					"环上的反向引用按 ADR-0002 的例外条款持有的是未代理对象");
			assertTrue(appender.list.stream()
					.anyMatch(event -> event.getLevel() == Level.WARN
							&& event.getFormattedMessage().contains("cycleTarget")),
					"该例外必须同时以 WARN 输出，使用者才可能发现它；实际日志：" + appender.list);
		} finally {
			factoryLogger.detachAppender(appender);
		}
	}

	@Test
	void keepsDestroyingOtherBeansWhenOneCallbackFails() {
		factory.registerBeanDefinition(new BeanDefinition("failingDestroyComponent", FailingDestroyComponent.class));
		factory.registerBeanDefinition(new BeanDefinition("recordingDestroyComponent", RecordingDestroyComponent.class));
		factory.getBean("failingDestroyComponent");
		factory.getBean("recordingDestroyComponent");
		factory.freeze();

		LifecycleException ex = assertThrows(LifecycleException.class, () -> factory.destroySingletons());

		assertEquals(List.of("failingDestroyComponent"), ex.getFailedBeanNames());
		assertTrue(LifecycleRecorder.events().contains("recording"), "单个销毁失败不应中断其余销毁");
		assertTrue(ex.getSuppressed().length >= 1, "失败原因必须保留为 suppressed 异常");
	}

	@Test
	void invokesPreDestroyOnBeansThatWereProxied() {
		factory.addBeanPostProcessor(new ProxyWrappingBeanPostProcessor());
		factory.registerBeanDefinition(new BeanDefinition("recordingService", RecordingServiceImpl.class));

		Object proxy = factory.getBean("recordingService");
		assertTrue(java.lang.reflect.Proxy.isProxyClass(proxy.getClass()));

		factory.freeze();
		factory.destroySingletons();

		assertTrue(LifecycleRecorder.events().contains("proxied-destroy"),
				"被代理的 Bean 也必须触发 @MiniPreDestroy：回调要作用在目标对象上，而不是按代理类反射（代理类上没有目标方法的注解，JDK 代理甚至没有该方法）");
	}
}
