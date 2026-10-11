package io.github.kwrld3939.minispring.context.support;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.github.kwrld3939.minispring.annotation.MiniAutowired;
import io.github.kwrld3939.minispring.annotation.MiniPostConstruct;
import io.github.kwrld3939.minispring.annotation.MiniPreDestroy;
import io.github.kwrld3939.minispring.annotation.MiniValue;
import io.github.kwrld3939.minispring.beans.BeanDefinition;
import io.github.kwrld3939.minispring.beans.BeanFactory;
import io.github.kwrld3939.minispring.beans.BeanPostProcessor;
import io.github.kwrld3939.minispring.env.Environment;
import io.github.kwrld3939.minispring.env.StandardEnvironment;
import io.github.kwrld3939.minispring.exception.BeanCreationException;
import io.github.kwrld3939.minispring.exception.BeanDefinitionException;
import io.github.kwrld3939.minispring.exception.BeansException;
import io.github.kwrld3939.minispring.exception.CircularDependencyException;
import io.github.kwrld3939.minispring.exception.ConfigException;
import io.github.kwrld3939.minispring.exception.LifecycleException;
import io.github.kwrld3939.minispring.exception.NoSuchBeanException;
import io.github.kwrld3939.minispring.exception.NoUniqueBeanException;
import io.github.kwrld3939.minispring.util.SimpleTypeConverter;

/**
 * 默认的 Bean 工厂：负责定义的注册、Bean 的创建、依赖注入、后置处理、生命周期与销毁。
 *
 * <h2>核心不变式（ADR-0001）</h2>
 * <p>{@link #getBean(String)} 是唯一漏斗：一次调用内完成"实例化 → 属性注入 → 后置处理 before →
 * {@code @MiniPostConstruct} → 后置处理 after（AOP 代理在此产生）→ 写入单例池"，并返回**终态对象**。
 * 依赖解析递归调用同一个漏斗，因此任何注入点拿到的都是终态对象（含代理）。
 *
 * <h2>循环依赖（ADR-0002）</h2>
 * <p>实例化后会立刻登记"早期引用"，因此字段注入环、setter 注入环与自注入可以解开；
 * 构造器注入参与的环、涉及 prototype 的环无法解开，直接抛
 * {@link CircularDependencyException} 并给出完整链路，而不会无限递归。
 * 例外：环上若需要 AOP 代理，反向引用持有的是未代理的早期引用（与 Spring 一致），
 * 该情况会被记录到 {@link #getDiagnostics()}。
 *
 * <h2>并发与冻结（ADR-0006）</h2>
 * <p>{@link #freeze()} 之后定义表与单例池不再变化，读路径无锁且安全发布；
 * 单例在冻结前已全部创建完毕，因此运行期不存在"首次 getBean 触发初始化"的竞争窗口。
 * prototype 的创建不触碰共享可变状态，可被多线程并发调用。
 *
 * <h2>构造器选择（ADR-0012）</h2>
 * <p>只有一个构造器就直接使用；存在多个构造器时必须恰好有一个标注 {@code @MiniAutowired}，
 * 否则启动失败并列出全部候选——容器不会替你猜该用哪一个，而猜错会把错误的依赖集合静默注入进去。
 *
 * <h2>已知简化</h2>
 * <ul>
 *   <li>{@link BeanPostProcessor} 的依赖只能是其他 BeanPostProcessor：普通 Bean 若在后置处理器
 *       注册完成前被创建，会跳过所有后置处理（包括 AOP），因此这里直接报错而不是静默放行。</li>
 * </ul>
 */
public class DefaultListableBeanFactory implements BeanFactory, BeanDefinitionRegistry {

	private static final Logger LOGGER = LoggerFactory.getLogger(DefaultListableBeanFactory.class);

	private final Environment environment;
	private final ClassLoader classLoader;

