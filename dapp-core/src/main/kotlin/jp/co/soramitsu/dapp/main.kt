/*
 * Copyright D3 Ledger, Inc. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

@file:JvmName("DappMain")

package jp.co.soramitsu.dapp

import org.springframework.context.annotation.AnnotationConfigApplicationContext
import org.springframework.context.annotation.ComponentScan

@ComponentScan("jp.co.soramitsu.dapp")
class DappApplication

fun main(args: Array<String>) {
    val context = AnnotationConfigApplicationContext()
    context.register(DappApplication::class.java)
    context.refresh()
}
