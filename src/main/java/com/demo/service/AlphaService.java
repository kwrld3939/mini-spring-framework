package com.demo.service;

import com.demo.mini.annotation.MyAutowired;
import com.demo.mini.annotation.MyComponent;

@MyComponent
public class AlphaService {

	private BetaService betaService;

	@MyAutowired
	public void setBetaService(BetaService betaService) {
		this.betaService = betaService;
	}

	public String alpha() {
		return "alpha";
	}

	public String chain() {
		return alpha() + " -> " + betaService.beta();
	}
}
