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
import jp.co.soramitsu.dapp.block.CommandsBlockParser
import jp.co.soramitsu.dapp.config.DAPP_NAME
import mu.KLogging
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

@Component
class CommandObservableSource(
    @Autowired
    private val commandsBlockParser: CommandsBlockParser
) {
    private val commandsObservables: Map<CommandCase, PublishSubject<Commands.Command>>
    private val sharedObservables: Map<Commands.Command.CommandCase, Observable<Commands.Command>>
    private val scheduler = Schedulers.from(createPrettySingleThreadPool(DAPP_NAME, "observable-source"))

    init {
        val obsMap = mutableMapOf<CommandCase, PublishSubject<Commands.Command>>()
        CommandCase.values().forEach { type ->
            obsMap[type] = PublishSubject.create()
        }
        commandsObservables = obsMap

        commandsBlockParser.getCommandsObservable()
            .observeOn(scheduler)
            .subscribe { command ->
                logger.info { "Appending command to the ${command.commandCase} observable" }
                commandsObservables[command.commandCase]?.onNext(command)
            }

        sharedObservables = commandsObservables.entries.map { (case, subject) ->
            case to subject.share()
        }.toMap()
    }

    fun getObservable(type: CommandCase): Observable<Commands.Command> {
        return sharedObservables[type]!!
    }

    companion object : KLogging()
}