	private final Map<String, BeanDefinition> beanDefinitions = new LinkedHashMap<>();
	private final Map<String, Object> singletonObjects = new LinkedHashMap<>();
	/**
	 * 单例的**目标对象**（初始化后可能是代理，这里是代理背后被包装的原始实例）。
	 * 生命周期回调必须在目标对象上执行：JDK 代理只实现接口，代理类上既没有目标方法的注解，
	 * 也无法直接调用目标类里那些不在接口中的方法（例如 {@code @MiniPreDestroy} 回调）。
	 */
	private final Map<String, Object> singletonTargets = new LinkedHashMap<>();
	/** 早期引用：仅在"创建中"期间可见，用于解开字段/setter 环。 */
	private final Map<String, Object> earlySingletonObjects = new LinkedHashMap<>();
	/** 哪些 Bean 的早期引用被真正注入给了别人（用于 ADR-0002 的例外诊断）。 */
	private final Set<String> earlyReferencesExposed = new LinkedHashSet<>();
	private final List<BeanPostProcessor> beanPostProcessors = new ArrayList<>();
	/** bean -> 它依赖的 bean，用于按依赖逆序销毁。 */
	private final Map<String, Set<String>> dependencies = new LinkedHashMap<>();
	private final List<String> diagnostics = new ArrayList<>();

	/** 冻结后的单例快照：volatile 一次性发布，读路径无需加锁。 */
	private volatile Map<String, Object> frozenSingletons;
	private volatile boolean frozen;
	private boolean registeringBeanPostProcessors;
	private boolean destroyed;

	public DefaultListableBeanFactory(Environment environment) {
		this(environment, null);
	}

	public DefaultListableBeanFactory(Environment environment, ClassLoader classLoader) {
		this.environment = environment != null ? environment : new StandardEnvironment();
		this.classLoader = classLoader != null ? classLoader : DefaultListableBeanFactory.class.getClassLoader();
	}

	// ------------------------------------------------------------------
	// BeanDefinitionRegistry
	// ------------------------------------------------------------------

	@Override
	public void registerBeanDefinition(BeanDefinition beanDefinition) {
		checkNotFrozen("注册 Bean 定义");
		BeanDefinition existing = beanDefinitions.get(beanDefinition.getBeanName());
		if (existing != null) {
			throw new BeanDefinitionException(beanDefinition.getBeanName(),
					"bean 名称重复：已被 " + existing.getBeanClass().getName() + " 使用，本次又注册了 "
							+ beanDefinition.getBeanClass().getName()
							+ "\n  建议: 用 @MiniComponent(\"名称\") 为其中一个显式指定不同的名称。");
		}
		beanDefinitions.put(beanDefinition.getBeanName(), beanDefinition);
	}

	@Override
	public BeanDefinition getBeanDefinition(String beanName) {
		return beanDefinitions.get(beanName);
	}

	@Override
	public boolean containsBeanDefinition(String beanName) {
		return beanDefinitions.containsKey(beanName);
	}

	@Override
	public String[] getBeanDefinitionNames() {
		return beanDefinitions.keySet().toArray(new String[0]);
	}

	@Override
	public int getBeanDefinitionCount() {
		return beanDefinitions.size();
	}

	// ------------------------------------------------------------------
	// 刷新、冻结与销毁
	// ------------------------------------------------------------------

	/**
	 * 先创建所有 {@link BeanPostProcessor} 类型的 Bean 并登记，之后创建的 Bean 才会经过它们。
	 *
	 * <p>与 Spring 一致：BeanPostProcessor 自身不经过其他后置处理器（此时它们尚未全部就绪）。
	 */
	public void registerBeanPostProcessors() {
		checkNotFrozen("注册 BeanPostProcessor");
		registeringBeanPostProcessors = true;
		try {
			for (BeanDefinition definition : new ArrayList<>(beanDefinitions.values())) {
				if (!BeanPostProcessor.class.isAssignableFrom(definition.getBeanClass())) {
					continue;
				}
				Object bean = doGetBean(definition.getBeanName(), definition, new CreationPath(), null);
				if (bean instanceof BeanPostProcessor beanPostProcessor) {
					beanPostProcessors.add(beanPostProcessor);
				}
			}
		} finally {
			registeringBeanPostProcessors = false;
		}
	}

	/** 运行期登记后置处理器（仅允许在冻结前）。 */
	public void addBeanPostProcessor(BeanPostProcessor beanPostProcessor) {
		checkNotFrozen("登记 BeanPostProcessor");
		beanPostProcessors.add(beanPostProcessor);
	}

