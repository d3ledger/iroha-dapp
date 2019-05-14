/*
 * Copyright D3 Ledger, Inc. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

@file:JvmName("DappMain")

package jp.co.soramitsu.dapp

import com.github.kittinunf.result.Result
import com.github.kittinunf.result.failure
import mu.KLogging
import org.springframework.context.annotation.AnnotationConfigApplicationContext
import org.springframework.context.annotation.ComponentScan

private val logger = KLogging().logger

@ComponentScan("jp.co.soramitsu.dapp")
class DappApplication

fun main(args: Array<String>) {
    Result.of {
        val context = AnnotationConfigApplicationContext()
        context.register(DappApplication::class.java)
        context.refresh()
    }.failure { ex ->
        logger.error("Dapp exited with an exception", ex)
        System.exit(1)
    }
}
