package io.github.kwrld3939.minispring.arch;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;

/**
 * 架构守卫：把 ADR-0004 的依赖方向写成断言。
 *
 * <p>放在 `mini-spring-web` 模块是有原因的——它的测试类路径同时包含 `beans`、`context`、`web`
 * 三个库模块的全部类，因此在一个测试里就能断言三者的方向关系。放在 `context` 里则看不到 `web`。
 *
 * <p>与 `maven-enforcer-plugin` 的"禁止依赖"互补：enforcer 管的是 Maven 依赖坐标，
 * 这里管的是**字节码层面实际引用**——即使有人绕过坐标（例如把 jar 塞进来）也会被这里拦住。
 */
class ArchitectureTests {

	private static final String BEANS_LAYER = "..minispring.beans..";
	private static final String ANNOTATION_LAYER = "..minispring.annotation..";
	private static final String UTIL_LAYER = "..minispring.util..";
	private static final String EXCEPTION_LAYER = "..minispring.exception..";
	private static final String CONTEXT_LAYER = "..minispring.context..";
	private static final String ENV_LAYER = "..minispring.env..";
	private static final String WEB_LAYER = "..minispring.web..";

	private static JavaClasses libraryClasses;

	@BeforeAll
	static void importLibraryClasses() {
		libraryClasses = new ClassFileImporter()
				.withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
				.importPackages("io.github.kwrld3939.minispring");
	}

	@Test
	void beansLayerDoesNotDependOnHigherLayers() {
		noClasses().that().resideInAnyPackage(BEANS_LAYER, ANNOTATION_LAYER, UTIL_LAYER, EXCEPTION_LAYER)
				.should().dependOnClassesThat().resideInAnyPackage(CONTEXT_LAYER, ENV_LAYER, WEB_LAYER)
				.because("beans 是最底层：它必须能被单独依赖，见 docs/adr/0004-module-layout-and-coordinates.md")
				.check(libraryClasses);
	}

	@Test
	void contextLayerDoesNotDependOnWebOrServletApi() {
		noClasses().that().resideInAnyPackage(CONTEXT_LAYER, ENV_LAYER)
				.should().dependOnClassesThat().resideInAnyPackage(WEB_LAYER, "jakarta.servlet..")
				.because("只用 IoC/AOP 的使用者不应被迫带上 servlet 依赖（ADR-0004 / ADR-0007）")
				.check(libraryClasses);
	}

	@Test
	void libraryDoesNotDependOnSpringOrJackson() {
		noClasses().should().dependOnClassesThat().resideInAnyPackage("org.springframework..", "com.fasterxml.jackson..")
				.because("库必须自足：Spring 与 Jackson 只允许出现在样例里（ADR-0004）")
				.check(libraryClasses);
	}

	@Test
	void libraryDoesNotDependOnJavaxServlet() {
		noClasses().should().dependOnClassesThat().resideInAnyPackage("javax.servlet..")
				.because("Boot 4 / Tomcat 11 只认 jakarta.servlet，两者无法共存（ADR-0007）")
				.check(libraryClasses);
	}

	// ------------------------------------------------------------------
	// 对照组：证明上面的规则不是"空过"
	// ------------------------------------------------------------------

	@Test
	void importedClassSetIsNotEmpty() {
		assertTrue(libraryClasses.size() > 20,
				"导入的类太少，规则可能因为集合为空而'全部通过'，实际导入：" + libraryClasses.size());
	}

	/**
	 * 反向对照：`mini-spring-web` 是 Servlet 适配层，它**确实**依赖 {@code jakarta.servlet}，
	 * 因此这条"禁止依赖 servlet"的规则必须报错。
	 *
	 * <p>没有这条断言的话，一旦 ClassFileImporter 导入了空集合（包名写错、类路径变化等），
	 * 上面四条规则会全部"通过"，而实际上什么都没检查。
	 */
	@Test
	void archUnitActuallyDetectsViolationsInThisClassSet() {
		ArchRule webMustNotUseServlet = noClasses().that().resideInAnyPackage(WEB_LAYER)
				.should().dependOnClassesThat().resideInAnyPackage("jakarta.servlet..");

		AssertionError error = assertThrows(AssertionError.class, () -> webMustNotUseServlet.check(libraryClasses));

		assertTrue(error.getMessage().contains("jakarta.servlet"), error.getMessage());
	}
}
