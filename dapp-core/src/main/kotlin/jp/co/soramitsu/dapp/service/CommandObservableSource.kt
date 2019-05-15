/*
 * Copyright D3 Ledger, Inc. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package jp.co.soramitsu.dapp.service

import com.d3.commons.util.createPrettySingleThreadPool
import io.reactivex.Observable
import io.reactivex.schedulers.Schedulers
import io.reactivex.subjects.PublishSubject
import iroha.protocol.Commands
import iroha.protocol.Commands.Command.CommandCase
import jp.co.soramitsu.dapp.block.BlockProcessor
import jp.co.soramitsu.dapp.config.DAPP_NAME
import mu.KLogging
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

@Component
final class CommandObservableSource(
    @Autowired
    private val blockProcessor: BlockProcessor
) {
    private val commandsObservables: Map<CommandCase, PublishSubject<Commands.Command>>
    private val scheduler = Schedulers.from(createPrettySingleThreadPool(DAPP_NAME, "observable-source"))

    init {
        val obsMap = mutableMapOf<CommandCase, PublishSubject<Commands.Command>>()
        CommandCase.values().forEach { type ->
            obsMap[type] = PublishSubject.create()
        }
        commandsObservables = obsMap

        blockProcessor.getCommandsObservable()
            .observeOn(scheduler)
            .subscribe { command ->
                logger.info { "Appending command to the ${command.commandCase} observable" }
                commandsObservables[command.commandCase]?.onNext(command)
            }
    }

    fun getObservable(type: CommandCase): Observable<Commands.Command> {
        return commandsObservables[type]!!
    }

    companion object : KLogging()
}
