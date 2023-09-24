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

package org.apache.shenyu.plugin.base;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.shenyu.common.dto.PluginData;
import org.apache.shenyu.common.dto.RuleData;
import org.apache.shenyu.common.dto.SelectorData;
import org.apache.shenyu.common.enums.SelectorTypeEnum;
import org.apache.shenyu.plugin.api.ShenyuPlugin;
import org.apache.shenyu.plugin.api.ShenyuPluginChain;
import org.apache.shenyu.plugin.base.cache.BaseDataCache;
import org.apache.shenyu.plugin.base.condition.strategy.MatchStrategyFactory;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.Collection;
import java.util.List;
import java.util.Objects;

/**
 * abstract shenyu plugin please extends.
 */
@RequiredArgsConstructor
@Slf4j
public abstract class AbstractShenyuPlugin implements ShenyuPlugin {

    /**
     * this is Template Method child has Implement your own logic.
     *
     * @param exchange exchange the current server exchange {@linkplain ServerWebExchange}
     * @param chain    chain the current chain  {@linkplain ServerWebExchange}
     * @param selector selector    {@linkplain SelectorData}
     * @param rule     rule    {@linkplain RuleData}
     * @return {@code Mono<Void>} to indicate when request handling is complete
     */
    protected abstract Mono<Void> doExecute(ServerWebExchange exchange, ShenyuPluginChain chain, SelectorData selector, RuleData rule);

    /**
     * Process the Web request and (optionally) delegate to the next
     * {@code ShenyuPlugin} through the given {@link ShenyuPluginChain}.
     * 看起来应该是如果没有命中selector或rule都不会执行当前插件，而是直接执行下一个插件
     * 1、如果根据插件名称没有找到当前插件，或插件没有开启，则直接执行下一个插件
     * 2、如果没有selector或selector的condition不匹配，则直接执行下一个插件
     * 3、如果没有rule或者rule的condition不匹配，则直接执行下一个插件
     * 执行当前plugin逻辑
     *
     * @param exchange the current server exchange
     * @param chain    provides a way to delegate to the next plugin
     * @return {@code Mono<Void>} to indicate when request processing is complete
     */
    @Override
    public Mono<Void> execute(final ServerWebExchange exchange, final ShenyuPluginChain chain) {
        String pluginName = named();
        // 通过插件名称获取插件数据
        PluginData pluginData = BaseDataCache.getInstance().obtainPluginData(pluginName);
        // 如果没有插件数据或者插件未开启，则直接执行下一个插件
        if (pluginData != null && pluginData.getEnabled()) {
            // 获取该插件的所有selectors
            final Collection<SelectorData> selectors = BaseDataCache.getInstance().obtainSelectorData(pluginName);
            // 如果没有selector，则直接调用下一个plugin
            if (CollectionUtils.isEmpty(selectors)) {
                return handleSelectorIfNull(pluginName, exchange, chain);
            }
            // 从多个selector中选取一个出来(这里就在进行selector的condition匹配)
            SelectorData selectorData = matchSelector(exchange, selectors);
            // 没有命中selector，则直接执行下一个plugin
            if (Objects.isNull(selectorData)) {
                return handleSelectorIfNull(pluginName, exchange, chain);
            }
            selectorLog(selectorData, pluginName);
            // 通过这个selector获取这个selector下的所有rule
            List<RuleData> rules = BaseDataCache.getInstance().obtainRuleData(selectorData.getId());
            // 没有获取到该selector下的rule，则直接调用下一个plugin
            if (CollectionUtils.isEmpty(rules)) {
                return handleRuleIfNull(pluginName, exchange, chain);
            }
            RuleData rule;
            // 如果selector的type是full，那么直接获取最后一个规则，相当于不进行rule匹配了（这里直接获取
            //  最后一个rule没有什么特别用意，只是为了不走进下面的 Objects.isNull(rule) 逻辑）
            if (selectorData.getType() == SelectorTypeEnum.FULL_FLOW.getCode()) {
                //get last
                rule = rules.get(rules.size() - 1);
            } else {
                // 这里就在进行rule的condition匹配
                rule = matchRule(exchange, rules);
            }
            if (Objects.isNull(rule)) {
                // 没有匹配的规则，则不执行该plugin，直接执行下一个plugin
                return handleRuleIfNull(pluginName, exchange, chain);
            }
            ruleLog(rule, pluginName);
            // 执行当前plugin
            return doExecute(exchange, chain, selectorData, rule);
        }
        // 执行下一个插件
        return chain.execute(exchange);
    }

    protected Mono<Void> handleSelectorIfNull(final String pluginName, final ServerWebExchange exchange, final ShenyuPluginChain chain) {
        return chain.execute(exchange);
    }

    protected Mono<Void> handleRuleIfNull(final String pluginName, final ServerWebExchange exchange, final ShenyuPluginChain chain) {
        return chain.execute(exchange);
    }

    private SelectorData matchSelector(final ServerWebExchange exchange, final Collection<SelectorData> selectors) {
        return selectors.stream()
                .filter(selector -> selector.getEnabled() && filterSelector(selector, exchange))
                .findFirst().orElse(null);
    }

    private Boolean filterSelector(final SelectorData selector, final ServerWebExchange exchange) {
        if (selector.getType() == SelectorTypeEnum.CUSTOM_FLOW.getCode()) {
            // 如果这个selector没有condition，则不选取
            if (CollectionUtils.isEmpty(selector.getConditionList())) {
                return false;
            }
            // 如果该selector有匹配条件，那么进行匹配
            return MatchStrategyFactory.match(selector.getMatchMode(), selector.getConditionList(), exchange);
        }
        // 如果是全局插件，则直接选中该插件
        return true;
    }

    private RuleData matchRule(final ServerWebExchange exchange, final Collection<RuleData> rules) {
        return rules.stream().filter(rule -> filterRule(rule, exchange)).findFirst().orElse(null);
    }

    private Boolean filterRule(final RuleData ruleData, final ServerWebExchange exchange) {
        // 如果rule是开启的，则进行rule的condition匹配
        return ruleData.getEnabled() && MatchStrategyFactory.match(ruleData.getMatchMode(), ruleData.getConditionDataList(), exchange);
    }

    private void selectorLog(final SelectorData selectorData, final String pluginName) {
        if (selectorData.getLogged()) {
            log.info("{} selector success match , selector name :{}", pluginName, selectorData.getName());
        }
    }

    private void ruleLog(final RuleData ruleData, final String pluginName) {
        if (ruleData.getLoged()) {
            log.info("{} rule success match , rule name :{}", pluginName, ruleData.getName());
        }
    }
}
