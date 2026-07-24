package com.example.demo;

import java.time.Instant;
import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HelloController {

	@GetMapping("/")
	public Map<String, Object> hello() {
		return Map.of(
				"message", "Spring Boot is running in VS Code",
				"time", Instant.now().toString());
	}
}
