/*
 * Copyright Soramitsu Co., Ltd. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package jp.co.soramitsu.dapp.config

const val DAPP_NAME = "dapp"

interface DappConfig {

    val accountId: String

    val repository: String

    val repositorySetter: String

    val pubKey: String

    val privKey: String

    val irohaUrl: String

    val queue: String

    val healthCheckPort: Int
}
