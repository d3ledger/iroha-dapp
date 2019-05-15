/*
 * Copyright D3 Ledger, Inc. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package jp.co.soramitsu.dapp;

import io.reactivex.Observable;
import iroha.protocol.Commands;
import jp.co.soramitsu.dapp.helper.CacheManager;
import jp.co.soramitsu.iroha.java.IrohaAPI;

import java.security.KeyPair;

/**
 * Dapp script base class
 */
public abstract class AbstractDappScript implements AutoCloseable {

    protected IrohaAPI irohaAPI;
    protected KeyPair keyPair;
    protected CacheManager cacheManager;

    /**
     * Retrieves Iroha command types on which the contract depends
     *
     * @return {@link Iterable} of {@link Commands.Command.CommandCase}
     */
    public abstract Iterable<Commands.Command.CommandCase> getCommandsToMonitor();

    /**
     * Sets a commands observable in the contract
     */
    public abstract void addCommandObservable(Observable<Commands.Command> observable);

    public void setIrohaAPI(IrohaAPI irohaAPI) {
        this.irohaAPI = irohaAPI;
    }

    public void setKeyPair(KeyPair keyPair) {
        this.keyPair = keyPair;
    }

    public void setCacheManager(CacheManager cacheManager) {
        this.cacheManager = cacheManager;
    }
}
