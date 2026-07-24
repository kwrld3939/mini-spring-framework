package com.demo.controller;

import com.demo.mini.annotation.MyComponent;
import com.demo.mini.annotation.MyPostConstruct;
import com.demo.mini.annotation.MyRequestMapping;
import com.demo.service.UserService;

@MyComponent
public class UserController {

	private final UserService userService;

	public UserController(UserService userService) {
		this.userService = userService;
	}

	@MyPostConstruct
	public void init() {
		System.out.println("[lifecycle] UserController init");
	}

	@MyRequestMapping(path = "/user/hello")
	public String hello() {
		return userService.hello();
	}
}
