package com.mzh.emock.spring.boot.starter;

import com.mzh.emock.spring.boot.starter.processor.EMAbstractProcessor;
import com.mzh.emock.spring.boot.starter.processor.EMAfterPostBeanProcessor;
import com.mzh.emock.spring.boot.starter.processor.EMApplicationReadyProcessor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.DependsOn;
import org.springframework.context.support.AbstractApplicationContext;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;
import com.mzh.emock.spring.boot.starter.EMConfigurationProperties.*;

@Component
@ConditionalOnProperty(prefix = Constant.CONFIGURATION_PREFIX, name = Constant.ENABLED_CONFIGURATION_NAME,
        havingValue = Constant.ENABLED_CONFIGURATION_VALUE)
@EnableConfigurationProperties(EMConfigurationProperties.class)
@DependsOn(Constant.PROPERTIES_FILE_NAME)
public class EMConfiguration {
    @Bean
    @ConditionalOnProperty(prefix = Constant.CONFIGURATION_PREFIX, name = Constant.PROCESSOR_TYPE, havingValue = Constant.TYPE_AB)
    public EMAfterPostBeanProcessor emAfterPostBeanProcessor(@Autowired AbstractApplicationContext context, @Autowired ResourceLoader resourceLoader){
        return new EMAfterPostBeanProcessor(context,resourceLoader);
    }


    @Bean
    @ConditionalOnMissingBean(value= EMAbstractProcessor.class)
    public EMApplicationReadyProcessor emApplicationReadyProcessor(@Autowired AbstractApplicationContext context, @Autowired ResourceLoader resourceLoader){
        return new EMApplicationReadyProcessor(context,resourceLoader);
    }






    //----------------------------------manager-----------------------------------//

    /*
    @Bean
    @ConditionalOnProperty(prefix=Constant.CONFIGURATION_PREFIX, name = Constant.ENABLED_MANAGER_NAME, havingValue = Constant.ENABLED_CONFIGURATION_VALUE)
    public EMManagerController managerController(){
        return new EMManagerController();
    }
     */
}
