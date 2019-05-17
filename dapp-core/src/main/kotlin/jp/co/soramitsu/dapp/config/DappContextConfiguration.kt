/*
 * Copyright D3 Ledger, Inc. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package jp.co.soramitsu.dapp.config

import com.d3.commons.config.loadRawLocalConfigs
import com.d3.commons.sidechain.iroha.util.ModelUtil.loadKeypair
import com.d3.commons.util.createPrettySingleThreadPool
import jp.co.soramitsu.dapp.listener.ReliableIrohaChainListener
import jp.co.soramitsu.iroha.java.IrohaAPI
import jp.co.soramitsu.iroha.java.QueryAPI
import mu.KLogging
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.net.URI

@Configuration
class DappContextConfiguration {

    private val dappConfig = loadRawLocalConfigs(DAPP_NAME, DappConfig::class.java, "dapp.properties")

    @Bean
    fun dappKeyPair() = loadKeypair(dappConfig.pubKeyPath, dappConfig.privKeyPath).get()

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
        dappConfig.rmqHost,
        dappConfig.rmqPort,
        dappConfig.rmqExchange,
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
