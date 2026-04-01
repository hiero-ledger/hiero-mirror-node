// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.restjava.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.google.protobuf.ByteString;
import com.hedera.hapi.node.base.HederaFunctionality;
import com.hedera.hapi.node.base.SignatureMap;
import com.hedera.hapi.node.base.SignaturePair;
import com.hedera.hapi.node.base.Transaction;
import com.hedera.hapi.node.token.CryptoTransferTransactionBody;
import com.hedera.hapi.node.transaction.SignedTransaction;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.config.ConfigProviderImpl;
import com.hedera.node.app.fees.StandaloneFeeCalculatorImpl;
import com.hedera.node.app.service.entityid.impl.AppEntityIdFactory;
import com.hedera.node.app.service.file.impl.schemas.V0490FileSchema;
import com.hedera.node.app.workflows.standalone.TransactionExecutors;
import com.hedera.pbj.runtime.ParseException;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.NoSuchElementException;
import lombok.RequiredArgsConstructor;
import org.hiero.hapi.support.fees.Extra;
import org.hiero.hapi.support.fees.FeeSchedule;
import org.hiero.mirror.common.domain.RecordItemBuilder;
import org.hiero.mirror.common.domain.SystemEntity;
import org.hiero.mirror.common.domain.transaction.TransactionType;
import org.hiero.mirror.rest.model.FeeEstimateMode;
import org.hiero.mirror.restjava.RestJavaIntegrationTest;
import org.hiero.mirror.restjava.service.fee.FeeEstimationService;
import org.hiero.mirror.restjava.service.fee.FeeEstimationState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.core.io.ClassPathResource;

@RequiredArgsConstructor
final class FeeEstimationServiceTest extends RestJavaIntegrationTest {

    private static final Map<String, String> INTRINSIC_PROPERTIES = Map.of("fees.simpleFeesEnabled", "true");

    private final FeeEstimationService service;
    private final FileService fileService;
    private final RecordItemBuilder recordItemBuilder;
    private final SystemEntity systemEntity;

    // Fee schedule constants match the bundled genesis JSON used to seed the test DB in @BeforeEach.
    // All expected fee values are derived from this schedule — no hardcoded numbers.
    private static final FeeSchedule FEE_SCHEDULE = loadFeeSchedule();

    // Node fee components
    private static final long NODE_BASE_FEE = FEE_SCHEDULE.node().baseFee();
    private static final int NETWORK_MULTIPLIER = FEE_SCHEDULE.network().multiplier();

    // For a DEFAULT transaction (no signatures, body under the included-bytes threshold):
    // total = nodeBase × (1 + networkMultiplier)
    private static final long NODE_PORTION = NODE_BASE_FEE + NODE_BASE_FEE * NETWORK_MULTIPLIER;
    private static final int SIGNATURES_INCLUDED = FEE_SCHEDULE.node().extras().stream()
            .filter(e -> e.name() == Extra.SIGNATURES)
            .findFirst()
            .orElseThrow()
            .includedCount();
    private static final long SIGNATURES_FEE = extraFee(Extra.SIGNATURES);
    private static final long STATE_BYTES_FEE = extraFee(Extra.STATE_BYTES);
    private static final long CONSENSUS_SUBMIT_MESSAGE_FEE = serviceFee(HederaFunctionality.CONSENSUS_SUBMIT_MESSAGE);
    private static final long CRYPTO_DELETE_FEE = serviceFee(HederaFunctionality.CRYPTO_DELETE);
    private static final long CRYPTO_CREATE_FEE = serviceFee(HederaFunctionality.CRYPTO_CREATE);
    private static final long HOOK_UPDATES_EXTRA = extraFee(Extra.HOOK_UPDATES);
    private static final int ED25519_SIGNATURE_SIZE = 64;
    private static final int INVALID_TX_SIZE = 100;
    private static final int LONG_MESSAGE_BYTES = 2_000;

    @BeforeEach
    void setup() {
        final var feeBytes = FeeSchedule.PROTOBUF.toBytes(FEE_SCHEDULE).toByteArray();
        domainBuilder
                .fileData()
                .customize(f -> f.entityId(systemEntity.simpleFeeScheduleFile()).fileData(feeBytes))
                .persist();
    }

