/*
 * Copyright Soramitsu Co., Ltd. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package jp.co.soramitsu.dapp.config

import com.d3.commons.config.RMQConfig
import com.d3.commons.config.loadRawLocalConfigs
import com.d3.commons.sidechain.iroha.ReliableIrohaChainListener
import com.d3.commons.util.createPrettySingleThreadPool
import jp.co.soramitsu.iroha.java.IrohaAPI
import jp.co.soramitsu.iroha.java.QueryAPI
import jp.co.soramitsu.iroha.java.Utils
import mu.KLogging
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.net.URI

@Configuration
class DappContextConfiguration {

    private val dappConfig = loadRawLocalConfigs(DAPP_NAME, DappConfig::class.java, "dapp.properties")
    private val rmqConfig = loadRawLocalConfigs(DAPP_NAME, RMQConfig::class.java, "rmq.properties")

    @Bean
    fun dappKeyPair() = Utils.parseHexKeypair(
        dappConfig.pubKey,
        dappConfig.privKey
    )

    @Bean
    fun irohaApi() = IrohaAPI(URI(dappConfig.irohaUrl))

    @Bean
    fun dAppAccountId() = dappConfig.accountId

    @Bean
    fun queryApi(): QueryAPI {
        return QueryAPI(irohaApi(), dAppAccountId(), dappKeyPair())
    }

    @Bean
    fun chainListener() = ReliableIrohaChainListener(
        rmqConfig,
        dappConfig.queue,
        createPrettySingleThreadPool(DAPP_NAME, "chain-listener")
    )

    @Bean
    fun repositoryAccountId() = dappConfig.repository

    @Bean
    fun repositorySetterId() = dappConfig.repositorySetter

    /**
     * Logger
     */
    companion object : KLogging()
}
