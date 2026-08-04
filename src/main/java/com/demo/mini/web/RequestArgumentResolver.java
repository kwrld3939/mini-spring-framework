package com.demo.mini.web;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;

import com.demo.mini.annotation.MyRequestParam;
import com.demo.mini.util.SimpleTypeConverter;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

public class RequestArgumentResolver {

	public Object[] resolve(Method method, HttpServletRequest request, HttpServletResponse response) {
		Parameter[] parameters = method.getParameters();
		Object[] args = new Object[parameters.length];

		for (int i = 0; i < parameters.length; i++) {
			Class<?> parameterType = parameters[i].getType();
			if (HttpServletRequest.class.isAssignableFrom(parameterType)) {
				args[i] = request;
				continue;
			}
			if (HttpServletResponse.class.isAssignableFrom(parameterType)) {
				args[i] = response;
				continue;
			}

			String parameterName = resolveParameterName(parameters[i]);
			if (parameterName == null) {
				throw new IllegalStateException("Missing @MyRequestParam for method parameter: " + parameters[i]);
			}

			String rawValue = request.getParameter(parameterName);
			if (rawValue == null) {
				if (parameterType.isPrimitive()) {
					throw new IllegalArgumentException("Missing required request parameter: " + parameterName);
				}
				args[i] = null;
				continue;
			}
			args[i] = SimpleTypeConverter.convert(rawValue, parameterType);
		}

		return args;
	}

	private String resolveParameterName(Parameter parameter) {
		MyRequestParam requestParam = parameter.getAnnotation(MyRequestParam.class);
		if (requestParam != null) {
			return requestParam.value();
		}
		if (parameter.isNamePresent()) {
			return parameter.getName();
		}
		return null;
	}
}
