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
import org.apache.shenyu.admin.model.dto.RuleConditionDTO;
import org.apache.shenyu.admin.model.dto.RuleDTO;
import org.apache.shenyu.admin.model.dto.SelectorConditionDTO;
import org.apache.shenyu.admin.model.dto.SelectorDTO;
import org.apache.shenyu.admin.model.entity.MetaDataDO;
import org.apache.shenyu.common.dto.convert.DivideUpstream;
import org.apache.shenyu.common.dto.convert.rule.RuleHandle;
import org.apache.shenyu.common.dto.convert.rule.RuleHandleFactory;
import org.apache.shenyu.common.enums.MatchModeEnum;
import org.apache.shenyu.common.enums.OperatorEnum;
import org.apache.shenyu.common.enums.ParamTypeEnum;
import org.apache.shenyu.common.enums.PluginEnum;
import org.apache.shenyu.common.enums.SelectorTypeEnum;
import org.apache.shenyu.register.common.dto.MetaDataRegisterDTO;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Abstract strategy.
 */
public abstract class AbstractShenyuClientRegisterServiceImpl implements ShenyuClientRegisterServiceFactory {

    /**
     * save or update meta data.
     *
     * @param exist       has been exist meta data {@link MetaDataDO}
     * @param metaDataDTO meta data dto {@link MetaDataRegisterDTO}
     */
    protected abstract void saveOrUpdateMetaData(MetaDataDO exist, MetaDataRegisterDTO metaDataDTO);

    /**
     * handler selector.
     *
     * @param metaDataDTO meta data register dto {@link MetaDataRegisterDTO}
     * @return primary key of selector
     */
    protected abstract String handlerSelector(MetaDataRegisterDTO metaDataDTO);

    /**
     * handler rule.
     *
     * @param selectorId  the primary key of selector
     * @param metaDataDTO meta data dto {@link MetaDataRegisterDTO}
     * @param exist       has been exist meta data {@link MetaDataDO}
     */
    protected abstract void handlerRule(String selectorId, MetaDataRegisterDTO metaDataDTO, MetaDataDO exist);

    @Override
    public String registerURI(final String contextPath, final List<String> uriList) {
        return null;
    }

    protected SelectorDTO registerSelector(final String contextPath, final String pluginId) {
        // 构建默认的selector
        SelectorDTO selectorDTO = buildDefaultSelectorDTO(contextPath);
        // 设置该selector所属的plugin
        selectorDTO.setPluginId(pluginId);
        // 构建该selector默认的condition信息
        selectorDTO.setSelectorConditions(buildDefaultSelectorConditionDTO(contextPath));
        return selectorDTO;
    }

    /**构建规则DTO*/
    protected RuleDTO registerRule(final String selectorId, final String path, final String pluginName, final String ruleName) {
        // TODO 什么作用？
        RuleHandle ruleHandle = pluginName.equals(PluginEnum.CONTEXT_PATH.getName())
                ? RuleHandleFactory.ruleHandle(pluginName, buildContextPath(path)) : RuleHandleFactory.ruleHandle(pluginName, path);
        RuleDTO ruleDTO = RuleDTO.builder()
                .selectorId(selectorId)
                .name(ruleName)
                .matchMode(MatchModeEnum.AND.getCode())
                .enabled(Boolean.TRUE)
                .loged(Boolean.TRUE)
                .sort(1)
                .handle(ruleHandle.toJson())
                .build();
        // rule的condition，默认是URI匹配
        RuleConditionDTO ruleConditionDTO = RuleConditionDTO.builder()
                .paramType(ParamTypeEnum.URI.getName())
                .paramName("/")
                // eg: /http/order/path/** 或 /http/order/findById
                .paramValue(path)
                .build();
        // 如果path里包含了 * 则表示需要match匹配
        if (path.indexOf("*") > 1) {
            ruleConditionDTO.setOperator(OperatorEnum.MATCH.getAlias());
        } else {
            // 否则用等于区匹配
            ruleConditionDTO.setOperator(OperatorEnum.EQ.getAlias());
        }
        ruleDTO.setRuleConditions(Collections.singletonList(ruleConditionDTO));
        return ruleDTO;
    }

    protected List<SelectorConditionDTO> buildDefaultSelectorConditionDTO(final String contextPath) {
        SelectorConditionDTO selectorConditionDTO = new SelectorConditionDTO();
        // 默认是使用URI匹配
        selectorConditionDTO.setParamType(ParamTypeEnum.URI.getName());
        selectorConditionDTO.setParamName("/");
        // 默认使用match
        selectorConditionDTO.setOperator(OperatorEnum.MATCH.getAlias());
        // 默认对 contextPath下的所有请求有效
        selectorConditionDTO.setParamValue(contextPath + "/**");
        return Collections.singletonList(selectorConditionDTO);
    }

    protected String buildContextPath(final String path) {
        String split = "/";
        // 通过 / 拆分 path
        String[] splitList = StringUtils.split(path, split);
        // 取path的第一个作为 contextPath
        if (splitList.length != 0) {
            return split.concat(splitList[0]);
        }
        return split;
    }

    /**构建默认的selector*/
    protected SelectorDTO buildDefaultSelectorDTO(final String name) {
        return SelectorDTO.builder()
                .name(name)
                // 默认是使用custom类型
                .type(SelectorTypeEnum.CUSTOM_FLOW.getCode())
                // 使用and匹配
                .matchMode(MatchModeEnum.AND.getCode())
                // 开启该selector
                .enabled(Boolean.TRUE)
                // 默认打印该selector的匹配日志
                .loged(Boolean.TRUE)
                // 默认经过该selector匹配后继续向下一个plugin传递
                .continued(Boolean.TRUE)
                .sort(1)
                .build();
    }

    protected DivideUpstream buildDivideUpstream(final String uri) {
        return DivideUpstream.builder().upstreamHost("localhost").protocol("http://").upstreamUrl(uri).weight(50).build();
    }

    protected boolean checkPathExist(final MetaDataDO existMetaDataDO, final MetaDataRegisterDTO dto) {
        return Objects.nonNull(existMetaDataDO)
                && (!existMetaDataDO.getMethodName().equals(dto.getMethodName())
                || !existMetaDataDO.getServiceName().equals(dto.getServiceName()));
    }
}
