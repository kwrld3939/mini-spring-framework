package io.github.kwrld3939.minispring.context.support;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

import io.github.kwrld3939.minispring.annotation.MiniComponent;
import io.github.kwrld3939.minispring.context.testing.StaticInitRegistry;
import io.github.kwrld3939.minispring.env.StandardEnvironment;
import io.github.kwrld3939.minispring.exception.BeansException;

/**
 * ADR-0003 的验证：扫描不加载类、不执行静态初始化块、支持 jar、结果确定性、拒绝嵌套 jar。
 *
 * <p>关键手法：**每个测试用一个全新的 ClassLoader**。JVM 里一个类的静态初始化块只会执行一次，
 * 如果复用同一个加载器，"静态块是否执行过"就无法区分，断言会变成偶然成立。
 */
class ClassPathScannerTests {

	private static final String PROBE_PACKAGE = "io.github.kwrld3939.minispring.context.scanprobe";
	private static final String STATIC_INIT_PROBE = PROBE_PACKAGE + ".StaticInitProbe";
	private static final String COMPONENT_PROBE = PROBE_PACKAGE + ".ComponentStaticInitProbe";

	@BeforeEach
	void resetRegistry() {
		StaticInitRegistry.clear();
	}

	@Test
	void scanFindsCandidatesWithoutInitializingAnything() {
		ClassLoader loader = freshLoader();

		List<String> names = new ClassPathScanner(loader).scan(PROBE_PACKAGE).stream()
				.map(ScannedClass::getClassName)
				.toList();

		assertTrue(names.contains(COMPONENT_PROBE), names.toString());
		assertTrue(names.contains(STATIC_INIT_PROBE), names.toString());
		assertTrue(StaticInitRegistry.initialized().isEmpty(),
				"扫描阶段不得初始化任何用户类，实际初始化了：" + StaticInitRegistry.initialized());
	}

	@Test
	void registrationLoadsComponentsWithoutInitializingThem() {
		ClassLoader loader = freshLoader();
		DefaultListableBeanFactory factory = new DefaultListableBeanFactory(new StandardEnvironment(), loader);

		int registered = new ClassPathBeanDefinitionScanner(factory, loader).scan(PROBE_PACKAGE);

		assertEquals(2, registered, "探针包里有两个组件");
		assertTrue(factory.containsBean("componentStaticInitProbe"));
		assertTrue(factory.containsBean("renamedProbe"), "名称应从字节码里的 @MiniComponent(\"renamedProbe\") 读出");
		assertTrue(factory.getBeanDefinition("renamedProbe").isPrototype(), "作用域应从字节码里的 @MiniScope 读出");
		assertFalse(factory.containsBean("staticInitProbe"), "非组件不应被注册");
		assertTrue(StaticInitRegistry.initialized().isEmpty(),
				"注册定义只是加载类，不应初始化：" + StaticInitRegistry.initialized());

		factory.getBean("componentStaticInitProbe");

		assertTrue(StaticInitRegistry.contains("ComponentStaticInitProbe"), "实例化时才应执行静态初始化块");
	}

	@Test
	void loadsOnlyComponentClasses() {
		FreshClassLoader loader = freshLoader();

		new ClassPathBeanDefinitionScanner(new DefaultListableBeanFactory(new StandardEnvironment(), loader), loader)
				.scan(PROBE_PACKAGE);

		assertTrue(loader.loaded().contains(COMPONENT_PROBE), loader.loaded().toString());
		assertFalse(loader.loaded().contains(STATIC_INIT_PROBE),
				"非组件类不应被加载（这正是用 ASM 读元数据而不是 loadClass 的收益）：" + loader.loaded());
	}

	@Test
	void staticInitDetectorItselfWorks() throws Exception {
		ClassLoader loader = freshLoader();
		new ClassPathScanner(loader).scan(PROBE_PACKAGE);
		assertTrue(StaticInitRegistry.initialized().isEmpty(), StaticInitRegistry.initialized().toString());

		Class.forName(STATIC_INIT_PROBE, true, loader);

		assertTrue(StaticInitRegistry.contains("StaticInitProbe"),
				"对照组：显式初始化必须能被探测到，否则上面几条断言毫无意义");
	}

	@Test
	void scanResultIsSortedAndDeterministic() {
		ClassPathScanner scanner = new ClassPathScanner(getClass().getClassLoader());

		List<String> first = scanner.scan(PROBE_PACKAGE).stream().map(ScannedClass::getClassName).toList();
		List<String> second = scanner.scan(PROBE_PACKAGE).stream().map(ScannedClass::getClassName).toList();

		assertEquals(first, second, "同一 classpath 的两次扫描必须完全一致");
		assertEquals(first.stream().sorted().toList(), first, "结果必须按类名排序（文件系统顺序不能作为依据）");
	}

