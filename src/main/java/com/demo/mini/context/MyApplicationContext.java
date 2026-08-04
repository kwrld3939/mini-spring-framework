package com.demo.mini.context;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.net.URL;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import com.demo.mini.annotation.MyAutowired;
import com.demo.mini.annotation.MyComponent;
import com.demo.mini.annotation.MyPostConstruct;
import com.demo.mini.annotation.MyPreDestroy;
import com.demo.mini.annotation.MyQualifier;
import com.demo.mini.annotation.MyScope;
import com.demo.mini.annotation.MyValue;
import com.demo.mini.beans.BeanDefinition;
import com.demo.mini.beans.BeanPostProcessor;
import com.demo.mini.util.SimpleTypeConverter;

public class MyApplicationContext implements AutoCloseable {

	private final Properties properties = new Properties();
	private final String rootPackage;
	private final ClassLoader classLoader;
	private final Map<String, BeanDefinition> beanDefinitions = new LinkedHashMap<>();
	private final Map<String, Object> beans = new LinkedHashMap<>();
	private final List<BeanPostProcessor> beanPostProcessors = new ArrayList<>();

	public MyApplicationContext(String rootPackage) {
		this(rootPackage, Thread.currentThread().getContextClassLoader());
	}

	public MyApplicationContext(String rootPackage, ClassLoader classLoader) {
		if (rootPackage == null || rootPackage.isBlank()) {
			throw new IllegalArgumentException("rootPackage must not be blank");
		}
		this.rootPackage = rootPackage;
		this.classLoader = classLoader == null ? MyApplicationContext.class.getClassLoader() : classLoader;
		loadProperties();
		scanBeanClasses();
		createBeans();
		injectDependencies();
		registerBeanPostProcessors();
		initializeBeans();
	}

	public Object getBean(String beanName) {
		BeanDefinition beanDefinition = beanDefinitions.get(beanName);
		if (beanDefinition == null) {
			return null;
		}
		if (beanDefinition.isSingleton()) {
			return getOrCreateBean(beanName, new HashSet<>());
		}
		Object bean = instantiate(beanDefinition.getBeanClass(), new HashSet<>());
		injectDependencies(bean);
		return initializeBean(beanName, bean);
	}

	public <T> T getBean(Class<T> beanType) {
		String beanName = findBeanNameByType(beanType);
		if (beanName == null) {
			return null;
		}
		return beanType.cast(getBean(beanName));
	}

	public Map<String, Object> getBeans() {
		return Collections.unmodifiableMap(beans);
	}

	public String getRootPackage() {
		return rootPackage;
	}

	@Override
	public void close() {
		invokePreDestroyMethods();
	}

	private void scanBeanClasses() {
		for (Class<?> clazz : scanClasses(rootPackage)) {
			MyComponent component = clazz.getAnnotation(MyComponent.class);
			if (component == null) {
				continue;
			}

			String beanName = component.value().isBlank() ? defaultBeanName(clazz) : component.value();
			if (beanDefinitions.containsKey(beanName)) {
				throw new IllegalStateException("Duplicate bean name: " + beanName);
			}

			String scope = "singleton";
			MyScope scopeAnnotation = clazz.getAnnotation(MyScope.class);
			if (scopeAnnotation != null && !scopeAnnotation.value().isBlank()) {
				scope = scopeAnnotation.value();
			}

			BeanDefinition beanDefinition = new BeanDefinition(beanName, clazz, scope, false);
			beanDefinitions.put(beanName, beanDefinition);
		}
	}

	private void loadProperties() {
		try (InputStream inputStream = classLoader.getResourceAsStream("application.properties")) {
			if (inputStream != null) {
				properties.load(inputStream);
			}
		} catch (IOException ex) {
			throw new IllegalStateException("Failed to load application.properties", ex);
		}
	}

	private void createBeans() {
		Set<String> creating = new HashSet<>();
		for (BeanDefinition beanDefinition : beanDefinitions.values()) {
			if (beanDefinition.isSingleton() && !beanDefinition.isLazyInit()) {
				getOrCreateBean(beanDefinition.getBeanName(), creating);
			}
		}
	}

	private List<Class<?>> scanClasses(String packageName) {
		String packagePath = packageName.replace('.', '/');
		List<Class<?>> classes = new ArrayList<>();

		try {
			Enumeration<URL> resources = classLoader.getResources(packagePath);
			while (resources.hasMoreElements()) {
				URL resource = resources.nextElement();
				if (!"file".equals(resource.getProtocol())) {
					continue;
				}
				File directory = new File(decode(resource.getFile()));
				scanDirectory(packageName, directory, classes);
			}
		} catch (Exception ex) {
			throw new IllegalStateException("Failed to scan package: " + packageName, ex);
		}

		return classes;
	}

