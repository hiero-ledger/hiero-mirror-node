// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.restjava.service.fee;

import static com.hedera.hapi.node.base.ResponseCodeEnum.BATCH_SIZE_LIMIT_EXCEEDED;
import static com.hedera.hapi.node.base.ResponseCodeEnum.PENDING_AIRDROP_ID_LIST_TOO_LONG;
import static com.hedera.hapi.node.base.ResponseCodeEnum.TOKEN_TRANSFER_LIST_SIZE_LIMIT_EXCEEDED;
import static com.hedera.hapi.node.base.ResponseCodeEnum.TRANSACTION_OVERSIZE;
import static com.hedera.hapi.node.base.ResponseCodeEnum.TRANSFER_LIST_SIZE_LIMIT_EXCEEDED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.hedera.hapi.node.base.AccountAmount;
import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.NftTransfer;
import com.hedera.hapi.node.base.PendingAirdropId;
import com.hedera.hapi.node.base.TokenID;
import com.hedera.hapi.node.base.TokenTransferList;
import com.hedera.hapi.node.base.TopicID;
import com.hedera.hapi.node.base.Transaction;
import com.hedera.hapi.node.base.TransferList;
import com.hedera.hapi.node.consensus.ConsensusSubmitMessageTransactionBody;
import com.hedera.hapi.node.contract.EthereumTransactionBody;
import com.hedera.hapi.node.token.CryptoTransferTransactionBody;
import com.hedera.hapi.node.token.TokenAirdropTransactionBody;
import com.hedera.hapi.node.token.TokenClaimAirdropTransactionBody;
import com.hedera.hapi.node.transaction.SignedTransaction;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.config.data.HederaConfig;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

final class FeeTransactionLimitsTest {

    @Test
    void acceptsTypicalCryptoTransfer() {
        final var body = cryptoTransferBody(1);
        FeeTransactionLimits.validate(wrap(body), body, 0);
    }

    @Test
    void acceptsMaxTokenTransferLists() {
        final var lists = tokenTransfers(10);
        final var body = TransactionBody.newBuilder()
                .cryptoTransfer(CryptoTransferTransactionBody.newBuilder()
                        .tokenTransfers(lists)
                        .build())
                .build();
        FeeTransactionLimits.validate(wrap(body), body, 0);
    }

