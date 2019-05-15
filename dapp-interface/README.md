# dApp interface
The `dapp-interface` module consists of a simple Java class located at `jp.co.soramitsu.dapp.AbstractDappScript`.  Dapp scripts development - is the only purpose of the class.
## Integration
The module is published to the jitpack repository. It may be integrated into any gradle project using the following command:
```
implementation 'com.github.d3ledger.iroha-dapp:dapp-interface:<version>'`
```
Don't forget to add jitpack repository to your project build script:
```
repositories {
    maven { url 'https://jitpack.io' }
}
```
## Interface structure
### Methods to implement:
1) `Iterable<TransactionType> getCommandsToMonitor()` - identify what transaction commands are needed by the contract
2) `void addCommandObservable(Observable<Commands.Command> observable)` - passes an observable of the commands of one type to the contract
3) `void setCacheManager(CacheManager cacheManager)` - passes cache manager in order to use the shared contract cache inside contract body if needed
