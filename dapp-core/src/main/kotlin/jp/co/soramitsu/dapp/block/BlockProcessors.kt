/*
 * Copyright D3 Ledger, Inc. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package jp.co.soramitsu.dapp.block

import com.d3.commons.util.createPrettySingleThreadPool
import io.reactivex.Observable
import io.reactivex.schedulers.Schedulers
import io.reactivex.subjects.PublishSubject
import iroha.protocol.BlockOuterClass
import iroha.protocol.Commands
import jp.co.soramitsu.dapp.config.DAPP_NAME
import jp.co.soramitsu.dapp.listener.ReliableIrohaChainListener
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

interface CommandsBlockParser {
    fun getCommandsObservable(): Observable<Commands.Command>
}

@Component
final class BlockProcessor(
    @Autowired
    private val chainListener: ReliableIrohaChainListener
) : CommandsBlockParser {

    private val commandsObservable = PublishSubject.create<Commands.Command>()
    private val scheduler = Schedulers.from(createPrettySingleThreadPool(DAPP_NAME, "block-processor"))

    init {
        chainListener.processIrohaBlocks(this::processBlock, scheduler)
    }

    private fun processBlock(block: BlockOuterClass.Block) {
        block.blockV1.payload.transactionsList
            .forEach { transaction ->
                transaction.payload.reducedPayload.commandsList.stream()
                    .forEach(commandsObservable::onNext)
            }
    }

    override fun getCommandsObservable(): Observable<Commands.Command> {
        return commandsObservable
    }
}
