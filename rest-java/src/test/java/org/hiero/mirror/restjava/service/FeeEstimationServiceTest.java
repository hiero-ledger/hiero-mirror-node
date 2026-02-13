// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.restjava.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hiero.hapi.fees.FeeScheduleUtils.makeExtraDef;
import static org.hiero.hapi.fees.FeeScheduleUtils.makeExtraIncluded;
import static org.hiero.hapi.fees.FeeScheduleUtils.makeService;
import static org.hiero.hapi.fees.FeeScheduleUtils.makeServiceFee;

import com.google.protobuf.ByteString;
import com.hedera.hapi.node.base.HederaFunctionality;
import com.hederahashgraph.api.proto.java.CryptoTransferTransactionBody;
import com.hederahashgraph.api.proto.java.SignatureMap;
import com.hederahashgraph.api.proto.java.SignaturePair;
import com.hederahashgraph.api.proto.java.SignedTransaction;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionBody;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.hiero.hapi.support.fees.Extra;
import org.hiero.hapi.support.fees.FeeSchedule;
import org.hiero.hapi.support.fees.NetworkFee;
import org.hiero.hapi.support.fees.NodeFee;
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
        persistFeeSchedule(simpleFeeSchedule());
        var transaction = cryptoTransferTransaction(0);

        // when
        var result = service.estimateFees(transaction, FeeEstimateMode.INTRINSIC);

        // then
        assertThat(result).isNotNull();
        assertThat(result.getNode()).isNotNull();
        assertThat(result.getNode().getBase()).isEqualTo(100000);
        assertThat(result.getNetwork()).isNotNull();
        assertThat(result.getNetwork().getMultiplier()).isEqualTo(9);
        assertThat(result.getService()).isNotNull();
        assertThat(result.getService().getBase()).isEqualTo(50000);
        assertThat(result.getTotal()).isPositive();
        assertThat(result.getNotes()).isEmpty();
    }

    @Test
    void estimateFeesCalculation() {
        // given
        var schedule = FeeSchedule.newBuilder()
                .extras(makeExtraDef(Extra.SIGNATURES, 100000))
                .node(NodeFee.newBuilder().baseFee(1000).build())
                .network(NetworkFee.newBuilder().multiplier(3).build())
                .services(makeService("Crypto", makeServiceFee(HederaFunctionality.CRYPTO_TRANSFER, 2000)))
                .build();
        persistFeeSchedule(schedule);
        var transaction = cryptoTransferTransaction(0);

        // when
        var result = service.estimateFees(transaction, FeeEstimateMode.INTRINSIC);

        // then
        assertThat(result.getNode().getBase()).isEqualTo(1000);
        assertThat(result.getNetwork().getSubtotal()).isEqualTo(3000);
        assertThat(result.getService().getBase()).isEqualTo(2000);
        assertThat(result.getTotal()).isEqualTo(6000);
    }

    @Test
    void estimateFeesWithExtras() {
        // given
        var schedule = FeeSchedule.newBuilder()
                .extras(makeExtraDef(Extra.SIGNATURES, 200000))
                .node(NodeFee.newBuilder()
                        .baseFee(1000)
                        .extras(makeExtraIncluded(Extra.SIGNATURES, 1))
                        .build())
                .network(NetworkFee.newBuilder().multiplier(1).build())
                .services(makeService("Crypto", makeServiceFee(HederaFunctionality.CRYPTO_TRANSFER, 500)))
                .build();
        persistFeeSchedule(schedule);
        var transaction = cryptoTransferTransaction(2);

        // when
        var result = service.estimateFees(transaction, FeeEstimateMode.INTRINSIC);

        // then
        assertThat(result.getNode().getExtras()).hasSize(1);
        var sigExtra = result.getNode().getExtras().getFirst();
        assertThat(sigExtra.getName()).isEqualTo("SIGNATURES");
        assertThat(sigExtra.getCount()).isEqualTo(2);
        assertThat(sigExtra.getIncluded()).isEqualTo(1);
        assertThat(sigExtra.getCharged()).isEqualTo(1);
        assertThat(sigExtra.getFeePerUnit()).isEqualTo(200000);
        assertThat(sigExtra.getSubtotal()).isEqualTo(200000);

        assertThat(result.getTotal()).isEqualTo(402500);
    }

    @SuppressWarnings("deprecation")
    @Test
    void estimateFeesLegacyFormat() {
        // given
        persistFeeSchedule(simpleFeeSchedule());
        final var body = TransactionBody.newBuilder()
                .setMemo("legacy")
                .setCryptoTransfer(CryptoTransferTransactionBody.newBuilder().build())
                .build();
        final var transaction =
                Transaction.newBuilder().setBodyBytes(body.toByteString()).build();

        // when
        var result = service.estimateFees(transaction, FeeEstimateMode.INTRINSIC);

        // then
        assertThat(result).isNotNull();
        assertThat(result.getTotal()).isPositive();
    }

    @Test
    void stateMode() {
        // given
        var transaction = cryptoTransferTransaction(0);

        // when / then
        assertThatThrownBy(() -> service.estimateFees(transaction, FeeEstimateMode.STATE))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("State-based fee estimation is not supported");
    }

    @Test
    void feeScheduleNotFound() {
        // given
        var transaction = cryptoTransferTransaction(0);

        // when / then
        assertThatThrownBy(() -> service.estimateFees(transaction, FeeEstimateMode.INTRINSIC))
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessageContaining("Simple fee schedule (file 113) not found");
    }

    @Test
    void invalidTransaction() {
        // given
        persistFeeSchedule(simpleFeeSchedule());
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
        // given
        persistFeeSchedule(simpleFeeSchedule());
        var transaction = Transaction.getDefaultInstance();

        // when / then
        assertThatThrownBy(() -> service.estimateFees(transaction, FeeEstimateMode.INTRINSIC))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Transaction must contain body bytes or signed transaction bytes");
    }

    @Test
    void unknownTransactionType() {
        // given
        persistFeeSchedule(simpleFeeSchedule());
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
    void unsupportedTransactionType() {
        // given
        var schedule = FeeSchedule.newBuilder()
                .extras(makeExtraDef(Extra.SIGNATURES, 100000))
                .node(NodeFee.newBuilder().baseFee(1000).build())
                .network(NetworkFee.newBuilder().multiplier(1).build())
                .services(makeService("Consensus", makeServiceFee(HederaFunctionality.CONSENSUS_CREATE_TOPIC, 1000)))
                .build();
        persistFeeSchedule(schedule);
        var transaction = cryptoTransferTransaction(0);

        // when / then
        assertThatThrownBy(() -> service.estimateFees(transaction, FeeEstimateMode.INTRINSIC))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unsupported transaction type");
    }

    private FeeSchedule simpleFeeSchedule() {
        return FeeSchedule.newBuilder()
                .extras(makeExtraDef(Extra.SIGNATURES, 100000), makeExtraDef(Extra.BYTES, 110000))
                .node(NodeFee.newBuilder()
                        .baseFee(100000)
                        .extras(makeExtraIncluded(Extra.BYTES, 1024), makeExtraIncluded(Extra.SIGNATURES, 1))
                        .build())
                .network(NetworkFee.newBuilder().multiplier(9).build())
                .services(makeService("Crypto", makeServiceFee(HederaFunctionality.CRYPTO_TRANSFER, 50000)))
                .build();
    }

    private void persistFeeSchedule(FeeSchedule schedule) {
        var bytes = FeeSchedule.PROTOBUF.toBytes(schedule).toByteArray();
        domainBuilder
                .fileData()
                .customize(f -> f.entityId(systemEntity.simpleFeeScheduleFile()).fileData(bytes))
                .persist();
    }

    private Transaction cryptoTransferTransaction(int signatureCount) {
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
