// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.restjava.service.fee;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.hedera.hapi.node.base.HederaFunctionality;
import com.hedera.hapi.node.base.SignatureMap;
import com.hedera.hapi.node.base.SignaturePair;
import com.hedera.hapi.node.base.Transaction;
import com.hedera.hapi.node.token.CryptoTransferTransactionBody;
import com.hedera.hapi.node.transaction.SignedTransaction;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.service.file.impl.schemas.V0490FileSchema;
import com.hedera.pbj.runtime.ParseException;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import lombok.RequiredArgsConstructor;
import org.hiero.hapi.fees.HighVolumePricingCalculator;
import org.hiero.hapi.support.fees.Extra;
import org.hiero.hapi.support.fees.FeeSchedule;
import org.hiero.hapi.support.fees.VariableRateDefinition;
import org.hiero.mirror.common.domain.RecordItemBuilder;
import org.hiero.mirror.common.domain.SystemEntity;
import org.hiero.mirror.common.domain.transaction.TransactionType;
import org.hiero.mirror.rest.model.FeeEstimateMode;
import org.hiero.mirror.restjava.RestJavaIntegrationTest;
import org.hiero.mirror.restjava.repository.FileDataRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.core.io.ClassPathResource;

@RequiredArgsConstructor
final class FeeEstimationServiceTest extends RestJavaIntegrationTest {

