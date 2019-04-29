package jp.co.soramitsu.dapp.test

import jp.co.soramitsu.dapp.test.environment.DappTestEnvironment
import jp.co.soramitsu.dapp.test.environment.dappDomain
import jp.co.soramitsu.dapp.test.environment.dappRepoAccountId
import jp.co.soramitsu.iroha.java.Transaction
import jp.co.soramitsu.iroha.testcontainers.detail.GenesisBlockBuilder
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

    @Test
    internal fun test() {
        environment.service.init()

        Thread.sleep(5000)

        environment.irohaAPI.transaction(
            Transaction.builder(dappRepoAccountId)
                .createAccount(
                    "test" + Random().nextInt(Integer.MAX_VALUE).toString(),
                    dappDomain,
                    GenesisBlockBuilder.defaultKeyPair.public
                )
                .sign(GenesisBlockBuilder.defaultKeyPair)
                .build()
        )

        Thread.sleep(10000)

        assertEquals(
            "1",
            environment.queryAPI.getAccountAssets(dappRepoAccountId).getAccountAssets(0).balance
        )
    }
}
