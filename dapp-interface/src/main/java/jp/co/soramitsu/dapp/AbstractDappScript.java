/*
 * Copyright D3 Ledger, Inc. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package jp.co.soramitsu.dapp;

import io.reactivex.Observable;
import iroha.protocol.Commands;
import jp.co.soramitsu.dapp.helper.CacheManager;
import jp.co.soramitsu.iroha.java.IrohaAPI;

import java.io.Closeable;
import java.security.KeyPair;

/**
 * Dapp script base class
 */
public abstract class AbstractDappScript implements AutoCloseable {

    protected final IrohaAPI irohaAPI;
    protected final KeyPair keyPair;
    protected final CacheManager cacheManager;

    /**
     * Constructs new {@link AbstractDappScript} instance
     *
     * @param irohaAPI     Iroha API to be used by the contract if needed
     * @param keyPair      dApp keypair
     * @param cacheManager cache manager to be used by the contract if needed
     */
    public AbstractDappScript(IrohaAPI irohaAPI, KeyPair keyPair, CacheManager cacheManager) {
        this.irohaAPI = irohaAPI;
        this.keyPair = keyPair;
        this.cacheManager = cacheManager;
    }

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
}
