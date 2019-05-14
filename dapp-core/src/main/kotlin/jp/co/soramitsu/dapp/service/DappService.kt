/*
 * Copyright D3 Ledger, Inc. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package jp.co.soramitsu.dapp.service

import com.google.common.util.concurrent.ThreadFactoryBuilder
import com.google.gson.JsonParser
import io.reactivex.schedulers.Schedulers
import jp.co.soramitsu.dapp.AbstractDappScript
import jp.co.soramitsu.dapp.helper.CacheManager
import jp.co.soramitsu.dapp.parser.ContractParser.Companion.parse
import jp.co.soramitsu.iroha.java.IrohaAPI
import jp.co.soramitsu.iroha.java.QueryAPI
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
    private val queryAPI: QueryAPI,
    @Autowired
    private val repositoryAccountId: String,
    @Autowired
    private val repositorySetterId: String,
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
        retrieveContracts()
        monitorDisabled()
        monitorNew()
    }

    private fun retrieveContracts() {
        JsonParser().parse(
            queryAPI.getAccountDetails(
                repositoryAccountId,
                repositorySetterId,
                null
            )
        ).asJsonObject
            .get(repositorySetterId)
            .asJsonObject
            .entrySet()
            .mapNotNull { (name, script) ->
                parseContract(name, script.asString)
            }
            .forEach { contractPair ->
                safePut(contractPair)
            }
    }

    fun init() {
        contracts.forEach { (_, contract) ->
            contract.commandsToMonitor.forEach { type ->
                contract.addCommandObservable(observableSource.getObservable(type))
            }
        }
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
            .subscribe { newContractName ->
                safePut(
                    parseContract(
                        newContractName,
                        queryAPI.getAccountDetails(
                            repositoryAccountId,
                            repositorySetterId,
                            newContractName
                        )
                    )
                )
            }

    }

    private fun monitorDisabled() {
        contractsRepositoryMonitor.getDisabledContractsSubject()
            .observeOn(scheduler)
            .subscribe(this::safeDelete)
    }

    @Synchronized
    private fun safePut(contractPair: Pair<String, AbstractDappScript>?) {
        if (contractPair != null) {
            contracts[contractPair.first] = contractPair.second
        }
    }

    @Synchronized
    private fun safeDelete(name: String) {
        contracts.remove(name)?.close()
    }

    companion object : KLogging()
}
