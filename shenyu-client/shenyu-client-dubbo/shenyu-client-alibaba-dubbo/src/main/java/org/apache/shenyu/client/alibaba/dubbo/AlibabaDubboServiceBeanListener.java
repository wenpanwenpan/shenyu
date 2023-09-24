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

package org.apache.shenyu.client.alibaba.dubbo;

import com.alibaba.dubbo.common.Constants;
import com.alibaba.dubbo.common.utils.StringUtils;
import com.alibaba.dubbo.config.spring.ServiceBean;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import lombok.extern.slf4j.Slf4j;
import org.apache.shenyu.client.core.disruptor.ShenyuClientRegisterEventPublisher;
import org.apache.shenyu.client.dubbo.common.annotation.ShenyuDubboClient;
import org.apache.shenyu.client.dubbo.common.dto.DubboRpcExt;
import org.apache.shenyu.common.utils.GsonUtils;
import org.apache.shenyu.common.utils.IpUtils;
import org.apache.shenyu.register.client.api.ShenyuClientRegisterRepository;
import org.apache.shenyu.register.common.config.ShenyuRegisterCenterConfig;
import org.apache.shenyu.register.common.dto.MetaDataRegisterDTO;
import org.springframework.aop.support.AopUtils;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.util.ReflectionUtils;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

/**
 * The Alibaba Dubbo ServiceBean Listener.
 */
@Slf4j
@SuppressWarnings("all")
public class AlibabaDubboServiceBeanListener implements ApplicationListener<ContextRefreshedEvent> {

    /**Disruptor的事件发布器*/
    private ShenyuClientRegisterEventPublisher publisher = ShenyuClientRegisterEventPublisher.getInstance();

    private AtomicBoolean registered = new AtomicBoolean(false);

    private final ExecutorService executorService;

    private final String contextPath;

    private final String appName;

    private final String host;

    private final String port;

    public AlibabaDubboServiceBeanListener(final ShenyuRegisterCenterConfig config, final ShenyuClientRegisterRepository shenyuClientRegisterRepository) {
        Properties props = config.getProps();
        String contextPath = props.getProperty("contextPath");
        String appName = props.getProperty("appName");
        if (StringUtils.isEmpty(contextPath)) {
            throw new RuntimeException("apache dubbo client must config the contextPath");
        }
        this.contextPath = contextPath;
        this.appName = appName;
        this.host = props.getProperty("host");
        this.port = props.getProperty("port");
        // 单线程的线程池
        executorService = Executors.newSingleThreadExecutor(new ThreadFactoryBuilder().setNameFormat("shenyu-alibaba-dubbo-client-thread-pool-%d").build());
        publisher.start(shenyuClientRegisterRepository);
    }

    /**
     * 处理每个RPC bean
     */
    private void handler(final ServiceBean<?> serviceBean) {
        Object refProxy = serviceBean.getRef();
        Class<?> clazz = refProxy.getClass();
        if (AopUtils.isAopProxy(refProxy)) {
            // 获取目标类的Class
            clazz = AopUtils.getTargetClass(refProxy);
        }
        Method[] methods = ReflectionUtils.getUniqueDeclaredMethods(clazz);
        for (Method method : methods) {
            // 找到标注了 ShenyuDubboClient 注解的方法，并发布元数据事件，该事件会被 ShenyuClientMetadataExecutorSubscriber 监听到
            ShenyuDubboClient shenyuDubboClient = method.getAnnotation(ShenyuDubboClient.class);
            if (Objects.nonNull(shenyuDubboClient)) {
                publisher.publishEvent(buildMetaDataDTO(serviceBean, shenyuDubboClient, method));
            }
        }
    }

    private MetaDataRegisterDTO buildMetaDataDTO(final ServiceBean<?> serviceBean, final ShenyuDubboClient shenyuDubboClient, final Method method) {
        String appName = this.appName;
        if (StringUtils.isEmpty(appName)) {
            appName = serviceBean.getApplication().getName();
        }
        // 比如：/dubbo（这个是自定义的）/findAll
        String path = contextPath + shenyuDubboClient.path();
        // 描述信息
        String desc = shenyuDubboClient.desc();
        // 接口
        String serviceName = serviceBean.getInterface();
        // 检查下IP是否是合法的（符合正则匹配），如果不合法则获取一下本机IP
        String host = IpUtils.isCompleteHost(this.host) ? this.host : IpUtils.getHost(this.host);
        int port = StringUtils.isBlank(this.port) ? -1 : Integer.parseInt(this.port);
        String configRuleName = shenyuDubboClient.ruleName();
        // 如果 shenyuDubboClient注解上没有指定该方法的rule，则使用该该方法的访问path作为规则名称
        String ruleName = ("".equals(configRuleName)) ? path : configRuleName;
        String methodName = method.getName();
        Class<?>[] parameterTypesClazz = method.getParameterTypes();
        // 方法参数类型
        String parameterTypes = Arrays.stream(parameterTypesClazz).map(Class::getName)
                .collect(Collectors.joining(","));
        return MetaDataRegisterDTO.builder()
                .appName(appName)
                .serviceName(serviceName)
                .methodName(methodName)
                .contextPath(contextPath)
                .host(host)
                .port(port)
                .path(path)
                .ruleName(ruleName)
                .pathDesc(desc)
                .parameterTypes(parameterTypes)
                .rpcExt(buildRpcExt(serviceBean))
                .rpcType("dubbo")
                .enabled(shenyuDubboClient.enabled())
                .build();
    }

    /**构建dubbo RPC扩展信息*/
    private String buildRpcExt(final ServiceBean<?> serviceBean) {
        DubboRpcExt builder = DubboRpcExt.builder()
                // 服务分组
                .group(StringUtils.isNotEmpty(serviceBean.getGroup()) ? serviceBean.getGroup() : "")
                // 服务版本
                .version(StringUtils.isNotEmpty(serviceBean.getVersion()) ? serviceBean.getVersion() : "")
                // 赋值均衡策略
                .loadbalance(StringUtils.isNotEmpty(serviceBean.getLoadbalance()) ? serviceBean.getLoadbalance() : Constants.DEFAULT_LOADBALANCE)
                // 重试策略
                .retries(Objects.isNull(serviceBean.getRetries()) ? Constants.DEFAULT_RETRIES : serviceBean.getRetries())
                // 超时时间
                .timeout(Objects.isNull(serviceBean.getTimeout()) ? Constants.DEFAULT_CONNECT_TIMEOUT : serviceBean.getTimeout())
                .url("")
                .build();
        return GsonUtils.getInstance().toJson(builder);

    }

    @Override
    public void onApplicationEvent(final ContextRefreshedEvent contextRefreshedEvent) {
        if (!registered.compareAndSet(false, true)) {
            return;
        }
        // Fix bug(https://github.com/dromara/shenyu/issues/415), upload dubbo metadata on ContextRefreshedEvent
        Map<String, ServiceBean> serviceBean = contextRefreshedEvent.getApplicationContext().getBeansOfType(ServiceBean.class);
        for (Map.Entry<String, ServiceBean> entry : serviceBean.entrySet()) {
            executorService.execute(() -> handler(entry.getValue()));
        }
    }
}
