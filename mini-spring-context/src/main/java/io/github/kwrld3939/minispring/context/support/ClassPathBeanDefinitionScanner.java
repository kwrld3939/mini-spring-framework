package io.github.kwrld3939.minispring.context.support;

import io.github.kwrld3939.minispring.beans.BeanDefinition;
import io.github.kwrld3939.minispring.exception.BeanDefinitionException;
import io.github.kwrld3939.minispring.exception.BeansException;

/**
 * 注解驱动的 Bean 定义扫描器：把 {@link ClassPathScanner} 找到的 {@code @MiniComponent} 注册为 Bean 定义。
 *
 * <p>它只依赖窄接口 {@link BeanDefinitionRegistry}，不依赖容器实现，因此可以单独测试；
 * 名称重复等定义问题由注册表统一判定（单一事实来源），本类不重复实现这套规则。
 *
 * <h2>加载时机（ADR-0003）</h2>
 * <p>扫描阶段完全不加载类；只有确认是组件、需要登记为定义时才加载，而且用
 * {@link Class.forName(String, boolean, ClassLoader)} 的 {@code initialize=false} 形式——
 * 类被加载但不初始化，静态初始化块推迟到真正实例化时执行。
 *
 * <p>加载失败一律抛出可读异常：这里**不会**像早期实现那样静默跳过，因为"某个组件没被注册"
 * 是最难排查的一类问题。
 */
public class ClassPathBeanDefinitionScanner {

	private final BeanDefinitionRegistry registry;
	private final ClassLoader classLoader;
	private final ClassPathScanner classPathScanner;

	public ClassPathBeanDefinitionScanner(BeanDefinitionRegistry registry, ClassLoader classLoader) {
		this.registry = registry;
		this.classLoader = classLoader != null ? classLoader : ClassPathBeanDefinitionScanner.class.getClassLoader();
		this.classPathScanner = new ClassPathScanner(this.classLoader);
	}

	/**
	 * 扫描并注册根包下的组件。
	 *
	 * @return 本次注册的 Bean 定义数量
	 */
	public int scan(String... basePackages) {
		int registered = 0;
		for (String basePackage : basePackages) {
			for (ScannedClass scannedClass : classPathScanner.scan(basePackage)) {
				if (!scannedClass.isComponent()) {
					continue;
				}
				register(scannedClass);
				registered++;
			}
		}
		return registered;
	}

	private void register(ScannedClass scannedClass) {
		if (scannedClass.isInterface()) {
			throw new BeanDefinitionException(scannedClass.getClassName(),
					"接口不能作为组件：它无法被实例化"
							+ "\n  建议: 把 @MiniComponent 标注在实现类上，接口本身不需要注解。");
		}
		Class<?> beanClass = loadWithoutInitialization(scannedClass.getClassName());
		String explicitName = scannedClass.getComponentBeanName();
		String beanName = explicitName == null || explicitName.isBlank() ? defaultBeanName(beanClass) : explicitName;
		String scope = scannedClass.getScope() == null ? BeanDefinition.SCOPE_SINGLETON : scannedClass.getScope();
		registry.registerBeanDefinition(new BeanDefinition(beanName, beanClass, scope));
	}

	private Class<?> loadWithoutInitialization(String className) {
		try {
			// initialize=false：类被加载但不执行静态初始化块，静态代码延迟到实例化时运行。
			return Class.forName(className, false, classLoader);
		} catch (ClassNotFoundException | LinkageError ex) {
			throw new BeansException("扫描到的组件类无法加载：" + className
					+ "\n  说明: 该类标注了 @MiniComponent，但类路径不完整（常见原因是它引用了未引入的可选依赖）。"
					+ "\n  建议: 补齐缺失依赖，或把该类移出被扫描的包。", ex);
		}
	}

	/**
	 * 默认 bean 名称：类名首字母小写。单字母类名整体小写（例如 {@code A} → {@code a}），
	 * 与 Spring 的 {@code AnnotationBeanNameGenerator} 行为一致。
	 */
	public static String defaultBeanName(Class<?> beanClass) {
		String simpleName = beanClass.getSimpleName();
		if (simpleName.length() == 1) {
			return simpleName.toLowerCase();
		}
		return Character.toLowerCase(simpleName.charAt(0)) + simpleName.substring(1);
	}
}
