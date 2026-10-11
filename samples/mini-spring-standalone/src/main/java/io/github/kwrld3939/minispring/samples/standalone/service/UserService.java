package io.github.kwrld3939.minispring.samples.standalone.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.github.kwrld3939.minispring.annotation.MiniComponent;
import io.github.kwrld3939.minispring.annotation.MiniPostConstruct;
import io.github.kwrld3939.minispring.annotation.MiniPreDestroy;
import io.github.kwrld3939.minispring.annotation.MiniValue;

@MiniComponent
public class UserService {

	private static final Logger LOGGER = LoggerFactory.getLogger(UserService.class);

	@MiniValue("app.name")
	private String appName;

	@MiniPostConstruct
	public void init() {
		LOGGER.info("UserService 初始化完成, appName={}", appName);
	}

	public String hello() {
		return "hello mini ioc from " + appName;
	}

	@MiniPreDestroy
	public void destroy() {
		LOGGER.info("UserService 已销毁");
	}
}
