package jp.co.soramitsu.dapp.service

import com.google.common.util.concurrent.ThreadFactoryBuilder
import io.reactivex.Observable
import io.reactivex.schedulers.Schedulers
import io.reactivex.subjects.PublishSubject
import jp.co.soramitsu.dapp.listener.ReliableIrohaChainListener
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import java.util.concurrent.Executors

@Component
class ContractsRepositoryMonitor(
    @Autowired
    private val chainListener: ReliableIrohaChainListener,
    @Autowired
    private val dAppAccountId: String
) {
    private val newContractsSubject: PublishSubject<String> = PublishSubject.create()
    private val disabledContractsSubject: PublishSubject<String> = PublishSubject.create()
    private val scheduler = Schedulers.from(
        Executors.newSingleThreadExecutor(
            ThreadFactoryBuilder().setNameFormat("observable-contracts-%d").build()
        )
    )

    init {
        chainListener
            .getBlockObservable()
            .observeOn(scheduler)
            .subscribe { block ->
                block.blockV1.payload.transactionsList
                    .forEach { transaction ->
                        transaction.payload.reducedPayload.commandsList.stream()
                            .filter { command ->
                                command.hasSetAccountDetail()
                            }
                            .map { command ->
                                command.setAccountDetail
                            }
                            .filter { setAccountDetail ->
                                setAccountDetail.accountId == dAppAccountId
                            }
                            .forEach { setAccountDetail ->
                                val contractName = setAccountDetail.key
                                if (setAccountDetail.value!!.toBoolean()) {
                                    newContractsSubject.onNext(contractName)
                                } else {
                                    disabledContractsSubject.onNext(contractName)
                                }
                            }
                    }
            }
    }

    fun getNewContractsObservable(): Observable<String> {
        return newContractsSubject
    }

    fun getDisabledContractsSubject(): Observable<String> {
        return disabledContractsSubject
    }
}