# dApp interface
The `dapp-interface` module consists of a simple Java interface located at `jp.co.soramitsu.bootstrap.changelog.ChangelogInterface`.  Changelog scripts development - is the only purpose of the interface.
## Integration
The module is published to the jitpack repository. It may be integrated into any gradle project using the following command:
```
implementation 'com.github.d3ledger.bootstrap-changelog:changelog-interface:2.0.0'`
```
Don't forget to add jitpack repository to your project build script:
```
repositories {
    mavenCentral()
    jcenter()
    maven { url 'https://jitpack.io' }
}
```
## Interface structure
### Methods to implement:
1) `String getSchemaVersion()` - returns changelog schema version. Every changelog script must have its own unique schema version. Due to changelog service idempotency, non-unique schema version changelogs won't be executed. 
2) `Transaction createChangelog(List<ChangelogAccountPublicInfo> accounts, List<ChangelogPeer> peers)` - the main logic of changelog script. All the changelog commands must be combined into a `Transaction` object. Peer and account public keys are stored in `peers` and `accounts` arguments respectively. 
### Constants:
1) `String superuserAccount` -  name of the superuser account
2) `String superuserDomain` - domain of the superuser account 
3) `String superuserAccountId` - name and domain of the superuser account combined.  This account is used to create and sign changelog transactions.
### Helper methods:
1) `static List<byte[]> getPubKeys(String accountName, List<ChangelogAccountPublicInfo> accounts)` - helper method that may be used to obtain public keys of given account.
