package io.github.kwrld3939.minispring.context;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.github.kwrld3939.minispring.beans.BeanFactory;
import io.github.kwrld3939.minispring.context.support.ClassPathBeanDefinitionScanner;
import io.github.kwrld3939.minispring.context.support.DefaultListableBeanFactory;
import io.github.kwrld3939.minispring.env.Environment;
import io.github.kwrld3939.minispring.env.StandardEnvironment;
import io.github.kwrld3939.minispring.exception.ConfigException;

/**
 * Mini Spring 的应用上下文：把"配置 + 扫描 + 容器"装配起来，并对外暴露只读的 {@link BeanFactory}。
 *
 * <p>它是**装配层**，不再是容器本身。扫描、定义注册、依赖注入、生命周期与销毁分别在
 * {@link ClassPathBeanDefinitionScanner} 与 {@link DefaultListableBeanFactory} 中，
 * 这样容器可以被单独测试与替换，而不是绑在一个万能类上。
 *
 * <h2>生命周期</h2>
 * <pre>
 * 构造 MiniApplicationContext(rootPackage)
 *   → 加载 application.properties 到 StandardEnvironment
 *   → 扫描 rootPackage（ASM 读字节码，不加载用户的类），注册 @MiniComponent 为 BeanDefinition
 *   → 注册 BeanPostProcessor（先创建它们，之后的 Bean 才能被增强）
 *   → 实例化全部单例（注入、初始化、生成 AOP 代理）
 *   → 冻结：定义表与单例池不再变化，运行期读路径无锁
 * close()
 *   → 按依赖逆序调用 @MiniPreDestroy
 * </pre>
 */
public class MiniApplicationContext implements BeanFactory, AutoCloseable {

	private static final Logger LOGGER = LoggerFactory.getLogger(MiniApplicationContext.class);

	/** 默认配置文件位置（相对类路径）。 */
	public static final String DEFAULT_CONFIG_LOCATION = "application.properties";

	private final String rootPackage;
	private final ClassLoader classLoader;
	private final StandardEnvironment environment;
	private final DefaultListableBeanFactory beanFactory;
	private boolean closed;

	public MiniApplicationContext(String rootPackage) {
		this(rootPackage, null);
	}

	public MiniApplicationContext(String rootPackage, ClassLoader classLoader) {
		if (rootPackage == null || rootPackage.isBlank()) {
			throw new ConfigException("rootPackage 不能为空：请传入要扫描的根包名（例如 MainApp.class.getPackageName()）。");
		}
		this.rootPackage = rootPackage;
		this.classLoader = classLoader != null ? classLoader : defaultClassLoader();
		this.environment = StandardEnvironment.loadFromClasspath(this.classLoader, DEFAULT_CONFIG_LOCATION);
		this.beanFactory = new DefaultListableBeanFactory(this.environment, this.classLoader);
		refresh();
	}

	/**
	 * 扫描 → 注册定义 → 注册后置处理器 → 实例化单例 → 冻结。
	 *
	 * <p>顺序不可调换：后置处理器必须在普通 Bean 之前就位，否则先创建的 Bean 会被静默漏掉增强
	 * （{@link DefaultListableBeanFactory} 对"后置处理器依赖普通 Bean"的情况会直接报错而不是放行）。
	 */
	protected void refresh() {
		int registered = new ClassPathBeanDefinitionScanner(beanFactory, classLoader).scan(rootPackage);
		beanFactory.registerBeanPostProcessors();
		beanFactory.preInstantiateSingletons();
		beanFactory.freeze();
		LOGGER.info("Mini Spring 容器已启动: rootPackage={}, 本次注册 bean 定义 {} 个, 单例 {} 个",
				rootPackage, registered, beanFactory.getSingletonBeans().size());
	}

	// ------------------------------------------------------------------
	// BeanFactory 委托
	// ------------------------------------------------------------------

	@Override
	public Object getBean(String beanName) {
		return beanFactory.getBean(beanName);
	}

	@Override
	public <T> T getBean(Class<T> beanType) {
		return beanFactory.getBean(beanType);
	}

	@Override
	public boolean containsBean(String beanName) {
		return beanFactory.containsBean(beanName);
	}

	@Override
	public String[] getBeanDefinitionNames() {
		return beanFactory.getBeanDefinitionNames();
	}

	@Override
	public <T> Map<String, T> getBeansOfType(Class<T> beanType) {
		return beanFactory.getBeansOfType(beanType);
	}

	// ------------------------------------------------------------------
	// 诊断
	// ------------------------------------------------------------------

	/**
	 * 单例 Bean 的只读快照（冻结后为空操作、无锁）。
	 *
	 * <p>这是**诊断用途**的 API（打印容器状态、遍历映射）；生产代码应按名称或类型获取 Bean。
	 */
	public Map<String, Object> getBeans() {
		return beanFactory.getSingletonBeans();
	}

	public String getRootPackage() {
		return rootPackage;
	}

	public int getBeanDefinitionCount() {
		return beanFactory.getBeanDefinitionCount();
	}

	public ClassLoader getClassLoader() {
		return classLoader;
	}

	public Environment getEnvironment() {
		return environment;
	}

	/** 暴露底层容器，便于需要注册自定义定义或读取诊断信息的场景（相关写操作只能在冻结前调用）。 */
	public DefaultListableBeanFactory getBeanFactory() {
		return beanFactory;
	}

	@Override
	public void close() {
		if (closed) {
			return;
		}
		closed = true;
		beanFactory.destroySingletons();
		LOGGER.info("Mini Spring 容器已关闭: rootPackage={}", rootPackage);
	}

	private static ClassLoader defaultClassLoader() {
		ClassLoader contextClassLoader = Thread.currentThread().getContextClassLoader();
		return contextClassLoader != null ? contextClassLoader : MiniApplicationContext.class.getClassLoader();
	}
}
