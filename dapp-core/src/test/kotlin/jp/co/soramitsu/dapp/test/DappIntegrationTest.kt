/*
 * Copyright D3 Ledger, Inc. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package jp.co.soramitsu.dapp.test

import iroha.protocol.Endpoint
import jp.co.soramitsu.dapp.test.environment.*
import jp.co.soramitsu.iroha.java.Transaction
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.math.BigDecimal
import java.util.*

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DappIntegrationTest {

    private val environment = DappTestEnvironment()

    @BeforeAll
    fun setUp() {
        environment.init()
    }

    @AfterAll
    fun tearDown() {
        environment.close()
    }

    /**
     * @given dApp instance running with all hte infrastructure including contracts
     * @when test contract is enabled and command type [CREATE_ACCOUNT] happens in Iroha
     * @then test contract is executed and the asset added to the dapprepo@dapp
     * @when test contract is disabled and command type [CREATE_ACCOUNT] happens in Iroha
     * @then test contract is not executed and the asset balance stays the same as before
     */
    @Test
    internal fun test() {
        val initialBalance = getBalance(assetId)

        environment.enableContract(testContractName)

        Thread.sleep(1000)

        var toriiResponse = createNewAccount()
        assertEquals(Endpoint.TxStatus.COMMITTED, toriiResponse.txStatus)

        Thread.sleep(5000)

        val balanceAfterOneAccountCreation = getBalance(assetId)
        assertEquals(initialBalance.plus(BigDecimal.ONE), balanceAfterOneAccountCreation)

        environment.disableContract(testContractName)

        Thread.sleep(1000)

        toriiResponse = createNewAccount()
        assertEquals(Endpoint.TxStatus.COMMITTED, toriiResponse.txStatus)

        Thread.sleep(5000)

        assertEquals(balanceAfterOneAccountCreation, getBalance(assetId))
    }

    /**
     * @given dApp instance running with all hte infrastructure including contracts
     * @when exchanger contract is enabled and a proper command of type [TRANSFER_ASSET] happens in Iroha
     * @then exchanger contract is executed and the converted asset sent to the dapprepo@dapp
     * @when exchanger contract is disabled and a proper command type [TRANSFER_ASSET] happens in Iroha
     * @then exchanger contract is not executed and the asset just spent in favor of exchanger@dapp
     */
    @Test
    internal fun exchangerTest() {
        val initialAssetBalance = getBalance(assetId)
        val initialAnotherBalance = getBalance(anotherAssetId)

        environment.enableContract(exchangerContractName)

        Thread.sleep(1000)

        var toriiResponse = sendExchangeOfOneAnotherAsset()
        assertEquals(Endpoint.TxStatus.COMMITTED, toriiResponse?.txStatus)

        val anotherAssetAfterFirstExchange = getBalance(anotherAssetId)
        assertEquals(initialAnotherBalance.minus(BigDecimal.ONE), anotherAssetAfterFirstExchange)

        Thread.sleep(5000)

        val assetAfterFirstExchange = getBalance(assetId)
        assertTrue(assetAfterFirstExchange > initialAssetBalance)

        environment.disableContract(exchangerContractName)

        Thread.sleep(1000)

        toriiResponse = sendExchangeOfOneAnotherAsset()
        assertEquals(Endpoint.TxStatus.COMMITTED, toriiResponse?.txStatus)

        Thread.sleep(5000)

        val anotherAssetAfterSecondExchange = getBalance(anotherAssetId)
        assertEquals(anotherAssetAfterFirstExchange.minus(BigDecimal.ONE), anotherAssetAfterSecondExchange)

        val assetAfterSecondExchange = getBalance(assetId)
        assertEquals(assetAfterSecondExchange, assetAfterFirstExchange)
    }

    private fun createNewAccount(): Endpoint.ToriiResponse {
        return environment.irohaAPI.transaction(
            Transaction.builder(dappRepoAccountId)
                .createAccount(
                    "test" + Random().nextInt(Integer.MAX_VALUE).toString(),
                    dappDomain,
                    environment.keyPair.public
                )
                .sign(environment.keyPair)
                .build()
        ).blockingLast()
    }

    private fun sendExchangeOfOneAnotherAsset(): Endpoint.ToriiResponse? {
        return environment.irohaAPI.transaction(
            Transaction.builder(dappRepoAccountId)
                .transferAsset(
                    dappRepoAccountId,
                    exchangerAccountId,
                    anotherAssetId,
                    assetId,
                    "1"
                )
                .sign(environment.keyPair)
                .build()
        ).blockingLast()
    }

    private fun getBalance(assetName: String): BigDecimal {
        val assets = environment.queryAPI.getAccountAssets(dappRepoAccountId)
        val filter = assets.accountAssetsList.filter { asset -> asset.assetId == assetName }
        return if (filter.isEmpty()) {
            BigDecimal.ZERO
        } else {
            BigDecimal(filter[0]?.balance ?: "0")
        }
    }
}
