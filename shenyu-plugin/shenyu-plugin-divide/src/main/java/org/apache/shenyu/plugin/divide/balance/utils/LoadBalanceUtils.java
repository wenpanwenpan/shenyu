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

package org.apache.shenyu.plugin.divide.balance.utils;

import org.apache.shenyu.plugin.divide.balance.LoadBalance;
import org.apache.shenyu.common.dto.convert.DivideUpstream;
import org.apache.shenyu.spi.ExtensionLoader;

import java.util.List;

/**
 * The type Load balance utils.
 */
public class LoadBalanceUtils {

    /**
     * Selector divide upstream.
     *
     * @param upstreamList the upstream list
     * @param algorithm    the loadBalance algorithm
     * @param ip           the ip
     * @return the divide upstream
     */
    public static DivideUpstream selector(final List<DivideUpstream> upstreamList, final String algorithm, final String ip) {
        // 获取负载均衡算法
        LoadBalance loadBalance = ExtensionLoader.getExtensionLoader(LoadBalance.class).getJoin(algorithm);
        // 根据负载均衡算法选取一个
        return loadBalance.select(upstreamList, ip);
    }
}