    @ParameterizedTest(name = "{0} [{1}]")
    @CsvSource(textBlock = """
            # consensus
            CONSENSUSCREATETOPIC,   INTRINSIC, 20300000000
            CONSENSUSDELETETOPIC,   INTRINSIC,    50000000
            CONSENSUSSUBMITMESSAGE, INTRINSIC,     8000000
            CONSENSUSUPDATETOPIC,   INTRINSIC,   402200000
            # contract — ContractCall fees are paid in gas; CN calculator clears all hbar fees
            CONTRACTCALL,           INTRINSIC,           0
            CONTRACTCREATEINSTANCE, INTRINSIC, 20000000000
            CONTRACTDELETEINSTANCE, INTRINSIC,    70000000
            CONTRACTUPDATEINSTANCE, INTRINSIC, 20260000000
            # crypto
            CRYPTOCREATEACCOUNT,    INTRINSIC, 10500000000
            CRYPTODELETE,           INTRINSIC,    50000000
            CRYPTOTRANSFER,         INTRINSIC,     1000000
            CRYPTOUPDATEACCOUNT,    INTRINSIC, 20002200000
            # ethereum
            ETHEREUMTRANSACTION,    INTRINSIC,     1000000
            # file
            FILEAPPEND,             INTRINSIC,   500000000
            FILECREATE,             INTRINSIC,   500000000
            FILEDELETE,             INTRINSIC,    70000000
            FILEUPDATE,             INTRINSIC,   500000000
            # schedule
            SCHEDULECREATE,         INTRINSIC,   100000000
            SCHEDULEDELETE,         INTRINSIC,    10000000
            SCHEDULESIGN,           INTRINSIC,    10000000
            # token
            TOKENASSOCIATE,         INTRINSIC,   500000000
            TOKENBURN,              INTRINSIC,    10000000
            TOKENCREATION,          INTRINSIC, 20700000000
            TOKENDELETION,          INTRINSIC,    10000000
            TOKENDISSOCIATE,        INTRINSIC,   500000000
            TOKENFREEZE,            INTRINSIC,    10000000
            TOKENGRANTKYC,          INTRINSIC,    10000000
            TOKENMINT,              INTRINSIC,   400000000
            TOKENPAUSE,             INTRINSIC,    10000000
            TOKENREVOKEKYC,         INTRINSIC,    10000000
            TOKENUNFREEZE,          INTRINSIC,    10000000
            TOKENUNPAUSE,           INTRINSIC,    10000000
            TOKENUPDATE,            INTRINSIC,   710000000
            TOKENWIPE,              INTRINSIC,    10000000
            # util
            UTILPRNG,               INTRINSIC,    10000000
            """)
    void estimatesFeeByTransactionType(TransactionType type, FeeEstimateMode mode, long expected) {
        final var pbjTransaction =
                toPbj(recordItemBuilder.lookup(type).get().build().getTransaction());
        final var result = service.estimateFees(pbjTransaction, mode);
        assertThat(result.totalTinycents())
                .as("Fee for %s in %s mode", type, mode)
                .isEqualTo(expected);
    }

    @Test
    void estimateFees() {
        // given
        final var transaction = cryptoTransfer(0);

        // when
        final var result = service.estimateFees(transaction, FeeEstimateMode.INTRINSIC);

        // then
        assertThat(result.getNodeBaseFeeTinycents()).isEqualTo(NODE_BASE_FEE);
        assertThat(result.getNetworkMultiplier()).isEqualTo(NETWORK_MULTIPLIER);
        assertThat(result.getServiceBaseFeeTinycents()).isZero(); // CryptoTransfer has no service fee
        assertThat(result.totalTinycents()).isEqualTo(NODE_PORTION);
    }

    @Test
    void estimateFeesWithSignatures() {
        // when
        final var base = service.estimateFees(cryptoTransfer(0), FeeEstimateMode.INTRINSIC);
        final var withSignatures = service.estimateFees(cryptoTransfer(2), FeeEstimateMode.INTRINSIC);

        // then
        // 0 signatures: no extras charged → NODE_PORTION
        assertThat(base.totalTinycents()).isEqualTo(NODE_PORTION);
        // 2 signatures, SIGNATURES_INCLUDED=1 free, 1 extra at SIGNATURES_FEE in the node component:
        // nodeTotal = NODE_BASE_FEE + 1 × SIGNATURES_FEE
        // total    = nodeTotal × (1 + NETWORK_MULTIPLIER)
        final var twoSigsNodeTotal = NODE_BASE_FEE + (2 - SIGNATURES_INCLUDED) * SIGNATURES_FEE;
        assertThat(withSignatures.totalTinycents()).isEqualTo(twoSigsNodeTotal * (1L + NETWORK_MULTIPLIER));
    }