	/** 实例化全部非延时单例（本版本没有延时 Bean，等同于实例化全部单例）。 */
	public void preInstantiateSingletons() {
		for (BeanDefinition definition : new ArrayList<>(beanDefinitions.values())) {
			if (definition.isSingleton()) {
				doGetBean(definition.getBeanName(), definition, new CreationPath(), null);
			}
		}
	}

	/** 冻结容器：之后的定义表与单例池不可变，读路径无锁。 */
	public void freeze() {
		if (frozen) {
			return;
		}
		frozenSingletons = Collections.unmodifiableMap(new LinkedHashMap<>(singletonObjects));
		frozen = true;
	}

	public boolean isFrozen() {
		return frozen;
	}

	/** 按依赖逆序销毁单例（依赖者先销毁），单个销毁回调失败不中断其余销毁。 */
	public void destroySingletons() {
		if (destroyed) {
			return;
		}
		destroyed = true;
		List<String> failures = new ArrayList<>();
		List<Throwable> causes = new ArrayList<>();
		for (String beanName : resolveDestroyOrder()) {
			Object bean = getSingleton(beanName);
			if (bean == null) {
				continue;
			}
			BeanDefinition definition = beanDefinitions.get(beanName);
			// 在**目标对象**上执行回调：代理只是对外的视图，注解与回调方法都在目标类上。
			Object target = singletonTargets.getOrDefault(beanName, bean);
			try {
				invokeLifecycleMethods(beanName, definition.getBeanClass(), target, MiniPreDestroy.class);
			} catch (RuntimeException ex) {
				failures.add(beanName);
				causes.add(ex);
			}
		}
		if (!failures.isEmpty()) {
			throw new LifecycleException("容器销毁", failures, causes);
		}
	}

	// ------------------------------------------------------------------
	// BeanFactory
	// ------------------------------------------------------------------

	@Override
	public Object getBean(String beanName) {
		return doGetBean(beanName, requireBeanDefinition(beanName), new CreationPath(), null);
	}

	@Override
	public <T> T getBean(Class<T> beanType) {
		if (beanType == null) {
			throw new NoSuchBeanException("null", beanDefinitions.keySet(), "getBean(Class) 的入参不能为 null。");
		}
		String beanName = findUniqueBeanNameByType(beanType, "按类型获取 " + beanType.getName());
		return beanType.cast(getBean(beanName));
	}

	@Override
	public boolean containsBean(String beanName) {
		return beanDefinitions.containsKey(beanName);
	}

	@Override
	public <T> Map<String, T> getBeansOfType(Class<T> beanType) {
		Map<String, T> result = new LinkedHashMap<>();
		for (BeanDefinition definition : new ArrayList<>(beanDefinitions.values())) {
			if (beanType.isAssignableFrom(definition.getBeanClass())) {
				result.put(definition.getBeanName(), beanType.cast(getBean(definition.getBeanName())));
			}
		}
		return result;
	}

	// ------------------------------------------------------------------
	// 诊断（供测试与后续日志使用）
	// ------------------------------------------------------------------

	/** 冻结后的单例快照；未冻结时返回当前池的只读拷贝。 */
	public Map<String, Object> getSingletonBeans() {
		if (frozen) {
			return frozenSingletons;
		}
		return Collections.unmodifiableMap(new LinkedHashMap<>(singletonObjects));
	}

	/** 已记录的依赖关系（bean -> 其依赖的 bean）。 */
	public Map<String, Set<String>> getDependencies() {
		Map<String, Set<String>> copy = new LinkedHashMap<>();
		dependencies.forEach((key, value) -> copy.put(key, Collections.unmodifiableSet(value)));
		return Collections.unmodifiableMap(copy);
	}

	/** 被注入出去的早期引用（这些注入点持有的是未代理对象，见 ADR-0002 的例外条款）。 */
	public Set<String> getEarlyReferencesExposed() {
		return Collections.unmodifiableSet(earlyReferencesExposed);
	}

