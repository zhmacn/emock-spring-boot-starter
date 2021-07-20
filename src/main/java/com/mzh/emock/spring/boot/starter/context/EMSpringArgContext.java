package com.mzh.emock.spring.boot.starter.context;

import com.mzh.emock.core.context.EMAbstractContext;
import com.mzh.emock.core.log.Logger;
import com.mzh.emock.core.support.EMProxySupport;
import com.mzh.emock.core.type.proxy.EMProxyHolder;
import com.mzh.emock.core.util.EMClassUtil;
import com.mzh.emock.core.util.EMObjectUtil;
import com.mzh.emock.core.util.EMResourceUtil;
import com.mzh.emock.core.util.entity.EMFieldInfo;
import com.mzh.emock.spring.boot.starter.EMConfigurationProperties;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanCreationException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.support.AbstractApplicationContext;
import org.springframework.core.env.Environment;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternUtils;
import org.springframework.core.type.classreading.CachingMetadataReaderFactory;
import org.springframework.core.type.classreading.MetadataReader;
import org.springframework.core.type.classreading.MetadataReaderFactory;
import org.springframework.util.PatternMatchUtils;

import java.util.*;

public class EMSpringArgContext extends EMAbstractContext {
    private static EMSpringArgContext curr;

    public static EMSpringArgContext getCurrContext(){
        return curr;
    }
    public static void preparePublicContext(AbstractApplicationContext context){
        curr=new EMSpringArgContext(context);
    }

    private final AbstractApplicationContext context;
    private EMProxySupport proxySupport;


    
    private EMSpringArgContext(AbstractApplicationContext applicationContext){
        this.context=applicationContext;
    }
    private final Logger logger=Logger.get(EMSpringArgContext.class);
    public void loadDefinition(ResourceLoader resourceLoader)throws Exception{
        if (context == null || resourceLoader == null) {
            return;
        }
        this.clearDefinition();
        ClassLoader loader=EMSpringArgContext.class.getClassLoader();
        String[] matchers=loadEMNameMatcher(context);
        ResourcePatternResolver resolver = ResourcePatternUtils.getResourcePatternResolver(resourceLoader);
        MetadataReaderFactory readerFactory = new CachingMetadataReaderFactory(resourceLoader);
        List<String> paths = EMConfigurationProperties.SCAN_PACKAGE;
        for (String path : paths) {
            Resource[] resources = resolver.getResources(EMResourceUtil.formatResourcePath(path));
            for (Resource resource : resources) {
                MetadataReader reader = readerFactory.getMetadataReader(resource);
                Class<?> clz = loader.loadClass(reader.getClassMetadata().getClassName());
                loadDefinition(clz, method -> {
                    String typeName=EMClassUtil.getParameterizedTypeClass(method.getGenericReturnType()).get(0).getTypeName();
                    Class<?> paramType=EMClassUtil.getParameterizedTypeClass(method.getGenericParameterTypes()[0]).get(0);
                    return PatternMatchUtils.simpleMatch(matchers,typeName) && EMClassUtil.isSuperClass(paramType,AbstractApplicationContext.class);
                });
            }
        }
        logger.info("emock : load definitionSource complete : "+this.getDefinitionKeys().size());
    }

    public void createWrapper()throws Exception{
        this.createWrapper(null,AbstractApplicationContext.class,()->this.context);
    }

    public void createEMBeanIfNecessary(Object bean)throws BeansException {
        try {
            this.updateMockObjectInfo(bean);
        }catch (Exception ex){
            throw new BeanCreationException("mock bean create exception:"+ex.getLocalizedMessage());
        }
    }
    public void createEMBeanIfNecessary() throws Exception{
        String[] beanNames=this.context.getBeanFactory().getBeanDefinitionNames();
        for(String name:beanNames){
            Object bean=this.context.getBean(name);
            createEMBeanIfNecessary(bean);
        }
    }

    public void proxyAndInject()throws Exception{
        String[] names = context.getBeanDefinitionNames();
        Object[] beans= Arrays.stream(names).map(context::getBean).toArray();
        for (Object target : this.getOldObjects()) {
            for (int j=0;j<names.length;j++) {
                createProxyAndSetField(beans[j], target);
            }
        }
    }
    private <T> void createProxyAndSetField(Object src, T target) throws Exception {
        Map<Object, List<EMFieldInfo>> matchedObject = EMObjectUtil.match(src, target);
        for (Object holder : matchedObject.keySet()) {
            List<EMFieldInfo> fields = matchedObject.get(holder);
            for(int i=fields.size()-1;i>=0;i--){
                createProxyAndSetField(fields.get(i),holder,target);
            }
        }
    }
    private <T> void createProxyAndSetField(EMFieldInfo info, Object holder, T target) throws Exception {
        Class<? super T> clz;
        if(info.isArrayIndex()){
            if(((Object[])holder)[info.getIndex()]!=target){
                logger.error("array object index changed "+",obj:"+holder);
                return;
            }
            clz=findBestMatchClz(target,(Class<? super T>)info.getNativeField().getType().getComponentType());
        }else{
            clz=findBestMatchClz(target,(Class<? super T>)info.getNativeField().getType());
        }
        EMProxyHolder<? super T> proxyHolder = getProxySupport().createProxy(clz, target);
        proxyHolder.addInjectField(info);
        doInject(info,holder,proxyHolder.getProxy());
    }

    public static boolean doInject(EMFieldInfo fieldInfo, Object holder, Object proxy)throws Exception{
        if(fieldInfo.isArrayIndex()){
            ((Object[]) holder)[fieldInfo.getIndex()] = proxy;
        }else{
            fieldInfo.getNativeField().setAccessible(true);
            fieldInfo.getNativeField().set(holder,proxy);
        }
        return true;
    }


    private <T> Class<? super T> findBestMatchClz(T oldBean,Class<? super T> fieldClz){
        Set<Class<? super T>> curr= this.getObjectGroup(oldBean).get.getMockInfo(fieldClz).keySet();
        Class<?> bestMatch=null;
        for(Class<?> c:curr){
            if(fieldClz.isAssignableFrom(c)){
                if(fieldClz==c){
                    return fieldClz;
                }
                if(bestMatch==null){
                    bestMatch=c;
                }
                if(bestMatch.isAssignableFrom(c)){
                    bestMatch=c;
                }
            }
        }
        return (Class<? super T>)bestMatch;
    }

    private EMProxySupport getProxySupport(){
        if(this.proxySupport==null){
            this.proxySupport=new EMProxySupport(this,EMSpringArgContext.class.getClassLoader());
        }
        return this.proxySupport;
    }

    private String[] loadEMNameMatcher(ApplicationContext context) {
        Environment environment = context.getEnvironment();
        if (!isEMEnvironment(environment)) {
            return new String[]{};
        }
        List<String> filters= EMConfigurationProperties.FILTER;
        return filters.size() == 0 ? new String[]{"*"} : filters.toArray(new String[0]);
    }

    private boolean isEMEnvironment(Environment environment) {
        String[] envProfiles = environment.getActiveProfiles();
        List<String> targetProfiles = EMConfigurationProperties.ENABLED_PROFILES;
        if (envProfiles.length == 0 || targetProfiles.size() == 0) {
            return false;
        }
        for (String envProfile : envProfiles) {
            if (targetProfiles.contains(envProfile)) {
                return true;
            }
        }
        return false;
    }
}
