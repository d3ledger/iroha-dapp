import io.reactivex.Observable
import iroha.protocol.Commands
import jp.co.soramitsu.dapp.AbstractDappScript
import jp.co.soramitsu.dapp.helper.CacheManager
import jp.co.soramitsu.dapp.helper.TransactionType
import jp.co.soramitsu.iroha.java.IrohaAPI
import jp.co.soramitsu.iroha.java.Transaction

import java.security.KeyPair

class TestContract extends AbstractDappScript {

    private Observable<Commands.Command> observable

    TestContract(IrohaAPI irohaAPI, KeyPair keyPair, CacheManager cacheManager) {
        super(irohaAPI, keyPair, cacheManager)
    }

    @Override
    Iterable<TransactionType> getCommandsToMonitor() {
        return [TransactionType.CREATE_ACCOUNT]
    }

    @Override
    void addCommandObservable(Observable<Commands.Command> observable) {
        this.observable = observable
        process()
    }

    private void process() {
        observable.subscribe { event ->
            irohaAPI.transactionSync(
                    Transaction.builder("dapprepo@dapp")
                            .addAssetQuantity("asset#dapp", "1")
                            .sign(keyPair)
                            .build()
            )
        }
    }
}
