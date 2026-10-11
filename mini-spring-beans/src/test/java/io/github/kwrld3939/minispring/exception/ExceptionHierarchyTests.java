package io.github.kwrld3939.minispring.exception;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

/**
 * 异常层级的契约：类型可被程序化判断、消息足以定位问题、原始原因不会被丢弃。
 *
 * <p>这些断言看起来琐碎，但正是"失败可诊断"这条目标的实现细节——见
 * {@code docs/adr/0005-exception-hierarchy-and-diagnostics.md}。
 */
class ExceptionHierarchyTests {

	@Test
	void hierarchyIsCatchableByBaseTypes() {
		assertTrue(MiniSpringException.class.isAssignableFrom(BeansException.class));
		assertTrue(BeansException.class.isAssignableFrom(BeanDefinitionException.class));
		assertTrue(BeansException.class.isAssignableFrom(NoSuchBeanException.class));
		assertTrue(BeansException.class.isAssignableFrom(NoUniqueBeanException.class));
		assertTrue(BeansException.class.isAssignableFrom(BeanCreationException.class));
		assertTrue(BeanCreationException.class.isAssignableFrom(BeanCurrentlyInCreationException.class));
		assertTrue(BeanCurrentlyInCreationException.class.isAssignableFrom(CircularDependencyException.class));
		assertTrue(BeansException.class.isAssignableFrom(LifecycleException.class));
		assertTrue(MiniSpringException.class.isAssignableFrom(ConfigException.class));

		assertTrue(RuntimeException.class.isAssignableFrom(MiniSpringException.class), "全部是 unchecked，调用方无需声明");
		assertFalse(BeansException.class.isAssignableFrom(ConfigException.class), "配置失败不是 Bean 失败，二者分开");
	}

	@Test
	void noSuchBeanListsAvailableNamesAndHint() {
		NoSuchBeanException ex = new NoSuchBeanException("payService",
				List.of("aliPayService", "wechatPayService"), "检查名称拼写");

		assertEquals("payService", ex.getRequested());
		assertTrue(ex.getMessage().contains("找不到 Bean：payService"), ex.getMessage());
		assertTrue(ex.getMessage().contains("aliPayService"), ex.getMessage());
		assertTrue(ex.getMessage().contains("wechatPayService"), ex.getMessage());
		assertTrue(ex.getMessage().contains("检查名称拼写"), ex.getMessage());
	}

	@Test
	void noSuchBeanExplainsEmptyContainer() {
		NoSuchBeanException ex = new NoSuchBeanException("x", List.of(), null);

		assertTrue(ex.getMessage().contains("容器中没有注册任何 Bean"), ex.getMessage());
	}

	@Test
	void noUniqueBeanListsEveryCandidate() {
		NoUniqueBeanException ex = new NoUniqueBeanException(CharSequence.class,
				Map.<String, Class<?>>of("a", String.class, "b", StringBuilder.class), "注入点 字段 X#service");

		assertSame(CharSequence.class, ex.getBeanType());
		assertEquals(2, ex.getCandidates().size());
		assertTrue(ex.getMessage().contains("a (java.lang.String)"), ex.getMessage());
		assertTrue(ex.getMessage().contains("b (java.lang.StringBuilder)"), ex.getMessage());
		assertTrue(ex.getMessage().contains("注入点 字段 X#service"), ex.getMessage());
		assertTrue(ex.getMessage().contains("@MiniQualifier"), ex.getMessage());
	}

	@Test
	void beanCreationKeepsCauseAndInjectionPoint() {
		IllegalStateException cause = new IllegalStateException("根因");
		BeanCreationException ex = new BeanCreationException("bean", String.class, "构造器参数 0",
				"构造器依赖无法解析", cause);

		assertEquals("bean", ex.getBeanName());
		assertSame(String.class, ex.getBeanClass());
		assertEquals("构造器参数 0", ex.getInjectionPoint());
		assertSame(cause, ex.getCause(), "原始异常不允许被丢弃");
		assertTrue(ex.getMessage().contains("创建 Bean 'bean'"), ex.getMessage());
		assertTrue(ex.getMessage().contains("构造器参数 0"), ex.getMessage());
		assertTrue(ex.getMessage().contains("根因"), ex.getMessage());
	}

	@Test
	void circularDependencyCarriesChainAndGuidance() {
		CircularDependencyException ex = new CircularDependencyException("a", String.class, "字段 X#b",
				"a -> b -> a", "\n    b 由 字段 X#b 触发");

		assertEquals("a -> b -> a", ex.getDependencyChain());
		assertInstanceOf(BeanCurrentlyInCreationException.class, ex);
		assertTrue(ex.getMessage().contains("检测到循环依赖：a -> b -> a"), ex.getMessage());
		assertTrue(ex.getMessage().contains("链路细节"), ex.getMessage());
		assertTrue(ex.getMessage().contains("支持范围"), ex.getMessage());
		assertTrue(ex.getMessage().contains("建议"), ex.getMessage());
	}

	@Test
	void lifecycleExceptionAggregatesFailuresAsSuppressed() {
		IllegalStateException first = new IllegalStateException("第一个失败");
		LifecycleException ex = new LifecycleException("容器销毁", List.of("a", "b"),
				List.of(first, new IllegalStateException("第二个失败")));

		assertEquals(List.of("a", "b"), ex.getFailedBeanNames());
		assertTrue(ex.getMessage().contains("2 个 Bean 的回调失败"), ex.getMessage());
		assertEquals(2, ex.getSuppressed().length, "每个失败都要保留原始栈，而不是只留一句消息");
		assertSame(first, ex.getSuppressed()[0]);
	}

	@Test
	void configExceptionFactoriesExplainWhatToDo() {
		ConfigException missing = ConfigException.missingProperty("app.name", "字段 X#name");
		assertTrue(missing.getMessage().contains("找不到配置项 'app.name'"), missing.getMessage());
		assertTrue(missing.getMessage().contains("字段 X#name"), missing.getMessage());
		assertTrue(missing.getMessage().contains("建议"), missing.getMessage());

		IllegalArgumentException cause = new IllegalArgumentException("bad");
		ConfigException conversion = ConfigException.conversionFailed("app.version", "abc", int.class, cause);
		assertTrue(conversion.getMessage().contains("app.version"), conversion.getMessage());
		assertTrue(conversion.getMessage().contains("abc"), conversion.getMessage());
		assertTrue(conversion.getMessage().contains("int"), conversion.getMessage());
		assertSame(cause, conversion.getCause());
	}
}