	/** 容器在启动期记录的诊断信息（后续接入 SLF4J 后同时输出为 WARN 日志）。 */
	public List<String> getDiagnostics() {
		return Collections.unmodifiableList(diagnostics);
	}

	public Environment getEnvironment() {
		return environment;
	}

	public ClassLoader getClassLoader() {
		return classLoader;
	}

	// ------------------------------------------------------------------
	// 创建流程
	// ------------------------------------------------------------------

	private Object doGetBean(String beanName, BeanDefinition definition, CreationPath path,
			DependencyDescriptor requestedBy) {
		if (definition.isPrototype()) {
			return createBean(beanName, definition, path, requestedBy, false);
		}
		Object existing = getSingleton(beanName);
		if (existing != null) {
			// 命中早期引用：说明这个 Bean 还在创建中就被别人注入走了（环上反向引用）。
			// 记录它，因为若它之后变成代理对象，那些注入点持有的将是未代理对象。
			if (!frozen && earlySingletonObjects.containsKey(beanName) && !singletonObjects.containsKey(beanName)) {
				earlyReferencesExposed.add(beanName);
			}
			return existing;
		}
		return createBean(beanName, definition, path, requestedBy, true);
	}

	private Object createBean(String beanName, BeanDefinition definition, CreationPath path,
			DependencyDescriptor requestedBy, boolean singleton) {
		checkBeanPostProcessorDependency(definition);

		String injectionPoint = requestedBy == null ? null : requestedBy.getDescription();
		if (path.contains(beanName)) {
			if (singleton) {
				Object early = earlySingletonObjects.get(beanName);
				if (early != null) {
					earlyReferencesExposed.add(beanName);
					return early;
				}
			}
			throw new CircularDependencyException(beanName, definition.getBeanClass(), injectionPoint,
					path.describeChain(beanName), path.describeHops(beanName));
		}

		path.push(beanName, injectionPoint);
		try {
			Object bean = instantiate(beanName, definition.getBeanClass(), path);
			if (singleton) {
				earlySingletonObjects.put(beanName, bean);
			}
			populateBean(beanName, definition.getBeanClass(), bean, path);
			Object initialized = initializeBean(beanName, definition.getBeanClass(), bean);

			if (singleton) {
				singletonTargets.put(beanName, bean);
				singletonObjects.put(beanName, initialized);
				if (earlyReferencesExposed.contains(beanName) && initialized != bean) {
					String diagnostic = "Bean '" + beanName + "' 已被其他 Bean 作为早期引用注入，但它在初始化后变成了代理对象；"
							+ "那些注入点持有的是未代理对象（见 docs/adr/0002-circular-dependency-scope.md 的例外条款）。";
					diagnostics.add(diagnostic);
					LOGGER.warn(diagnostic);
				}
			}
			return initialized;
		} finally {
			path.pop();
			if (singleton) {
				earlySingletonObjects.remove(beanName);
			}
		}
	}

	private Object instantiate(String beanName, Class<?> beanClass, CreationPath path) {
		Constructor<?> constructor = chooseConstructor(beanName, beanClass);
		constructor.setAccessible(true);
		Parameter[] parameters = constructor.getParameters();
		Object[] arguments = new Object[parameters.length];
		for (int i = 0; i < parameters.length; i++) {
			DependencyDescriptor descriptor = DependencyDescriptor.forConstructorParameter(constructor, parameters[i], i);
			try {
				arguments[i] = resolveDependency(descriptor, path);
			} catch (NoSuchBeanException | NoUniqueBeanException ex) {
				throw new BeanCreationException(beanName, beanClass, descriptor.getDescription(),
						"构造器依赖无法解析", ex);
			}
		}
		try {
			return constructor.newInstance(arguments);
		} catch (InvocationTargetException ex) {
			throw new BeanCreationException(beanName, beanClass, null, "构造器抛出异常", ex.getTargetException());
		} catch (ReflectiveOperationException ex) {
			throw new BeanCreationException(beanName, beanClass, null, "构造器调用失败", ex);
		}
	}

