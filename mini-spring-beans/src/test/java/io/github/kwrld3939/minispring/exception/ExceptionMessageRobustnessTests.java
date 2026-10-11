package io.github.kwrld3939.minispring.exception;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

/**
 * 消息拼装在"上下文缺失"时也必须健壮。
 *
 * <p>这些构造器都允许 {@code null}（没有注入点、没有 cause、没有链路明细），因为异常经常是在
 * 信息不全时构造的。**构造异常本身再抛 NPE，会把真正的失败信息彻底吞掉**——这是最坏的结果，
 * 所以这些分支值得逐个固定。
 */
class ExceptionMessageRobustnessTests {

	@Test
	void beanCreationWithoutOptionalDetailsStillProducesMessage() {
		BeanCreationException ex = new BeanCreationException("bean", null, null, null, null);

		assertEquals("bean", ex.getBeanName());
		assertNull(ex.getBeanClass());
		assertNull(ex.getInjectionPoint());
		assertNull(ex.getCause());
		assertTrue(ex.getMessage().contains("创建 Bean 'bean'"), ex.getMessage());
		assertFalse(ex.getMessage().contains("注入点"), ex.getMessage());
	}

	@Test
	void beanDefinitionWithoutBeanNameOmitsTheNamePart() {
		BeanDefinitionException ex = new BeanDefinitionException(null, "作用域非法");

		assertNull(ex.getBeanName());
		assertTrue(ex.getMessage().contains("Bean 定义不合法"), ex.getMessage());
		assertTrue(ex.getMessage().contains("作用域非法"), ex.getMessage());
		assertFalse(ex.getMessage().contains("beanName"), ex.getMessage());
	}

	@Test
	void noSuchBeanWithoutAvailableNamesOrHint() {
		NoSuchBeanException ex = new NoSuchBeanException("x", null, null);

		assertTrue(ex.getMessage().contains("容器中没有注册任何 Bean"), ex.getMessage());
		assertFalse(ex.getMessage().contains("建议"), ex.getMessage());
	}

	@Test
	void noUniqueBeanWithSingleCandidateStillSuggestsIt() {
		NoUniqueBeanException ex = new NoUniqueBeanException(String.class,
				Map.<String, Class<?>>of("only", String.class), null);

		assertEquals(1, ex.getCandidates().size());
		assertTrue(ex.getMessage().contains("only"), ex.getMessage());
		assertTrue(ex.getMessage().contains("@MiniQualifier(\"only\")"), ex.getMessage());
	}

	@Test
	void circularDependencyWithoutChainDetailOmitsThatSection() {
		CircularDependencyException ex = new CircularDependencyException("a", String.class, "字段 X#b", "a -> b -> a");

		assertEquals("a -> b -> a", ex.getDependencyChain());
		assertFalse(ex.getMessage().contains("链路细节"), ex.getMessage());
		assertTrue(ex.getMessage().contains("支持范围"), ex.getMessage());
	}

	@Test
	void lifecycleExceptionWithoutCausesStillReportsBeanNames() {
		LifecycleException ex = new LifecycleException("容器销毁", List.of("a"), null);

		assertEquals(List.of("a"), ex.getFailedBeanNames());
		assertTrue(ex.getMessage().contains("(无原因)"), ex.getMessage());
		assertEquals(0, ex.getSuppressed().length);
	}
}
