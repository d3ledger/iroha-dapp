/*
 * Copyright D3 Ledger, Inc. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

import iroha.protocol.Commands
import jp.co.soramitsu.dapp.AbstractDappScript
import jp.co.soramitsu.iroha.java.Transaction

class TestContract extends AbstractDappScript {

    @Override
    Iterable<Commands.Command.CommandCase> getCommandsToMonitor() {
        return [Commands.Command.CommandCase.CREATE_ACCOUNT]
    }

    @Override
    void processCommand(Commands.Command command, long createdTime) {
        if (getCommandsToMonitor().contains(command.commandCase)) {
            irohaAPI.transactionSync(
                    Transaction.builder("dapprepo@dapp", createdTime)
                            .addAssetQuantity("asset#dapp", "1")
                            .sign(keyPair)
                            .build()
            )
        }
    }
}
