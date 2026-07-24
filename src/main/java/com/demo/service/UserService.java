package com.demo.service;

import com.demo.mini.annotation.MyComponent;
import com.demo.mini.annotation.MyPostConstruct;
import com.demo.mini.annotation.MyPreDestroy;
import com.demo.mini.annotation.MyValue;

@MyComponent
public class UserService {

	@MyValue("app.name")
	private String appName;

	@MyPostConstruct
	public void init() {
		System.out.println("[lifecycle] UserService init, appName=" + appName);
	}

	public String hello() {
		return "hello mini ioc from " + appName;
	}

	@MyPreDestroy
	public void destroy() {
		System.out.println("[lifecycle] UserService destroy");
	}
}
