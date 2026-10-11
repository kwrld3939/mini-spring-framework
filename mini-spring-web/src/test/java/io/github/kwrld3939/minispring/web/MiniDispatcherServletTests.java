package io.github.kwrld3939.minispring.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;

import org.junit.jupiter.api.Test;

import io.github.kwrld3939.minispring.context.MiniApplicationContext;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletResponse;

/**
 * DispatcherServlet 的装配与请求处理契约。
 *
 * <p>三种拿到容器的方式（构造器注入 / ServletContext 属性 / `rootPackage` 初始化参数）都要可用，
 * 一个都拿不到时必须明确报错——静默启动一个"什么都不处理"的 Servlet 是最糟的结果。
 */
class MiniDispatcherServletTests {

	private static final String FIXTURE_PACKAGE = "io.github.kwrld3939.minispring.web.fixture";

	@Test
	void rejectsInitWithoutAnyContext() {
		MiniDispatcherServlet servlet = new MiniDispatcherServlet();

		ServletException ex = assertThrows(ServletException.class, () -> servlet.init(
				ServletStubs.servletConfig(ServletStubs.servletContext(Map.of()), Map.of())));

		assertTrue(ex.getMessage().contains("MiniApplicationContext is required"), ex.getMessage());
	}

	@Test
	void takesContextFromServletContextAttribute() throws Exception {
		try (MiniApplicationContext context = new MiniApplicationContext(FIXTURE_PACKAGE)) {
			MiniDispatcherServlet servlet = new MiniDispatcherServlet();
			servlet.init(ServletStubs.servletConfig(
					ServletStubs.servletContext(Map.of(MiniDispatcherServlet.CONTEXT_ATTRIBUTE, context)), Map.of()));

			assertEquals("hello", serviceAndReadBody(servlet, "/user/hello", ""));
		}
	}

	@Test
	void createsContextFromRootPackageInitParameter() throws Exception {
		MiniDispatcherServlet servlet = new MiniDispatcherServlet();
		servlet.init(ServletStubs.servletConfig(ServletStubs.servletContext(Map.of()),
				Map.of(MiniDispatcherServlet.ROOT_PACKAGE_PARAM, FIXTURE_PACKAGE)));

		assertEquals("hello", serviceAndReadBody(servlet, "/user/hello", ""));
	}

	@Test
	void usesConstructorInjectedContext() throws Exception {
		try (MiniApplicationContext context = new MiniApplicationContext(FIXTURE_PACKAGE)) {
			MiniDispatcherServlet servlet = new MiniDispatcherServlet(context);
			servlet.init(ServletStubs.servletConfig(ServletStubs.servletContext(Map.of()), Map.of()));

			assertEquals("hello", serviceAndReadBody(servlet, "/user/hello", ""));
		}
	}

	@Test
	void sendsNotFoundWhenNoHandlerMatches() throws Exception {
		try (MiniApplicationContext context = new MiniApplicationContext(FIXTURE_PACKAGE)) {
			MiniDispatcherServlet servlet = initServlet(context);
			ServletStubs.ResponseRecorder recorder = new ServletStubs.ResponseRecorder();

			servlet.service(ServletStubs.request(Map.of(), "", "/nowhere"), ServletStubs.response(recorder));

			assertEquals(HttpServletResponse.SC_NOT_FOUND, recorder.errorCode);
			assertTrue(recorder.errorMessage.contains("/nowhere"), recorder.errorMessage);
		}
	}

	@Test
	void stripsContextPathAndWritesUtf8Body() throws Exception {
		try (MiniApplicationContext context = new MiniApplicationContext(FIXTURE_PACKAGE)) {
			MiniDispatcherServlet servlet = initServlet(context);
			ServletStubs.ResponseRecorder recorder = new ServletStubs.ResponseRecorder();

			servlet.service(ServletStubs.request(ServletStubs.parameters("name", "Alice", "count", "2"),
					"/app", "/app/user/inspect"), ServletStubs.response(recorder));

			assertEquals("Alice:2", recorder.bodyText());
			assertTrue(recorder.headers.contains("setCharacterEncoding=UTF-8"), recorder.headers.toString());
			assertTrue(recorder.headers.contains("setContentType=text/plain;charset=UTF-8"),
					recorder.headers.toString());
		}
	}

	@Test
	void doesNotWriteWhenResponseIsAlreadyCommitted() throws Exception {
		try (MiniApplicationContext context = new MiniApplicationContext(FIXTURE_PACKAGE)) {
			MiniDispatcherServlet servlet = initServlet(context);
			ServletStubs.ResponseRecorder recorder = new ServletStubs.ResponseRecorder();
			recorder.committed = true;

			servlet.service(ServletStubs.request(Map.of(), "", "/user/hello"), ServletStubs.response(recorder));

			assertEquals("", recorder.bodyText(), "响应已提交时不得再写内容");
		}
	}

	@Test
	void wrapsHandlerFailureInServletException() throws Exception {
		try (MiniApplicationContext context = new MiniApplicationContext(FIXTURE_PACKAGE)) {
			MiniDispatcherServlet servlet = initServlet(context);

			ServletException ex = assertThrows(ServletException.class, () -> servlet.service(
					ServletStubs.request(Map.of(), "", "/boom"), ServletStubs.response(new ServletStubs.ResponseRecorder())));

			assertTrue(ex.getMessage().contains("Failed to invoke handler for /boom"), ex.getMessage());
		}
	}

	private MiniDispatcherServlet initServlet(MiniApplicationContext context) throws ServletException {
		MiniDispatcherServlet servlet = new MiniDispatcherServlet(context);
		servlet.init(ServletStubs.servletConfig(ServletStubs.servletContext(Map.of()), Map.of()));
		return servlet;
	}

	private String serviceAndReadBody(MiniDispatcherServlet servlet, String requestUri, String contextPath)
			throws Exception {
		ServletStubs.ResponseRecorder recorder = new ServletStubs.ResponseRecorder();
		servlet.service(ServletStubs.request(Map.of(), contextPath, requestUri), ServletStubs.response(recorder));
		return recorder.bodyText();
	}
}
