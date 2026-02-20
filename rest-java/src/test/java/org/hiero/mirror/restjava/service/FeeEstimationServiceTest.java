// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.restjava.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.google.protobuf.ByteString;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ConsensusSubmitMessageTransactionBody;
import com.hederahashgraph.api.proto.java.CryptoCreateTransactionBody;
import com.hederahashgraph.api.proto.java.CryptoDeleteTransactionBody;
import com.hederahashgraph.api.proto.java.CryptoTransferTransactionBody;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.KeyList;
import com.hederahashgraph.api.proto.java.SignatureMap;
import com.hederahashgraph.api.proto.java.SignaturePair;
import com.hederahashgraph.api.proto.java.SignedTransaction;
import com.hederahashgraph.api.proto.java.TopicID;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionBody;
import lombok.RequiredArgsConstructor;
import org.hiero.mirror.common.util.DomainUtils;
import org.hiero.mirror.rest.model.FeeEstimateMode;
import org.hiero.mirror.restjava.RestJavaIntegrationTest;
import org.junit.jupiter.api.Test;

@RequiredArgsConstructor
final class FeeEstimationServiceTest extends RestJavaIntegrationTest {

    private final FeeEstimationService service;

    @Test
    void estimateFees() {
        // given
        var transaction = cryptoTransfer(0);

        // when
        var result = service.estimateFees(transaction, FeeEstimateMode.INTRINSIC);

        // then
        assertThat(result).isNotNull();
        assertThat(result.getNode().getBase()).isNotNegative();
        assertThat(result.getNetwork().getMultiplier()).isPositive();
        assertThat(result.getTotal()).isPositive();
        assertThat(result.getNotes()).isEmpty();
    }

    @Test
    void estimateFeesWithSignatures() {
        // when
        var base = service.estimateFees(cryptoTransfer(0), FeeEstimateMode.INTRINSIC);
        var withSignatures = service.estimateFees(cryptoTransfer(2), FeeEstimateMode.INTRINSIC);

        // then
        assertThat(withSignatures.getTotal()).isGreaterThan(base.getTotal());
    }

    @SuppressWarnings("deprecation")
    @Test
    void estimateFeesLegacyFormat() {
        // given
        var body = TransactionBody.newBuilder()
                .setMemo("legacy")
                .setCryptoTransfer(CryptoTransferTransactionBody.newBuilder().build())
                .build();
        var transaction =
                Transaction.newBuilder().setBodyBytes(body.toByteString()).build();

        // when
        var result = service.estimateFees(transaction, FeeEstimateMode.INTRINSIC);

        // then
        assertThat(result.getTotal()).isPositive();
    }

    @Test
    void stateMode() {
        assertThatThrownBy(() -> service.estimateFees(cryptoTransfer(0), FeeEstimateMode.STATE))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("State-based fee estimation is not supported");
    }

    @Test
    void invalidTransaction() {
        // given
        var transaction = Transaction.newBuilder()
                .setSignedTransactionBytes(DomainUtils.fromBytes(domainBuilder.bytes(100)))
                .build();

        // when / then
        assertThatThrownBy(() -> service.estimateFees(transaction, FeeEstimateMode.INTRINSIC))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unable to parse transaction");
    }

    @Test
    void emptyTransaction() {
        assertThatThrownBy(() -> service.estimateFees(Transaction.getDefaultInstance(), FeeEstimateMode.INTRINSIC))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Transaction must contain body bytes or signed transaction bytes");
    }

    @Test
    void unknownTransactionType() {
        // given
        var body = TransactionBody.newBuilder().setMemo("test").build();
        var signedTransaction =
                SignedTransaction.newBuilder().setBodyBytes(body.toByteString()).build();
        var transaction = Transaction.newBuilder()
                .setSignedTransactionBytes(signedTransaction.toByteString())
                .build();

        // when / then
        assertThatThrownBy(() -> service.estimateFees(transaction, FeeEstimateMode.INTRINSIC))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unknown transaction type");
    }

