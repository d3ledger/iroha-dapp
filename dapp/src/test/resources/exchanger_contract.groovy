/*
 * Copyright Soramitsu Co., Ltd. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */


import iroha.protocol.Commands
import iroha.protocol.Commands.TransferAsset
import iroha.protocol.Endpoint
import jp.co.soramitsu.dapp.AbstractDappScript
import jp.co.soramitsu.iroha.java.Query
import jp.co.soramitsu.iroha.java.Transaction
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import java.util.concurrent.atomic.AtomicInteger

class ExchangerContract extends AbstractDappScript {

    private static final Logger logger = LoggerFactory.getLogger(ExchangerContract.class)
    private static final String exchangerAccountId = "exchanger@dapp"
    private static final int ITERATIONS = 1000
    private static final double FEE_RATIO = 0.01
    private static final String DELIMITER = "."
    private static final AtomicInteger counter = new AtomicInteger(1)

    private static double compute(double sourceAssetBalance, double targetAssetBalance, double x) {
        return targetAssetBalance / (sourceAssetBalance + x + 1)
    }

    private static double integrate(double sourceAssetBalance, double targetAssetBalance, double amount) {
        final double heightStep = amount / ITERATIONS
        double fistSum = compute(sourceAssetBalance, targetAssetBalance, heightStep / 2)
        double secondSum = 0

        for (int i = 0; i < ITERATIONS; i++) {
            fistSum += compute(sourceAssetBalance, targetAssetBalance, heightStep * i + heightStep / 2)
            secondSum += compute(sourceAssetBalance, targetAssetBalance, heightStep * i)
        }

        return heightStep / 6 * (
                compute(sourceAssetBalance, targetAssetBalance, 0) +
                        compute(sourceAssetBalance, targetAssetBalance, amount) +
                        4 * fistSum + 2 * secondSum
        )
    }

    @Override
    Iterable<Commands.Command.CommandCase> getCommandsToMonitor() {
        return [Commands.Command.CommandCase.TRANSFER_ASSET]
    }

    @Override
    synchronized void processCommand(Commands.Command command, long createdTime) {
        final TransferAsset transfer = command.transferAsset
        if (getCommandsToMonitor().contains(command.commandCase) &&
                transfer.destAccountId == exchangerAccountId) {
            final String sourceAsset = transfer.assetId
            final String targetAsset = transfer.description
            final String amount = transfer.amount
            final String destAccountId = transfer.srcAccountId
            logger.info("Got a conversion request from $destAccountId: $amount $sourceAsset to $targetAsset.")
            try {
                final String relevantAmount = calculateRelevantAmount(sourceAsset, targetAsset, Double.parseDouble(amount))

                def response = irohaAPI.transaction(
                        Transaction.builder(exchangerAccountId)
                                .transferAsset(
                                exchangerAccountId,
                                destAccountId,
                                targetAsset,
                                "Conversion from $sourceAsset to $targetAsset",
                                relevantAmount
                        )
                                .sign(keyPair)
                                .build()
                ).blockingLast()

                if (response.txStatus != Endpoint.TxStatus.COMMITTED) {
                    throw new Exception("Conversion operation failed in Iroha, response: $response")
                }

                logger.info("Successfully converted $amount of $sourceAsset to $targetAsset.")

            } catch (Exception e) {
                logger.error("Exchanger error occurred. Performing rollback.", e)

                irohaAPI.transactionSync(
                        Transaction.builder(exchangerAccountId)
                                .transferAsset(
                                exchangerAccountId,
                                destAccountId,
                                sourceAsset,
                                "Conversion rollback transaction",
                                amount
                        )
                                .sign(keyPair)
                                .build()
                )
            }
        }
    }

    private String calculateRelevantAmount(String sourceAsset, String targetAsset, double amount) {
        def sourceAssetBalance = getExchangerBalance(sourceAsset, amount)
        def targetAssetBalance = getExchangerBalance(targetAsset, 0)
        def amountMinusFee = amount * (1 - FEE_RATIO)

        def precision = getAssetPrecision(targetAsset)

        def calculatedAmount = integrate(sourceAssetBalance, targetAssetBalance, amountMinusFee)

        if (calculatedAmount >= targetAssetBalance) {
            throw new TooMuchAssetVolumeException("Asset supplement exceeds the balance.")
        }

        def respectPrecision = respectPrecision(new BigDecimal(calculatedAmount).toPlainString(), precision)
        // If the result is not bigger than zero
        if (new BigDecimal(respectPrecision) <= BigDecimal.ZERO) {
            throw new TooLittleAssetVolumeException("Asset supplement it too low for specified conversion")
        }
        return respectPrecision
    }

    private double getExchangerBalance(String assetId, double offset) {
        return Double.parseDouble(
                irohaAPI.query(
                        Query.builder(exchangerAccountId, counter.andIncrement)
                                .getAccountAssets(exchangerAccountId)
                                .buildSigned(keyPair)
                ).accountAssetsResponse.accountAssetsList
                        .stream()
                        .filter { asset -> (asset.assetId == assetId) }
                        .map { asset -> asset.balance }
                        .findAny()
                        .orElse("0")
        ) - offset
    }

    private int getAssetPrecision(String asset) {
        def assetResponse = irohaAPI.query(
                Query.builder(exchangerAccountId, counter.andIncrement)
                        .getAssetInfo(asset)
                        .buildSigned(keyPair)
        ).assetResponse.asset

        if (assetResponse.assetId == null || assetResponse.assetId.isEmpty()) {
            throw new AssetNotFoundException("There is no such asset $asset")
        }

        return assetResponse.precision
    }

    private static String respectPrecision(String rawValue, int precision) {
        def index = rawValue.indexOf(DELIMITER)
        def fractionalPart = index == -1 ? rawValue : rawValue.substring(index + 1, rawValue.length())
        def diff = fractionalPart.length() - precision
        if (diff == 0) {
            return rawValue
        }
        if (diff < 0) {
            def zero = "0"
            for (int i = 0; i < diff * (-1); i++) {
                zero += zero
            }
            return rawValue + zero
        }
        return rawValue.substring(0, index) + DELIMITER + fractionalPart.substring(0, precision)
    }

    class AssetNotFoundException extends Exception {
        AssetNotFoundException(String message) { super(message) }
    }

    class TooMuchAssetVolumeException extends Exception {
        TooMuchAssetVolumeException(String message) { super(message) }
    }

    class TooLittleAssetVolumeException extends Exception {
        TooLittleAssetVolumeException(String message) { super(message) }
    }
}
