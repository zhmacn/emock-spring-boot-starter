package com.mzh.emock.spring.boot.starter.processor;

import com.mzh.emock.core.context.EMContext;
import com.mzh.emock.spring.boot.starter.context.EMSpringArgContext;
import org.springframework.context.support.AbstractApplicationContext;
import org.springframework.core.io.ResourceLoader;

public abstract class EMAbstractProcessor {
    protected final EMSpringArgContext context;
    protected final ResourceLoader resourceLoader;
    protected EMAbstractProcessor(EMSpringArgContext context,ResourceLoader resourceLoader){
        this.context=context;
        this.resourceLoader=resourceLoader;
    }
}
