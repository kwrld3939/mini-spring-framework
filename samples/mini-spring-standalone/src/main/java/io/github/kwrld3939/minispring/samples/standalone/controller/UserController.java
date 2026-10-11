package io.github.kwrld3939.minispring.samples.standalone.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.github.kwrld3939.minispring.annotation.MiniComponent;
import io.github.kwrld3939.minispring.annotation.MiniPostConstruct;
import io.github.kwrld3939.minispring.web.annotation.MiniRequestMapping;
import io.github.kwrld3939.minispring.web.annotation.MiniRequestParam;
import io.github.kwrld3939.minispring.samples.standalone.service.UserService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@MiniComponent
public class UserController {

	private static final Logger LOGGER = LoggerFactory.getLogger(UserController.class);

	private final UserService userService;

	public UserController(UserService userService) {
		this.userService = userService;
	}

	@MiniPostConstruct
	public void init() {
		LOGGER.info("UserController 初始化完成");
	}

	@MiniRequestMapping(path = "/user/hello")
	public String hello() {
		return userService.hello();
	}

	@MiniRequestMapping(path = "/user/inspect")
	public String inspect(HttpServletRequest request, HttpServletResponse response,
			@MiniRequestParam("name") String name,
			@MiniRequestParam("count") int count) {
		return userService.hello() + " | " + name + ":" + count;
	}
}
