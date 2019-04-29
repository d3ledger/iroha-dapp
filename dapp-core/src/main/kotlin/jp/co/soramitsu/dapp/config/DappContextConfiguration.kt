package jp.co.soramitsu.dapp.config

import com.jdiazcano.cfg4k.loaders.EnvironmentConfigLoader
import com.jdiazcano.cfg4k.loaders.PropertyConfigLoader
import com.jdiazcano.cfg4k.providers.OverrideConfigProvider
import com.jdiazcano.cfg4k.providers.ProxyConfigProvider
import com.jdiazcano.cfg4k.sources.InputConfigSource
import jp.co.soramitsu.dapp.listener.ReliableIrohaChainListener
import jp.co.soramitsu.iroha.java.IrohaAPI
import jp.co.soramitsu.iroha.java.QueryAPI
import jp.co.soramitsu.iroha.java.Utils
import mu.KLogging
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.io.File
import java.io.IOException
import java.net.URI
import java.nio.file.Files
import java.nio.file.Paths
import java.security.KeyPair
import java.util.concurrent.Executors

@Configuration
class DappContextConfiguration {

    private val dappConfig = loadConfigs("dapp", DappConfig::class.java, "${getConfigFolder()}/dapp.properties")

    @Bean
    fun dappKeyPair() = loadKeypair(dappConfig.pubKeyPath, dappConfig.privKeyPath)

    @Bean
    fun irohaApi() = IrohaAPI(URI(dappConfig.irohaUrl))

    @Bean
    fun queryApi() = QueryAPI(irohaApi(), dappConfig.accountId, dappKeyPair())

    @Bean
    fun chainListener() = ReliableIrohaChainListener(
        dappConfig.rmqHost,
        dappConfig.rmqPort,
        dappConfig.rmqExchange,
        dappConfig.queue,
        Executors.newCachedThreadPool()
    )

    private fun <T : Any> loadConfigs(prefix: String, type: Class<T>, filename: String): T {
        val envLoader = EnvironmentConfigLoader()
        val envProvider = ProxyConfigProvider(envLoader)
        return try {
            val stream = InputConfigSource(File(filename).inputStream())
            val configLoader = PropertyConfigLoader(stream)
            val provider = OverrideConfigProvider(
                envProvider,
                ProxyConfigProvider(configLoader)
            )
            provider.bind(prefix, type)
        } catch (e: IOException) {
            logger.warn("Couldn't open a file $filename. Trying to use only env variables.")
            envProvider.bind(prefix, type)
        }
    }

    private fun getConfigFolder() = System.getProperty("user.dir") + "/config"

    private fun loadKeypair(pubKeyPath: String, privKeyPath: String): KeyPair {
        return try {
            Utils.parseHexKeypair(readKeyFromFile(pubKeyPath), readKeyFromFile(privKeyPath))
        } catch (e: IOException) {
            throw Exception("Unable to read Iroha key files. Public key: $pubKeyPath, Private key: $privKeyPath", e)
        }
    }

    private fun readKeyFromFile(path: String): String {
        return String(Files.readAllBytes(Paths.get(path)))
    }

    /**
     * Logger
     */
    companion object : KLogging()
}
