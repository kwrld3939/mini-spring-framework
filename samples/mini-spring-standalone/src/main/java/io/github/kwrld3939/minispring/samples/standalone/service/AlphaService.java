package io.github.kwrld3939.minispring.samples.standalone.service;

import io.github.kwrld3939.minispring.annotation.MiniAutowired;
import io.github.kwrld3939.minispring.annotation.MiniComponent;

@MiniComponent
public class AlphaService {

	private BetaService betaService;

	@MiniAutowired
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
