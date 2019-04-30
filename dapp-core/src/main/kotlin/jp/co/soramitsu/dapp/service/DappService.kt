package jp.co.soramitsu.dapp.service

import com.google.gson.JsonParser
import jp.co.soramitsu.dapp.AbstractDappScript
import jp.co.soramitsu.dapp.helper.CacheManager
import jp.co.soramitsu.dapp.parser.ContractParser.Companion.parse
import jp.co.soramitsu.iroha.java.IrohaAPI
import jp.co.soramitsu.iroha.java.QueryAPI
import jp.co.soramitsu.iroha.java.Utils
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import java.security.KeyPair

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
    private val cacheManager: CacheManager
) {

    private val contracts: Map<String, AbstractDappScript>

    init {
        contracts = retrieveContracts()
    }

    private fun retrieveContracts(): Map<String, AbstractDappScript> {
        return JsonParser().parse(
            queryAPI.getAccountDetails(
                repositoryAccountId,
                repositorySetterId,
                null
            )
        ).asJsonObject
            .get(repositorySetterId)
            .asJsonObject
            .entrySet()
            .map { (name, script) ->
                name to parse(Utils.irohaUnEscape(script.asString), irohaAPI, dappKeyPair, cacheManager)
            }.toMap()
    }

    fun init() {
        contracts.forEach { (_, contract) ->
            contract.commandsToMonitor.forEach { type ->
                contract.addCommandObservable(observableSource.getObservable(type))
            }
        }
    }
}