	/**
	 * 构造器选择：**不做猜测**（ADR-0012）。
	 *
	 * <ul>
	 *   <li>只有一个构造器：直接使用（包括隐式无参构造器），无需注解；</li>
	 *   <li>多个构造器：必须恰好有一个标注 {@code @MiniAutowired}，否则失败并列出全部候选。</li>
	 * </ul>
	 *
	 * <p>候选列表按参数类型签名排序，保证同一定义的报错内容可复现
	 * （{@code getDeclaredConstructors()} 的顺序没有规范保证）。
	 */
	private Constructor<?> chooseConstructor(String beanName, Class<?> beanClass) {
		Constructor<?>[] constructors = beanClass.getDeclaredConstructors().clone();
		if (constructors.length == 0) {
			throw new BeanCreationException(beanName, beanClass, null, "找不到任何构造器", null);
		}
		Arrays.sort(constructors, Comparator.comparing(this::describeParameterTypes));
		if (constructors.length == 1) {
			return constructors[0];
		}

		Constructor<?> annotated = null;
		for (Constructor<?> candidate : constructors) {
			if (!candidate.isAnnotationPresent(MiniAutowired.class)) {
				continue;
			}
			if (annotated != null) {
				throw new BeanDefinitionException(beanName, "有多个构造器标注了 @MiniAutowired，无法判断该用哪一个"
						+ describeCandidateConstructors(constructors)
						+ "\n  建议: 只保留一个构造器上的 @MiniAutowired。");
			}
			annotated = candidate;
		}
		if (annotated == null) {
			throw new BeanDefinitionException(beanName,
					"存在多个构造器，但没有任何一个标注 @MiniAutowired；容器不会替你猜该用哪一个"
							+ describeCandidateConstructors(constructors)
							+ "\n  建议: 在需要容器使用的构造器上加 @MiniAutowired。");
		}
		return annotated;
	}

	private String describeCandidateConstructors(Constructor<?>[] constructors) {
		StringBuilder description = new StringBuilder("\n  候选:");
		for (Constructor<?> candidate : constructors) {
			description.append("\n    ").append(candidate.getDeclaringClass().getSimpleName())
					.append('(').append(describeParameterTypes(candidate)).append(')');
			if (candidate.isAnnotationPresent(MiniAutowired.class)) {
				description.append("  [@MiniAutowired]");
			}
		}
		return description.toString();
	}

	private String describeParameterTypes(Constructor<?> constructor) {
		StringBuilder description = new StringBuilder();
		Class<?>[] parameterTypes = constructor.getParameterTypes();
		for (int i = 0; i < parameterTypes.length; i++) {
			if (i > 0) {
				description.append(',');
			}
			description.append(parameterTypes[i].getName());
		}
		return description.toString();
	}

	private void populateBean(String beanName, Class<?> beanClass, Object bean, CreationPath path) {
		for (Field field : beanClass.getDeclaredFields()) {
			if (field.isAnnotationPresent(MiniValue.class)) {
				injectValue(beanName, beanClass, bean, field);
				continue;
			}
			if (!field.isAnnotationPresent(MiniAutowired.class)) {
				continue;
			}
			DependencyDescriptor descriptor = DependencyDescriptor.forField(field);
			Object dependency = resolveDependencyForInjection(beanName, beanClass, descriptor, path);
			try {
				field.setAccessible(true);
				field.set(bean, dependency);
			} catch (IllegalAccessException ex) {
				throw new BeanCreationException(beanName, beanClass, descriptor.getDescription(), "字段注入失败", ex);
			}
		}

		for (Method method : beanClass.getDeclaredMethods()) {
			if (!method.isAnnotationPresent(MiniAutowired.class)) {
				continue;
			}
			if (method.getParameterCount() != 1) {
				throw new BeanCreationException(beanName, beanClass, method.toString(),
						"@MiniAutowired 标注的方法必须且只能有一个参数", null);
			}
			DependencyDescriptor descriptor = DependencyDescriptor.forSetterParameter(method, method.getParameters()[0]);
			Object dependency = resolveDependencyForInjection(beanName, beanClass, descriptor, path);
			try {
				method.setAccessible(true);
				method.invoke(bean, dependency);
			} catch (InvocationTargetException ex) {
				throw new BeanCreationException(beanName, beanClass, descriptor.getDescription(),
						"setter 注入调用抛出异常", ex.getTargetException());
			} catch (ReflectiveOperationException ex) {
				throw new BeanCreationException(beanName, beanClass, descriptor.getDescription(), "setter 注入失败", ex);
			}
		}
	}

