package io.github.kwrld3939.minispring.web;

import java.io.IOException;
import java.lang.reflect.Method;

import io.github.kwrld3939.minispring.context.MiniApplicationContext;

import jakarta.servlet.ServletConfig;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

public class MiniDispatcherServlet extends HttpServlet {

	public static final String CONTEXT_ATTRIBUTE = MiniApplicationContext.class.getName();
	public static final String ROOT_PACKAGE_PARAM = "rootPackage";

	private MiniApplicationContext applicationContext;
	private MiniHandlerMapping handlerMapping;
	private final RequestArgumentResolver argumentResolver = new RequestArgumentResolver();

	public MiniDispatcherServlet() {
	}

	public MiniDispatcherServlet(MiniApplicationContext applicationContext) {
		this.applicationContext = applicationContext;
	}

	@Override
	public void init(ServletConfig config) throws ServletException {
		super.init(config);
		if (applicationContext == null) {
			Object contextAttribute = config.getServletContext().getAttribute(CONTEXT_ATTRIBUTE);
			if (contextAttribute instanceof MiniApplicationContext context) {
				applicationContext = context;
			}
		}
		if (applicationContext == null) {
			String rootPackage = config.getInitParameter(ROOT_PACKAGE_PARAM);
			if (rootPackage != null && !rootPackage.isBlank()) {
				applicationContext = new MiniApplicationContext(rootPackage);
			}
		}
		if (applicationContext == null) {
			throw new ServletException("MiniApplicationContext is required");
		}
		handlerMapping = new MiniHandlerMapping(applicationContext);
	}

	@Override
	protected void service(HttpServletRequest request, HttpServletResponse response)
			throws ServletException, IOException {
		String path = resolvePath(request);
		MiniHandlerMapping.HandlerMethod handlerMethod = handlerMapping.getMappings().get(path);
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

	private Object invoke(MiniHandlerMapping.HandlerMethod handlerMethod, HttpServletRequest request, HttpServletResponse response)
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
		return MiniHandlerMapping.normalizePath(path);
	}
}
