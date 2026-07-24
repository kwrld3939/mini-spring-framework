package com.demo.mini.beans;

public interface BeanPostProcessor {
    Object postProcessBeforeInitialization( String beanName,Object bean);
    Object postProcessAfterInitialization(String beanName,Object bean);
}
