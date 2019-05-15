/*
 * Copyright D3 Ledger, Inc. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package jp.co.soramitsu.dapp.test

import iroha.protocol.Endpoint
import jp.co.soramitsu.dapp.test.environment.DappTestEnvironment
import jp.co.soramitsu.dapp.test.environment.dappDomain
import jp.co.soramitsu.dapp.test.environment.dappRepoAccountId
import jp.co.soramitsu.iroha.java.Transaction
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.util.*

class DappIntegrationTest {

    private val environment = DappTestEnvironment()

    @BeforeEach
    internal fun setUp() {
        environment.init()
    }

    @AfterEach
    internal fun tearDown() {
        environment.close()
    }

    /**
     * @given dApp instance running with all hte infrastructure including one contract
     * @when contract is enabled and command type [CREATE_ACCOUNT] happens in Iroha
     * @then contract is executed and the asset added to the dapprepo@dapp
     * @when contract is disabled and command type [CREATE_ACCOUNT] happens in Iroha
     * @then contract is not executed and the asset balance stays the same as before
     */
    @Test
    internal fun test() {
        val initialBalance = getXorBalance()

        environment.enableTestContract()

        Thread.sleep(1000)

        var toriiResponse = createNewAccount()
        assertEquals(Endpoint.TxStatus.COMMITTED, toriiResponse.txStatus)

        Thread.sleep(5000)

        val balanceAfterOneAccountCreation = getXorBalance()
        assertEquals(initialBalance.plus(BigDecimal.ONE), balanceAfterOneAccountCreation)

        environment.disableTestContract()

        Thread.sleep(1000)

        toriiResponse = createNewAccount()
        assertEquals(Endpoint.TxStatus.COMMITTED, toriiResponse.txStatus)

        Thread.sleep(5000)

        assertEquals(balanceAfterOneAccountCreation, getXorBalance())
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

    private fun getXorBalance(): BigDecimal {
        val assets = environment.queryAPI.getAccountAssets(dappRepoAccountId)
        return if (assets.accountAssetsCount == 0) {
            BigDecimal.ZERO
        } else
            BigDecimal(assets.getAccountAssets(0)?.balance ?: "0")
    }
}
