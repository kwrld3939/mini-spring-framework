package io.github.kwrld3939.minispring.samples.standalone.controller;

import io.github.kwrld3939.minispring.annotation.MiniComponent;
import io.github.kwrld3939.minispring.annotation.MiniQualifier;
import io.github.kwrld3939.minispring.web.annotation.MiniRequestMapping;
import io.github.kwrld3939.minispring.samples.standalone.service.PayService;

@MiniComponent
public class PayController {
    private final PayService payService;

    public PayController(@MiniQualifier("aliPayService") PayService payService) {
        this.payService = payService;
    }

    @MiniRequestMapping(path = "/pay")
    public String pay() {
        return payService.pay();
    }

    /**
     * 暴露构造器注入得到的依赖，用于验证 ADR-0001 的不变式：
     * 注入点拿到的必须与容器里的终态对象是同一个（这里即 AOP 代理）。
     */
    public PayService payService() {
        return payService;
    }

}
