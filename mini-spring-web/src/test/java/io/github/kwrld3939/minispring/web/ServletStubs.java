package io.github.kwrld3939.minispring.web;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import jakarta.servlet.ServletConfig;
import jakarta.servlet.ServletContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * 用动态代理伪造 Servlet 接口。
 *
 * <p>刻意不引入 Mockito：这里要验证的是框架与 Servlet 契约的交互，几个明确的桩就够，
 * 而且桩的行为写在代码里，比注解更可读。
 */
final class ServletStubs {

	private ServletStubs() {
	}

	/** 只关心参数与路径的请求桩。 */
	static HttpServletRequest request(Map<String, String> parameters, String contextPath, String requestUri) {
		Map<String, String> safeParameters = parameters == null ? Map.of() : parameters;
		return (HttpServletRequest) Proxy.newProxyInstance(ServletStubs.class.getClassLoader(),
				new Class<?>[] { HttpServletRequest.class }, (proxy, method, args) -> {
					switch (method.getName()) {
						case "getParameter":
							return safeParameters.get(String.valueOf(args[0]));
						case "getContextPath":
							return contextPath;
						case "getRequestURI":
							return requestUri;
						case "getMethod":
							return "GET";
						default:
							return defaultValue(method);
					}
				});
	}

	static HttpServletRequest request(Map<String, String> parameters) {
		return request(parameters, "", "/user/hello");
	}

	static HttpServletResponse response(ResponseRecorder recorder) {
		return (HttpServletResponse) Proxy.newProxyInstance(ServletStubs.class.getClassLoader(),
				new Class<?>[] { HttpServletResponse.class }, (proxy, method, args) -> {
					switch (method.getName()) {
						case "setCharacterEncoding":
						case "setContentType":
							recorder.headers.add(method.getName() + "=" + args[0]);
							return null;
						case "getWriter":
							return recorder.writer;
						case "sendError":
							recorder.errorCode = (Integer) args[0];
							recorder.errorMessage = args.length > 1 ? String.valueOf(args[1]) : null;
							return null;
						case "isCommitted":
							return recorder.committed;
						default:
							return defaultValue(method);
					}
				});
	}

	static ServletConfig servletConfig(ServletContext servletContext, Map<String, String> initParameters) {
		return (ServletConfig) Proxy.newProxyInstance(ServletStubs.class.getClassLoader(),
				new Class<?>[] { ServletConfig.class }, (proxy, method, args) -> {
					switch (method.getName()) {
						case "getServletContext":
							return servletContext;
						case "getInitParameter":
							return initParameters.get(String.valueOf(args[0]));
						case "getServletName":
							return "miniDispatcherServlet";
						default:
							return defaultValue(method);
					}
				});
	}

	static ServletContext servletContext(Map<String, Object> attributes) {
		Map<String, Object> safeAttributes = attributes == null ? Map.of() : attributes;
		return (ServletContext) Proxy.newProxyInstance(ServletStubs.class.getClassLoader(),
				new Class<?>[] { ServletContext.class }, (proxy, method, args) -> {
					if ("getAttribute".equals(method.getName())) {
						return safeAttributes.get(String.valueOf(args[0]));
					}
					return defaultValue(method);
				});
	}

	static Map<String, String> parameters(String... keyValues) {
		Map<String, String> parameters = new HashMap<>();
		for (int i = 0; i + 1 < keyValues.length; i += 2) {
			parameters.put(keyValues[i], keyValues[i + 1]);
		}
		return parameters;
	}

	/** 基本类型方法必须返回对应的零值，否则代理会因为 null 拆箱而抛 NPE。 */
	private static Object defaultValue(Method method) {
		Class<?> returnType = method.getReturnType();
		if (!returnType.isPrimitive() || returnType == void.class) {
			return null;
		}
		if (returnType == boolean.class) {
			return false;
		}
		if (returnType == int.class) {
			return 0;
		}
		if (returnType == long.class) {
			return 0L;
		}
		if (returnType == double.class) {
			return 0d;
		}
		if (returnType == float.class) {
			return 0f;
		}
		if (returnType == short.class) {
			return (short) 0;
		}
		if (returnType == byte.class) {
			return (byte) 0;
		}
		return (char) 0;
	}

	/** 记录响应侧发生的事，供断言使用。 */
	static final class ResponseRecorder {

		final List<String> headers = new ArrayList<>();
		final StringWriter body = new StringWriter();
		final PrintWriter writer = new PrintWriter(body);
		int errorCode;
		String errorMessage;
		boolean committed;

		String bodyText() {
			writer.flush();
			return body.toString();
		}
	}
}