    @Test
    void estimateFeesLegacyFormat() {
        // given
        final var body = TransactionBody.newBuilder()
                .memo("legacy")
                .cryptoTransfer(CryptoTransferTransactionBody.DEFAULT)
                .build();
        final var transaction = Transaction.newBuilder()
                .bodyBytes(TransactionBody.PROTOBUF.toBytes(body))
                .build();

        // when
        final var result = service.estimateFees(transaction, FeeEstimateMode.INTRINSIC);

        // then
        assertThat(result.totalTinycents()).isEqualTo(NODE_PORTION);
    }

    @Test
    void loadsSimpleFeeScheduleFromDatabase() {
        // given
        final var state = new FeeEstimationState(systemEntity, fileService);
        final var overrideValues = new HashMap<>(INTRINSIC_PROPERTIES);
        overrideValues.put("hedera.realm", String.valueOf(commonProperties.getRealm()));
        overrideValues.put("hedera.shard", String.valueOf(commonProperties.getShard()));
        final var properties = TransactionExecutors.Properties.newBuilder()
                .state(state)
                .appProperties(overrideValues)
                .build();
        final var config = new ConfigProviderImpl(false, null, overrideValues).getConfiguration();
        final var calculator = new StandaloneFeeCalculatorImpl(state, properties, new AppEntityIdFactory(config));
        final var freshService = new FeeEstimationService(calculator);

        // when
        final var result = freshService.estimateFees(cryptoTransfer(0), FeeEstimateMode.INTRINSIC);

        // then
        assertThat(result.getNodeBaseFeeTinycents()).isEqualTo(NODE_BASE_FEE);
        assertThat(result.getNetworkMultiplier()).isEqualTo(NETWORK_MULTIPLIER);
        assertThat(result.totalTinycents()).isEqualTo(NODE_PORTION);
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
        final var transaction = Transaction.newBuilder()
                .signedTransactionBytes(Bytes.wrap(domainBuilder.bytes(INVALID_TX_SIZE)))
                .build();

        // when / then
        assertThatThrownBy(() -> service.estimateFees(transaction, FeeEstimateMode.INTRINSIC))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unable to parse transaction");
    }

    @Test
    void emptyTransaction() {
        assertThatThrownBy(() -> service.estimateFees(Transaction.DEFAULT, FeeEstimateMode.INTRINSIC))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Transaction must contain body bytes or signed transaction bytes");
    }

    @Test
    void unknownTransactionType() {
        // given
        final var body = TransactionBody.newBuilder().memo("test").build();
        final var signedTransaction = SignedTransaction.newBuilder()
                .bodyBytes(TransactionBody.PROTOBUF.toBytes(body))
                .build();
        final var transaction = Transaction.newBuilder()
                .signedTransactionBytes(SignedTransaction.PROTOBUF.toBytes(signedTransaction))
                .build();

        // when / then
        assertThatThrownBy(() -> service.estimateFees(transaction, FeeEstimateMode.INTRINSIC))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unknown transaction type");
    }

    @Test
    void contractCall() {
        final var result = service.estimateFees(
                toPbj(recordItemBuilder.contractCall().build().getTransaction()), FeeEstimateMode.INTRINSIC);
        assertThat(result.totalTinycents()).isZero();
    }

    @Test
    void cryptoDelete() {
        // when
        final var result = service.estimateFees(
                toPbj(recordItemBuilder.cryptoDelete().build().getTransaction()), FeeEstimateMode.INTRINSIC);

        // then
        assertThat(result.getServiceBaseFeeTinycents()).isEqualTo(CRYPTO_DELETE_FEE);
        assertThat(result.totalTinycents()).isEqualTo(NODE_PORTION + CRYPTO_DELETE_FEE);
    }

