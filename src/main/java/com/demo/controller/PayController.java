package com.demo.controller;

import com.demo.mini.annotation.MyComponent;
import com.demo.mini.annotation.MyQualifier;
import com.demo.mini.annotation.MyRequestMapping;
import com.demo.service.PayService;

@MyComponent
public class PayController {
    private final PayService payService;

    public PayController(@MyQualifier("aliPayService") PayService payService) {
        this.payService = payService;
    }

    @MyRequestMapping(path = "/pay")
    public String pay() {
        return payService.pay();
    }

}
