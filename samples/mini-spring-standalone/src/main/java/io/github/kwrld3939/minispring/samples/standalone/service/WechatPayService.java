package io.github.kwrld3939.minispring.samples.standalone.service;

import io.github.kwrld3939.minispring.annotation.MiniComponent;


@MiniComponent
public class WechatPayService implements PayService {

    @Override
    public String pay() {
        return "wechat pay";
    }

}
