package io.github.kwrld3939.minispring.context.fixture;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import io.github.kwrld3939.minispring.annotation.MiniAutowired;
import io.github.kwrld3939.minispring.annotation.MiniComponent;
import io.github.kwrld3939.minispring.annotation.MiniPostConstruct;
import io.github.kwrld3939.minispring.annotation.MiniPreDestroy;
import io.github.kwrld3939.minispring.annotation.MiniScope;
import io.github.kwrld3939.minispring.annotation.MiniValue;

/**
 * 会被容器扫描到的测试组件（均为包私有顶层类，因此测试可以直接断言其内部状态）。
 *
 * <p>文件内允许出现多个非 public 类型，javac 会为每个顶层类生成独立的 {@code .class}，
 * 扫描器按 class 文件逐个发现，效果与"一个类一个文件"相同。
 *
 * <p>不要在本文件中放"启动即失败"的组件（例如构造器环、缺失配置），否则所有上下文测试都会在
 * refresh 阶段挂掉——那些组件放在 {@code ManualFixtures}，通过手工注册定义来测试。
 */
final class LifecycleRecorder {

	private static final List<String> EVENTS = Collections.synchronizedList(new ArrayList<>());

	private LifecycleRecorder() {
	}

	static void record(String event) {
		EVENTS.add(event);
	}

	static List<String> events() {
		return new ArrayList<>(EVENTS);
	}

	static void clear() {
		EVENTS.clear();
	}
}

@MiniComponent
class SimpleComponent {
}

@MiniComponent
class ConsumerComponent {

	private final SimpleComponent simpleComponent;

	ConsumerComponent(SimpleComponent simpleComponent) {
		this.simpleComponent = simpleComponent;
	}

	SimpleComponent simple() {
		return simpleComponent;
	}
}

@MiniComponent
class FieldCycleA {

	@MiniAutowired
	private FieldCycleB other;

	FieldCycleB other() {
		return other;
	}
}

@MiniComponent
class FieldCycleB {

	@MiniAutowired
	private FieldCycleA other;

	FieldCycleA other() {
		return other;
	}
}

@MiniComponent
class SelfInjectingComponent {

	@MiniAutowired
	private SelfInjectingComponent self;

	SelfInjectingComponent self() {
		return self;
	}
}

@MiniComponent
class SingletonComponent {
}

@MiniComponent
@MiniScope("prototype")
class PrototypeComponent {
}

@MiniComponent
class ConfiguredComponent {

	@MiniValue("fixture.name")
	private String name;

	String name() {
		return name;
	}
}

@MiniComponent
class DestroyOrderA {

	private final DestroyOrderB dependency;

	DestroyOrderA(DestroyOrderB dependency) {
		this.dependency = dependency;
	}

	DestroyOrderB dependency() {
		return dependency;
	}

	@MiniPreDestroy
	void destroy() {
		LifecycleRecorder.record("A");
	}
}

@MiniComponent
class DestroyOrderB {

	@MiniPostConstruct
	void init() {
		LifecycleRecorder.record("B-init");
	}

	@MiniPreDestroy
	void destroy() {
		LifecycleRecorder.record("B");
	}
}