    @Test
    void rejectsTooManyTokenTransferLists() {
        final var lists = tokenTransfers(11);
        final var body = TransactionBody.newBuilder()
                .cryptoTransfer(CryptoTransferTransactionBody.newBuilder()
                        .tokenTransfers(lists)
                        .build())
                .build();
        final var transaction = wrap(body);

        assertThatThrownBy(() -> FeeTransactionLimits.validate(transaction, body, 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(TOKEN_TRANSFER_LIST_SIZE_LIMIT_EXCEEDED.protoName());
    }

    @Test
    void rejectsTooManyHbarTransfers() {
        final var amounts = new ArrayList<AccountAmount>(11);
        for (int i = 0; i < 11; i++) {
            amounts.add(AccountAmount.newBuilder()
                    .accountID(AccountID.newBuilder().accountNum(i + 1).build())
                    .amount(i % 2 == 0 ? -1 : 1)
                    .build());
        }
        final var body = TransactionBody.newBuilder()
                .cryptoTransfer(CryptoTransferTransactionBody.newBuilder()
                        .transfers(TransferList.newBuilder()
                                .accountAmounts(amounts)
                                .build())
                        .build())
                .build();
        final var transaction = wrap(body);

        assertThatThrownBy(() -> FeeTransactionLimits.validate(transaction, body, 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(TRANSFER_LIST_SIZE_LIMIT_EXCEEDED.protoName());
    }

    @Test
    void rejectsTooManyNftTransfers() {
        final var nftTransfers = new ArrayList<NftTransfer>(11);
        for (int i = 0; i < 11; i++) {
            nftTransfers.add(NftTransfer.newBuilder()
                    .senderAccountID(AccountID.newBuilder().accountNum(1).build())
                    .receiverAccountID(AccountID.newBuilder().accountNum(2).build())
                    .serialNumber(i + 1)
                    .build());
        }
        final var body = TransactionBody.newBuilder()
                .cryptoTransfer(CryptoTransferTransactionBody.newBuilder()
                        .tokenTransfers(TokenTransferList.newBuilder()
                                .token(TokenID.newBuilder().tokenNum(1).build())
                                .nftTransfers(nftTransfers)
                                .build())
                        .build())
                .build();
        final var transaction = wrap(body);

        assertThatThrownBy(() -> FeeTransactionLimits.validate(transaction, body, 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(BATCH_SIZE_LIMIT_EXCEEDED.protoName());
    }

    @Test
    void rejectsTooManyAirdropTokenTransferLists() {
        final var body = TransactionBody.newBuilder()
                .tokenAirdrop(TokenAirdropTransactionBody.newBuilder()
                        .tokenTransfers(tokenTransfers(11))
                        .build())
                .build();
        final var transaction = wrap(body);

        assertThatThrownBy(() -> FeeTransactionLimits.validate(transaction, body, 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(TOKEN_TRANSFER_LIST_SIZE_LIMIT_EXCEEDED.protoName());
    }

    @Test
    void rejectsTooManyPendingAirdropsToClaim() {
        final var pending = new ArrayList<PendingAirdropId>(11);
        for (int i = 0; i < 11; i++) {
            pending.add(PendingAirdropId.newBuilder()
                    .senderId(AccountID.newBuilder().accountNum(1).build())
                    .receiverId(AccountID.newBuilder().accountNum(2).build())
                    .fungibleTokenType(TokenID.newBuilder().tokenNum(i + 1).build())
                    .build());
        }
        final var body = TransactionBody.newBuilder()
                .tokenClaimAirdrop(TokenClaimAirdropTransactionBody.newBuilder()
                        .pendingAirdrops(pending)
                        .build())
                .build();
        final var transaction = wrap(body);

        assertThatThrownBy(() -> FeeTransactionLimits.validate(transaction, body, 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(PENDING_AIRDROP_ID_LIST_TOO_LONG.protoName());
    }

    @Test
    void rejectsOversizedNonEthereumTransaction() {
        final int maxBytes = FeeEstimationFeeContext.CONFIGURATION
                .getConfigData(HederaConfig.class)
                .transactionMaxBytes();
        final var body = TransactionBody.newBuilder()
                .consensusSubmitMessage(ConsensusSubmitMessageTransactionBody.newBuilder()
                        .topicID(TopicID.newBuilder().topicNum(1).build())
                        .message(Bytes.wrap(new byte[maxBytes]))
                        .build())
                .build();
        final var transaction = wrap(body);

        assertThat(Transaction.PROTOBUF.measureRecord(transaction)).isGreaterThan(maxBytes);
        assertThatThrownBy(() -> FeeTransactionLimits.validate(transaction, body, 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(TRANSACTION_OVERSIZE.protoName());
    }

    @Test
    void allowsJumboEthereumTransaction() {
        final var body = TransactionBody.newBuilder()
                .ethereumTransaction(EthereumTransactionBody.newBuilder()
                        .ethereumData(Bytes.wrap(new byte[10_000]))
                        .build())
                .build();
        final var transaction = wrap(body);

        assertThat(Transaction.PROTOBUF.measureRecord(transaction)).isGreaterThan(6_144);
        FeeTransactionLimits.validate(transaction, body, 0);
    }

    @Test
    void rejectsTooManySignaturePairs() {
        final var body = cryptoTransferBody(1);
        final var transaction = wrap(body);

        assertThatThrownBy(() ->
                        FeeTransactionLimits.validate(transaction, body, FeeTransactionLimits.MAX_SIGNATURE_PAIRS + 1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(TRANSACTION_OVERSIZE.protoName());
    }

    @Test
    void rejectsAboveMaxTokenTransferLists() {
        final var lists = tokenTransfers(12);
        final var body = TransactionBody.newBuilder()
                .cryptoTransfer(CryptoTransferTransactionBody.newBuilder()
                        .tokenTransfers(lists)
                        .build())
                .build();
        final var transaction = wrap(body);

        assertThatThrownBy(() -> FeeTransactionLimits.validate(transaction, body, 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(TOKEN_TRANSFER_LIST_SIZE_LIMIT_EXCEEDED.protoName());
    }

    private static TransactionBody cryptoTransferBody(final int tokenCount) {
        return TransactionBody.newBuilder()
                .cryptoTransfer(CryptoTransferTransactionBody.newBuilder()
                        .tokenTransfers(tokenTransfers(tokenCount))
                        .build())
                .build();
    }

    private static List<TokenTransferList> tokenTransfers(final int count) {
        final var lists = new ArrayList<TokenTransferList>(count);
        for (int i = 0; i < count; i++) {
            lists.add(tokenTransfer(i + 1));
        }
        return lists;
    }

    private static TokenTransferList tokenTransfer(final long tokenNum) {
        return TokenTransferList.newBuilder()
                .token(TokenID.newBuilder().tokenNum(tokenNum).build())
                .transfers(AccountAmount.newBuilder()
                        .accountID(AccountID.newBuilder().accountNum(1).build())
                        .amount(-1)
                        .build())
                .build();
    }

    private static Transaction wrap(final TransactionBody body) {
        final var signedTransaction = SignedTransaction.newBuilder()
                .bodyBytes(TransactionBody.PROTOBUF.toBytes(body))
                .build();
        return Transaction.newBuilder()
                .signedTransactionBytes(SignedTransaction.PROTOBUF.toBytes(signedTransaction))
                .build();
    }
}
