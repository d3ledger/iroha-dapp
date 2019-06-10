/*
 * Copyright Soramitsu Co., Ltd. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package jp.co.soramitsu.dapp.test.environment

import com.d3.commons.config.RMQConfig
import com.d3.commons.sidechain.iroha.ReliableIrohaChainListener
import com.d3.commons.util.createPrettySingleThreadPool
import com.google.common.io.Files
import iroha.protocol.BlockOuterClass
import iroha.protocol.Endpoint
import jp.co.soramitsu.crypto.ed25519.Ed25519Sha3
import jp.co.soramitsu.dapp.block.BlockProcessor
import jp.co.soramitsu.dapp.cache.DefaultCacheManager
import jp.co.soramitsu.dapp.config.DAPP_NAME
import jp.co.soramitsu.dapp.service.CommandObservableSource
import jp.co.soramitsu.dapp.service.ContractsRepositoryMonitor
import jp.co.soramitsu.dapp.service.DappService
import jp.co.soramitsu.iroha.java.IrohaAPI
import jp.co.soramitsu.iroha.java.QueryAPI
import jp.co.soramitsu.iroha.java.Transaction
import jp.co.soramitsu.iroha.java.Utils
import jp.co.soramitsu.iroha.java.detail.Const
import jp.co.soramitsu.iroha.testcontainers.IrohaContainer
import jp.co.soramitsu.iroha.testcontainers.PeerConfig
import jp.co.soramitsu.iroha.testcontainers.detail.GenesisBlockBuilder
import org.testcontainers.containers.FixedHostPortGenericContainer
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.InternetProtocol
import java.io.Closeable
import java.io.File
import java.nio.charset.Charset
import java.security.KeyPair
import java.util.*


class KGenericFixedContainer(imageName: String) :
    FixedHostPortGenericContainer<KGenericFixedContainer>(imageName)

private const val DEFAULT_RMQ_PORT = 5672
private const val DEFAULT_IROHA_PORT = 50051
const val dappDomain = "dapp"
const val dappInstanceAccountId = "dapp1" + Const.accountIdDelimiter + dappDomain
const val dappRepoAccountId = "dapprepo" + Const.accountIdDelimiter + dappDomain
const val exchangerAccountId = "exchanger" + Const.accountIdDelimiter + dappDomain
val irohaKeyPair: KeyPair = Ed25519Sha3().generateKeypair()
const val rmqContainerName = "rmq"
const val irohaContainerName = "iroha"
val rmq = KGenericFixedContainer("rabbitmq:3-management").withExposedPorts(DEFAULT_RMQ_PORT)
    .withFixedExposedPort(DEFAULT_RMQ_PORT, DEFAULT_RMQ_PORT)
    .withCreateContainerCmdModifier { it.withName(rmqContainerName) }!!
val iroha = IrohaContainer().withPeerConfig(peerConfig)!!
var postgresDockerContainer: GenericContainer<*> = GenericContainer<Nothing>()
var irohaContainer: GenericContainer<*> = GenericContainer<Nothing>()
val chainAdapter = KGenericFixedContainer("nexus.iroha.tech:19002/d3-deploy/chain-adapter:1.0.0")
const val rmqExchange = "iroha"
const val testContractName = "testcontract"
const val exchangerContractName = "exchangercontract"
const val assetId = "asset#$dappDomain"
const val anotherAssetId = "anotherasset#$dappDomain"

val genesisBlock: BlockOuterClass.Block
    get() {
        return GenesisBlockBuilder()
            .addDefaultTransaction()
            .addTransaction(
                Transaction.builder(dappRepoAccountId)
                    .createDomain(dappDomain, GenesisBlockBuilder.defaultRoleName)
                    .createAccount(
                        dappInstanceAccountId,
                        irohaKeyPair.public
                    )
                    .createAccount(
                        dappRepoAccountId,
                        irohaKeyPair.public
                    )
                    .createAccount(
                        exchangerAccountId,
                        irohaKeyPair.public
                    )
                    .setAccountDetail(
                        dappRepoAccountId, testContractName, Utils.irohaEscape(
                            Files
                                .toString(
                                    File("src/test/resources/sample_contract.groovy"),
                                    Charset.defaultCharset()
                                )
                        )
                    )
                    .setAccountDetail(
                        dappRepoAccountId, exchangerContractName, Utils.irohaEscape(
                            Files
                                .toString(
                                    File("src/test/resources/exchanger_contract.groovy"),
                                    Charset.defaultCharset()
                                )
                        )
                    )
                    .createAsset("asset", dappDomain, 2)
                    .createAsset("anotherasset", dappDomain, 5)
                    .addAssetQuantity(anotherAssetId, "2")
                    .build()
                    .build()
            )
            .build()
    }

val peerConfig: PeerConfig
    get() = PeerConfig.builder()
        .genesisBlock(genesisBlock)
        .build()

class DappTestEnvironment : Closeable {

    lateinit var service: DappService
    lateinit var irohaAPI: IrohaAPI
    lateinit var queryAPI: QueryAPI
    private var rmqPort: Int = 0

    val keyPair = irohaKeyPair

    fun enableContract(contractName: String) {
        val response = irohaAPI.transaction(
            Transaction.builder(dappRepoAccountId)
                .setAccountDetail(dappInstanceAccountId, contractName, "true")
                .sign(irohaKeyPair)
                .build()
        ).blockingLast()
        if (response.txStatus != Endpoint.TxStatus.COMMITTED) {
            throw RuntimeException("Enabling of $contractName contract failed")
        }
    }

    fun disableContract(contractName: String) {
        val response = irohaAPI.transaction(
            Transaction.builder(dappRepoAccountId)
                .setAccountDetail(dappInstanceAccountId, contractName, "false")
                .sign(irohaKeyPair)
                .build()
        ).blockingLast()
        if (response.txStatus != Endpoint.TxStatus.COMMITTED) {
            throw RuntimeException("Disabling of $contractName contract failed")
        }
    }

    fun init() {
        iroha.withLogger(null)
        iroha.configure()
        postgresDockerContainer = iroha.postgresDockerContainer
        postgresDockerContainer.start()
        irohaContainer =
                iroha.irohaDockerContainer.withCreateContainerCmdModifier { it.withName(irohaContainerName) }
                    .withExposedPorts(DEFAULT_IROHA_PORT)
        irohaContainer.getPortBindings().add(
            String.format(
                "%d:%d/%s",
                DEFAULT_IROHA_PORT,
                DEFAULT_IROHA_PORT,
                InternetProtocol.TCP.toDockerNotation()
            )
        )
        irohaContainer.start()

        val irohaPort = irohaContainer.getMappedPort(DEFAULT_IROHA_PORT)!!

        irohaAPI = IrohaAPI(
            irohaContainer.getContainerIpAddress(),
            irohaPort
        )

        rmq.withNetwork(iroha.network).start()
        rmqPort = rmq.getMappedPort(DEFAULT_RMQ_PORT)

        chainAdapter.withNetwork(iroha.network)
            .withEnv(
                "CHAIN-ADAPTER_RMQHOST",
                rmqContainerName
            )
            .withEnv(
                "CHAIN-ADAPTER_IROHA_HOSTNAME",
                irohaContainerName
            )
            .withEnv(
                "CHAIN-ADAPTER_IROHACREDENTIAL_ACCOUNTID",
                dappInstanceAccountId
            )
            .withEnv(
                "CHAIN-ADAPTER_IROHACREDENTIAL_PUBKEY",
                Utils.toHex(keyPair.public.encoded).toLowerCase()
            )
            .withEnv(
                "CHAIN-ADAPTER_IROHACREDENTIAL_PRIVKEY",
                Utils.toHex(keyPair.private.encoded).toLowerCase()
            )
            .withEnv(
                "CHAIN-ADAPTER_DROPLASTREADBLOCK",
                "true"
            )
            .withEnv(
                "WAIT_HOSTS",
                "$irohaContainerName:$irohaPort, $rmqContainerName:$rmqPort"
            )
            .start()

        queryAPI = QueryAPI(irohaAPI, dappInstanceAccountId, irohaKeyPair)

        val chainListener = ReliableIrohaChainListener(
            object : RMQConfig {
                override val host = rmq.containerIpAddress
                override val irohaExchange = rmqExchange
                override val port = rmqPort
            },
            Random().nextLong().toString(),
            createPrettySingleThreadPool(DAPP_NAME, "chain-listener")
        )

        val blockProcessor = BlockProcessor(chainListener)

        val observableSource = CommandObservableSource(blockProcessor)

        service = DappService(
            irohaAPI,
            irohaKeyPair,
            observableSource,
            DefaultCacheManager(),
            ContractsRepositoryMonitor(
                observableSource,
                queryAPI,
                dappInstanceAccountId,
                dappRepoAccountId,
                dappRepoAccountId
            )
        )

        irohaAPI.transactionSync(
            Transaction.builder(exchangerAccountId)
                .addAssetQuantity(assetId, "100000")
                .addAssetQuantity(anotherAssetId, "100000")
                .sign(irohaKeyPair)
                .build()
        )

        // To be sure the service is initialized
        Thread.sleep(1000)
    }

    override fun close() {
        irohaAPI.close()
        chainAdapter.stop()
        irohaContainer.stop()
        postgresDockerContainer.stop()
        iroha.conf.deleteTempDir()
        rmq.stop()
    }
}