    @Test
    void cryptoDelete() {
        // given
        var body = TransactionBody.newBuilder()
                .setCryptoDelete(CryptoDeleteTransactionBody.newBuilder()
                        .setDeleteAccountID(
                                AccountID.newBuilder().setAccountNum(1001L).build())
                        .setTransferAccountID(
                                AccountID.newBuilder().setAccountNum(3L).build())
                        .build())
                .build();

        // when
        var result = service.estimateFees(buildTransaction(body), FeeEstimateMode.INTRINSIC);

        // then
        assertThat(result.getService().getBase()).isEqualTo(49_000_000L);
        assertThat(result.getTotal()).isEqualTo(50_000_000L);
    }

    @Test
    void cryptoCreateExtraKeys() {
        // given
        var key = Key.newBuilder()
                .setKeyList(KeyList.newBuilder()
                        .addKeys(ed25519Key(1))
                        .addKeys(ed25519Key(2))
                        .build())
                .build();
        var body = TransactionBody.newBuilder()
                .setCryptoCreateAccount(
                        CryptoCreateTransactionBody.newBuilder().setKey(key).build())
                .build();

        // when
        var result = service.estimateFees(buildTransaction(body), FeeEstimateMode.INTRINSIC);

        // then
        assertThat(result.getTotal()).isEqualTo(600_000_000L);
    }

    @Test
    void consensusSubmitMessageShort() {
        // given
        var body = TransactionBody.newBuilder()
                .setConsensusSubmitMessage(ConsensusSubmitMessageTransactionBody.newBuilder()
                        .setTopicID(TopicID.newBuilder().setTopicNum(1L).build())
                        .setMessage(ByteString.copyFrom(new byte[100]))
                        .build())
                .build();

        // when
        var result = service.estimateFees(buildTransaction(body), FeeEstimateMode.INTRINSIC);

        // then
        assertThat(result.getService().getBase()).isEqualTo(7_000_000L);
        assertThat(result.getTotal()).isEqualTo(8_000_000L);
    }

    @Test
    void consensusSubmitMessageLong() {
        // given
        var body = TransactionBody.newBuilder()
                .setConsensusSubmitMessage(ConsensusSubmitMessageTransactionBody.newBuilder()
                        .setTopicID(TopicID.newBuilder().setTopicNum(1L).build())
                        .setMessage(ByteString.copyFrom(new byte[2000]))
                        .build())
                .build();

        // when
        var result = service.estimateFees(buildTransaction(body), FeeEstimateMode.INTRINSIC);

        // then
        assertThat(result.getService().getBase()).isEqualTo(7_000_000L);
        assertThat(result.getTotal()).isEqualTo(115_360_000L);
    }

    private Transaction buildTransaction(TransactionBody body) {
        var signedTransaction =
                SignedTransaction.newBuilder().setBodyBytes(body.toByteString()).build();
        return Transaction.newBuilder()
                .setSignedTransactionBytes(signedTransaction.toByteString())
                .build();
    }

    private static Key ed25519Key(int seed) {
        var key = new byte[32];
        key[0] = (byte) seed;
        return Key.newBuilder().setEd25519(ByteString.copyFrom(key)).build();
    }

    private Transaction cryptoTransfer(int signatureCount) {
        var body = TransactionBody.newBuilder()
                .setMemo("test")
                .setCryptoTransfer(CryptoTransferTransactionBody.newBuilder().build())
                .build();
        var sigMapBuilder = SignatureMap.newBuilder();
        for (int i = 0; i < signatureCount; i++) {
            sigMapBuilder.addSigPair(SignaturePair.newBuilder()
                    .setPubKeyPrefix(ByteString.copyFrom(new byte[] {(byte) i}))
                    .setEd25519(ByteString.copyFrom(new byte[64]))
                    .build());
        }
        var signedTransaction = SignedTransaction.newBuilder()
                .setBodyBytes(body.toByteString())
                .setSigMap(sigMapBuilder.build())
                .build();
        return Transaction.newBuilder()
                .setSignedTransactionBytes(signedTransaction.toByteString())
                .build();
    }
}
