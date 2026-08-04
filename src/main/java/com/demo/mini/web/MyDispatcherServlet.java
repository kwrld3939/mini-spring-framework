package com.demo.mini.web;

import java.io.IOException;
import java.lang.reflect.Method;

import com.demo.mini.context.MyApplicationContext;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

public class MyDispatcherServlet extends HttpServlet {

	public static final String CONTEXT_ATTRIBUTE = MyApplicationContext.class.getName();
	public static final String ROOT_PACKAGE_PARAM = "rootPackage";

	private MyApplicationContext applicationContext;
	private MyHandlerMapping handlerMapping;
	private final RequestArgumentResolver argumentResolver = new RequestArgumentResolver();

	public MyDispatcherServlet() {
	}

	public MyDispatcherServlet(MyApplicationContext applicationContext) {
		this.applicationContext = applicationContext;
	}

	@Override
	public void init(ServletConfig config) throws ServletException {
		super.init(config);
		if (applicationContext == null) {
			Object contextAttribute = config.getServletContext().getAttribute(CONTEXT_ATTRIBUTE);
			if (contextAttribute instanceof MyApplicationContext context) {
				applicationContext = context;
			}
		}
		if (applicationContext == null) {
			String rootPackage = config.getInitParameter(ROOT_PACKAGE_PARAM);
			if (rootPackage != null && !rootPackage.isBlank()) {
				applicationContext = new MyApplicationContext(rootPackage);
			}
		}
		if (applicationContext == null) {
			throw new ServletException("MyApplicationContext is required");
		}
		handlerMapping = new MyHandlerMapping(applicationContext);
	}

	@Override
	protected void service(HttpServletRequest request, HttpServletResponse response)
			throws ServletException, IOException {
		String path = resolvePath(request);
		MyHandlerMapping.HandlerMethod handlerMethod = handlerMapping.getMappings().get(path);
		if (handlerMethod == null) {
			response.sendError(HttpServletResponse.SC_NOT_FOUND, "No handler for " + path);
			return;
		}

		try {
			Object result = invoke(handlerMethod, request, response);
			if (result != null && !response.isCommitted()) {
				response.setCharacterEncoding("UTF-8");
				response.setContentType("text/plain;charset=UTF-8");
				response.getWriter().write(String.valueOf(result));
			}
		} catch (Exception ex) {
			throw new ServletException("Failed to invoke handler for " + path, ex);
		}
	}

	private Object invoke(MyHandlerMapping.HandlerMethod handlerMethod, HttpServletRequest request, HttpServletResponse response)
			throws Exception {
		Method method = handlerMethod.method();
		if (method.getParameterCount() == 0) {
			return method.invoke(handlerMethod.bean());
		}

		Object[] args = argumentResolver.resolve(method, request, response);
		return method.invoke(handlerMethod.bean(), args);
	}

	private String resolvePath(HttpServletRequest request) {
		String path = request.getRequestURI();
		String contextPath = request.getContextPath();
		if (contextPath != null && !contextPath.isBlank() && path.startsWith(contextPath)) {
			path = path.substring(contextPath.length());
		}
		return MyHandlerMapping.normalizePath(path);
	}
}
