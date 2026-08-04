package com.demo.service;

import com.demo.mini.annotation.MyComponent;
import com.demo.mini.annotation.MyLog;

@MyComponent
public class ReportService {

	@MyLog
	public String buildReport() {
		return "report ready";
	}
}
