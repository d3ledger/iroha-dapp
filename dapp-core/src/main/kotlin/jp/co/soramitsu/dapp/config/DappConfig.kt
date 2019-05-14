/*
 * Copyright D3 Ledger, Inc. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package jp.co.soramitsu.dapp.config

const val DAPP_NAME = "dapp"

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
