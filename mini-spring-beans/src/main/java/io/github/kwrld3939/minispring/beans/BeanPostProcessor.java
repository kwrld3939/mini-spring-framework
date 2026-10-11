package io.github.kwrld3939.minispring.beans;

public interface BeanPostProcessor {
    Object postProcessBeforeInitialization( String beanName,Object bean);
    Object postProcessAfterInitialization(String beanName,Object bean);
}
