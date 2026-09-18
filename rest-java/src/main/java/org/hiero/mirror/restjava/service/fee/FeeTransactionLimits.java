// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.restjava.service.fee;

import static com.hedera.hapi.node.base.ResponseCodeEnum.BATCH_SIZE_LIMIT_EXCEEDED;
import static com.hedera.hapi.node.base.ResponseCodeEnum.PENDING_AIRDROP_ID_LIST_TOO_LONG;
import static com.hedera.hapi.node.base.ResponseCodeEnum.TOKEN_TRANSFER_LIST_SIZE_LIMIT_EXCEEDED;
import static com.hedera.hapi.node.base.ResponseCodeEnum.TRANSACTION_OVERSIZE;
import static com.hedera.hapi.node.base.ResponseCodeEnum.TRANSFER_LIST_SIZE_LIMIT_EXCEEDED;

import com.hedera.hapi.node.base.ResponseCodeEnum;
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
            throw new IllegalArgumentException(TRANSACTION_OVERSIZE.protoName());
        }

        requireAtMost(numSignatures, MAX_SIGNATURE_PAIRS, TRANSACTION_OVERSIZE);

        if (body.hasCryptoTransfer()) {
            validateCryptoTransfer(body.cryptoTransferOrThrow(), ledger);
        }
        if (body.hasTokenAirdrop()) {
            validateTokenTransferLists(body.tokenAirdropOrThrow().tokenTransfers(), ledger);
        }
        if (body.hasTokenClaimAirdrop()) {
            requireAtMost(
                    body.tokenClaimAirdropOrThrow().pendingAirdrops().size(),
                    tokens.maxAllowedPendingAirdropsToClaim(),
                    PENDING_AIRDROP_ID_LIST_TOO_LONG);
        }
        if (body.hasTokenCancelAirdrop()) {
            requireAtMost(
                    body.tokenCancelAirdropOrThrow().pendingAirdrops().size(),
                    tokens.maxAllowedPendingAirdropsToCancel(),
                    PENDING_AIRDROP_ID_LIST_TOO_LONG);
        }
    }

    private static void validateCryptoTransfer(final CryptoTransferTransactionBody op, final LedgerConfig ledger) {
        requireAtMost(
                op.transfersOrElse(TransferList.DEFAULT).accountAmounts().size(),
                ledger.transfersMaxLen(),
                TRANSFER_LIST_SIZE_LIMIT_EXCEEDED);
        validateTokenTransferLists(op.tokenTransfers(), ledger);
    }

    private static void validateTokenTransferLists(
            final List<TokenTransferList> tokenTransfers, final LedgerConfig ledger) {
        requireAtMost(tokenTransfers.size(), ledger.tokenTransfersMaxLen(), TOKEN_TRANSFER_LIST_SIZE_LIMIT_EXCEEDED);
        int nftCount = 0;
        for (final var tokenTransfer : tokenTransfers) {
            nftCount += tokenTransfer.nftTransfers().size();
        }
        requireAtMost(nftCount, ledger.nftTransfersMaxLen(), BATCH_SIZE_LIMIT_EXCEEDED);
    }

    private static void requireAtMost(final int actual, final int max, final ResponseCodeEnum code) {
        if (actual > max) {
            throw new IllegalArgumentException(code.protoName());
        }
    }
}
