package jp.co.soramitsu.dapp.test.environment

import com.d3.chainadapter.adapter.ChainAdapter
import com.d3.chainadapter.provider.FileBasedLastReadBlockProvider
import com.d3.commons.config.IrohaConfig
import com.d3.commons.config.IrohaCredentialConfig
import com.d3.commons.config.RMQConfig
import com.d3.commons.model.IrohaCredential
import com.d3.commons.sidechain.iroha.IrohaChainListener
import com.google.common.io.Files
import io.ktor.util.error
import iroha.protocol.BlockOuterClass
import jp.co.soramitsu.dapp.cache.DefaultCacheManager
import jp.co.soramitsu.dapp.listener.ReliableIrohaChainListener
import jp.co.soramitsu.dapp.service.CommandObservableSource
import jp.co.soramitsu.dapp.service.DappService
import jp.co.soramitsu.iroha.java.IrohaAPI
import jp.co.soramitsu.iroha.java.QueryAPI
import jp.co.soramitsu.iroha.java.Transaction
import jp.co.soramitsu.iroha.java.Utils
import jp.co.soramitsu.iroha.java.detail.Const
import jp.co.soramitsu.iroha.testcontainers.IrohaContainer
import jp.co.soramitsu.iroha.testcontainers.PeerConfig
import jp.co.soramitsu.iroha.testcontainers.detail.GenesisBlockBuilder
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import mu.KLogging
import org.testcontainers.containers.GenericContainer
import java.io.Closeable
import java.io.File
import java.io.IOException
import java.nio.charset.Charset
import java.util.*

class KGenericContainer(imageName: String) : GenericContainer<KGenericContainer>(imageName)

const val dappDomain = "dapp"
const val dappInstanceAccountId = "dapp1" + Const.accountIdDelimiter + dappDomain
const val dappRepoAccountId = "dapprepo" + Const.accountIdDelimiter + dappDomain
val rmq = KGenericContainer("rabbitmq:3-management").withExposedPorts(5672)!!
val iroha = IrohaContainer().withPeerConfig(peerConfig)!!
const val rmqExchange = "iroha"

val logger = KLogging().logger

val genesisBlock: BlockOuterClass.Block
    get() {
        try {
            return GenesisBlockBuilder()
                .addDefaultTransaction()
                .addTransaction(
                    Transaction.builder(dappRepoAccountId)
                        .createDomain(dappDomain, GenesisBlockBuilder.defaultRoleName)
                        .createAccount(
                            dappInstanceAccountId,
                            GenesisBlockBuilder.defaultKeyPair.public
                        )
                        .createAccount(dappRepoAccountId, GenesisBlockBuilder.defaultKeyPair.public)
                        .setAccountDetail(
                            dappRepoAccountId, "testcontract", Utils.irohaEscape(
                                Files
                                    .toString(
                                        File("src/test/resources/sample_contract.groovy"),
                                        Charset.defaultCharset()
                                    )
                            )
                        )
                        .createAsset("asset", dappDomain, 2)
                        .build()
                        .build()
                )
                .build()
        } catch (e: IOException) {
            logger.error(e)
            throw RuntimeException(e)
        }
    }

val peerConfig: PeerConfig
    get() = PeerConfig.builder()
        .genesisBlock(genesisBlock)
        .build()

class DappTestEnvironment : Closeable {

    lateinit var service: DappService
    lateinit var irohaAPI: IrohaAPI
    lateinit var queryAPI: QueryAPI
    private lateinit var rmqHost: String
    private var rmqPort: Int = 0
    private lateinit var chainAdapter: ChainAdapter

    fun init() {
        iroha.start()
        irohaAPI = iroha.api

        rmq.start()
        rmqHost = rmq.containerIpAddress
        rmqPort = rmq.getMappedPort(5672)

        queryAPI = QueryAPI(irohaAPI, dappInstanceAccountId, GenesisBlockBuilder.defaultKeyPair)

        val rmqConfig = object : RMQConfig {
            override val host = rmqHost
            override val iroha = object : IrohaConfig {
                override val hostname = irohaAPI.uri.host
                override val port = irohaAPI.uri.port
            }
            override val irohaCredential = object : IrohaCredentialConfig {
                override val accountId = GenesisBlockBuilder.defaultAccountId
                override val privkeyPath = "/src/test/resources/pub.key"
                override val pubkeyPath = "/src/test/resources/priv.key"
            }
            override val irohaExchange = rmqExchange
            override val lastReadBlockFilePath = "/src/test/resources/last_block.txt"
        }

        GlobalScope.async {
            delay(5_000)
            chainAdapter = ChainAdapter(
                rmqConfig,
                queryAPI,
                IrohaChainListener(
                    irohaAPI,
                    IrohaCredential(
                        GenesisBlockBuilder.defaultAccountId,
                        GenesisBlockBuilder.defaultKeyPair
                    )
                ),
                FileBasedLastReadBlockProvider(rmqConfig)
            )

            chainAdapter.init()

        }

        service = DappService(
            irohaAPI,
            queryAPI,
            dappRepoAccountId,
            dappRepoAccountId,
            GenesisBlockBuilder.defaultKeyPair,
            CommandObservableSource(
                ReliableIrohaChainListener(
                    rmqHost,
                    rmqPort,
                    rmqExchange,
                    Random().nextLong().toString(),
                    null
                )
            ),
            DefaultCacheManager()
        )
    }

    override fun close() {
        irohaAPI.close()
        chainAdapter.close()
        iroha.close()
        rmq.stop()
    }
}
