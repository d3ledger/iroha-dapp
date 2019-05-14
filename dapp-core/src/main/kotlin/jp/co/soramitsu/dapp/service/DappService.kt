/*
 * Copyright D3 Ledger, Inc. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package jp.co.soramitsu.dapp.service

import com.google.common.util.concurrent.ThreadFactoryBuilder
import io.reactivex.schedulers.Schedulers
import jp.co.soramitsu.dapp.AbstractDappScript
import jp.co.soramitsu.dapp.helper.CacheManager
import jp.co.soramitsu.dapp.parser.ContractParser.Companion.parse
import jp.co.soramitsu.iroha.java.IrohaAPI
import jp.co.soramitsu.iroha.java.Utils
import mu.KLogging
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import java.security.KeyPair
import java.util.concurrent.Executors

@Component
class DappService(
    @Autowired
    private val irohaAPI: IrohaAPI,
    @Autowired
    private val dappKeyPair: KeyPair,
    @Autowired
    private val observableSource: CommandObservableSource,
    @Autowired
    private val cacheManager: CacheManager,
    @Autowired
    private val contractsRepositoryMonitor: ContractsRepositoryMonitor
) {
    private val scheduler = Schedulers.from(
        Executors.newFixedThreadPool(
            2,
            ThreadFactoryBuilder().setNameFormat("dapp-service-%d").build()
        )
    )

    private val contracts: MutableMap<String, AbstractDappScript> = mutableMapOf()

    init {
        monitorDisabled()
        monitorNew()
    }

    private fun parseContract(name: String, script: String): Pair<String, AbstractDappScript>? {
        return try {
            name to parse(Utils.irohaUnEscape(script), irohaAPI, dappKeyPair, cacheManager)
        } catch (e: Exception) {
            logger.warn("Couldn't create $name contract from script", e)
            null
        }
    }

    private fun monitorNew() {
        contractsRepositoryMonitor.getNewContractsObservable()
            .observeOn(scheduler)
            .subscribe { (name, script) ->
                logger.info("Got new contract to run: $name")
                safePut(parseContract(name, script))
            }
        contractsRepositoryMonitor.initObservable()
    }

    private fun monitorDisabled() {
        contractsRepositoryMonitor.getDisabledContractsSubject()
            .observeOn(scheduler)
            .subscribe(this::safeDelete)
    }

    @Synchronized
    private fun safePut(contractPair: Pair<String, AbstractDappScript>?) {
        if (contractPair != null) {
            val contractName = contractPair.first
            if (contracts.containsKey(contractName)) {
                logger.info("Removing the old version of $contractName contract")
                safeDelete(contractName)
            }
            val contractObject = contractPair.second
            contracts[contractName] = contractObject
            contractObject.commandsToMonitor?.forEach { type ->
                contractObject.addCommandObservable(observableSource.getObservable(type))
            }
            logger.info("Inserted $contractName")
        }
    }

    @Synchronized
    private fun safeDelete(name: String) {
        logger.info("Removing $name contract")
        contracts.remove(name)?.close()
    }

    companion object : KLogging()
}
