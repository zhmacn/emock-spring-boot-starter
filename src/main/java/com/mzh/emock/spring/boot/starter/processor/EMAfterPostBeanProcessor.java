package com.mzh.emock.spring.boot.starter.processor;

import com.mzh.emock.core.log.Logger;
import com.mzh.emock.spring.boot.starter.context.EMSpringArgContext;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.support.AbstractApplicationContext;
import org.springframework.core.io.ResourceLoader;

import java.util.Set;

public class EMAfterPostBeanProcessor extends EMAbstractProcessor implements BeanPostProcessor {
    private final Logger logger=Logger.get(EMAfterPostBeanProcessor.class);

    public EMAfterPostBeanProcessor(AbstractApplicationContext context, ResourceLoader resourceLoader){
        super(context,resourceLoader);
        logger.info("Effective Processor: EMAfterPostBeanProcessor,context:"+context.toString()+",resourceLoader:"+resourceLoader.toString());
        initMockResources(context,resourceLoader);
    }

    private void initMockResources(AbstractApplicationContext context,ResourceLoader loader){
        try{
            EMSpringArgContext.preparePublicContext(context);
            EMSpringArgContext.getCurrContext().loadDefinition(loader);
            EMSpringArgContext.getCurrContext().createWrapper();
        }catch (Exception ex){
            ex.printStackTrace();
        }
    }


    @Override
    public Object postProcessBeforeInitialization(Object bean, String beanName) throws BeansException {
        return BeanPostProcessor.super.postProcessBeforeInitialization(bean, beanName);
    }

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
        EMSpringArgContext.getCurrContext().createEMBeanIfNecessary(bean);
        if(EMSpringArgContext.getCurrContext().getObjectGroup(bean)!=null){
            Set<Class<? super Object>> tClz= EMSpringArgContext.getCurrContext().getObjectGroup(bean).getEmMap().keySet();
            Class<?> t1=null;
            for(Class<?> tt: tClz){
                if(t1==null)
                    t1=tt;
                if(t1.isAssignableFrom(tt)){
                    t1=tt;
                }
            }
           return EMSpringArgContext.getCurrContext().getObjectGroup(bean).getProxyHolderMap().get(t1).getProxy();
        }
        return bean;
    }


}
