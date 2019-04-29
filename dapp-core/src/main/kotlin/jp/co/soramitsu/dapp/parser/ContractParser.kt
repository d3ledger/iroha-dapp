package jp.co.soramitsu.dapp.parser

import groovy.lang.GroovyClassLoader
import jp.co.soramitsu.dapp.AbstractDappScript
import jp.co.soramitsu.dapp.helper.CacheManager
import jp.co.soramitsu.iroha.java.IrohaAPI
import java.security.KeyPair

/**
 * Contract Groovy parser
 */
sealed class ContractParser {

    companion object {
        /**
         * Parses given Groovy script
         * @param script - groovy script to parse
         * @param irohaAPI - Iroha API to be used in tx creation
         * @param dappKeyPair - keypair to be used in tx creation
         * @param cacheManager - cache manager to be used by the contract
         * @throws IllegalArgumentException if script is empty or script class doesn't implement DappInterface
         * or in case of invalid(not compilable) script
         * @return parsed DappInterface instance
         */
        fun parse(
            script: String,
            irohaAPI: IrohaAPI,
            dappKeyPair: KeyPair,
            cacheManager: CacheManager
        ): AbstractDappScript {
            if (script.isEmpty()) {
                throw IllegalArgumentException("Cannot parse empty script")
            }
            val scriptClass: Class<Any>
            try {
                scriptClass = GroovyClassLoader().parseClass(script)
            } catch (e: Exception) {
                throw IllegalArgumentException("Invalid dApp script", e)
            }
            val instance = scriptClass
                .getConstructor(IrohaAPI::class.java, KeyPair::class.java, CacheManager::class.java)
                .newInstance(irohaAPI, dappKeyPair, cacheManager)
            when (instance) {
                is AbstractDappScript -> return instance
                else -> throw IllegalArgumentException(
                    "Script class must extend " + AbstractDappScript::class.java.simpleName
                )
            }
        }
    }
}
