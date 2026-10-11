package io.github.kwrld3939.minispring.context.support;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

import io.github.kwrld3939.minispring.annotation.MiniComponent;
import io.github.kwrld3939.minispring.env.StandardEnvironment;
import io.github.kwrld3939.minispring.exception.BeanDefinitionException;
import io.github.kwrld3939.minispring.exception.BeansException;

/**
 * 扫描与注册阶段的失败语义。
 *
 * <p>"扫不到"和"加载不了"是最容易退化成静默漏扫的两类情况（旧实现就直接 `continue` 掉），
 * 所以每条都必须给出可执行的错误信息，这里逐条固化。
 */
class ClassPathScanningFailureTests {

	private static final String INTERFACE_PACKAGE = "io.github.kwrld3939.minispring.context.interfaces";

	@Test
	void rejectsComponentAnnotatedInterfaceWithActionableMessage() {
		DefaultListableBeanFactory factory = new DefaultListableBeanFactory(new StandardEnvironment(),
				getClass().getClassLoader());

		BeanDefinitionException ex = assertThrows(BeanDefinitionException.class,
				() -> new ClassPathBeanDefinitionScanner(factory, getClass().getClassLoader()).scan(INTERFACE_PACKAGE));

		assertTrue(ex.getMessage().contains("接口不能作为组件"), ex.getMessage());
		assertTrue(ex.getMessage().contains("建议"), ex.getMessage());
	}

	@Test
	void rejectsCorruptClassFileWithActionableMessage() throws IOException {
		Path root = Files.createTempDirectory("mini-spring-corrupt-class");
		Path packageDirectory = Files.createDirectories(root.resolve("broken/pkg"));
		Files.write(packageDirectory.resolve("Broken.class"), new byte[] { 1, 2, 3, 4, 5, 6, 7, 8 });
		try (URLClassLoader loader = new URLClassLoader(new URL[] { root.toUri().toURL() },
				getClass().getClassLoader())) {
			BeansException ex = assertThrows(BeansException.class, () -> new ClassPathScanner(loader).scan("broken.pkg"));

			assertTrue(ex.getMessage().contains("class 文件"), ex.getMessage());
		} finally {
			deleteRecursively(root);
		}
	}

	@Test
	void rejectsComponentWhoseClassCannotBeLoaded() throws IOException {
		String internalName = "broken/dep/MissingDependencyComponent";
		Path root = Files.createTempDirectory("mini-spring-missing-dependency");
		Path classFile = root.resolve(internalName + ".class");
		Files.createDirectories(classFile.getParent());
		Files.write(classFile, generateComponentExtendingMissingClass(internalName));
		try (URLClassLoader loader = new URLClassLoader(new URL[] { root.toUri().toURL() },
				getClass().getClassLoader())) {
			DefaultListableBeanFactory factory = new DefaultListableBeanFactory(new StandardEnvironment(), loader);

			BeansException ex = assertThrows(BeansException.class,
					() -> new ClassPathBeanDefinitionScanner(factory, loader).scan("broken.dep"));

			assertTrue(ex.getMessage().contains("无法加载"), ex.getMessage());
			assertTrue(ex.getMessage().contains("建议"), ex.getMessage());
		} finally {
			deleteRecursively(root);
		}
	}

	/** 生成一个继承了不存在父类的组件：扫描能读到注解，加载时会失败。 */
	private byte[] generateComponentExtendingMissingClass(String internalName) {
		ClassWriter writer = new ClassWriter(0);
		writer.visit(Opcodes.V17, Opcodes.ACC_PUBLIC | Opcodes.ACC_SUPER, internalName, null, "does/not/Exist", null);

		MethodVisitor constructor = writer.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
		constructor.visitCode();
		constructor.visitVarInsn(Opcodes.ALOAD, 0);
		constructor.visitMethodInsn(Opcodes.INVOKESPECIAL, "does/not/Exist", "<init>", "()V", false);
		constructor.visitInsn(Opcodes.RETURN);
		constructor.visitMaxs(1, 1);
		constructor.visitEnd();

		AnnotationVisitor annotation = writer.visitAnnotation(Type.getDescriptor(MiniComponent.class), true);
		annotation.visitEnd();
		writer.visitEnd();
		return writer.toByteArray();
	}

	private void deleteRecursively(Path root) throws IOException {
		try (Stream<Path> paths = Files.walk(root)) {
			for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
				Files.deleteIfExists(path);
			}
		}
	}
}
