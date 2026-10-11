package io.github.kwrld3939.minispring.context.support;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.JarURLConnection;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

import io.github.kwrld3939.minispring.annotation.MiniComponent;
import io.github.kwrld3939.minispring.annotation.MiniScope;
import io.github.kwrld3939.minispring.exception.BeansException;

/**
 * 类路径扫描器：找出根包下的候选类，并用 ASM 读出它们的注解元数据。
 *
 * <h2>为什么不加载类（ADR-0003）</h2>
 * <p>用 {@code ClassLoader#loadClass} 读注解会**执行静态初始化块**——一个声称"只看一眼"的只读动作
 * 因此可能触发任意用户代码，还会因为缺失可选依赖而抛 {@code NoClassDefFoundError}。
 * 本实现改为读取 {@code .class} 字节码：只取类名、类级注解与访问标志，不加载、不链接、不初始化。
 *
 * <h2>支持范围</h2>
 * <ul>
 *   <li>{@code file:}（目录）与 {@code jar:} 两种类路径形式；</li>
 *   <li>jar 需要有**目录条目**（{@code jar} 工具与 Maven 生成的 jar 默认都有）。这是
 *       {@code ClassLoader#getResources(String)} 定位包的机制决定的：没有目录条目的 jar
 *       无法通过包路径被发现。手工拼装的 jar 若缺少目录条目会表现为"扫不到"，属于已知限制；</li>
 *   <li>嵌套 jar（Spring Boot fat jar 的 {@code BOOT-INF/classes!/}）明确报错而不是静默漏扫；</li>
 *   <li>其他协议（如 http）直接报错，避免"扫不到但没人知道"。</li>
 * </ul>
 *
 * <h2>确定性</h2>
 * <p>结果按类的全限定名排序。目录枚举顺序由文件系统决定，不能作为定序依据，否则注册顺序、
 * 启动日志顺序与失败顺序在不同机器上会不一致。
 */
public class ClassPathScanner {

	private static final String COMPONENT_DESCRIPTOR = Type.getDescriptor(MiniComponent.class);
	private static final String SCOPE_DESCRIPTOR = Type.getDescriptor(MiniScope.class);
	private static final String CLASS_SUFFIX = ".class";

	private final ClassLoader classLoader;

	public ClassPathScanner(ClassLoader classLoader) {
		this.classLoader = classLoader != null ? classLoader : ClassPathScanner.class.getClassLoader();
	}

	/** 扫描根包及其子包，返回按类名排序的候选类元数据。 */
	public List<ScannedClass> scan(String basePackage) {
		if (basePackage == null || basePackage.isBlank()) {
			throw new BeansException("扫描包名不能为空");
		}
		String packagePath = basePackage.replace('.', '/');
		Map<String, ScannedClass> found = new LinkedHashMap<>();
		try {
			Enumeration<URL> resources = classLoader.getResources(packagePath);
			while (resources.hasMoreElements()) {
				URL resource = resources.nextElement();
				switch (resource.getProtocol()) {
					case "file" -> scanDirectory(basePackage, toFile(resource), found);
					case "jar" -> scanJar(basePackage, resource, found);
					default -> throw new BeansException("不支持的类路径形式：" + resource + "（协议 "
							+ resource.getProtocol() + "）\n  说明: 当前支持目录（file:）与 jar（jar:），见 docs/adr/0003-classpath-scanning.md。");
				}
			}
		} catch (IOException ex) {
			throw new BeansException("扫描包 '" + basePackage + "' 失败", ex);
		}
		List<ScannedClass> result = new ArrayList<>(found.values());
		result.sort(Comparator.comparing(ScannedClass::getClassName));
		return result;
	}

	private File toFile(URL resource) {
		try {
			// 用 URI 而不是 URLDecoder：后者会把路径中的 '+' 误解码成空格。
			return new File(resource.toURI());
		} catch (URISyntaxException ex) {
			throw new BeansException("无法把类路径资源转换为文件路径：" + resource, ex);
		}
	}

	private void scanDirectory(String packageName, File directory, Map<String, ScannedClass> found) {
		if (!directory.exists() || !directory.isDirectory()) {
			return;
		}
		File[] files = directory.listFiles();
		if (files == null) {
			return;
		}
		for (File file : files) {
			if (file.isDirectory()) {
				scanDirectory(packageName + "." + file.getName(), file, found);
				continue;
			}
			if (!isCandidateClassFileName(file.getName())) {
				continue;
			}
			try (InputStream inputStream = new FileInputStream(file)) {
				readMetadata(inputStream, found);
			} catch (IOException ex) {
				throw new BeansException("读取类文件失败：" + file, ex);
			}
		}
	}

