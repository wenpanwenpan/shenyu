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

package org.apache.shenyu.admin.controller;

import org.apache.shenyu.admin.config.RegisterCenterConfiguration;
import org.apache.shenyu.admin.utils.ShenyuResultMessage;
import org.apache.shenyu.common.utils.JsonUtils;
import org.apache.shenyu.register.common.config.ShenyuRegisterCenterConfig;
import org.apache.shenyu.register.common.dto.MetaDataRegisterDTO;
import org.apache.shenyu.register.server.api.ShenyuServerRegisterPublisher;
import org.apache.shenyu.register.server.api.ShenyuServerRegisterRepository;
import org.apache.shenyu.spi.Join;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.Collections;

/**
 * The type shenyu client controller.
 * 这个controller什么时候被注入容器呢？
 * @see RegisterCenterConfiguration#shenyuServerRegisterRepository(org.apache.shenyu.register.common.config.ShenyuRegisterCenterConfig, java.util.Map)
 */
@RequestMapping("/shenyu-client")
@Join
public class ShenyuHttpRegistryController implements ShenyuServerRegisterRepository {

    private ShenyuServerRegisterPublisher publisher;

    @Override
    public void init(final ShenyuServerRegisterPublisher publisher, final ShenyuRegisterCenterConfig config) {
        this.init(config);
        this.publisher = publisher;
    }

    /**
     * Register spring mvc string.
     *
     * @param metaDataRegisterDTO the meta data register dto
     * @return the string
     */
    @PostMapping("/springmvc-register")
    @ResponseBody
    public String registerSpringMvc(@RequestBody final MetaDataRegisterDTO metaDataRegisterDTO) {
        System.out.println("====>>> 收到MVC注册，请求参数：" + JsonUtils.toJson(metaDataRegisterDTO));
        publish(metaDataRegisterDTO);
        return ShenyuResultMessage.SUCCESS;
    }

    /**
     * Register spring cloud string.
     *
     * @param metaDataRegisterDTO the meta data register dto
     * @return the string
     */
    @PostMapping("/springcloud-register")
    @ResponseBody
    public String registerSpringCloud(@RequestBody final MetaDataRegisterDTO metaDataRegisterDTO) {
        publish(metaDataRegisterDTO);
        return ShenyuResultMessage.SUCCESS;
    }

    /**
     * Register rpc string.
     * 当dubbo服务提供者启动时，便会将元数据上报到这个接口
     * @param metaDataRegisterDTO the meta data register dto
     * @return the string
     */
    @PostMapping("/dubbo-register")
    @ResponseBody
    public String registerRpc(@RequestBody final MetaDataRegisterDTO metaDataRegisterDTO) {
        publish(metaDataRegisterDTO);
        return ShenyuResultMessage.SUCCESS;
    }

    /**
     * Register sofa rpc string.
     *
     * @param metaDataRegisterDTO the meta data register dto
     * @return the string
     */
    @PostMapping("/sofa-register")
    @ResponseBody
    public String registerSofaRpc(@RequestBody final MetaDataRegisterDTO metaDataRegisterDTO) {
        publish(metaDataRegisterDTO);
        return ShenyuResultMessage.SUCCESS;
    }

    /**
     * Register tars rpc string.
     *
     * @param metaDataRegisterDTO the meta data register dto
     * @return the string
     */
    @PostMapping("/tars-register")
    @ResponseBody
    public String registerTarsRpc(@RequestBody final MetaDataRegisterDTO metaDataRegisterDTO) {
        publish(metaDataRegisterDTO);
        return ShenyuResultMessage.SUCCESS;
    }

    /**
     * Register grpc string.
     *
     * @param metaDataRegisterDTO the meta data register dto
     * @return the string
     */
    @PostMapping("/grpc-register")
    @ResponseBody
    public String registerGrpc(@RequestBody final MetaDataRegisterDTO metaDataRegisterDTO) {
        publish(metaDataRegisterDTO);
        return ShenyuResultMessage.SUCCESS;
    }

    /**
     * Register motan string.
     *
     * @param metaDataRegisterDTO the meta data register dto
     * @return the string
     */
    @PostMapping("/motan-register")
    @ResponseBody
    public String registerMotan(@RequestBody final MetaDataRegisterDTO metaDataRegisterDTO) {
        publish(metaDataRegisterDTO);
        return ShenyuResultMessage.SUCCESS;
    }

    private void publish(final MetaDataRegisterDTO metaDataRegisterDTO) {
        publisher.publish(Collections.singletonList(metaDataRegisterDTO));
    }
}
