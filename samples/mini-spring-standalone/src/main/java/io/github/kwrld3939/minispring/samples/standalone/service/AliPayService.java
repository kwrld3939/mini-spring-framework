package io.github.kwrld3939.minispring.samples.standalone.service;

import io.github.kwrld3939.minispring.annotation.MiniComponent;
import io.github.kwrld3939.minispring.samples.standalone.annotation.MiniLog;


@MiniComponent
public class AliPayService implements PayService {

    @Override
    @MiniLog
    public String pay() {
        return "ali pay";
    }
    
}