	private void scanJar(String basePackage, URL resource, Map<String, ScannedClass> found) {
		checkNotNestedJar(resource.toString());
		String prefix = basePackage.replace('.', '/') + "/";
		try {
			JarURLConnection connection = (JarURLConnection) resource.openConnection();
			try (JarFile jarFile = connection.getJarFile()) {
				Enumeration<JarEntry> entries = jarFile.entries();
				while (entries.hasMoreElements()) {
					JarEntry entry = entries.nextElement();
					String entryName = entry.getName();
					if (entry.isDirectory() || !entryName.startsWith(prefix) || !isCandidateClassFileName(entryName)) {
						continue;
					}
					try (InputStream inputStream = jarFile.getInputStream(entry)) {
						readMetadata(inputStream, found);
					}
				}
			}
		} catch (IOException ex) {
			throw new BeansException("扫描 jar 失败：" + resource, ex);
		}
	}

	/**
	 * 拒绝嵌套 jar。
	 *
	 * <p>Boot 的 fat jar 会出现 {@code jar:file:/app.jar!/BOOT-INF/lib/x.jar!/com/foo} 这类路径，
	 * 普通 {@link java.net.JarURLConnection} 无法处理。这里明确报错，而不是返回空结果让使用者以为"没有组件"。
	 */
	static void checkNotNestedJar(String urlSpec) {
		if (urlSpec.indexOf("!/") != urlSpec.lastIndexOf("!/")) {
			throw new BeansException("不支持嵌套 jar 中的组件扫描：" + urlSpec
					+ "\n  说明: Spring Boot fat jar 的 BOOT-INF/classes!/ 这类嵌套路径需要专用的 ClassLoader 支持，当前未实现，见 docs/adr/0003-classpath-scanning.md。"
					+ "\n  建议: 用 Boot 启动时的 ClassLoader 直接扫描 BOOT-INF/classes（它本身就是目录形式），或把组件放进普通 jar。");
		}
	}

	private void readMetadata(InputStream inputStream, Map<String, ScannedClass> found) throws IOException {
		ClassReader classReader;
		try {
			classReader = new ClassReader(inputStream);
		} catch (IllegalArgumentException | ArrayIndexOutOfBoundsException ex) {
			throw new BeansException("无法解析 class 文件（格式不支持或已损坏）", ex);
		}
		MetadataVisitor visitor = new MetadataVisitor();
		classReader.accept(visitor, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
		ScannedClass scannedClass = visitor.toScannedClass();
		if (scannedClass == null) {
			return;
		}
		found.putIfAbsent(scannedClass.getClassName(), scannedClass);
	}

	private static boolean isCandidateClassFileName(String path) {
		if (!path.endsWith(CLASS_SUFFIX)) {
			return false;
		}
		String fileName = path.substring(path.lastIndexOf('/') + 1);
		if (fileName.contains("$")) {
			// 内部类/匿名类：文件名快速过滤，真正的判定仍以字节码里的类名为准。
			return false;
		}
		String simpleName = fileName.substring(0, fileName.length() - CLASS_SUFFIX.length());
		return !"package-info".equals(simpleName) && !"module-info".equals(simpleName);
	}

	/** 只读类级信息：类名、访问标志与两个框架注解，代码/调试信息全部跳过以降低开销。 */
	private static final class MetadataVisitor extends ClassVisitor {

		private String className;
		private boolean interfaceType;
		private boolean abstractType;
		private String componentBeanName;
		private String scope;

		MetadataVisitor() {
			super(Opcodes.ASM9);
		}

		@Override
		public void visit(int version, int access, String name, String signature, String superName,
				String[] interfaces) {
			this.className = name.replace('/', '.');
			this.interfaceType = (access & Opcodes.ACC_INTERFACE) != 0;
			this.abstractType = (access & Opcodes.ACC_ABSTRACT) != 0;
		}

		@Override
		public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
			if (COMPONENT_DESCRIPTOR.equals(descriptor)) {
				componentBeanName = "";
				return new ValueCollector(value -> componentBeanName = value);
			}
			if (SCOPE_DESCRIPTOR.equals(descriptor)) {
				return new ValueCollector(value -> scope = value);
			}
			return null;
		}

		ScannedClass toScannedClass() {
			if (className == null || className.contains("$")) {
				// 以字节码里的类名为准：即使是内部类，也在这里被排除。
				return null;
			}
			return new ScannedClass(className, componentBeanName, scope, interfaceType, abstractType);
		}
	}

	/** 读取注解的 {@code value} 元素。 */
	private static final class ValueCollector extends AnnotationVisitor {

		private final java.util.function.Consumer<String> consumer;

		ValueCollector(java.util.function.Consumer<String> consumer) {
			super(Opcodes.ASM9);
			this.consumer = consumer;
		}

		@Override
		public void visit(String name, Object value) {
			if (("value".equals(name) || name == null) && value instanceof String text) {
				consumer.accept(text);
			}
		}
	}
}