	@Test
	void scansComponentsInsideAJar() throws Exception {
		Path jar = createJarWithGeneratedComponent();
		try (URLClassLoader loader = new URLClassLoader(new URL[] { jar.toUri().toURL() },
				getClass().getClassLoader())) {
			DefaultListableBeanFactory factory = new DefaultListableBeanFactory(new StandardEnvironment(), loader);

			int registered = new ClassPathBeanDefinitionScanner(factory, loader)
					.scan("io.github.kwrld3939.minispring.context.jaronly");

			assertEquals(1, registered, "jar 内的组件必须能被发现");
			Object bean = factory.getBean("jarOnlyComponent");
			assertEquals("jar-component", bean.getClass().getMethod("ping").invoke(bean));
		} finally {
			Files.deleteIfExists(jar);
		}
	}

	@Test
	void rejectsNestedJarWithActionableMessage() {
		BeansException ex = assertThrows(BeansException.class,
				() -> ClassPathScanner.checkNotNestedJar("jar:file:/app.jar!/BOOT-INF/classes!/com/demo"));

		assertTrue(ex.getMessage().contains("嵌套 jar"), ex.getMessage());
		assertTrue(ex.getMessage().contains("BOOT-INF"), ex.getMessage());
		assertDoesNotThrow(() -> ClassPathScanner.checkNotNestedJar("jar:file:/lib/mini-spring-beans.jar!/io/github"));
	}

	/** 生成一个只存在于 jar 里的组件类（包 {@code ...context.jaronly} 在别处不存在）。 */
	private Path createJarWithGeneratedComponent() throws IOException {
		String internalName = "io/github/kwrld3939/minispring/context/jaronly/JarOnlyComponent";
		ClassWriter writer = new ClassWriter(0);
		writer.visit(Opcodes.V17, Opcodes.ACC_PUBLIC | Opcodes.ACC_SUPER, internalName, null, "java/lang/Object",
				null);

		MethodVisitor constructor = writer.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
		constructor.visitCode();
		constructor.visitVarInsn(Opcodes.ALOAD, 0);
		constructor.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
		constructor.visitInsn(Opcodes.RETURN);
		constructor.visitMaxs(1, 1);
		constructor.visitEnd();

		AnnotationVisitor annotation = writer.visitAnnotation(Type.getDescriptor(MiniComponent.class), true);
		annotation.visitEnd();

		MethodVisitor ping = writer.visitMethod(Opcodes.ACC_PUBLIC, "ping", "()Ljava/lang/String;", null, null);
		ping.visitCode();
		ping.visitLdcInsn("jar-component");
		ping.visitInsn(Opcodes.ARETURN);
		ping.visitMaxs(1, 1);
		ping.visitEnd();

		writer.visitEnd();

		// 与 jar 工具/Maven 生成的真实 jar 一致：目录条目也要写进去。
		// URLClassLoader#getResources("pkg/path") 依赖目录条目才能定位到包，缺少它就会"扫不到"。
		List<String> directoryEntries = new ArrayList<>();
		String directory = internalName;
		while (directory.contains("/")) {
			directory = directory.substring(0, directory.lastIndexOf('/'));
			directoryEntries.add(0, directory + "/");
		}

		Path jar = Files.createTempFile("mini-spring-jar-scan", ".jar");
		try (JarOutputStream outputStream = new JarOutputStream(Files.newOutputStream(jar))) {
			for (String directoryEntry : directoryEntries) {
				outputStream.putNextEntry(new JarEntry(directoryEntry));
				outputStream.closeEntry();
			}
			outputStream.putNextEntry(new JarEntry(internalName + ".class"));
			outputStream.write(writer.toByteArray());
			outputStream.closeEntry();
		}
		return jar;
	}

	private FreshClassLoader freshLoader() {
		return new FreshClassLoader(getClass().getClassLoader(), PROBE_PACKAGE + ".");
	}

	/**
	 * 对探针包"自己定义类"、其余一律委派父加载器的加载器。
	 *
	 * <p>这样探针类在每个测试里都是全新的类，静态块会真的重新执行；
	 * 而 {@link StaticInitRegistry} 由父加载器提供，测试与探针看到的是同一个类。
	 */
	private static final class FreshClassLoader extends ClassLoader {

		private final String localPrefix;
		private final List<String> loaded = new ArrayList<>();

		FreshClassLoader(ClassLoader parent, String localPrefix) {
			super(parent);
			this.localPrefix = localPrefix;
		}

		@Override
		protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
			loaded.add(name);
			synchronized (getClassLoadingLock(name)) {
				Class<?> alreadyLoaded = findLoadedClass(name);
				if (alreadyLoaded != null) {
					return alreadyLoaded;
				}
				if (name.startsWith(localPrefix)) {
					return defineLocally(name, resolve);
				}
			}
			return super.loadClass(name, resolve);
		}

		private Class<?> defineLocally(String name, boolean resolve) throws ClassNotFoundException {
			String resource = name.replace('.', '/') + ".class";
			try (InputStream inputStream = getParent().getResourceAsStream(resource)) {
				if (inputStream == null) {
					throw new ClassNotFoundException(name);
				}
				byte[] bytes = inputStream.readAllBytes();
				Class<?> defined = defineClass(name, bytes, 0, bytes.length);
				if (resolve) {
					resolveClass(defined);
				}
				return defined;
			} catch (IOException ex) {
				throw new ClassNotFoundException(name, ex);
			}
		}

		List<String> loaded() {
			return new ArrayList<>(loaded);
		}
	}
}
