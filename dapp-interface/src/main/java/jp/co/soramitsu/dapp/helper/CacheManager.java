/*
 * Copyright D3 Ledger, Inc. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package jp.co.soramitsu.dapp.helper;

public interface CacheManager {

    Object put(String key, Object value);

    Object get(String key);

    Object remove(String key);

    boolean contains(String key);
}
