package jp.co.soramitsu.dapp.test.environment

import com.google.common.io.Files
import com.google.common.util.concurrent.ThreadFactoryBuilder
import iroha.protocol.BlockOuterClass
import jp.co.soramitsu.crypto.ed25519.Ed25519Sha3
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
import mu.KLogging
import org.apache.commons.configuration2.FileBasedConfiguration
import org.apache.commons.configuration2.PropertiesConfiguration
import org.apache.commons.configuration2.builder.FileBasedConfigurationBuilder
import org.apache.commons.configuration2.builder.fluent.Parameters
import org.testcontainers.containers.BindMode
import org.testcontainers.containers.FixedHostPortGenericContainer
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.InternetProtocol
import java.io.Closeable
import java.io.File
import java.io.IOException
import java.net.URI
import java.nio.charset.Charset
import java.util.*
import java.util.concurrent.Executors
import javax.xml.bind.DatatypeConverter


class KGenericFixedContainer(imageName: String) : FixedHostPortGenericContainer<KGenericFixedContainer>(imageName)

private const val DEFAULT_RMQ_PORT = 5672
private const val DEFAULT_IROHA_PORT = 50051
const val dappDomain = "dapp"
const val dappInstanceAccountId = "dapp1" + Const.accountIdDelimiter + dappDomain
const val dappRepoAccountId = "dapprepo" + Const.accountIdDelimiter + dappDomain
val irohaKeyPair = Ed25519Sha3().generateKeypair()!!
val rmq = KGenericFixedContainer("rabbitmq:3-management").withExposedPorts(DEFAULT_RMQ_PORT)
    .withFixedExposedPort(DEFAULT_RMQ_PORT, DEFAULT_RMQ_PORT)
    .withCreateContainerCmdModifier { it.withName("rmq") }!!
val iroha = IrohaContainer().withPeerConfig(peerConfig)!!
var postgresDockerContainer: GenericContainer<*> = GenericContainer<Nothing>()
var irohaContainer: GenericContainer<*> = GenericContainer<Nothing>()
val chainAdapter = KGenericFixedContainer("nexus.iroha.tech:19002/d3-deploy/chain-adapter:1.0.0_rc5")
const val rmqExchange = "iroha"
const val resourcesLocation = "src/test/resources"

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
                            irohaKeyPair.public
                        )
                        .createAccount(
                            dappRepoAccountId,
                            irohaKeyPair.public
                        )
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
            logger.error("Genesis block building exception occurred", e)
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

    private val pubKeyFile = File("$resourcesLocation/pub.key")
    private val privKeyFile = File("$resourcesLocation/priv.key")
    private val lastBlockFile = File("$resourcesLocation/last_block.txt")

    val keyPair = irohaKeyPair

    fun init() {
        iroha.withLogger(null)
        iroha.configure()
        postgresDockerContainer = iroha.postgresDockerContainer
        postgresDockerContainer.start()
        irohaContainer = iroha.irohaDockerContainer.withCreateContainerCmdModifier { it.withName("iroha") }
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

        val host = irohaContainer.getContainerIpAddress()
        val port = irohaContainer.getMappedPort(DEFAULT_IROHA_PORT)!!

        irohaAPI = IrohaAPI(URI("grpc", null, host, port, null, null, null))

        rmq.withNetwork(iroha.network).start()
        rmqHost = rmq.containerIpAddress
        rmqPort = rmq.getMappedPort(DEFAULT_RMQ_PORT)

        val params = Parameters()
        val builder = FileBasedConfigurationBuilder<FileBasedConfiguration>(PropertiesConfiguration::class.java)
            .configure(
                params.properties()
                    .setFileName(resourcesLocation + "/rmq.properties")
            )
        val config = builder.configuration
        config.setProperty("rmq.iroha.port", iroha.toriiAddress.port)
        config.setProperty("rmq.irohaCredential.accountId", dappInstanceAccountId)
        builder.save()

        Files.write(
            DatatypeConverter.printHexBinary(keyPair.public.encoded),
            pubKeyFile,
            Charsets.UTF_8
        )
        Files.write(
            DatatypeConverter.printHexBinary(keyPair.private.encoded),
            privKeyFile,
            Charsets.UTF_8
        )
        Files.write(
            "0",
            lastBlockFile,
            Charsets.UTF_8
        )

        chainAdapter.addFileSystemBind(
            resourcesLocation,
            "/opt/chain-adapter/configs",
            BindMode.READ_WRITE
        )

        chainAdapter.withNetwork(iroha.network).start()

        queryAPI = QueryAPI(irohaAPI, dappInstanceAccountId, irohaKeyPair)

        service = DappService(
            irohaAPI,
            queryAPI,
            dappRepoAccountId,
            dappRepoAccountId,
            irohaKeyPair,
            CommandObservableSource(
                ReliableIrohaChainListener(
                    rmqHost,
                    rmqPort,
                    rmqExchange,
                    Random().nextLong().toString(),
                    Executors.newSingleThreadExecutor(
                        ThreadFactoryBuilder().setNameFormat("chain-listener-%d").build()
                    )
                )
            ),
            DefaultCacheManager()
        )
    }

    override fun close() {
        irohaAPI.close()
        chainAdapter.stop()
        irohaContainer.stop()
        postgresDockerContainer.stop()
        iroha.conf.deleteTempDir()
        rmq.stop()
        pubKeyFile.delete()
        privKeyFile.delete()
        lastBlockFile.delete()
    }
}
