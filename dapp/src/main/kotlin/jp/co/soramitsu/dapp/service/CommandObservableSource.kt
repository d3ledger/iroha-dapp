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
import jp.co.soramitsu.dapp.block.TimedCommandsBlockParser
import jp.co.soramitsu.dapp.config.DAPP_NAME
import mu.KLogging
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

@Component
class CommandObservableSource(
    @Autowired
    private val timedCommandsBlockParser: TimedCommandsBlockParser
) {
    private val timedCommandsObservables: Map<CommandCase, PublishSubject<Pair<Commands.Command, Long>>>
    private val sharedObservables: Map<Commands.Command.CommandCase, Observable<Pair<Commands.Command, Long>>>
    private val scheduler = Schedulers.from(createPrettySingleThreadPool(DAPP_NAME, "observable-source"))

    init {
        val obsMap = mutableMapOf<CommandCase, PublishSubject<Pair<Commands.Command, Long>>>()
        CommandCase.values().forEach { type ->
            obsMap[type] = PublishSubject.create()
        }
        timedCommandsObservables = obsMap

        timedCommandsBlockParser.getTimedCommandsObservable()
            .observeOn(scheduler)
            .subscribe { (command, time) ->
                logger.info { "Appending command to the ${command.commandCase} observable" }
                timedCommandsObservables[command.commandCase]?.onNext(Pair(command, time))
            }

        sharedObservables = timedCommandsObservables.entries.map { (case, subject) ->
            case to subject.share()
        }.toMap()
    }

    fun getObservable(type: CommandCase): Observable<Pair<Commands.Command, Long>> {
        return sharedObservables[type]!!
    }

    companion object : KLogging()
}