	private void injectValue(String beanName, Class<?> beanClass, Object bean, Field field) {
		MiniValue annotation = field.getAnnotation(MiniValue.class);
		String key = annotation.value();
		String description = "字段 " + beanClass.getName() + "#" + field.getName();
		if (key == null || key.isBlank()) {
			throw new ConfigException("@MiniValue 的配置 key 不能为空（" + description + "）");
		}
		String value = environment.getProperty(key);
		if (value == null) {
			throw ConfigException.missingProperty(key, description);
		}
		Object converted;
		try {
			converted = SimpleTypeConverter.convert(value, field.getType());
		} catch (RuntimeException ex) {
			throw ConfigException.conversionFailed(key, value, field.getType(), ex);
		}
		try {
			field.setAccessible(true);
			field.set(bean, converted);
		} catch (IllegalAccessException ex) {
			throw new BeanCreationException(beanName, beanClass, description, "配置值注入失败", ex);
		}
	}

	private Object resolveDependencyForInjection(String beanName, Class<?> beanClass, DependencyDescriptor descriptor,
			CreationPath path) {
		try {
			return resolveDependency(descriptor, path);
		} catch (NoSuchBeanException | NoUniqueBeanException ex) {
			throw new BeanCreationException(beanName, beanClass, descriptor.getDescription(), "依赖无法解析", ex);
		}
	}

	private Object resolveDependency(DependencyDescriptor descriptor, CreationPath path) {
		String dependencyBeanName = findDependencyBeanName(descriptor);
		BeanDefinition dependencyDefinition = requireBeanDefinition(dependencyBeanName);
		if (!frozen) {
			recordDependency(path.currentBeanName(), dependencyBeanName);
		}
		return doGetBean(dependencyBeanName, dependencyDefinition, path, descriptor);
	}

	private String findDependencyBeanName(DependencyDescriptor descriptor) {
		String qualifier = descriptor.getQualifier();
		if (qualifier != null && !qualifier.isBlank()) {
			BeanDefinition target = beanDefinitions.get(qualifier);
			if (target == null) {
				throw new NoSuchBeanException(qualifier, beanDefinitions.keySet(),
						"注入点 " + descriptor.getDescription() + " 上的 @MiniQualifier(\"" + qualifier
								+ "\") 指向的 Bean 未注册。");
			}
			if (!descriptor.getType().isAssignableFrom(target.getBeanClass())) {
				throw new BeansException("注入点 " + descriptor.getDescription() + " 需要 "
						+ descriptor.getType().getName() + "，但 @MiniQualifier(\"" + qualifier + "\") 指向的 "
						+ target.getBeanClass().getName() + " 不是该类型。");
			}
			return target.getBeanName();
		}

		if (descriptor.isNameBasedFallback() && descriptor.getName() != null) {
			BeanDefinition byName = beanDefinitions.get(descriptor.getName());
			if (byName != null && descriptor.getType().isAssignableFrom(byName.getBeanClass())) {
				return byName.getBeanName();
			}
		}

		return findUniqueBeanNameByType(descriptor.getType(), "注入点 " + descriptor.getDescription());
	}

	private String findUniqueBeanNameByType(Class<?> beanType, String requestedBy) {
		Map<String, Class<?>> candidates = new LinkedHashMap<>();
		for (BeanDefinition definition : beanDefinitions.values()) {
			if (beanType.isAssignableFrom(definition.getBeanClass())) {
				candidates.put(definition.getBeanName(), definition.getBeanClass());
			}
		}
		if (candidates.isEmpty()) {
			throw new NoSuchBeanException(beanType.getName() + "（" + requestedBy + "）", beanDefinitions.keySet(),
					"确认该类型是否被标注为 @MiniComponent 且位于被扫描的包下。");
		}
		if (candidates.size() > 1) {
			throw new NoUniqueBeanException(beanType, candidates, requestedBy);
		}
		return candidates.keySet().iterator().next();
	}

