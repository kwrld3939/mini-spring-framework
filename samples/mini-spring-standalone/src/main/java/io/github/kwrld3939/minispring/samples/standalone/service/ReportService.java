package io.github.kwrld3939.minispring.samples.standalone.service;

import io.github.kwrld3939.minispring.annotation.MiniComponent;
import io.github.kwrld3939.minispring.samples.standalone.annotation.MiniLog;

@MiniComponent
public class ReportService {

	@MiniLog
	public String buildReport() {
		return "report ready";
	}
}
