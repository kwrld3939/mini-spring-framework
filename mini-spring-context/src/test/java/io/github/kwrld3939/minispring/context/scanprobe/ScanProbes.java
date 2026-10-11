package io.github.kwrld3939.minispring.context.scanprobe;

import io.github.kwrld3939.minispring.annotation.MiniComponent;
import io.github.kwrld3939.minispring.annotation.MiniScope;
import io.github.kwrld3939.minispring.context.testing.StaticInitRegistry;

/**
 * 扫描器测试用的探针组件。
 *
 * <p>本包**只被 {@code ClassPathScannerTests} 扫描**，不会被容器的集成测试扫到；
 * 配合"每个测试一个全新的 ClassLoader"，静态初始化块每次都真的会被执行，断言因此可靠。
 */
final class ScanProbes {

	private ScanProbes() {
	}
}

/** 不带注解：扫描阶段既不该加载它，更不该初始化它。 */
class StaticInitProbe {

	static {
		StaticInitRegistry.record("StaticInitProbe");
	}

	public String ping() {
		return "static-init-probe";
	}
}

/** 带 @MiniComponent：扫描/注册期间只加载不初始化，实例化时才执行静态块。 */
@MiniComponent
class ComponentStaticInitProbe {

	static {
		StaticInitRegistry.record("ComponentStaticInitProbe");
	}

	public String ping() {
		return "component-static-init-probe";
	}
}

/** 验证 ASM 能从字节码读出 @MiniComponent 的名称与 @MiniScope 的作用域。 */
@MiniComponent("renamedProbe")
@MiniScope("prototype")
class RenamedScopedProbe {
}
