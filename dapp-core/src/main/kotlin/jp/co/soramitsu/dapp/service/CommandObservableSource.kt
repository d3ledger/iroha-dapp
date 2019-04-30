package jp.co.soramitsu.dapp.service

import io.reactivex.Observable
import io.reactivex.schedulers.Schedulers
import io.reactivex.subjects.PublishSubject
import iroha.protocol.Commands
import iroha.protocol.Commands.Command.CommandCase
import jp.co.soramitsu.dapp.listener.ReliableIrohaChainListener
import mu.KLogging
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import java.util.concurrent.Executors

@Component
class CommandObservableSource(
    @Autowired
    private val chainListener: ReliableIrohaChainListener
) {
    private val commandsObservables: Map<CommandCase, PublishSubject<Commands.Command>>
    private val scheduler = Schedulers.from(Executors.newSingleThreadExecutor())

    init {
        val obsMap = mutableMapOf<CommandCase, PublishSubject<Commands.Command>>()
        CommandCase.values().forEach { type ->
            obsMap[type] = PublishSubject.create()
        }
        commandsObservables = obsMap

        chainListener
            .getBlockObservable()
            .subscribeOn(scheduler)
            .subscribe { block ->
                block.blockV1.payload.transactionsList.forEach { transaction ->
                    transaction.payload.reducedPayload.commandsList.forEach { command ->
                        logger.info { "Appending command to the ${command.commandCase} observable" }
                        commandsObservables[command.commandCase]?.onNext(command)
                    }
                }
            }
    }

    fun getObservable(type: CommandCase): Observable<Commands.Command> {
        return commandsObservables[type]!!
    }

    companion object : KLogging()
}
