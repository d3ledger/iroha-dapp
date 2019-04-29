package jp.co.soramitsu.dapp.service

import io.reactivex.Observable
import io.reactivex.schedulers.Schedulers
import io.reactivex.subjects.PublishSubject
import iroha.protocol.Commands
import jp.co.soramitsu.dapp.helper.TransactionType
import jp.co.soramitsu.dapp.listener.ReliableIrohaChainListener
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import java.util.concurrent.Executors

@Component
class CommandObservableSource(
    @Autowired
    private val chainListener: ReliableIrohaChainListener
) {
    private val commandsObservables: Map<TransactionType, PublishSubject<Commands.Command>>
    private val scheduler = Schedulers.from(Executors.newSingleThreadExecutor())

    init {
        val obsMap = mutableMapOf<TransactionType, PublishSubject<Commands.Command>>()
        TransactionType.values().forEach { type ->
            obsMap[type] = PublishSubject.create()
        }
        commandsObservables = obsMap

        chainListener
            .getBlockObservable()
            .subscribeOn(scheduler)
            .subscribe { block ->
                block.blockV1.payload.transactionsList.forEach { transaction ->
                    transaction.payload.reducedPayload.commandsList.forEach { command ->
                        commandsObservables[TransactionType.byIndex(command.descriptorForType.index)]?.onNext(command)
                    }
                }
            }
    }

    fun getObservable(type: TransactionType): Observable<Commands.Command> {
        return commandsObservables[type]!!
    }
}