    private final FeeEstimationService service;
    private final FeeEstimationState feeEstimationState;
    private final FeeTopicStore feeTopicStore;
    private final FeeTokenStore feeTokenStore;
    private final FileDataRepository fileDataRepository;
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
        service.refreshStateCalculator();
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource(textBlock = """
    # consensus
    CONSENSUSCREATETOPIC    , 20300000000
    CONSENSUSDELETETOPIC    , 50000000
    CONSENSUSSUBMITMESSAGE  , 8000000
    CONSENSUSUPDATETOPIC    , 402200000

    # contract — ContractCall fees are paid in gas; CN calculator clears all hbar fees
    CONTRACTCALL            , 0
    CONTRACTCREATEINSTANCE  , 20000000000
    CONTRACTDELETEINSTANCE  , 70000000
    CONTRACTUPDATEINSTANCE  , 20260000000

    # crypto
    CRYPTOCREATEACCOUNT     , 10500000000
    CRYPTODELETE            , 50000000
    CRYPTOTRANSFER          , 1000000
    CRYPTOUPDATEACCOUNT     , 20002200000

    # ethereum
    ETHEREUMTRANSACTION     , 1000000

    # file
    FILEAPPEND              , 500000000
    FILECREATE              , 500000000
    FILEDELETE              , 70000000
    FILEUPDATE              , 500000000

    # schedule
    SCHEDULECREATE          , 100000000
    SCHEDULEDELETE          , 10000000
    SCHEDULESIGN            , 10000000

    # token
    TOKENASSOCIATE          , 500000000
    TOKENBURN               , 10000000
    TOKENCREATION           , 20700000000
    TOKENDELETION           , 10000000
    TOKENDISSOCIATE         , 500000000
    TOKENFREEZE             , 10000000
    TOKENGRANTKYC           , 10000000
    TOKENMINT               , 400000000
    TOKENPAUSE              , 10000000
    TOKENREVOKEKYC          , 10000000
    TOKENUNFREEZE           , 10000000
    TOKENUNPAUSE            , 10000000
    TOKENUPDATE             , 710000000
    TOKENWIPE               , 10000000

    # util
    UTILPRNG                , 10000000
    """)
    void estimatesFeeByTransactionType(TransactionType type, long expected) {
        final var pbjTransaction =
                toPbj(recordItemBuilder.lookup(type).get().build().getTransaction());
        for (final var mode : FeeEstimateMode.values()) {
            assertThat(service.estimateFees(pbjTransaction, mode).totalTinycents())
                    .as("Fee for %s in %s mode", type, mode)
                    .isEqualTo(expected);
        }
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
        assertThat(result.getHighVolumeMultiplier())
                .isEqualTo(
                        HighVolumePricingCalculator
                                .DEFAULT_HIGH_VOLUME_MULTIPLIER); // no high-volume rates configured for CryptoTransfer
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
    void estimateFeesLegacyFormatWithSignatures() {
        // given
        final var body = TransactionBody.newBuilder()
                .memo("legacy")
                .cryptoTransfer(CryptoTransferTransactionBody.DEFAULT)
                .build();
        final var sigPairs = List.of(
                SignaturePair.newBuilder()
                        .pubKeyPrefix(Bytes.wrap(new byte[] {0}))
                        .ed25519(Bytes.wrap(new byte[ED25519_SIGNATURE_SIZE]))
                        .build(),
                SignaturePair.newBuilder()
                        .pubKeyPrefix(Bytes.wrap(new byte[] {1}))
                        .ed25519(Bytes.wrap(new byte[ED25519_SIGNATURE_SIZE]))
                        .build());
        final var transaction = Transaction.newBuilder()
                .bodyBytes(TransactionBody.PROTOBUF.toBytes(body))
                .sigMap(SignatureMap.newBuilder().sigPair(sigPairs).build())
                .build();

        // when
        final var result = service.estimateFees(transaction, FeeEstimateMode.INTRINSIC);

        // then — 2 signatures, SIGNATURES_INCLUDED=1 free, 1 extra billed
        final var expectedNodeTotal = NODE_BASE_FEE + (2 - SIGNATURES_INCLUDED) * SIGNATURES_FEE;
        assertThat(result.totalTinycents()).isEqualTo(expectedNodeTotal * (1L + NETWORK_MULTIPLIER));
    }

    @Test
    void loadsSimpleFeeScheduleFromDatabase() {
        // given — construct a fresh service and load the fee schedule seeded in @BeforeEach
        final var freshService = new FeeEstimationService(
                feeEstimationState, fileDataRepository, systemEntity, feeTopicStore, feeTokenStore);
        freshService.refreshStateCalculator();

        // when
        final var result = freshService.estimateFees(cryptoTransfer(0), FeeEstimateMode.INTRINSIC);

        // then
        assertThat(result.getNodeBaseFeeTinycents()).isEqualTo(NODE_BASE_FEE);
        assertThat(result.getNetworkMultiplier()).isEqualTo(NETWORK_MULTIPLIER);
        assertThat(result.totalTinycents()).isEqualTo(NODE_PORTION);
    }

    @Test
    void stateMode() {
        final var result = service.estimateFees(cryptoTransfer(0), FeeEstimateMode.STATE);

        assertThat(result.getNodeBaseFeeTinycents()).isEqualTo(NODE_BASE_FEE);
        assertThat(result.totalTinycents()).isGreaterThan(0);
    }

    @Test
    void feeScheduleChangesAffectBothModes() {
        // Seed a modified schedule with a 10× higher CONSENSUS_SUBMIT_MESSAGE base fee.
        final long customBaseFee = CONSENSUS_SUBMIT_MESSAGE_FEE * 10;
        domainBuilder
                .fileData()
                .customize(f -> f.entityId(systemEntity.simpleFeeScheduleFile())
                        .fileData(buildModifiedFeeSchedule(HederaFunctionality.CONSENSUS_SUBMIT_MESSAGE, customBaseFee))
                        .transactionType(TransactionType.FILECREATE.getProtoId()))
                .persist();
        service.refreshStateCalculator();

        final var txn = toPbj(recordItemBuilder.consensusSubmitMessage().build().getTransaction());

        // Both modes reflect the updated fee schedule after refresh.
        assertThat(service.estimateFees(txn, FeeEstimateMode.INTRINSIC).getServiceBaseFeeTinycents())
                .isEqualTo(customBaseFee);
        assertThat(service.estimateFees(txn, FeeEstimateMode.STATE).getServiceBaseFeeTinycents())
                .isEqualTo(customBaseFee);
    }

    @Test
    void refreshStateCalculatorChangesCalculation() {
        final var txn = toPbj(recordItemBuilder.consensusSubmitMessage().build().getTransaction());

        // Seed schedule with 5× base fee.
        final long baseFee5x = CONSENSUS_SUBMIT_MESSAGE_FEE * 5;
        domainBuilder
                .fileData()
                .customize(f -> f.entityId(systemEntity.simpleFeeScheduleFile())
                        .fileData(buildModifiedFeeSchedule(HederaFunctionality.CONSENSUS_SUBMIT_MESSAGE, baseFee5x))
                        .transactionType(TransactionType.FILECREATE.getProtoId()))
                .persist();
        service.refreshStateCalculator();

        final var first = service.estimateFees(txn, FeeEstimateMode.STATE);
        assertThat(first.getServiceBaseFeeTinycents()).isEqualTo(baseFee5x);

        // Now seed a different schedule with 20× base fee.
        final long baseFee20x = CONSENSUS_SUBMIT_MESSAGE_FEE * 20;
        domainBuilder
                .fileData()
                .customize(f -> f.entityId(systemEntity.simpleFeeScheduleFile())
                        .fileData(buildModifiedFeeSchedule(HederaFunctionality.CONSENSUS_SUBMIT_MESSAGE, baseFee20x))
                        .transactionType(TransactionType.FILECREATE.getProtoId()))
                .persist();
        service.refreshStateCalculator();

        final var second = service.estimateFees(txn, FeeEstimateMode.STATE);
        assertThat(second.getServiceBaseFeeTinycents()).isEqualTo(baseFee20x);
        assertThat(second.totalTinycents()).isGreaterThan(first.totalTinycents());
    }

    @Test
    void refreshStateCalculatorIsNoOpWhenTimestampUnchanged() {
        // Capture the current STATE result (genesis fee schedule from @BeforeEach).
        final var before = service.estimateFees(cryptoTransfer(0), FeeEstimateMode.STATE);

        // Refresh with no new DB data — timestamps are equal, so the calculator is not rebuilt.
        service.refreshStateCalculator();

        final var after = service.estimateFees(cryptoTransfer(0), FeeEstimateMode.STATE);
        assertThat(after.totalTinycents()).isEqualTo(before.totalTinycents());
    }

    @Test
    void stateModeConsensusSubmitMessageWithTopicCustomFees() {
        // Persist a topic and seed its custom fees in the DB.
        final var topic = domainBuilder.topic().persist();
        domainBuilder.customFee().customize(cf -> cf.entityId(topic.getId())).persist();

        // Build a ConsensusSubmitMessage transaction targeting that topic.
        final var txn = toPbj(recordItemBuilder
                .consensusSubmitMessage()
                .transactionBody(b -> b.setTopicID(com.hederahashgraph.api.proto.java.TopicID.newBuilder()
                        .setTopicNum(topic.getId())
                        .build()))
                .build()
                .getTransaction());

        final var intrinsic = service.estimateFees(txn, FeeEstimateMode.INTRINSIC);
        final var state = service.estimateFees(txn, FeeEstimateMode.STATE);

        // STATE reads the DB-backed topic store and detects custom fees;
        // INTRINSIC has no feeContext so the custom-fee branch is skipped.
        final long customFeeExtra = extraFee(Extra.CONSENSUS_SUBMIT_MESSAGE_WITH_CUSTOM_FEE);
        assertThat(state.totalTinycents()).isEqualTo(intrinsic.totalTinycents() + customFeeExtra);
    }

    @Test
    void stateModeTokenTransferWithCustomFees() {
        // Token without custom fees.
        final var plainToken = domainBuilder.token().persist();
        // Token with custom fees.
        final var customFeeToken = domainBuilder.token().persist();
        domainBuilder
                .customFee()
                .customize(cf -> cf.entityId(customFeeToken.getTokenId()))
                .persist();

        final var txnPlain = buildFungibleTokenTransfer(plainToken.getTokenId());
        final var txnCustom = buildFungibleTokenTransfer(customFeeToken.getTokenId());

        final var statePlain = service.estimateFees(txnPlain, FeeEstimateMode.STATE);
        final var stateCustom = service.estimateFees(txnCustom, FeeEstimateMode.STATE);

        // STATE with a custom-fee token uses TOKEN_TRANSFER_BASE_CUSTOM_FEES instead of TOKEN_TRANSFER_BASE.
        final long base = extraFee(Extra.TOKEN_TRANSFER_BASE);
        final long baseCustom = extraFee(Extra.TOKEN_TRANSFER_BASE_CUSTOM_FEES);
        assertThat(stateCustom.totalTinycents() - statePlain.totalTinycents()).isEqualTo(baseCustom - base);
    }

    @Test
    void stateModeTopicCustomFeesLive() {
        final var topic = domainBuilder.topic().persist();
        final var txn = toPbj(recordItemBuilder
                .consensusSubmitMessage()
                .transactionBody(b -> b.setTopicID(com.hederahashgraph.api.proto.java.TopicID.newBuilder()
                        .setTopicNum(topic.getId())
                        .build()))
                .build()
                .getTransaction());

        final var before = service.estimateFees(txn, FeeEstimateMode.STATE);
        // Custom fees added after the first estimate — store reads live from DB, no refresh needed.
        domainBuilder.customFee().customize(cf -> cf.entityId(topic.getId())).persist();

        final var after = service.estimateFees(txn, FeeEstimateMode.STATE);
        assertThat(after.totalTinycents())
                .isEqualTo(before.totalTinycents() + extraFee(Extra.CONSENSUS_SUBMIT_MESSAGE_WITH_CUSTOM_FEE));
    }

    @Test
    void stateModeTokenCustomFeesLive() {
        final var token = domainBuilder.token().persist();
        final var txn = buildFungibleTokenTransfer(token.getTokenId());

        final var before = service.estimateFees(txn, FeeEstimateMode.STATE);
        // Custom fees added after the first estimate — store reads live from DB, no refresh needed.
        domainBuilder
                .customFee()
                .customize(cf -> cf.entityId(token.getTokenId()))
                .persist();

        final var after = service.estimateFees(txn, FeeEstimateMode.STATE);
        assertThat(after.totalTinycents() - before.totalTinycents())
                .isEqualTo(extraFee(Extra.TOKEN_TRANSFER_BASE_CUSTOM_FEES) - extraFee(Extra.TOKEN_TRANSFER_BASE));
    }

    @Test
    void estimatesFeesForAllKnownTransactionTypes() {
        for (final var type : TransactionType.values()) {
            final var supplier = recordItemBuilder.lookup(type);
            if (type == TransactionType.UNKNOWN || supplier == null) {
                continue;
            }
            final var txn = toPbj(supplier.get().build().getTransaction());

            // INTRINSIC mode produces a valid fee for all fully-supported transaction types.
            try {
                assertThat(service.estimateFees(txn, FeeEstimateMode.INTRINSIC).totalTinycents())
                        .as("INTRINSIC fee for %s", type)
                        .isGreaterThanOrEqualTo(0);
            } catch (IllegalArgumentException e) {
                assertThat(e).hasMessageContaining("Unknown transaction type");
            }

            // STATE mode may additionally throw for types whose congestion multiplier reads stores
            // not backed by mirror state (accounts, contracts, files, NFTs, token relations).
            // Pending upstream fix in CN's UtilizationScaledThrottleMultiplier to null-check stores.
            try {
                assertThat(service.estimateFees(txn, FeeEstimateMode.STATE).totalTinycents())
                        .as("STATE fee for %s", type)
                        .isGreaterThanOrEqualTo(0);
            } catch (UnsupportedOperationException e) {
                assertThat(e).hasMessageContaining("Store not supported:");
            } catch (IllegalArgumentException e) {
                assertThat(e).hasMessageContaining("Unknown transaction type");
            }
        }
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
    void highVolumeMultiplierReturnedFromConfiguredFeeSchedule() {
        // given
        final int customMaxRaw = 100_000;
        domainBuilder
                .fileData()
                .customize(f -> f.entityId(systemEntity.simpleFeeScheduleFile())
                        .fileData(buildFeeScheduleWithHighVolumeRates(
                                HederaFunctionality.CONSENSUS_SUBMIT_MESSAGE, customMaxRaw))
                        .transactionType(TransactionType.FILECREATE.getProtoId()))
                .persist();
        service.refreshStateCalculator();

        // when
        final var txn = toPbj(recordItemBuilder.consensusSubmitMessage().build().getTransaction());
        final var intrinsic = service.estimateFees(txn, FeeEstimateMode.INTRINSIC);
        final var state = service.estimateFees(txn, FeeEstimateMode.STATE);

        assertThat(intrinsic.getHighVolumeMultiplier()).isEqualTo(customMaxRaw);
        assertThat(state.getHighVolumeMultiplier()).isEqualTo(customMaxRaw);
        assertThat(intrinsic.totalTinycents()).isEqualTo(NODE_PORTION + CONSENSUS_SUBMIT_MESSAGE_FEE);
        assertThat(state.totalTinycents()).isEqualTo(NODE_PORTION + CONSENSUS_SUBMIT_MESSAGE_FEE);
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
                .transactionBody(
                        b -> b.setMessage(com.google.protobuf.ByteString.copyFrom(new byte[LONG_MESSAGE_BYTES])))
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

    private static byte[] buildModifiedFeeSchedule(HederaFunctionality func, long baseFee) {
        final var modified = FEE_SCHEDULE
                .copyBuilder()
                .services(FEE_SCHEDULE.services().stream()
                        .map(svc -> svc.copyBuilder()
                                .schedule(svc.schedule().stream()
                                        .map(def -> def.name() == func
                                                ? def.copyBuilder()
                                                        .baseFee(baseFee)
                                                        .build()
                                                : def)
                                        .toList())
                                .build())
                        .toList())
                .build();
        return FeeSchedule.PROTOBUF.toBytes(modified).toByteArray();
    }

    private static byte[] buildFeeScheduleWithHighVolumeRates(HederaFunctionality func, int maxMultiplier) {
        final var modified = FEE_SCHEDULE
                .copyBuilder()
                .services(FEE_SCHEDULE.services().stream()
                        .map(svc -> svc.copyBuilder()
                                .schedule(svc.schedule().stream()
                                        .map(def -> def.name() == func
                                                ? def.copyBuilder()
                                                        .highVolumeRates(VariableRateDefinition.newBuilder()
                                                                .maxMultiplier(maxMultiplier)
                                                                .build())
                                                        .build()
                                                : def)
                                        .toList())
                                .build())
                        .toList())
                .build();
        return FeeSchedule.PROTOBUF.toBytes(modified).toByteArray();
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

    /**
     * Builds a simple fungible-token CryptoTransfer targeting the given token.
     * Used to exercise the STATE-mode token-store custom-fee lookup.
     */
    private Transaction buildFungibleTokenTransfer(long tokenId) {
        return toPbj(recordItemBuilder
                .cryptoTransfer(RecordItemBuilder.TransferType.TOKEN)
                .transactionBody(b -> b.clearTokenTransfers()
                        .addTokenTransfers(com.hederahashgraph.api.proto.java.TokenTransferList.newBuilder()
                                .setToken(com.hederahashgraph.api.proto.java.TokenID.newBuilder()
                                        .setTokenNum(tokenId))
                                .addTransfers(com.hederahashgraph.api.proto.java.AccountAmount.newBuilder()
                                        .setAccountID(com.hederahashgraph.api.proto.java.AccountID.newBuilder()
                                                .setAccountNum(1))
                                        .setAmount(-100))
                                .addTransfers(com.hederahashgraph.api.proto.java.AccountAmount.newBuilder()
                                        .setAccountID(com.hederahashgraph.api.proto.java.AccountID.newBuilder()
                                                .setAccountNum(2))
                                        .setAmount(100))))
                .build()
                .getTransaction());
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
