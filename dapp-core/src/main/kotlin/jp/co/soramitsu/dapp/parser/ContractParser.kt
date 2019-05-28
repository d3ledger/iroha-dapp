/*
 * Copyright Soramitsu Co., Ltd. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package jp.co.soramitsu.dapp.parser

import groovy.lang.GroovyClassLoader
import jp.co.soramitsu.dapp.AbstractDappScript

/**
 * Contract Groovy parser
 */
sealed class ContractParser {

    companion object {
        /**
         * Parses given Groovy script
         * @param script - groovy script to parse
         * @throws IllegalArgumentException if script is empty or script class doesn't implement DappInterface
         * or in case of invalid(not compilable) script
         * @return parsed DappInterface instance
         */
        fun parse(
            script: String
        ): AbstractDappScript {
            if (script.isEmpty()) {
                throw IllegalArgumentException("Cannot parse empty script")
            }
            val scriptClass: Class<Any>
            try {
                scriptClass = GroovyClassLoader().parseClass(script)
            } catch (e: Exception) {
                throw IllegalArgumentException("Invalid dApp script", e)
            }
            val instance = scriptClass.newInstance()
            when (instance) {
                is AbstractDappScript -> return instance
                else -> throw IllegalArgumentException(
                    "Script class must extend " + AbstractDappScript::class.java.simpleName
                )
            }
        }
    }
}
