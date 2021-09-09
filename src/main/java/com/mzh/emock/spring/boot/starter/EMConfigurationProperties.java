package com.mzh.emock.spring.boot.starter;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.lang.NonNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@ConfigurationProperties(prefix = EMConfigurationProperties.Constant.CONFIGURATION_PREFIX)
public class EMConfigurationProperties {
    public static class Constant{
        public static final String CONFIGURATION_PREFIX="mzh.emock";
        public static final String ENABLED_CONFIGURATION_NAME="enabled";
        public static final String ENABLED_CONFIGURATION_VALUE="true";
        public static final String ENABLED_MANAGER_NAME="enabled-manager";
        public static final String PROPERTIES_FILE_NAME=CONFIGURATION_PREFIX+"-com.mzh.emock.spring.boot.starter.EMConfigurationProperties";
        public static final String PROCESSOR_TYPE="processor-type";
        public static final String TYPE_RD="application-ready";
        public static final String TYPE_AB="after-bean-post";
    }
    public static final List<String> FILTER=new ArrayList<>();
    public static long WAIT_FOR_APPLICATION_READY=5*60*1000L;
    public static final  List<String> ENABLED_PROFILES= Collections.synchronizedList(new ArrayList<String>(){{add("test");add("dev");}});
    public static final List<String> SCAN_PACKAGE=Collections.synchronizedList(new ArrayList<>());
    public static final List<String> DYNAMIC_MOCKS=Collections.synchronizedList(new ArrayList<>());


    public void setEnabledProfiles(@NonNull List<String> profiles){
        ENABLED_PROFILES.clear();
        ENABLED_PROFILES.addAll(profiles);
    }

    public void setScanPackage(@NonNull List<String> packages){
        SCAN_PACKAGE.clear();
        SCAN_PACKAGE.addAll(packages);
    }

    public void setDynamicMocks(@NonNull List<String> interfaceNames){
        DYNAMIC_MOCKS.clear();
        DYNAMIC_MOCKS.addAll(interfaceNames);
    }

    public void setWaitTime(long waitTime){
        if(waitTime<30*1000L){
            WAIT_FOR_APPLICATION_READY=30*1000L;
            return;
        }
        WAIT_FOR_APPLICATION_READY=waitTime;
    }

    public void setFilter(@NonNull List<String> filters){
        FILTER.clear();
        FILTER.addAll(filters);
    }
}
