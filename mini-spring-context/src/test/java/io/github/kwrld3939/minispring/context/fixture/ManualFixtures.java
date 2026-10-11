package io.github.kwrld3939.minispring.context.fixture;

import io.github.kwrld3939.minispring.annotation.MiniAutowired;
import io.github.kwrld3939.minispring.annotation.MiniPostConstruct;
import io.github.kwrld3939.minispring.annotation.MiniPreDestroy;
import io.github.kwrld3939.minispring.annotation.MiniQualifier;
import io.github.kwrld3939.minispring.annotation.MiniValue;
import io.github.kwrld3939.minispring.beans.BeanPostProcessor;

/**
 * 只用于"手工注册 BeanDefinition"的测试组件：刻意**不标注** {@code @MiniComponent}，
 * 因此不会被容器扫描注册，也就不会影响其他集成测试的启动。
 */
final class ManualFixtures {

	private ManualFixtures() {
	}
}

// ---------- 构造器注入环：必须快速失败 ----------

final class ConstructorCycleA {

	ConstructorCycleA(ConstructorCycleB dependency) {
	}
}

final class ConstructorCycleB {

	ConstructorCycleB(ConstructorCycleA dependency) {
	}
}

// ---------- prototype 参与的环：必须快速失败 ----------

final class PrototypeCycleA {

	PrototypeCycleA(PrototypeCycleB dependency) {
	}
}

final class PrototypeCycleB {

	PrototypeCycleB(PrototypeCycleA dependency) {
	}
}

// ---------- 多候选：按类型注入时必须报出全部候选 ----------

interface ManualService {
}

final class ManualServiceA implements ManualService {
}

final class ManualServiceB implements ManualService {
}

// ---------- 配置缺失与类型转换失败 ----------

final class MissingValueComponent {

	@MiniValue("fixture.missing")
	private String value;

	String value() {
		return value;
	}
}

final class BadValueComponent {

	@MiniValue("fixture.not-a-number")
	private int value;
}

// ---------- 销毁：一个失败不能影响另一个 ----------

final class RecordingDestroyComponent {

	@MiniPreDestroy
	void destroy() {
		LifecycleRecorder.record("recording");
	}
}

final class FailingDestroyComponent {

	@MiniPreDestroy
	void destroy() {
		throw new IllegalStateException("销毁回调故意失败");
	}
}

// ---------- BeanPostProcessor ----------

final class NoOpBeanPostProcessor implements BeanPostProcessor {

	@Override
	public Object postProcessBeforeInitialization(String beanName, Object bean) {
		return bean;
	}

	@Override
	public Object postProcessAfterInitialization(String beanName, Object bean) {
		return bean;
	}
}

/** 依赖了普通 Bean 的后置处理器：容器必须在启动期报错，而不是静默跳过增强。 */
final class DependentBeanPostProcessor implements BeanPostProcessor {

	private final SimpleComponent dependency;

	DependentBeanPostProcessor(SimpleComponent dependency) {
		this.dependency = dependency;
	}

	SimpleComponent dependency() {
		return dependency;
	}

	@Override
	public Object postProcessBeforeInitialization(String beanName, Object bean) {
		return bean;
	}

	@Override
	public Object postProcessAfterInitialization(String beanName, Object bean) {
		return bean;
	}
}

// ---------- 环 + 代理：ADR-0002 的例外条款必须被记录 ----------

class CycleTarget {

	@MiniAutowired
	private CycleConsumer consumer;

	String value() {
		return "original";
	}

	CycleConsumer consumer() {
		return consumer;
	}
}

final class CycleReplacement extends CycleTarget {

	@Override
	String value() {
		return "replacement";
	}
}

final class CycleConsumer {

	@MiniAutowired
	private CycleTarget target;

	CycleTarget target() {
		return target;
	}
}

/** 把 cycleTarget 换成子类实例，模拟"环上 Bean 在初始化后变成代理对象"。 */
final class ReplacingBeanPostProcessor implements BeanPostProcessor {

	@Override
	public Object postProcessBeforeInitialization(String beanName, Object bean) {
		return bean;
	}