    @Test
    void cryptoCreate() {
        // when
        final var result = service.estimateFees(
                toPbj(recordItemBuilder.cryptoCreate().build().getTransaction()), FeeEstimateMode.INTRINSIC);

        // then
        assertThat(result.getServiceBaseFeeTinycents()).isEqualTo(CRYPTO_CREATE_FEE);
        // total
        assertThat(result.totalTinycents()).isEqualTo(NODE_PORTION + CRYPTO_CREATE_FEE + HOOK_UPDATES_EXTRA);
    }

    @Test
    void consensusSubmitMessage() {
        // given
        final var result = service.estimateFees(
                toPbj(recordItemBuilder.consensusSubmitMessage().build().getTransaction()), FeeEstimateMode.INTRINSIC);

        // then
        assertThat(result.getServiceBaseFeeTinycents()).isEqualTo(CONSENSUS_SUBMIT_MESSAGE_FEE);
        assertThat(result.totalTinycents()).isEqualTo(NODE_PORTION + CONSENSUS_SUBMIT_MESSAGE_FEE);
    }

    @Test
    void consensusSubmitMessageLong() {
        // given
        final var pbjTransaction = toPbj(recordItemBuilder
                .consensusSubmitMessage()
                .transactionBody(b -> b.setMessage(ByteString.copyFrom(new byte[LONG_MESSAGE_BYTES])))
                .build()
                .getTransaction());

        // when
        final var result = service.estimateFees(pbjTransaction, FeeEstimateMode.INTRINSIC);

        // then
        final long expectedServiceExtra = (long) (LONG_MESSAGE_BYTES - 1_024) * STATE_BYTES_FEE;
        assertThat(result.getServiceBaseFeeTinycents()).isEqualTo(CONSENSUS_SUBMIT_MESSAGE_FEE);
        assertThat(result.totalTinycents())
                .isGreaterThanOrEqualTo(NODE_PORTION + CONSENSUS_SUBMIT_MESSAGE_FEE + expectedServiceExtra);
    }

    private static FeeSchedule loadFeeSchedule() {
        try (final var in = new ClassPathResource(
                        "genesis/simpleFeesSchedules.json", V0490FileSchema.class.getClassLoader())
                .getInputStream()) {
            return V0490FileSchema.parseSimpleFeesSchedules(in.readAllBytes());
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load simpleFeesSchedules.json", e);
        }
    }

    private static long extraFee(Extra extra) {
        return FEE_SCHEDULE.extras().stream()
                .filter(e -> e.name() == extra)
                .findFirst()
                .orElseThrow(() -> new NoSuchElementException("Extra fee not found: " + extra.protoName()))
                .fee();
    }

    private static long serviceFee(HederaFunctionality func) {
        return FEE_SCHEDULE.services().stream()
                .flatMap(s -> s.schedule().stream())
                .filter(f -> f.name() == func)
                .findFirst()
                .orElseThrow(() -> new NoSuchElementException("Service fee not found: " + func.protoName()))
                .baseFee();
    }

    private Transaction toPbj(com.hederahashgraph.api.proto.java.Transaction protoc) {
        try {
            return Transaction.PROTOBUF.parse(Bytes.wrap(protoc.toByteArray()));
        } catch (ParseException e) {
            throw new IllegalStateException("Failed to convert protoc Transaction to PBJ", e);
        }
    }

    private Transaction cryptoTransfer(int signatureCount) {
        final var body = TransactionBody.newBuilder()
                .memo("test")
                .cryptoTransfer(CryptoTransferTransactionBody.DEFAULT)
                .build();
        final var sigPairs = new ArrayList<SignaturePair>(signatureCount);
        for (int i = 0; i < signatureCount; i++) {
            sigPairs.add(SignaturePair.newBuilder()
                    .pubKeyPrefix(Bytes.wrap(new byte[] {(byte) i}))
                    .ed25519(Bytes.wrap(new byte[ED25519_SIGNATURE_SIZE]))
                    .build());
        }
        final var signedTransaction = SignedTransaction.newBuilder()
                .bodyBytes(TransactionBody.PROTOBUF.toBytes(body))
                .sigMap(SignatureMap.newBuilder().sigPair(sigPairs).build())
                .build();
        return Transaction.newBuilder()
                .signedTransactionBytes(SignedTransaction.PROTOBUF.toBytes(signedTransaction))
                .build();
    }
}
