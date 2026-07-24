package com.demo.service;

import com.demo.mini.annotation.MyComponent;


@MyComponent
public class WechatPayService implements PayService {

    @Override
    public String pay() {
        return "wechat pay";
    }

}
