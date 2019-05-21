/*
 * Copyright D3 Ledger, Inc. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package jp.co.soramitsu.dapp.block

import com.d3.commons.sidechain.iroha.ReliableIrohaChainListener
import com.d3.commons.util.createPrettySingleThreadPool
import com.github.kittinunf.result.map
import io.reactivex.Observable
import io.reactivex.schedulers.Schedulers
import io.reactivex.subjects.PublishSubject
import iroha.protocol.BlockOuterClass
import iroha.protocol.Commands
import jp.co.soramitsu.dapp.config.DAPP_NAME
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

interface TimedCommandsBlockParser {
    fun getTimedCommandsObservable(): Observable<Pair<Commands.Command, Long>>
}

@Component
final class BlockProcessor(
    @Autowired
    private val chainListener: ReliableIrohaChainListener
) : TimedCommandsBlockParser {

    private val timedCommandsObservable = PublishSubject.create<Pair<Commands.Command, Long>>()
    private val scheduler = Schedulers.from(createPrettySingleThreadPool(DAPP_NAME, "block-processor"))

    init {
        chainListener.getBlockObservable().map { observable ->
            observable.observeOn(scheduler).subscribe { (block, _) ->
                processBlock(block)
            }
        }
        chainListener.listen()
    }

    private fun processBlock(block: BlockOuterClass.Block) {
        block.blockV1.payload.transactionsList
            .forEach { transaction ->
                transaction.payload.reducedPayload.commandsList.stream()
                    .forEach { command ->
                        timedCommandsObservable.onNext(
                            Pair(
                                command,
                                transaction.payload.reducedPayload.createdTime
                            )
                        )
                    }
            }
    }

    override fun getTimedCommandsObservable(): Observable<Pair<Commands.Command, Long>> {
        return timedCommandsObservable
    }
}
