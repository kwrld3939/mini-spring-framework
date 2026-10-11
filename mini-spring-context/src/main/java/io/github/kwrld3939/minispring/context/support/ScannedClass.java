package io.github.kwrld3939.minispring.context.support;

/**
 * 扫描阶段得到的类元数据，由 ASM 直接从字节码读出，**不需要加载类**。
 *
 * <p>这样做的收益有三个（见 {@code docs/adr/0003-classpath-scanning.md}）：
 * 扫描不会触发用户类的静态初始化块；不会因为某个类缺少可选依赖而中断扫描；
 * 也能判断"是不是组件"而不必把整个类路径加载进来。
 */
public final class ScannedClass {

	private final String className;
	private final String componentBeanName;
	private final String scope;
	private final boolean interfaceType;
	private final boolean abstractType;

	ScannedClass(String className, String componentBeanName, String scope, boolean interfaceType,
			boolean abstractType) {
		this.className = className;
		this.componentBeanName = componentBeanName;
		this.scope = scope;
		this.interfaceType = interfaceType;
		this.abstractType = abstractType;
	}

	public String getClassName() {
		return className;
	}

	/**
	 * {@code @MiniComponent} 上显式指定的 bean 名称。
	 *
	 * @return 未标注 {@code @MiniComponent} 时为 {@code null}；标注但未指定名称时为空串
	 */
	public String getComponentBeanName() {
		return componentBeanName;
	}

	/** {@code @MiniScope} 指定的作用域；未标注时为 {@code null}。 */
	public String getScope() {
		return scope;
	}

	public boolean isComponent() {
		return componentBeanName != null;
	}

	public boolean isInterface() {
		return interfaceType;
	}

	public boolean isAbstract() {
		return abstractType;
	}

	@Override
	public String toString() {
		return "ScannedClass[" + className + (isComponent() ? ", component" : "") + "]";
	}
}
