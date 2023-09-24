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

package org.apache.shenyu.admin.spring;

import lombok.SneakyThrows;
import org.apache.shenyu.admin.controller.ShenyuHttpRegistryController;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.lang.reflect.Method;

/**
 * The type shenyu http registry controller bean post processor.
 */
@Component
public class ShenyuHttpRegistryControllerBeanPostProcessor implements BeanPostProcessor {
    
    @SneakyThrows
    @Override
    public Object postProcessAfterInitialization(final Object bean, final String beanName) throws BeansException {
        // 可以看到ShenyuHttpRegistryController并不会被直接注入容器
        // @see org.apache.shenyu.admin.config.RegisterCenterConfiguration.shenyuServerRegisterRepository
        if (bean instanceof ShenyuHttpRegistryController) {
            // 将 ShenyuHttpRegistryController 里的handler添加给springmvc管理
            RequestMappingHandlerMapping requestMappingHandlerMapping = SpringBeanUtils.getInstance().getBean(RequestMappingHandlerMapping.class);
            Method method = requestMappingHandlerMapping.getClass().getSuperclass().getSuperclass().getDeclaredMethod("detectHandlerMethods", Object.class);
            method.setAccessible(true);
            method.invoke(requestMappingHandlerMapping, beanName);
        }
        return bean;
    }
}
