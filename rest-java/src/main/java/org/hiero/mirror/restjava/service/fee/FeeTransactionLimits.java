// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.restjava.service.fee;

import com.hedera.hapi.node.base.TokenTransferList;
import com.hedera.hapi.node.base.Transaction;
import com.hedera.hapi.node.base.TransferList;
import com.hedera.hapi.node.token.CryptoTransferTransactionBody;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.config.data.HederaConfig;
import com.hedera.node.config.data.JumboTransactionsConfig;
import com.hedera.node.config.data.LedgerConfig;
import com.hedera.node.config.data.TokensConfig;
import java.util.List;
import lombok.experimental.UtilityClass;

@UtilityClass
final class FeeTransactionLimits {

    /**
     * Consensus has no dedicated signature-pair cap; size is the real bound. 100 pairs already exceeds what fits in a
     * 6KB user transaction and is a backstop for jumbo Ethereum wrappers.
     */
    static final int MAX_SIGNATURE_PAIRS = 100;

    static void validate(final Transaction transaction, final TransactionBody body, final int numSignatures) {
        final var config = FeeEstimationFeeContext.CONFIGURATION;
        final var hedera = config.getConfigData(HederaConfig.class);
        final var jumbo = config.getConfigData(JumboTransactionsConfig.class);
        final var ledger = config.getConfigData(LedgerConfig.class);
        final var tokens = config.getConfigData(TokensConfig.class);

        final int size = Transaction.PROTOBUF.measureRecord(transaction);
        final int maxBytes = body.hasEthereumTransaction() ? jumbo.maxTxnSize() : hedera.transactionMaxBytes();
        if (size > maxBytes) {
            throw new IllegalArgumentException(
                    "Transaction size %d exceeds maximum %d bytes".formatted(size, maxBytes));
        }

        requireAtMost(numSignatures, MAX_SIGNATURE_PAIRS, "signature pairs");

        if (body.hasCryptoTransfer()) {
            validateCryptoTransfer(body.cryptoTransferOrThrow(), ledger);
        }
        if (body.hasTokenAirdrop()) {
            validateTokenTransferLists(
                    body.tokenAirdropOrThrow().tokenTransfers(),
                    tokens.maxAllowedAirdropTransfersPerTx(),
                    ledger.nftTransfersMaxLen(),
                    "airdrop token transfer lists");
        }
        if (body.hasTokenClaimAirdrop()) {
            requireAtMost(
                    body.tokenClaimAirdropOrThrow().pendingAirdrops().size(),
                    tokens.maxAllowedPendingAirdropsToClaim(),
                    "pending airdrops to claim");
        }
        if (body.hasTokenCancelAirdrop()) {
            requireAtMost(
                    body.tokenCancelAirdropOrThrow().pendingAirdrops().size(),
                    tokens.maxAllowedPendingAirdropsToCancel(),
                    "pending airdrops to cancel");
        }
    }

    private static void validateCryptoTransfer(final CryptoTransferTransactionBody op, final LedgerConfig ledger) {
        requireAtMost(
                op.transfersOrElse(TransferList.DEFAULT).accountAmounts().size(),
                ledger.transfersMaxLen(),
                "hbar transfers");
        validateTokenTransferLists(
                op.tokenTransfers(),
                ledger.tokenTransfersMaxLen(),
                ledger.nftTransfersMaxLen(),
                "token transfer lists");
    }

    private static void validateTokenTransferLists(
            final List<TokenTransferList> tokenTransfers,
            final int maxLists,
            final int maxNftTransfers,
            final String listName) {
        requireAtMost(tokenTransfers.size(), maxLists, listName);
        var nftCount = 0;
        for (final var tokenTransfer : tokenTransfers) {
            nftCount += tokenTransfer.nftTransfers().size();
        }
        requireAtMost(nftCount, maxNftTransfers, "nft transfers");
    }

    private static void requireAtMost(final int actual, final int max, final String name) {
        if (actual > max) {
            throw new IllegalArgumentException(
                    "Transaction contains %d %s, which exceeds the maximum of %d".formatted(actual, name, max));
        }
    }
}
