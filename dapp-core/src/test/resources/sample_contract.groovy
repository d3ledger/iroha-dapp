import io.reactivex.Observable
import io.reactivex.disposables.Disposable
import iroha.protocol.Commands
import jp.co.soramitsu.dapp.AbstractDappScript
import jp.co.soramitsu.dapp.helper.CacheManager
import jp.co.soramitsu.iroha.java.IrohaAPI
import jp.co.soramitsu.iroha.java.Transaction

import java.security.KeyPair

class TestContract extends AbstractDappScript {

    private Observable<Commands.Command> observable

    private Disposable disposable

    TestContract(IrohaAPI irohaAPI, KeyPair keyPair, CacheManager cacheManager) {
        super(irohaAPI, keyPair, cacheManager)
    }

    @Override
    Iterable<Commands.Command.CommandCase> getCommandsToMonitor() {
        return [Commands.Command.CommandCase.CREATE_ACCOUNT]
    }

    @Override
    void addCommandObservable(Observable<Commands.Command> observable) {
        this.observable = observable
        disposable = process()
    }

    private Disposable process() {
        return observable.subscribe { event ->
            irohaAPI.transactionSync(
                    Transaction.builder("dapprepo@dapp")
                            .addAssetQuantity("asset#dapp", "1")
                            .sign(keyPair)
                            .build()
            )
        }
    }

    @Override
    void close() {
        disposable.dispose()
        observable = null
    }
}
