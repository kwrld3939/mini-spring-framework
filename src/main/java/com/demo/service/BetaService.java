package com.demo.service;

import com.demo.mini.annotation.MyAutowired;
import com.demo.mini.annotation.MyComponent;

@MyComponent
public class BetaService {

	private AlphaService alphaService;

	@MyAutowired
	public void setAlphaService(AlphaService alphaService) {
		this.alphaService = alphaService;
	}

	public String beta() {
		return "beta";
	}

	public String chain() {
		return beta() + " -> " + alphaService.alpha();
	}
}
