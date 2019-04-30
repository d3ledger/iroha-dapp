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
     * @when contract command type happens in Iroha [CREATE_ACCOUNT]
     * @then contract is executed and the asset added to the dapprepo@dapp
     */
    @Test
    internal fun test() {
        environment.service.init()

        val toriiResponse = environment.irohaAPI.transaction(
            Transaction.builder(dappRepoAccountId)
                .createAccount(
                    "test" + Random().nextInt(Integer.MAX_VALUE).toString(),
                    dappDomain,
                    environment.keyPair.public
                )
                .sign(environment.keyPair)
                .build()
        ).blockingLast()

        assertEquals(Endpoint.TxStatus.COMMITTED, toriiResponse.txStatus)

        Thread.sleep(5000)

        assertEquals(
            "1",
            environment.queryAPI.getAccountAssets(dappRepoAccountId).getAccountAssets(0).balance
        )
    }
}
