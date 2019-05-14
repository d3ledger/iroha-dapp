/*
 * Copyright D3 Ledger, Inc. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package jp.co.soramitsu.dapp.service

import com.google.common.util.concurrent.ThreadFactoryBuilder
import com.google.gson.JsonParser
import io.reactivex.Observable
import io.reactivex.schedulers.Schedulers
import io.reactivex.subjects.PublishSubject
import jp.co.soramitsu.dapp.listener.ReliableIrohaChainListener
import jp.co.soramitsu.iroha.java.QueryAPI
import mu.KLogging
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import java.util.concurrent.Executors

@Component
class ContractsRepositoryMonitor(
    @Autowired
    private val chainListener: ReliableIrohaChainListener,
    @Autowired
    private val queryAPI: QueryAPI,
    @Autowired
    private val dAppAccountId: String,
    @Autowired
    private val repositoryAccountId: String,
    @Autowired
    private val repositorySetterId: String
) {
    private val jsonParser = JsonParser()
    private val newContractsSubject: PublishSubject<Pair<String, String>> = PublishSubject.create()
    private val disabledContractsSubject: PublishSubject<String> = PublishSubject.create()
    private val scheduler = Schedulers.from(
        Executors.newSingleThreadExecutor(
            ThreadFactoryBuilder().setNameFormat("observable-contracts-%d").build()
        )
    )

    fun initObservable() {
        logger.info("Subscribed to contracts status updates")
        chainListener
            .getBlockObservable()
            .observeOn(scheduler)
            .subscribe { block ->
                block.blockV1.payload.transactionsList
                    .forEach { transaction ->
                        transaction.payload.reducedPayload.commandsList.stream()
                            .filter { command ->
                                command.hasSetAccountDetail()
                            }
                            .map { command ->
                                command.setAccountDetail
                            }
                            .filter { setAccountDetail ->
                                setAccountDetail.accountId == dAppAccountId
                            }
                            .forEach { setAccountDetail ->
                                val contractName = setAccountDetail.key
                                if (isContractEnabled(contractName)) {
                                    logger.info("New contract has been enabled $contractName")
                                    newContractsSubject.onNext(
                                        Pair(
                                            contractName,
                                            retrieveContract(contractName)
                                        )
                                    )
                                } else {
                                    logger.info("A contract has been disabled $contractName")
                                    disabledContractsSubject.onNext(contractName)
                                }
                            }
                    }
            }
    }

    fun isContractEnabled(name: String): Boolean {
        return try {
            jsonParser.parse(
                queryAPI.getAccountDetails(
                    dAppAccountId,
                    repositorySetterId,
                    name
                )
            ).asJsonObject.get(repositorySetterId).asJsonObject.get(name).asString!!.toBoolean()
        } catch (e: Exception) {
            logger.error("Error during checking status of $name contract. Won't run it: ", e)
            return false
        }
    }

    private fun retrieveContract(name: String): String {
        return jsonParser.parse(
            queryAPI.getAccountDetails(
                repositoryAccountId,
                repositorySetterId,
                name
            )
        ).asJsonObject
            .get(repositorySetterId)
            .asJsonObject
            .get(name)
            .asString
    }

    fun getNewContractsObservable(): Observable<Pair<String, String>> {
        return newContractsSubject
    }

    fun getDisabledContractsSubject(): Observable<String> {
        return disabledContractsSubject
    }

    companion object : KLogging()
}