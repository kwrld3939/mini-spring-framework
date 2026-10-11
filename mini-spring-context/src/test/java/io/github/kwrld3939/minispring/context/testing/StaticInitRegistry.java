package io.github.kwrld3939.minispring.context.testing;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 记录"静态初始化块确实执行过"的类名。
 *
 * <p>用途：证明扫描阶段没有初始化用户的类。探针类在自己的静态块里调用 {@link #record(String)}，
 * 而读取方只访问本类，因此读取这个记录本身不会触发探针类的初始化。
 */
public final class StaticInitRegistry {

	private static final List<String> INITIALIZED = Collections.synchronizedList(new ArrayList<>());

	private StaticInitRegistry() {
	}

	public static void record(String className) {
		INITIALIZED.add(className);
	}

	public static List<String> initialized() {
		return new ArrayList<>(INITIALIZED);
	}

	public static boolean contains(String className) {
		return INITIALIZED.contains(className);
	}

	public static void clear() {
		INITIALIZED.clear();
	}
}
