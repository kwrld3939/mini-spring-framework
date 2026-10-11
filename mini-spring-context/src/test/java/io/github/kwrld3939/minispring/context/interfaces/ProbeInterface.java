package io.github.kwrld3939.minispring.context.interfaces;

import io.github.kwrld3939.minispring.annotation.MiniComponent;

/**
 * 故意给接口标注 {@code @MiniComponent}：注册阶段就必须给出可执行的错误，
 * 而不是等到实例化时抛出难以理解的失败。
 *
 * <p>独立成包，避免被其他扫描测试的根包递归扫到。
 */
@MiniComponent
public interface ProbeInterface {

	String ping();
}