	@Override
	public Object postProcessAfterInitialization(String beanName, Object bean) {
		if ("cycleTarget".equals(beanName)) {
			return new CycleReplacement();
		}
		return bean;
	}
}

// ---------- 被代理 Bean 的销毁回调 ----------

interface RecordingService {

	String ping();
}

final class RecordingServiceImpl implements RecordingService {

	@MiniPreDestroy
	void destroy() {
		LifecycleRecorder.record("proxied-destroy");
	}

	@Override
	public String ping() {
		return "pong";
	}
}

/** 用 JDK 动态代理包住目标对象（模拟 AOP 代理）：销毁仍必须触发目标上的 {@code @MiniPreDestroy}。 */
final class ProxyWrappingBeanPostProcessor implements BeanPostProcessor {

	@Override
	public Object postProcessBeforeInitialization(String beanName, Object bean) {
		return bean;
	}

	@Override
	public Object postProcessAfterInitialization(String beanName, Object bean) {
		if (!"recordingService".equals(beanName)) {
			return bean;
		}
		return java.lang.reflect.Proxy.newProxyInstance(bean.getClass().getClassLoader(),
				new Class<?>[] { RecordingService.class },
				(proxy, method, args) -> method.invoke(bean, args));
	}
}

// ---------- 失败语义：每一条都必须是可诊断的启动期失败 ----------

/** 限定符指向不存在的 Bean。 */
final class QualifierMissesComponent {

	@MiniAutowired
	@MiniQualifier("doesNotExist")
	private ManualService service;
}

/** 限定符指向的 Bean 类型不匹配。 */
final class QualifierWrongTypeComponent {

	@MiniAutowired
	@MiniQualifier("simpleComponent")
	private ManualService service;
}

final class ConstructorThrowsComponent {

	ConstructorThrowsComponent() {
		throw new IllegalStateException("构造器故意失败");
	}
}

final class TwoParameterSetterComponent {

	@MiniAutowired
	void configure(SimpleComponent first, SimpleComponent second) {
	}
}

final class LifecycleWithParameterComponent {

	@MiniPostConstruct
	void init(String unexpected) {
	}
}

final class BlankValueKeyComponent {

	@MiniValue("   ")
	private String value;
}

final class NullReturningBeanPostProcessor implements BeanPostProcessor {

	@Override
	public Object postProcessBeforeInitialization(String beanName, Object bean) {
		return null;
	}

	@Override
	public Object postProcessAfterInitialization(String beanName, Object bean) {
		return bean;
	}
}

final class ThrowingBeanPostProcessor implements BeanPostProcessor {

	@Override
	public Object postProcessBeforeInitialization(String beanName, Object bean) {
		return bean;
	}

	@Override
	public Object postProcessAfterInitialization(String beanName, Object bean) {
		throw new IllegalStateException("后置处理故意失败");
	}
}

// ---------- 构造器选择（ADR-0012）：容器不猜，多构造器必须显式指定 ----------

final class TwoConstructorsOneAnnotated {

	private final SimpleComponent viaConstructor;

	TwoConstructorsOneAnnotated() {
		this.viaConstructor = null;
	}

	@MiniAutowired
	TwoConstructorsOneAnnotated(SimpleComponent simpleComponent) {
		this.viaConstructor = simpleComponent;
	}

	SimpleComponent viaConstructor() {
		return viaConstructor;
	}
}

final class TwoConstructorsNoneAnnotated {

	TwoConstructorsNoneAnnotated() {
	}

	TwoConstructorsNoneAnnotated(SimpleComponent simpleComponent) {
	}
}

final class TwoConstructorsBothAnnotated {

	@MiniAutowired
	TwoConstructorsBothAnnotated() {
	}

	@MiniAutowired
	TwoConstructorsBothAnnotated(SimpleComponent simpleComponent) {
	}
}

final class SingleAnnotatedConstructor {

	private final SimpleComponent simpleComponent;

	@MiniAutowired
	SingleAnnotatedConstructor(SimpleComponent simpleComponent) {
		this.simpleComponent = simpleComponent;
	}

	SimpleComponent simple() {
		return simpleComponent;
	}
}
