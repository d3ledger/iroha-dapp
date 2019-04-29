package jp.co.soramitsu.dapp.cache

import jp.co.soramitsu.dapp.helper.CacheManager
import org.springframework.stereotype.Component

@Component
class DefaultCacheManager : CacheManager {

    private val cache = HashMap<String, Any?>()

    override fun put(key: String?, value: Any?): Any? {
        checkArgument(key)
        return cache.put(key!!, value)
    }

    override fun get(key: String?): Any? {
        checkArgument(key)
        return cache[key]
    }

    override fun remove(key: String?): Any? {
        checkArgument(key)
        return cache.remove(key)
    }

    override fun contains(key: String?): Boolean {
        checkArgument(key)
        return cache.contains(key)
    }

    private fun checkArgument(key: String?) {
        if (key == null) {
            throw IllegalArgumentException("Null keys are not allowed")
        }
    }
}
