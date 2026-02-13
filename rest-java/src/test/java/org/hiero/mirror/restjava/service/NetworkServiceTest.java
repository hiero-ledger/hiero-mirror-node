// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.restjava.service;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.hiero.hapi.fees.FeeScheduleUtils.makeExtraDef;
import static org.hiero.hapi.fees.FeeScheduleUtils.makeService;
import static org.hiero.hapi.fees.FeeScheduleUtils.makeServiceFee;

import com.hedera.hapi.node.base.HederaFunctionality;
import com.hederahashgraph.api.proto.java.CryptoTransferTransactionBody;
import com.hederahashgraph.api.proto.java.SignedTransaction;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionBody;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.hiero.hapi.support.fees.Extra;
import org.hiero.hapi.support.fees.FeeSchedule;
import org.hiero.hapi.support.fees.NetworkFee;
import org.hiero.hapi.support.fees.NodeFee;
import org.hiero.mirror.rest.model.FeeEstimateMode;
import org.hiero.mirror.restjava.RestJavaIntegrationTest;
import org.junit.jupiter.api.Test;

@RequiredArgsConstructor
final class NetworkServiceTest extends RestJavaIntegrationTest {

    private final NetworkService networkService;

    @Test
    void returnsLatestStake() {
        // given
        final var expected = domainBuilder.networkStake().persist();

        // when
        final var actual = networkService.getLatestNetworkStake();

        // then
        assertThat(actual).isEqualTo(expected);
    }

    @Test
    void throwsIfNoStakePresent() {
        assertThatThrownBy(networkService::getLatestNetworkStake)
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessage("No network stake data found");
    }

    @Test
    void getSupplyFromEntity() {
        // given
        final var balance = 1_000_000_000L;
        final var timestamp = domainBuilder.timestamp();
        domainBuilder
                .entity()
                .customize(e -> e.id(domainBuilder.entityNum(2).getId())
                        .balance(balance)
                        .balanceTimestamp(timestamp))
                .persist();

        // when
        final var result = networkService.getSupply(Bound.EMPTY);

        // then
        assertThat(result).isNotNull();
        assertThat(result.consensusTimestamp()).isEqualTo(timestamp);
        assertThat(result.releasedSupply()).isNotNull();
    }

    @Test
    void getSupplyNotFound() {
        // when, then
        assertThatThrownBy(() -> networkService.getSupply(Bound.EMPTY))
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessageContaining("Network supply not found");
    }

    @Test
    void estimateFees() {
        // given
        persistSimpleFeeSchedule();
        var transaction = cryptoTransferTransaction();

        // when
        var result = networkService.estimateFees(transaction, FeeEstimateMode.INTRINSIC);

        // then
        assertThat(result).isNotNull();
        assertThat(result.getTotal()).isPositive();
    }

    @Test
    void estimateFeesStateMode() {
        // given
        var transaction = cryptoTransferTransaction();

        // when / then
        assertThatThrownBy(() -> networkService.estimateFees(transaction, FeeEstimateMode.STATE))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("State-based fee estimation is not supported");
    }

    @Test
    void estimateFeesFeeScheduleNotFound() {
        // given
        var transaction = cryptoTransferTransaction();

        // when / then
        assertThatThrownBy(() -> networkService.estimateFees(transaction, FeeEstimateMode.INTRINSIC))
                .isInstanceOf(EntityNotFoundException.class);
    }

    private void persistSimpleFeeSchedule() {
        var schedule = FeeSchedule.newBuilder()
                .extras(makeExtraDef(Extra.SIGNATURES, 100000), makeExtraDef(Extra.BYTES, 110000))
                .node(NodeFee.newBuilder().baseFee(100000).build())
                .network(NetworkFee.newBuilder().multiplier(9).build())
                .services(makeService("Crypto", makeServiceFee(HederaFunctionality.CRYPTO_TRANSFER, 50000)))
                .build();
        var bytes = FeeSchedule.PROTOBUF.toBytes(schedule).toByteArray();
        domainBuilder
                .fileData()
                .customize(f -> f.entityId(systemEntity.simpleFeeScheduleFile()).fileData(bytes))
                .persist();
    }

    private Transaction cryptoTransferTransaction() {
        var body = TransactionBody.newBuilder()
                .setMemo("test")
                .setCryptoTransfer(CryptoTransferTransactionBody.newBuilder().build())
                .build();
        var signedTransaction =
                SignedTransaction.newBuilder().setBodyBytes(body.toByteString()).build();
        return Transaction.newBuilder()
                .setSignedTransactionBytes(signedTransaction.toByteString())
                .build();
    }
}