	private Object initializeBean(String beanName, Class<?> beanClass, Object bean) {
		if (registeringBeanPostProcessors) {
			invokeLifecycleMethods(beanName, beanClass, bean, MiniPostConstruct.class);
			return bean;
		}

		Object current = bean;
		for (BeanPostProcessor beanPostProcessor : beanPostProcessors) {
			current = applyBefore(beanName, beanClass, beanPostProcessor, current);
		}
		invokeLifecycleMethods(beanName, beanClass, current, MiniPostConstruct.class);
		for (BeanPostProcessor beanPostProcessor : beanPostProcessors) {
			current = applyAfter(beanName, beanClass, beanPostProcessor, current);
		}
		return current;
	}

	private Object applyBefore(String beanName, Class<?> beanClass, BeanPostProcessor beanPostProcessor, Object bean) {
		try {
			Object result = beanPostProcessor.postProcessBeforeInitialization(beanName, bean);
			if (result == null) {
				throw new BeanCreationException(beanName, beanClass,
						"BeanPostProcessor " + beanPostProcessor.getClass().getName(),
						"postProcessBeforeInitialization 返回了 null（不允许把 Bean 变成 null）", null);
			}
			return result;
		} catch (BeanCreationException ex) {
			throw ex;
		} catch (RuntimeException ex) {
			throw new BeanCreationException(beanName, beanClass,
					"BeanPostProcessor " + beanPostProcessor.getClass().getName(), "前置处理失败", ex);
		}
	}

	private Object applyAfter(String beanName, Class<?> beanClass, BeanPostProcessor beanPostProcessor, Object bean) {
		try {
			Object result = beanPostProcessor.postProcessAfterInitialization(beanName, bean);
			if (result == null) {
				throw new BeanCreationException(beanName, beanClass,
						"BeanPostProcessor " + beanPostProcessor.getClass().getName(),
						"postProcessAfterInitialization 返回了 null（不允许把 Bean 变成 null）", null);
			}
			return result;
		} catch (BeanCreationException ex) {
			throw ex;
		} catch (RuntimeException ex) {
			throw new BeanCreationException(beanName, beanClass,
					"BeanPostProcessor " + beanPostProcessor.getClass().getName(), "后置处理失败", ex);
		}
	}

	/**
	 * 调用生命周期回调。
	 *
	 * <p>注意：反射的目标类是**定义里的原始类**而不是实例的运行时类。因为实例可能是 JDK/CGLIB 代理，
	 * 而代理类上不会带有目标方法的注解——若按运行时类反射，被代理 Bean 的 {@code @MiniPreDestroy}
	 * 会被静默跳过。
	 */
	private void invokeLifecycleMethods(String beanName, Class<?> beanClass, Object bean,
			Class<? extends java.lang.annotation.Annotation> annotationType) {
		for (Method method : beanClass.getDeclaredMethods()) {
			if (!method.isAnnotationPresent(annotationType)) {
				continue;
			}
			if (method.getParameterCount() != 0) {
				throw new BeanCreationException(beanName, beanClass, method.toString(),
						"生命周期方法必须无参数（" + annotationType.getSimpleName() + "）", null);
			}
			try {
				method.setAccessible(true);
				method.invoke(bean);
			} catch (InvocationTargetException ex) {
				throw new BeanCreationException(beanName, beanClass, method.toString(),
						"生命周期方法抛出异常（" + annotationType.getSimpleName() + "）", ex.getTargetException());
			} catch (ReflectiveOperationException ex) {
				throw new BeanCreationException(beanName, beanClass, method.toString(),
						"生命周期方法调用失败（" + annotationType.getSimpleName() + "）", ex);
			}
		}
	}

	private Object getSingleton(String beanName) {
		Map<String, Object> snapshot = frozenSingletons;
		if (frozen && snapshot != null) {
			return snapshot.get(beanName);
		}
		Object bean = singletonObjects.get(beanName);
		return bean != null ? bean : earlySingletonObjects.get(beanName);
	}