	private void scanDirectory(String packageName, File directory, List<Class<?>> classes) {
		if (!directory.exists() || !directory.isDirectory()) {
			return;
		}

		File[] files = directory.listFiles();
		if (files == null) {
			return;
		}

		for (File file : files) {
			if (file.isDirectory()) {
				scanDirectory(packageName + "." + file.getName(), file, classes);
				continue;
			}
			String fileName = file.getName();
			if (!fileName.endsWith(".class") || fileName.contains("$")) {
				continue;
			}
			String simpleName = fileName.substring(0, fileName.length() - ".class".length());
			if ("package-info".equals(simpleName) || "module-info".equals(simpleName)) {
				continue;
			}
			String className = packageName + "." + simpleName;
			try {
				classes.add(classLoader.loadClass(className));
			} catch (ClassNotFoundException | NoClassDefFoundError ex) {
				continue;
			}
		}
	}

	private Object getOrCreateBean(String beanName, Set<String> creating) {
		Object existingBean = beans.get(beanName);
		if (existingBean != null) {
			return existingBean;
		}

		BeanDefinition beanDefinition = beanDefinitions.get(beanName);
		if (beanDefinition == null) {
			throw new IllegalStateException("No bean definition found for name: " + beanName);
		}

		if (!creating.add(beanName)) {
			throw new IllegalStateException("Circular constructor dependency detected: " + beanName);
		}

		Object bean = instantiate(beanDefinition.getBeanClass(), creating);
		if (beanDefinition.isSingleton()) {
			beans.put(beanName, bean);
		}

		creating.remove(beanName);
		return bean;
	}

	private Object instantiate(Class<?> clazz, Set<String> creating) {
		try {
			Constructor<?> constructor = chooseConstructor(clazz);
			constructor.setAccessible(true);
			Class<?>[] parameterTypes = constructor.getParameterTypes();
			Object[] args = new Object[parameterTypes.length];
			Parameter[] parameters = constructor.getParameters();
			for (int i = 0; i < parameterTypes.length; i++) {
				args[i] = resolveDependency(parameters[i], parameterTypes[i], creating);
			}
			return constructor.newInstance(args);
		} catch (Exception ex) {
			throw new IllegalStateException("Failed to instantiate bean: " + clazz.getName(), ex);
		}
	}

	private Constructor<?> chooseConstructor(Class<?> clazz) {
		Constructor<?>[] constructors = clazz.getDeclaredConstructors();
		Constructor<?> chosen = constructors[0];
		for (Constructor<?> constructor : constructors) {
			if (constructor.getParameterCount() > chosen.getParameterCount()) {
				chosen = constructor;
			}
		}
		return chosen;
	}

	private void injectDependencies() {
		for (Object bean : beans.values()) {
			injectDependencies(bean);
		}
	}

	private void injectDependencies(Object bean) {
		for (Field field : bean.getClass().getDeclaredFields()) {
			if (field.isAnnotationPresent(MyValue.class)) {
				injectValue(bean, field);
				continue;
			}

			if (!field.isAnnotationPresent(MyAutowired.class)) {
				continue;
			}
			Object dependency = resolveDependency(field);
			if (dependency == null) {
				throw new IllegalStateException("No bean found for field: " + field);
			}
			try {
				field.setAccessible(true);
				field.set(bean, dependency);
			} catch (IllegalAccessException ex) {
				throw new IllegalStateException("Failed to inject field: " + field, ex);
			}
		}

		for (Method method : bean.getClass().getDeclaredMethods()) {
			if (!method.isAnnotationPresent(MyAutowired.class)) {
				continue;
			}
			if (method.getParameterCount() != 1) {
				throw new IllegalStateException("Autowired method must have exactly one parameter: " + method);
			}
			Object dependency = resolveDependency(method.getParameters()[0], method.getParameterTypes()[0], new HashSet<>());
			try {
				method.setAccessible(true);
				method.invoke(bean, dependency);
			} catch (Exception ex) {
				throw new IllegalStateException("Failed to inject method: " + method, ex);
			}
		}
	}

	private void injectValue(Object bean, Field field) {
		MyValue myValue = field.getAnnotation(MyValue.class);
		String propertyKey = myValue.value();
		String propertyValue = properties.getProperty(propertyKey);
		if (propertyValue == null) {
			throw new IllegalStateException("No property found for key: " + propertyKey);
		}

		Object convertedValue = SimpleTypeConverter.convert(propertyValue, field.getType());
		try {
			field.setAccessible(true);
			field.set(bean, convertedValue);
		} catch (IllegalAccessException ex) {
			throw new IllegalStateException("Failed to inject value into field: " + field, ex);
		}
	}

