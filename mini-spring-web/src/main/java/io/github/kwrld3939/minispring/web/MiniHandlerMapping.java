package io.github.kwrld3939.minispring.web;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import io.github.kwrld3939.minispring.web.annotation.MiniRequestMapping;
import io.github.kwrld3939.minispring.context.MiniApplicationContext;

public class MiniHandlerMapping {

	private final Map<String, HandlerMethod> mappings = new LinkedHashMap<>();

	public MiniHandlerMapping(MiniApplicationContext applicationContext) {
		for (Object bean : applicationContext.getBeans().values()) {
			for (Method method : bean.getClass().getDeclaredMethods()) {
				MiniRequestMapping mapping = method.getAnnotation(MiniRequestMapping.class);
				if (mapping == null) {
					continue;
				}
				String path = normalizePath(mapping.path());
				if (mappings.containsKey(path)) {
					throw new IllegalStateException("Duplicate request mapping: " + path);
				}
				method.setAccessible(true);
				mappings.put(path, new HandlerMethod(bean, method));
			}
		}
	}

	public Map<String, HandlerMethod> getMappings() {
		return Collections.unmodifiableMap(mappings);
	}

	public Object invoke(String path) {
		HandlerMethod handlerMethod = mappings.get(normalizePath(path));
		if (handlerMethod == null) {
			throw new IllegalArgumentException("No handler for " + path);
		}
		return handlerMethod.invoke();
	}

	public static String normalizePath(String path) {
		if (path == null || path.isBlank()) {
			return "/";
		}
		String normalized = path.startsWith("/") ? path : "/" + path;
		if (normalized.length() > 1 && normalized.endsWith("/")) {
			normalized = normalized.substring(0, normalized.length() - 1);
		}
		return normalized;
	}

	public record HandlerMethod(Object bean, Method method) {

		public Object invoke(Object... args) {
			try {
				return method.invoke(bean, args);
			} catch (Exception ex) {
				throw new IllegalStateException("Failed to invoke handler: " + method, ex);
			}
		}

		public String describe() {
			return bean.getClass().getName() + "#" + method.getName();
		}
	}
}
