package io.github.kwrld3939.minispring.web.duplicate;

import io.github.kwrld3939.minispring.annotation.MiniComponent;
import io.github.kwrld3939.minispring.web.annotation.MiniRequestMapping;

/**
 * 两个组件映射到同一个路径：容器必须在建立映射表时失败，而不是悄悄覆盖掉其中一个。
 *
 * <p>独立成包（不是 fixture 的子包），否则扫描 fixture 的测试会连带扫到这里而失败。
 */
@MiniComponent
class DuplicateMappingFirst {

	@MiniRequestMapping(path = "/duplicate")
	public String first() {
		return "first";
	}
}

@MiniComponent
class DuplicateMappingSecond {

	@MiniRequestMapping(path = "/duplicate")
	public String second() {
		return "second";
	}
}
