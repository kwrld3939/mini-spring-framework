package io.github.kwrld3939.minispring.samples.standalone.service;

import io.github.kwrld3939.minispring.annotation.MiniAutowired;
import io.github.kwrld3939.minispring.annotation.MiniComponent;

@MiniComponent
public class BetaService {

	private AlphaService alphaService;

	@MiniAutowired
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
