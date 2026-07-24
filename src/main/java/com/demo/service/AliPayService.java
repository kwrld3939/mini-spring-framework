package com.demo.service;

import com.demo.mini.annotation.MyComponent;
import com.demo.mini.annotation.MyLog;


@MyComponent
public class AliPayService implements PayService {

    @Override
    @MyLog
    public String pay() {
        return "ali pay";
    }
    
}
