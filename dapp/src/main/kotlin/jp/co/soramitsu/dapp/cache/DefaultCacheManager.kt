/*
 * Copyright Soramitsu Co., Ltd. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package jp.co.soramitsu.dapp.cache

import jp.co.soramitsu.dapp.helper.CacheManager
import org.springframework.stereotype.Component

@Component
class DefaultCacheManager : CacheManager {

    private val cache = HashMap<String, Any?>()

    @Synchronized
    override fun put(key: String, value: Any?): Any? {
        return cache.put(key, value)
    }

    @Synchronized
    override fun get(key: String): Any? {
        return cache[key]
    }

    @Synchronized
    override fun remove(key: String): Any? {
        return cache.remove(key)
    }

    @Synchronized
    override fun contains(key: String): Boolean {
        return cache.contains(key)
    }
}
