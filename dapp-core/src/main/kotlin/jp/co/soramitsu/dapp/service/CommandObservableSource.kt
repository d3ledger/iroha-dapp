/*
 * Copyright D3 Ledger, Inc. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package jp.co.soramitsu.dapp.service

import com.google.common.util.concurrent.ThreadFactoryBuilder
import io.reactivex.Observable
import io.reactivex.schedulers.Schedulers
import io.reactivex.subjects.PublishSubject
import iroha.protocol.BlockOuterClass
import iroha.protocol.Commands
import iroha.protocol.Commands.Command.CommandCase
import jp.co.soramitsu.dapp.listener.ReliableIrohaChainListener
import mu.KLogging
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import java.util.concurrent.Executors

@Component
final class CommandObservableSource(
    @Autowired
    private val chainListener: ReliableIrohaChainListener
) {
    private val commandsObservables: Map<CommandCase, PublishSubject<Commands.Command>>
    private val scheduler = Schedulers.from(
        Executors.newSingleThreadExecutor(
            ThreadFactoryBuilder().setNameFormat("observable-source-%d").build()
        )
    )

    init {
        val obsMap = mutableMapOf<CommandCase, PublishSubject<Commands.Command>>()
        CommandCase.values().forEach { type ->
            obsMap[type] = PublishSubject.create()
        }
        commandsObservables = obsMap

        chainListener.processIrohaBlocks(this::processBlock, scheduler)
    }

    private fun processBlock(block: BlockOuterClass.Block) {
        block.blockV1.payload.transactionsList.forEach { transaction ->
            transaction.payload.reducedPayload.commandsList.forEach { command ->
                logger.info { "Appending command to the ${command.commandCase} observable" }
                commandsObservables[command.commandCase]?.onNext(command)
            }
        }
    }

    fun getObservable(type: CommandCase): Observable<Commands.Command> {
        return commandsObservables[type]!!
    }

    companion object : KLogging()
}
