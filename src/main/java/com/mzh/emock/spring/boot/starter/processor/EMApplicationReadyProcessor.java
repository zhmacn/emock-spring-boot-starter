package com.mzh.emock.spring.boot.starter.processor;

import com.mzh.emock.core.context.EMContext;
import com.mzh.emock.core.log.Logger;
import com.mzh.emock.spring.boot.starter.EMConfigurationProperties;
import com.mzh.emock.spring.boot.starter.context.EMSpringArgContext;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.context.support.AbstractApplicationContext;
import org.springframework.core.Ordered;
import org.springframework.core.io.ResourceLoader;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

public class EMApplicationReadyProcessor extends EMAbstractProcessor implements ApplicationListener<ApplicationReadyEvent>, Ordered {
    private static final Logger logger= Logger.get(EMApplicationReadyProcessor.class);
    public EMApplicationReadyProcessor(EMSpringArgContext context, ResourceLoader resourceLoader){
        super(context, resourceLoader);
        logger.info("Effective Processor: EMApplicationReadyProcessor,context:"+context.toString()+",resourceLoader:"+resourceLoader.toString());
    }

    @Override
    public void onApplicationEvent(ApplicationReadyEvent applicationReadyEvent) {
        Thread main=Thread.currentThread();
        new Thread(()->{
            LocalDateTime startTime= LocalDateTime.now();
            long maxWaiting= EMConfigurationProperties.WAIT_FOR_APPLICATION_READY;
            try{
                main.join(maxWaiting);
            }catch (Exception ex){
                logger.error("emock : wait for main thread complete error",ex);
            }
            long waitTimes=startTime.until(LocalDateTime.now(), ChronoUnit.MILLIS);
            if(waitTimes>=maxWaiting){
                logger.info("emock : wait for main thread complete until "+maxWaiting+" stop mock initial");
                return;
            }
            initialMockBeans();
        },"EMThread").start();
    }
    private void initialMockBeans() {
        try {
            LocalDateTime initStart= LocalDateTime.now();
            logger.info("emock : init processor start , time:"+initStart);
            this.context.loadDefinition(resourceLoader);
            this.context.createAllWrapper();
            this.context.createEMBeanIfNecessary();
            this.context.proxyAndInject();
            LocalDateTime initEnd=LocalDateTime.now();
            logger.info("emock : init processor complete, time: "+ initEnd
                    +", cost : "+initStart.until(initEnd,ChronoUnit.MILLIS)/1000.00+"s");
        }catch (Exception ex){
           logger.error("emock : init error",ex);
        }finally {
            logger.info("===");
        }
    }

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }
}
