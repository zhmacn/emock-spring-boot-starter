package com.mzh.emock.spring.boot.starter.context;

import com.mzh.emock.core.compiler.EMMemoryClassLoader;
import com.mzh.emock.core.compiler.EMRTCompiler;
import com.mzh.emock.core.compiler.result.EMCompilerResult;
import com.mzh.emock.core.context.EMAbstractContext;
import com.mzh.emock.core.exception.EMDefinitionException;
import com.mzh.emock.core.log.Logger;
import com.mzh.emock.core.support.EMProxySupport;
import com.mzh.emock.core.type.object.field.EMFieldInfo;
import com.mzh.emock.core.type.proxy.EMProxyHolder;
import com.mzh.emock.core.util.EMClassUtil;
import com.mzh.emock.core.util.EMObjectUtil;
import com.mzh.emock.core.util.EMResourceUtil;
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
import java.util.function.Predicate;

public class EMSpringArgContext extends EMAbstractContext{

    private final AbstractApplicationContext context;
    private EMProxySupport proxySupport;

    public EMSpringArgContext(AbstractApplicationContext applicationContext){
        this.context=applicationContext;
    }
    private final Logger logger=Logger.get(EMSpringArgContext.class);

    public void loadListener(ResourceLoader resourceLoader){
        if (context == null || resourceLoader == null) {
            return;
        }
    }
    //support simple definition
    public void loadDefinition(ResourceLoader resourceLoader)throws Exception{
        if(context==null){
            return;
        }
        this.clearDefinition();
        this.loadDefinitionFromPath(resourceLoader);
        this.loadDefinitionFromParameter();
    }
    private void loadDefinitionFromParameter()throws Exception{
        List<String> dynamicMocks=EMConfigurationProperties.DYNAMIC_MOCKS;
        if(dynamicMocks.size()>1000){
            throw new EMDefinitionException("dynamic mocks must less then 1000");
        }
        List<Class<?>> cNames=new ArrayList<>();
        ClassLoader loader=this.getClass().getClassLoader();
        for (String dynamicMock : dynamicMocks) {
            Class<?> clz = loader.loadClass(dynamicMock);
            cNames.add(clz);
        }
        //使用编译器直接编译字符串类,或者使用asm生成
        //直接采用生成类文件的方式【同项目中已有的方式一致】
        String importStr="package com.mzh.emock.instance.dynamic;\r\n"+
                "import com.mzh.emock.core.type.EMock;\r\n"+
                "import com.mzh.emock.core.type.object.EMObjectWrapper;\r\n"+
                "import org.springframework.context.ApplicationContext;\r\n"+
                "import java.util.function.Supplier;\r\n";
        StringBuilder body=new StringBuilder("public class EMDynamicInstance{\r\n");
        for(Class<?> c:cNames){
            body.append("@EMock(name=\"").append(c.getName().replace(".", "-")).append("\",order=1,objectEnableMock=false)\r\n");
            body.append("public static EMObjectWrapper<").append(c.getName()).append("> ").append(c.getName().replace(".","_"))
                    .append("(Supplier<ApplicationContext> args){\r\n").append("return api->api;\r\n}\r\n");
        }
        body.append("}");
        String code=importStr+body;
        String clzName="com.mzh.emock.instance.dynamic.EMDynamicInstance";
        EMCompilerResult result=EMRTCompiler.compile("EMDynamicInstance.java",code);
        if(result.isSuccess()){
            Class<?> clz=EMMemoryClassLoader.loadFromBytes(clzName,result.getResult());
            super.loadDefinition(clz,m->true);
        }else{
            throw result.getException();
        }
        logger.info("emock : load definitionSource complete : "+this.getDefinitionKeys().size());
    }
    private void loadDefinitionFromPath(ResourceLoader resourceLoader)throws Exception{
        if (resourceLoader == null) {
            return;
        }
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
                super.loadDefinition(clz, method -> {
                    String typeName=EMClassUtil.getParameterizedTypeClass(method.getGenericReturnType()).get(0).getTypeName();
                    Class<?> paramType=EMClassUtil.getParameterizedTypeClass(method.getGenericParameterTypes()[0]).get(0);
                    return PatternMatchUtils.simpleMatch(matchers,typeName) && EMClassUtil.isSuperClass(paramType,AbstractApplicationContext.class);
                });
            }
        }
    }


    public void createAllWrapper()throws Exception{
        this.createWrapper(d -> EMClassUtil.isSuperClass(d.getAClass(),this.context.getClass()), () -> this.context);
    }
    public void createWrapperForBean(Object bean) throws Exception{
        this.createWrapper(d-> EMClassUtil.isSuperClass(d.getAClass(),this.context.getClass())
                    && EMClassUtil.isSubClass(bean.getClass(),d.getTClass()), ()->this.context);
    }

    public void createEMBeanIfNecessary(Object bean,String name)throws BeansException {
        try {
            this.updateMockObjectInfo(bean,name);
        }catch (Exception ex){
            throw new BeanCreationException("mock bean create exception:"+ex.getLocalizedMessage());
        }
    }
    public void createEMBeanIfNecessary() {
        String[] beanNames=this.context.getBeanFactory().getBeanDefinitionNames();
        Map<String,Object> beans=new HashMap<>();
        Arrays.stream(beanNames).forEach(n->beans.put(n,this.context.getBean(n)));
        for(String name:beans.keySet()){
            createEMBeanIfNecessary(beans.get(name),name);
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
    @SuppressWarnings("unchecked")
    private <T> void createProxyAndSetField(EMFieldInfo info, Object holder, T target) throws Exception {
        Class<?> tempType=info.getNativeField().getType();
        Class<?> fieldType=info.isArrayIndex()?tempType.getComponentType():tempType;
        if(!EMClassUtil.isSubClass(target.getClass(),fieldType)){
            logger.error("target is not a subClass of field "+",holder:"+holder+",field:"+info.getNativeField().getName());
            return;
        }
        if(info.isArrayIndex()){
            if(((Object[])holder)[info.getIndex()]!=target){
                logger.error("array object index changed ,obj:"+holder);
                return;
            }
        }
        Class<? super T> bestMatched=findBestMatchClz(target,(Class<? super T>)fieldType);
        if(bestMatched==null){
            logger.error("field set error,no matched proxy,target:"+target.getClass()+",fieldType:"+fieldType.getName());
            return ;
        }
        EMProxyHolder<? super T> proxyHolder = getProxySupport().createProxy(bestMatched, target);
        proxyHolder.addInjectField(info);
        if(info.isArrayIndex()){
            ((Object[]) holder)[info.getIndex()] = proxyHolder.getProxy();
        }else{
            info.getNativeField().setAccessible(true);
            info.getNativeField().set(holder,proxyHolder.getProxy());
        }
    }


    //更新bestmatch注入逻辑，添加子类注入逻辑
    private <T> Class<? super T> findBestMatchClz(T oldBean,Class<? super T> fieldClz){
        List<Class<? super T>> tClzList = this.getObjectGroup(oldBean).getMockClass();
        Class<? super T> bestMatch=null;
        for(Class<? super T> tClz:tClzList){
            if(EMClassUtil.isSubClass(tClz,fieldClz)){
                if(bestMatch==null){
                    bestMatch=tClz;
                }
                if(fieldClz==tClz){
                    return fieldClz;
                }
                if(EMClassUtil.isSubClass(tClz,bestMatch)){
                    bestMatch=tClz;
                }
            }
        }
        return bestMatch;
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
