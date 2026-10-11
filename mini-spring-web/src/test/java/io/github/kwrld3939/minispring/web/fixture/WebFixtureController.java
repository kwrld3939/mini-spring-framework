package io.github.kwrld3939.minispring.web.fixture;

import io.github.kwrld3939.minispring.annotation.MiniComponent;
import io.github.kwrld3939.minispring.web.annotation.MiniRequestMapping;
import io.github.kwrld3939.minispring.web.annotation.MiniRequestParam;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * web 模块测试用的控制器夹具（独立成包，避免影响其他模块的扫描测试）。
 *
 * <p>覆盖四种处理器形态：零参数、请求/响应注入 + 参数绑定、根路径、抛异常。
 */
@MiniComponent
public class WebFixtureController {

	@MiniRequestMapping(path = "/user/hello")
	public String hello() {
		return "hello";
	}

	@MiniRequestMapping(path = "/user/inspect")
	public String inspect(HttpServletRequest request, HttpServletResponse response,
			@MiniRequestParam("name") String name,
			@MiniRequestParam("count") int count) {
		return name + ":" + count;
	}

	@MiniRequestMapping(path = "")
	public String root() {
		return "root";
	}

	@MiniRequestMapping(path = "/boom")
	public String boom() {
		throw new IllegalStateException("处理器故意失败");
	}
}
