/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.shenyu.client.springmvc.init;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.shenyu.client.core.disruptor.ShenyuClientRegisterEventPublisher;
import org.apache.shenyu.client.springmvc.annotation.ShenyuSpringMvcClient;
import org.apache.shenyu.common.utils.IpUtils;
import org.apache.shenyu.register.client.api.ShenyuClientRegisterRepository;
import org.apache.shenyu.register.common.config.ShenyuRegisterCenterConfig;
import org.apache.shenyu.register.common.dto.MetaDataRegisterDTO;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Controller;
import org.springframework.util.ReflectionUtils;
import org.springframework.web.bind.annotation.RequestMapping;

import java.lang.reflect.Method;
import java.util.Objects;
import java.util.Properties;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * The type Shenyu spring mvc client bean post processor.
 */
@Slf4j
public class SpringMvcClientBeanPostProcessor implements BeanPostProcessor {

    private final ShenyuClientRegisterEventPublisher publisher = ShenyuClientRegisterEventPublisher.getInstance();

    private final ExecutorService executorService;

    private final String contextPath;

    private final String appName;

    private final String host;

    private final Integer port;

    private final Boolean isFull;

    /**
     * Instantiates a new Shenyu client bean post processor.
     */
    public SpringMvcClientBeanPostProcessor(final ShenyuRegisterCenterConfig config, final ShenyuClientRegisterRepository shenyuClientRegisterRepository) {
        String registerType = config.getRegisterType();
        String serverLists = config.getServerLists();
        Properties props = config.getProps();
        int port = Integer.parseInt(props.getProperty("port"));
        if (StringUtils.isBlank(registerType) || StringUtils.isBlank(serverLists) || port <= 0) {
            String errorMsg = "http register param must config the registerType , serverLists and port must > 0";
            log.error(errorMsg);
            throw new RuntimeException(errorMsg);
        }
        this.appName = props.getProperty("appName");
        this.host = props.getProperty("host");
        this.port = port;
        this.contextPath = props.getProperty("contextPath");
        this.isFull = Boolean.parseBoolean(props.getProperty("isFull", "false"));
        executorService = Executors.newSingleThreadExecutor(new ThreadFactoryBuilder().setNameFormat("shenyu-spring-mvc-client-thread-pool-%d").build());
        publisher.start(shenyuClientRegisterRepository);
    }

    /**
     * todo 这里是否注册过早，有可能应用还没启动成功就进行了过早的暴露，是否要修改为监听容器启动成功事件
     */
    @Override
    public Object postProcessAfterInitialization(@NonNull final Object bean, @NonNull final String beanName) throws BeansException {
        // TODO 这个是什么含义呢？
        if (isFull) {
            return bean;
        }
        Controller controller = AnnotationUtils.findAnnotation(bean.getClass(), Controller.class);
        RequestMapping requestMapping = AnnotationUtils.findAnnotation(bean.getClass(), RequestMapping.class);
        // 找到要暴露的controller类
        if (controller != null || requestMapping != null) {
            // 找到类上的ShenyuSpringMvcClient注解
            ShenyuSpringMvcClient clazzAnnotation = AnnotationUtils.findAnnotation(bean.getClass(), ShenyuSpringMvcClient.class);
            String prePath = "";
            // 如果没有 ShenyuSpringMvcClient 注解则不处理
            if (Objects.isNull(clazzAnnotation)) {
                return bean;
            }
            // 如果controller类上的 ShenyuSpringMvcClient 注解的path里包含了*，则只为这个controller类注册元数据即可（不用细化到方法粒度）
            if (clazzAnnotation.path().indexOf("*") > 1) {
                String finalPrePath = prePath;
                // 发布注册元数据的事件，这里会被 ShenyuClientMetadataExecutorSubscriber 监听到
                executorService.execute(() -> publisher.publishEvent(buildMetaDataDTO(clazzAnnotation, finalPrePath)));
                return bean;
            }
            prePath = clazzAnnotation.path();
            final Method[] methods = ReflectionUtils.getUniqueDeclaredMethods(bean.getClass());
            // 扫描每个controller的方法
            for (Method method : methods) {
                // 找到标注了 ShenyuSpringMvcClient 注解的方法
                ShenyuSpringMvcClient shenyuSpringMvcClient = AnnotationUtils.findAnnotation(method, ShenyuSpringMvcClient.class);
                if (Objects.nonNull(shenyuSpringMvcClient)) {
                    String finalPrePath = prePath;
                    // 注册方法元数据到shenyu-admin，这里会被 ShenyuClientMetadataExecutorSubscriber 监听到
                    executorService.execute(() -> publisher.publishEvent(buildMetaDataDTO(shenyuSpringMvcClient, finalPrePath)));
                }
            }
        }
        return bean;
    }

    private MetaDataRegisterDTO buildMetaDataDTO(final ShenyuSpringMvcClient shenyuSpringMvcClient, final String prePath) {
        String contextPath = this.contextPath;
        String appName = this.appName;
        Integer port = this.port;
        String path;
        // 如果配置文件中没有指定contextPath，则取controller类上的 ShenyuSpringMvcClient 注解的path作为元数据的 path
        if (StringUtils.isEmpty(contextPath)) {
            path = prePath + shenyuSpringMvcClient.path();
        } else {
            path = contextPath + prePath + shenyuSpringMvcClient.path();
        }
        String desc = shenyuSpringMvcClient.desc();
        // 检查一下配置的host是否服务规范，不符合则重新获取本机IP作为host
        String host = IpUtils.isCompleteHost(this.host) ? this.host : IpUtils.getHost(this.host);
        String configRuleName = shenyuSpringMvcClient.ruleName();
        // 如果 shenyuSpringMvcClient 上没有指定规则名称，则取path作为规则名称
        String ruleName = StringUtils.isBlank(configRuleName) ? path : configRuleName;
        return MetaDataRegisterDTO.builder()
                .contextPath(contextPath)
                .host(host)
                .port(port)
                .appName(appName)
                .path(path)
                .pathDesc(desc)
                // 如果是http的那么这里就是http，如果是dubbo的服务提供者那么这里就是dubbo
                .rpcType(shenyuSpringMvcClient.rpcType())
                // shenyuSpringMvcClient 默认都是开启的
                .enabled(shenyuSpringMvcClient.enabled())
                .ruleName(ruleName)
                // 是否需要注册元数据，http方式默认不注册
                .registerMetaData(shenyuSpringMvcClient.registerMetaData())
                .build();
    }
}