	private BeanDefinition requireBeanDefinition(String beanName) {
		BeanDefinition definition = beanDefinitions.get(beanName);
		if (definition == null) {
			throw new NoSuchBeanException(beanName, beanDefinitions.keySet(),
					"检查名称是否拼写正确，或该组件是否位于被扫描的包下。");
		}
		return definition;
	}

	private void recordDependency(String consumer, String dependency) {
		if (consumer == null || dependency == null || consumer.equals(dependency)) {
			return;
		}
		dependencies.computeIfAbsent(consumer, key -> new LinkedHashSet<>()).add(dependency);
	}

	private List<String> resolveDestroyOrder() {
		List<String> ordered = new ArrayList<>();
		Set<String> visited = new LinkedHashSet<>();
		for (String beanName : new ArrayList<>(singletonObjects.keySet())) {
			visitForDestroy(beanName, visited, ordered);
		}
		Collections.reverse(ordered);
		return ordered;
	}

	private void visitForDestroy(String beanName, Set<String> visited, List<String> ordered) {
		if (!visited.add(beanName)) {
			return;
		}
		for (String dependency : dependencies.getOrDefault(beanName, Collections.emptySet())) {
			if (singletonObjects.containsKey(dependency)) {
				visitForDestroy(dependency, visited, ordered);
			}
		}
		ordered.add(beanName);
	}

	private void checkBeanPostProcessorDependency(BeanDefinition definition) {
		if (!registeringBeanPostProcessors) {
			return;
		}
		if (BeanPostProcessor.class.isAssignableFrom(definition.getBeanClass())) {
			return;
		}
		throw new BeanCreationException(definition.getBeanName(), definition.getBeanClass(), null,
				"BeanPostProcessor 依赖了普通 Bean '" + definition.getBeanName()
						+ "'，它会在后置处理器注册完成之前被创建，从而跳过全部后置处理（包括 AOP 代理）"
						+ "\n  建议: 让 BeanPostProcessor 不持有普通 Bean 依赖，改为在回调方法内按需 getBean。",
				null);
	}

	private void checkNotFrozen(String action) {
		if (frozen) {
			throw new BeansException("容器已刷新（frozen），不支持在运行期" + action
					+ "\n  建议: 在容器构造完成前完成注册（扫描到的 @MiniComponent 会自动注册）；运行期可变状态会破坏并发安全，见 docs/adr/0006-concurrency-and-lifecycle-freeze.md。");
		}
	}

	/**
	 * 单次创建过程的路径记录：既用于环检测（判定依据是"当前线程的创建路径"，因此天然线程安全），
	 * 也用于生成可读的依赖链与注入点明细。
	 */
	private static final class CreationPath {

		private final List<String> beanNames = new ArrayList<>();
		private final List<String> injectionPoints = new ArrayList<>();

		boolean contains(String beanName) {
			return beanNames.contains(beanName);
		}

		void push(String beanName, String injectionPoint) {
			beanNames.add(beanName);
			injectionPoints.add(injectionPoint == null ? "(容器刷新触发)" : injectionPoint);
		}

		void pop() {
			beanNames.remove(beanNames.size() - 1);
			injectionPoints.remove(injectionPoints.size() - 1);
		}

		String currentBeanName() {
			return beanNames.isEmpty() ? null : beanNames.get(beanNames.size() - 1);
		}

		/** 依赖链，形如 {@code a -> b -> a}。 */
		String describeChain(String closingBeanName) {
			int start = beanNames.indexOf(closingBeanName);
			List<String> cycle = new ArrayList<>(beanNames.subList(Math.max(start, 0), beanNames.size()));
			cycle.add(closingBeanName);
			return String.join(" -> ", cycle);
		}

		/** 链路上每个节点是被哪一处注入点触发的。 */
		String describeHops(String closingBeanName) {
			int start = beanNames.indexOf(closingBeanName);
			StringBuilder description = new StringBuilder();
			for (int i = Math.max(start, 0); i < beanNames.size(); i++) {
				description.append("\n    ").append(beanNames.get(i)).append(" 由 ")
						.append(injectionPoints.get(i)).append(" 触发");
			}
			return description.toString();
		}
	}
}
