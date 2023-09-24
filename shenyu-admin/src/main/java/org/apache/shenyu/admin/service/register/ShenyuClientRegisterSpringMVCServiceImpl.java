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

package org.apache.shenyu.admin.service.register;

import org.apache.commons.lang3.StringUtils;
import org.apache.shenyu.admin.listener.DataChangedEvent;
import org.apache.shenyu.admin.model.dto.RuleDTO;
import org.apache.shenyu.admin.model.dto.SelectorDTO;
import org.apache.shenyu.admin.model.entity.MetaDataDO;
import org.apache.shenyu.admin.model.entity.SelectorDO;
import org.apache.shenyu.admin.service.MetaDataService;
import org.apache.shenyu.admin.service.PluginService;
import org.apache.shenyu.admin.service.RuleService;
import org.apache.shenyu.admin.service.SelectorService;
import org.apache.shenyu.admin.transfer.MetaDataTransfer;
import org.apache.shenyu.admin.utils.ShenyuResultMessage;
import org.apache.shenyu.common.constant.Constants;
import org.apache.shenyu.common.enums.ConfigGroupEnum;
import org.apache.shenyu.common.enums.DataEventTypeEnum;
import org.apache.shenyu.common.enums.PluginEnum;
import org.apache.shenyu.common.utils.UUIDUtils;
import org.apache.shenyu.register.common.dto.MetaDataRegisterDTO;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.util.Collections;
import java.util.Objects;

/**
 * spring mvc service register.
 */
@Service("http")
public class ShenyuClientRegisterSpringMVCServiceImpl extends AbstractShenyuClientRegisterServiceImpl {

    private final ApplicationEventPublisher eventPublisher;

    private final SelectorService selectorService;

    private final RuleService ruleService;

    private final MetaDataService metaDataService;

    private final PluginService pluginService;

    public ShenyuClientRegisterSpringMVCServiceImpl(final MetaDataService metaDataService,
                                                    final ApplicationEventPublisher eventPublisher,
                                                    final SelectorService selectorService,
                                                    final RuleService ruleService,
                                                    final PluginService pluginService) {
        this.metaDataService = metaDataService;
        this.eventPublisher = eventPublisher;
        this.selectorService = selectorService;
        this.ruleService = ruleService;
        this.pluginService = pluginService;
    }

    /**http服务提供者启动时注册数据到shenyu-admin时就会调用这个接口*/
    @Override
    @Transactional(rollbackFor = Exception.class)
    public synchronized String register(final MetaDataRegisterDTO dto) {
        // 如果需要注册元数据(http服务提供者默认不需要注册元数据)
        if (dto.isRegisterMetaData()) {
            MetaDataDO exist = metaDataService.findByPath(dto.getPath());
            // 元数据不存在才操作，那么存在了要更新怎么办
            if (Objects.isNull(exist)) {
                saveOrUpdateMetaData(null, dto);
            }
        }
        // 元数据(不是必须的) -》selector -》rule
        // 处理这个元数据对应的selector
        String selectorId = handlerSelector(dto);
        // 处理这个selector的rules
        handlerRule(selectorId, dto, null);
        String contextPath = dto.getContextPath();
        // 如果说元数据里有 contextPath，则需要为该 contextPath 创建一个selector和rule
        if (StringUtils.isNotEmpty(contextPath)) {
            //register context path plugin
            registerContextPathPlugin(contextPath);
        }
        return ShenyuResultMessage.SUCCESS;
    }

    @Override
    public void saveOrUpdateMetaData(final MetaDataDO exist, final MetaDataRegisterDTO dto) {
        Timestamp currentTime = new Timestamp(System.currentTimeMillis());
        MetaDataDO metaDataDO = MetaDataDO.builder()
                .appName(dto.getAppName())
                .path(dto.getPath())
                .pathDesc(dto.getPathDesc())
                .rpcType(dto.getRpcType())
                .enabled(dto.isEnabled())
                .id(UUIDUtils.getInstance().generateShortUuid())
                .dateCreated(currentTime)
                .dateUpdated(currentTime)
                .build();
        // 元数据落库
        metaDataService.insert(metaDataDO);
        // publish AppAuthData's event 发布元数据创建事件（这里其实就会同步给gateway）
        eventPublisher.publishEvent(new DataChangedEvent(ConfigGroupEnum.META_DATA, DataEventTypeEnum.CREATE,
                // 把DO转为元数据
                Collections.singletonList(MetaDataTransfer.INSTANCE.mapToData(metaDataDO))));
    }

    @Override
    public String handlerSelector(final MetaDataRegisterDTO dto) {
        return selectorService.handlerSelectorNeedUpstreamCheck(dto, PluginEnum.DIVIDE.getName());
    }

    @Override
    public void handlerRule(final String selectorId, final MetaDataRegisterDTO dto, final MetaDataDO exist) {
        // 根据元数据构建rule
        RuleDTO ruleDTO = registerRule(selectorId, dto.getPath(), PluginEnum.DIVIDE.getName(), dto.getRuleName());
        // 如果规则不存在则落库然后发送规则变更事件，如果规则存在则直接发送规则变更事件（同步给gateway）
        ruleService.register(ruleDTO, dto.getRuleName(), false);
    }

    /**
     * 给 context_path插件 注册一个selector和对应的rule。什么意思呢？
     */
    private void registerContextPathPlugin(final String contextPath) {
        // eg: /context-path/xxxContextPath
        String name = Constants.CONTEXT_PATH_NAME_PREFIX + contextPath;
        SelectorDO selectorDO = selectorService.findByName(name);
        // 不存在才创建
        if (Objects.isNull(selectorDO)) {
            // 给 context_path插件 构建一个selector
            String contextPathSelectorId = registerContextPathSelector(contextPath, name);
            // 构建rule
            RuleDTO ruleDTO = registerRule(contextPathSelectorId, contextPath + "/**", PluginEnum.CONTEXT_PATH.getName(), name);
            // 注册rule，如果规则不存在则落库然后发送规则变更事件，如果规则存在则直接发送规则变更事件（同步给gateway）
            ruleService.register(ruleDTO, name, false);
        }
    }

    private String registerContextPathSelector(final String contextPath, final String name) {
        // 构建selector数据
        SelectorDTO selectorDTO = buildDefaultSelectorDTO(name);
        // 可以看到该selector是属于 CONTEXT_PATH 插件的
        selectorDTO.setPluginId(pluginService.selectIdByName(PluginEnum.CONTEXT_PATH.getName()));
        // 设置selector的默认condition
        selectorDTO.setSelectorConditions(buildDefaultSelectorConditionDTO(contextPath));
        // selector落库并推送给gateway
        return selectorService.register(selectorDTO);
    }
}
