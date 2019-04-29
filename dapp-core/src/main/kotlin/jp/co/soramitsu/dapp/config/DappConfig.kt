package jp.co.soramitsu.dapp.config

interface DappConfig {

    val accountId: String

    val repository: String

    val repositorySetter: String

    val pubKeyPath: String

    val privKeyPath: String

    val irohaUrl: String

    val rmqHost: String

    val rmqPort: Int

    val rmqExchange: String

    val queue: String
}
