package com.mzh.emock.spring.boot.starter.processor;

import com.mzh.emock.core.log.Logger;
import com.mzh.emock.core.util.EMClassUtil;
import com.mzh.emock.spring.boot.starter.context.EMSpringArgContext;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanCreationException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.core.io.ResourceLoader;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class EMAfterBeanPostProcessor extends EMAbstractProcessor implements BeanPostProcessor {
    private final Logger logger=Logger.get(EMAfterBeanPostProcessor.class);

    public EMAfterBeanPostProcessor(EMSpringArgContext context, ResourceLoader resourceLoader){
        super(context,resourceLoader);
        logger.info("Effective Processor: EMAfterBeanPostProcessor,context:"+context.toString()+",resourceLoader:"+resourceLoader.toString());
        initMockResources(resourceLoader);
    }

    private void initMockResources(ResourceLoader loader){
        try{
            logger.info("start init mock resources");
            this.context.loadDefinition(loader);
        }catch (Exception ex){
            logger.error("error in init mock resources:",ex);
        }
    }


    @Override
    public Object postProcessBeforeInitialization(Object bean, String beanName) throws BeansException {
        return BeanPostProcessor.super.postProcessBeforeInitialization(bean, beanName);
    }

    private Set<Object> inCreatedObject=new HashSet<>();

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
        try{
            if(inCreatedObject.contains(bean)){
                throw new BeanCreationException("loop reference in create em wrapper ");
            }
            inCreatedObject.add(bean);
            this.context.createWrapperForBean(bean);
            inCreatedObject.remove(bean);

        }catch (Exception ex){
            return new BeanCreationException("EMBean create exception!"+ex.getMessage());
        }
        this.context.createEMBeanIfNecessary(bean,beanName);
        if(this.context.getObjectGroup(bean)!=null){
            List<Class<? super Object>> tClzList= this.context.getObjectGroup(bean).getMockClass();
            Class<? super Object> matched=null;
            for(Class<? super Object> tClz: tClzList){
                if(matched==null)
                    matched=tClz;
                if(EMClassUtil.isSubClass(tClz,matched)){
                    matched=tClz;
                }
            }
            logger.info("em bean created in afterBeanPostProcessor,bean:"+bean+",object:"+beanName+",mockDef:"
                    +context.getObjectGroup(bean).getMockInfo(matched).get(0).getDefinition().getName());
           return this.context.getObjectGroup(bean).getProxyHolder(matched).getProxy();
        }
        return bean;
    }


}
