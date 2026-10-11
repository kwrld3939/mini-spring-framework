package io.github.kwrld3939.minispring.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;
import java.util.Map;

import org.junit.jupiter.api.Test;

import io.github.kwrld3939.minispring.web.annotation.MiniRequestParam;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * 请求参数解析的契约。
 *
 * <p>注意当前的两条"硬失败"语义：必填参数缺失与类型转换失败都抛运行时异常，最终会变成 500。
 * 按 ADR-0007 它们应该是 400——这是阶段 2 的待办，这里先把**现状**固定下来，
 * 避免以后改动时没人知道原本是什么行为。
 */
class RequestArgumentResolverTests {

	private final RequestArgumentResolver resolver = new RequestArgumentResolver();

	@Test
	void injectsRequestResponseAndConvertsRequestParameters() throws Exception {
		Method method = Handler.class.getMethod("inspect", HttpServletRequest.class, HttpServletResponse.class,
				String.class, int.class);
		HttpServletRequest request = ServletStubs.request(ServletStubs.parameters("name", "Alice", "count", "3"));
		HttpServletResponse response = ServletStubs.response(new ServletStubs.ResponseRecorder());

		Object[] args = resolver.resolve(method, request, response);

		assertEquals(4, args.length);
		assertSame(request, args[0]);
		assertSame(response, args[1]);
		assertEquals("Alice", args[2]);
		assertEquals(3, args[3]);
	}

	@Test
	void fallsBackToParameterNameWhenAnnotationIsAbsent() throws Exception {
		Method method = Handler.class.getMethod("withoutAnnotation", String.class);
		HttpServletRequest request = ServletStubs.request(ServletStubs.parameters("name", "Bob"));

		Object[] args = resolver.resolve(method, request, ServletStubs.response(new ServletStubs.ResponseRecorder()));

		assertEquals("Bob", args[0], "编译保留了参数名（-parameters），因此可以退化为按参数名绑定");
	}

	@Test
	void bindsNullForMissingNonPrimitiveParameter() throws Exception {
		Method method = Handler.class.getMethod("optional", String.class);

		Object[] args = resolver.resolve(method, ServletStubs.request(Map.of()),
				ServletStubs.response(new ServletStubs.ResponseRecorder()));

		assertNull(args[0]);
	}

	@Test
	void rejectsMissingPrimitiveParameter() throws Exception {
		Method method = Handler.class.getMethod("requiredPrimitive", int.class);

		IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
				() -> resolver.resolve(method, ServletStubs.request(Map.of()),
						ServletStubs.response(new ServletStubs.ResponseRecorder())));

		assertTrue(ex.getMessage().contains("Missing required request parameter"), ex.getMessage());
		assertTrue(ex.getMessage().contains("count"), ex.getMessage());
	}

	@Test
	void propagatesTypeConversionFailure() throws Exception {
		Method method = Handler.class.getMethod("requiredPrimitive", int.class);
		HttpServletRequest request = ServletStubs.request(ServletStubs.parameters("count", "abc"));

		NumberFormatException ex = assertThrows(NumberFormatException.class,
				() -> resolver.resolve(method, request, ServletStubs.response(new ServletStubs.ResponseRecorder())));

		assertTrue(ex.getMessage().contains("abc"), ex.getMessage());
	}

	/** 处理器形态夹具：方法签名决定解析行为。 */
	public static class Handler {

		public String inspect(HttpServletRequest request, HttpServletResponse response,
				@MiniRequestParam("name") String name, @MiniRequestParam("count") int count) {
			return name + ":" + count;
		}

		public String withoutAnnotation(String name) {
			return name;
		}

		public String optional(@MiniRequestParam("name") String name) {
			return name;
		}

		public String requiredPrimitive(@MiniRequestParam("count") int count) {
			return String.valueOf(count);
		}
	}
}