	private Object resolveDependency(Field field) {
		MyQualifier qualifier = field.getAnnotation(MyQualifier.class);
		if (qualifier != null) {
			return getBean(qualifier.value());
		}
		BeanDefinition beanDefinition = beanDefinitions.get(field.getName());
		if (beanDefinition != null && field.getType().isAssignableFrom(beanDefinition.getBeanClass())) {
			return getBean(field.getName());
		}
		return getBean(field.getType());
	}

	private Object resolveDependency(Parameter parameter, Class<?> dependencyType, Set<String> creating) {
		MyQualifier qualifier = parameter.getAnnotation(MyQualifier.class);
		if (qualifier == null) {
			return resolveDependency(dependencyType, creating);
		}

		BeanDefinition beanDefinition = beanDefinitions.get(qualifier.value());
		if (beanDefinition == null) {
			throw new IllegalStateException("No bean found for qualifier: " + qualifier.value());
		}
		if (!dependencyType.isAssignableFrom(beanDefinition.getBeanClass())) {
			throw new IllegalStateException("Bean " + qualifier.value()
					+ " is not assignable to " + dependencyType.getName());
		}
		if (beanDefinition.isPrototype()) {
			return instantiate(beanDefinition.getBeanClass(), creating);
		}
		return getOrCreateBean(qualifier.value(), creating);
	}

	private Object resolveDependency(Class<?> dependencyType, Set<String> creating) {
		String matchName = null;
		for (BeanDefinition beanDefinition : beanDefinitions.values()) {
			if (!dependencyType.isAssignableFrom(beanDefinition.getBeanClass())) {
				continue;
			}
			if (matchName != null) {
				throw new IllegalStateException("Multiple beans found for type: " + dependencyType.getName());
			}
			matchName = beanDefinition.getBeanName();
		}

		if (matchName == null) {
			throw new IllegalStateException("No bean found for constructor dependency: " + dependencyType.getName());
		}

		BeanDefinition beanDefinition = beanDefinitions.get(matchName);
		if (beanDefinition.isPrototype()) {
			return instantiate(beanDefinition.getBeanClass(), creating);
		}
		return getOrCreateBean(matchName, creating);
	}


	private void invokePreDestroyMethods() {
		for (Object bean : beans.values()) {
			invokeLifecycleMethods(bean, MyPreDestroy.class);
		}
	}

	private void invokeLifecycleMethods(Object bean, Class<? extends java.lang.annotation.Annotation> annotationType) {
		for (Method method : bean.getClass().getDeclaredMethods()) {
			if (!method.isAnnotationPresent(annotationType)) {
				continue;
			}
			if (method.getParameterCount() != 0) {
				throw new IllegalStateException("Lifecycle method must have no parameters: " + method);
			}
			try {
				method.setAccessible(true);
				method.invoke(bean);
			} catch (Exception ex) {
				throw new IllegalStateException("Failed to invoke lifecycle method: " + method, ex);
			}
		}
	}

	private String findBeanNameByType(Class<?> beanType) {
		String matchName = null;
		for (BeanDefinition beanDefinition : beanDefinitions.values()) {
			if (!beanType.isAssignableFrom(beanDefinition.getBeanClass())) {
				continue;
			}
			if (matchName != null) {
				throw new IllegalStateException("Multiple beans found for type: " + beanType.getName());
			}
			matchName = beanDefinition.getBeanName();
		}
		return matchName;
	}

	private String defaultBeanName(Class<?> clazz) {
		String simpleName = clazz.getSimpleName();
		if (simpleName.length() == 1) {
			return simpleName.toLowerCase();
		}
		return Character.toLowerCase(simpleName.charAt(0)) + simpleName.substring(1);
	}



	private void registerBeanPostProcessors() {
		beanPostProcessors.clear();

		for (Object bean : beans.values()) {
			if (bean instanceof BeanPostProcessor beanPostProcessor) {
				beanPostProcessors.add(beanPostProcessor);
			}
		}
	}

	private void initializeBeans() {
		List<String> beanNames = new ArrayList<>(beans.keySet());

		for (String beanName : beanNames) {
			Object bean = beans.get(beanName);
			Object initializedBean = initializeBean(beanName, bean);
			beans.put(beanName, initializedBean);
		}
	}

	private Object initializeBean(String beanName, Object bean) {
		Object currentBean = bean;

		for (BeanPostProcessor beanPostProcessor : beanPostProcessors) {
			currentBean = beanPostProcessor.postProcessBeforeInitialization(beanName, currentBean);
		}

		invokeLifecycleMethods(currentBean, MyPostConstruct.class);

		for (BeanPostProcessor beanPostProcessor : beanPostProcessors) {
			currentBean = beanPostProcessor.postProcessAfterInitialization(beanName, currentBean);
		}

		return currentBean;
	}

	private String decode(String value) throws UnsupportedEncodingException {
		return URLDecoder.decode(value, StandardCharsets.UTF_8.name());
	}
}
