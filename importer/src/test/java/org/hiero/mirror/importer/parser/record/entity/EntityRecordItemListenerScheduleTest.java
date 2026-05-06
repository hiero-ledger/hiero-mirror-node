// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.parser.record.entity;

import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.from;
import static org.hiero.mirror.common.domain.entity.EntityType.SCHEDULE;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.google.protobuf.ByteString;
import com.google.protobuf.UnknownFieldSet;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.SchedulableTransactionBody;
import com.hederahashgraph.api.proto.java.ScheduleCreateTransactionBody;
import com.hederahashgraph.api.proto.java.ScheduleDeleteTransactionBody;
import com.hederahashgraph.api.proto.java.ScheduleID;
import com.hederahashgraph.api.proto.java.SignatureMap;
import com.hederahashgraph.api.proto.java.SignaturePair;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionReceipt;
import com.hederahashgraph.api.proto.java.TransactionRecord;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.RandomStringUtils;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.hiero.mirror.common.domain.schedule.Schedule;
import org.hiero.mirror.common.domain.transaction.BlockTransaction;
import org.hiero.mirror.common.domain.transaction.RecordItem;
import org.hiero.mirror.common.domain.transaction.TransactionSignature;
import org.hiero.mirror.common.util.DomainUtils;
import org.hiero.mirror.importer.TestUtils;
import org.hiero.mirror.importer.downloader.block.BlockFileTransformer;
import org.hiero.mirror.importer.parser.domain.BlockFileBuilder;
import org.hiero.mirror.importer.parser.domain.BlockTransactionBuilder;
import org.hiero.mirror.importer.repository.ScheduleRepository;
import org.hiero.mirror.importer.repository.TransactionRepository;
import org.hiero.mirror.importer.repository.TransactionSignatureRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

@RequiredArgsConstructor
final class EntityRecordItemListenerScheduleTest extends AbstractEntityRecordItemListenerTest {

    private static final long CREATE_TIMESTAMP = 1L;
    private static final long EXECUTE_TIMESTAMP = 500L;
    private static final String SCHEDULE_CREATE_MEMO = "ScheduleCreate memo";
    private static final SchedulableTransactionBody SCHEDULED_TRANSACTION_BODY =
            SchedulableTransactionBody.getDefaultInstance();
    private static final Key SCHEDULE_REF_KEY = keyFromString(KEY);
    private static final long SIGN_TIMESTAMP = 10L;

    private final BlockFileBuilder blockFileBuilder;
    private final BlockFileTransformer blockFileTransformer;
    private final BlockTransactionBuilder blockTransactionBuilder;
    private final ScheduleRepository scheduleRepository;
    private final TransactionSignatureRepository transactionSignatureRepository;
    private final TransactionRepository transactionRepository;

    private List<TransactionSignature> defaultSignatureList;
    private ScheduleID scheduleId;

    private static Stream<Arguments> provideScheduleCreatePayer() {
        return Stream.of(
                Arguments.of(null, PAYER, "no payer expect same as creator", false),
                Arguments.of(PAYER, PAYER, "payer set to creator", false),
                Arguments.of(PAYER2, PAYER2, "payer different than creator", false),
                Arguments.of(null, PAYER, "no payer expect same as creator", true),
                Arguments.of(PAYER, PAYER, "payer set to creator", true),
                Arguments.of(PAYER2, PAYER2, "payer different than creator", true));
    }

    @BeforeEach
    void before() {
        entityProperties.getPersist().setSchedules(true);
        scheduleId = recordItemBuilder.scheduleId();
        defaultSignatureList = toTransactionSignatureList(CREATE_TIMESTAMP, scheduleId, DEFAULT_SIG_MAP);
    }

    @ParameterizedTest(name = "{2}")
    @MethodSource("provideScheduleCreatePayer")
    void scheduleCreate(
            final AccountID payer,
            final AccountID expectedPayer,
            final String name,
            final boolean useBlockTransformer) {
        final var recordItem =
                insertScheduleCreateTransaction(CREATE_TIMESTAMP, payer, scheduleId, useBlockTransformer);

        // verify entity count
        final var expectedEntity = createEntity(
                EntityId.of(scheduleId),
                SCHEDULE,
                SCHEDULE_REF_KEY,
                null,
                null,
                false,
                null,
                SCHEDULE_CREATE_MEMO,
                CREATE_TIMESTAMP,
                CREATE_TIMESTAMP);

        final var expectedSchedule = Schedule.builder()
                .consensusTimestamp(CREATE_TIMESTAMP)
                .creatorAccountId(EntityId.of(PAYER))
                .payerAccountId(EntityId.of(expectedPayer))
                .scheduleId(EntityId.of(scheduleId).getId())
                .transactionBody(SCHEDULED_TRANSACTION_BODY.toByteArray())
                .build();

        assertEquals(1, entityRepository.count());
        assertEntity(expectedEntity);

        // verify schedule and signatures
        assertThat(scheduleRepository.findAll()).containsOnly(expectedSchedule);

        assertTransactionSignatureInRepository(defaultSignatureList);

        // verify transaction
        assertTransactionInRepository(recordItem.getCongestionPricingMultiplier(), CREATE_TIMESTAMP, false, SUCCESS);
    }

    @Test
    void scheduleCreateLongTermScheduledTransaction() {
        var expirationTime = recordItemBuilder.timestamp();
        var pubKeyPrefix = recordItemBuilder.bytes(16);
        var signature = recordItemBuilder.bytes(32);
        var recordItem = recordItemBuilder
                .scheduleCreate()
                .receipt(r -> r.setScheduleID(scheduleId))
                .signatureMap(s -> s.clear()
                        .addSigPair(SignaturePair.newBuilder()
                                .setPubKeyPrefix(pubKeyPrefix)
                                .setEd25519(signature)))
                .transactionBody(b -> b.setExpirationTime(expirationTime).setWaitForExpiry(true))
                .build();
        var scheduleCreate = recordItem.getTransactionBody().getScheduleCreate();
        var timestamp = recordItem.getConsensusTimestamp();
        var expectedEntity = createEntity(
                EntityId.of(scheduleId),
                SCHEDULE,
                scheduleCreate.getAdminKey(),
                null,
                null,
                false,
                null,
                scheduleCreate.getMemo(),
                timestamp,
                timestamp);
        var expectedSchedule = Schedule.builder()
                .consensusTimestamp(recordItem.getConsensusTimestamp())
                .creatorAccountId(recordItem.getPayerAccountId())
                .expirationTime(DomainUtils.timestampInNanosMax(expirationTime))
                .payerAccountId(EntityId.of(scheduleCreate.getPayerAccountID()))
                .scheduleId(EntityId.of(scheduleId).getId())
                .transactionBody(scheduleCreate.getScheduledTransactionBody().toByteArray())
                .waitForExpiry(true)
                .build();
        var expectedTransactionSignature = TransactionSignature.builder()
                .consensusTimestamp(timestamp)
                .entityId(EntityId.of(scheduleId))
                .publicKeyPrefix(DomainUtils.toBytes(pubKeyPrefix))
                .signature(DomainUtils.toBytes(signature))
                .type(SignaturePair.ED25519_FIELD_NUMBER)
                .build();

        // when
        parseRecordItemAndCommit(recordItem);

        // then
        assertThat(entityRepository.findAll()).containsOnly(expectedEntity);
        assertThat(scheduleRepository.findAll()).containsOnly(expectedSchedule);
        assertThat(transactionSignatureRepository.findAll()).containsOnly(expectedTransactionSignature);
        assertTransactionInRepository(recordItem.getCongestionPricingMultiplier(), timestamp, false, SUCCESS);
    }

    @ValueSource(booleans = {true, false})
    @ParameterizedTest
    void scheduleDelete(final boolean useBlockTransformer) {
        // given
        final var scheduleCreateRecordItem =
                insertScheduleCreateTransaction(CREATE_TIMESTAMP, null, scheduleId, useBlockTransformer);

        // when
        final long deletedTimestamp = CREATE_TIMESTAMP + 10;
        final var scheduleDeleteRecordItem =
                insertScheduleDeleteTransaction(deletedTimestamp, scheduleId, useBlockTransformer);

        // then
        final var expected = createEntity(
                EntityId.of(scheduleId),
                SCHEDULE,
                SCHEDULE_REF_KEY,
                null,
                null,
                true,
                null,
                SCHEDULE_CREATE_MEMO,
                CREATE_TIMESTAMP,
                deletedTimestamp);
        assertEquals(1, entityRepository.count());
        assertEntity(expected);

        // verify schedule
        assertThat(scheduleRepository.count()).isOne();
        assertScheduleInRepository(scheduleId, CREATE_TIMESTAMP, PAYER, null);

        // verify transaction
        assertTransactionInRepository(
                scheduleCreateRecordItem.getCongestionPricingMultiplier(), CREATE_TIMESTAMP, false, SUCCESS);
        assertTransactionInRepository(
                scheduleDeleteRecordItem.getCongestionPricingMultiplier(), deletedTimestamp, false, SUCCESS);
    }

    @ValueSource(booleans = {true, false})
    @ParameterizedTest
    void scheduleSign(final boolean useBlockTransformer) {
        final var scheduleCreateRecordItem =
                insertScheduleCreateTransaction(CREATE_TIMESTAMP, null, scheduleId, useBlockTransformer);

        // sign
        final var signatureMap = getSigMap(3, true);
        final var scheduleSignRecorditem =
                insertScheduleSign(SIGN_TIMESTAMP, signatureMap, scheduleId, useBlockTransformer);

        // verify entity count
        assertEquals(1, entityRepository.count());

        // verify schedule
        assertThat(scheduleRepository.count()).isOne();
        assertScheduleInRepository(scheduleId, CREATE_TIMESTAMP, PAYER, null);

        // verify schedule signatures
        List<TransactionSignature> expectedTransactionSignatureList = new ArrayList<>(defaultSignatureList);
        expectedTransactionSignatureList.addAll(toTransactionSignatureList(SIGN_TIMESTAMP, scheduleId, signatureMap));
        assertTransactionSignatureInRepository(expectedTransactionSignatureList);

        // verify transaction
        assertTransactionInRepository(
                scheduleCreateRecordItem.getCongestionPricingMultiplier(), CREATE_TIMESTAMP, false, SUCCESS);
        assertTransactionInRepository(
                scheduleSignRecorditem.getCongestionPricingMultiplier(), SIGN_TIMESTAMP, false, SUCCESS);
    }

    @ValueSource(booleans = {true, false})
    @ParameterizedTest
    void scheduleSignTwoBatches(final boolean useBlockTransformer) {
        insertScheduleCreateTransaction(CREATE_TIMESTAMP, null, scheduleId, useBlockTransformer);

        // first sign
        final var firstSignatureMap = getSigMap(2, true);
        final var scheduleSignRecordItem1 =
                insertScheduleSign(SIGN_TIMESTAMP, firstSignatureMap, scheduleId, useBlockTransformer);

        // verify schedule signatures
        final var expectedTransactionSignatureList = new ArrayList<>(defaultSignatureList);
        expectedTransactionSignatureList.addAll(
                toTransactionSignatureList(SIGN_TIMESTAMP, scheduleId, firstSignatureMap));
        assertTransactionSignatureInRepository(expectedTransactionSignatureList);

        // second sign
        final long timestamp = SIGN_TIMESTAMP + 10;
        final var secondSignatureMap = getSigMap(3, true);
        final var scheduleSignRecordItem2 =
                insertScheduleSign(timestamp, secondSignatureMap, scheduleId, useBlockTransformer);

        expectedTransactionSignatureList.addAll(toTransactionSignatureList(timestamp, scheduleId, secondSignatureMap));
        assertTransactionSignatureInRepository(expectedTransactionSignatureList);

        // verify entity count
        final var expected = createEntity(
                EntityId.of(scheduleId),
                SCHEDULE,
                SCHEDULE_REF_KEY,
                null,
                null,
                false,
                null,
                SCHEDULE_CREATE_MEMO,
                CREATE_TIMESTAMP,
                CREATE_TIMESTAMP);
        assertEquals(1, entityRepository.count());
        assertEntity(expected);

        // verify schedule
        assertThat(scheduleRepository.count()).isOne();
        assertScheduleInRepository(scheduleId, CREATE_TIMESTAMP, PAYER, null);

        // verify transaction
        assertTransactionInRepository(
                scheduleSignRecordItem1.getCongestionPricingMultiplier(), SIGN_TIMESTAMP, false, SUCCESS);
        assertTransactionInRepository(
                scheduleSignRecordItem2.getCongestionPricingMultiplier(), timestamp, false, SUCCESS);
    }

    @ValueSource(booleans = {true, false})
    @ParameterizedTest
    void scheduleSignDuplicateEd25519Signatures(final boolean useBlockTransformer) {
        final var signatureMap = getSigMap(3, true);
        final var first = signatureMap.getSigPair(0);
        final var third = signatureMap.getSigPair(2);
        final var signatureMapWithDuplicate =
                signatureMap.toBuilder().addSigPair(first).addSigPair(third).build();

        insertScheduleSign(SIGN_TIMESTAMP, signatureMapWithDuplicate, scheduleId, useBlockTransformer);

        // verify lack of schedule data and transaction
        assertTransactionSignatureInRepository(toTransactionSignatureList(SIGN_TIMESTAMP, scheduleId, signatureMap));
        assertThat(transactionRepository.count()).isEqualTo(1);
    }

    @SuppressWarnings("deprecation")
    @ValueSource(booleans = {true, false})
    @ParameterizedTest
    void unknownSignatureType(final boolean useBlockTransformer) {
        int unknownType = 999;
        final var sig = ByteString.copyFromUtf8("123");
        final var unknownField =
                UnknownFieldSet.Field.newBuilder().addLengthDelimited(sig).build();

        final var signatureMap = SignatureMap.newBuilder()
                .addSigPair(SignaturePair.newBuilder()
                        .setPubKeyPrefix(byteString(8))
                        .setContract(sig)
                        .build())
                .addSigPair(SignaturePair.newBuilder()
                        .setPubKeyPrefix(byteString(8))
                        .setECDSA384(sig)
                        .build())
                .addSigPair(SignaturePair.newBuilder()
                        .setPubKeyPrefix(byteString(8))
                        .setECDSASecp256K1(sig)
                        .build())
                .addSigPair(SignaturePair.newBuilder()
                        .setPubKeyPrefix(byteString(8))
                        .setEd25519(sig)
                        .build())
                .addSigPair(SignaturePair.newBuilder()
                        .setPubKeyPrefix(byteString(8))
                        .setRSA3072(sig)
                        .build())
                .addSigPair(SignaturePair.newBuilder()
                        .setPubKeyPrefix(byteString(8))
                        .setUnknownFields(UnknownFieldSet.newBuilder()
                                .addField(unknownType, unknownField)
                                .build())
                        .build());

        insertScheduleSign(SIGN_TIMESTAMP, signatureMap.build(), scheduleId, useBlockTransformer);

        // verify
        assertThat(transactionRepository.count()).isEqualTo(1);
        assertThat(transactionSignatureRepository.findAll())
                .isNotNull()
                .hasSize(signatureMap.getSigPairCount())
                .allSatisfy(t -> assertThat(t)
                        .returns(sig.toByteArray(), TransactionSignature::getSignature)
                        .extracting(TransactionSignature::getPublicKeyPrefix)
                        .isNotNull())
                .extracting(TransactionSignature::getType)
                .containsExactlyInAnyOrder(
                        SignaturePair.SignatureCase.CONTRACT.getNumber(),
                        SignaturePair.SignatureCase.ECDSA_384.getNumber(),
                        SignaturePair.SignatureCase.ECDSA_SECP256K1.getNumber(),
                        SignaturePair.SignatureCase.ED25519.getNumber(),
                        SignaturePair.SignatureCase.RSA_3072.getNumber(),
                        unknownType);
    }

    @ValueSource(booleans = {true, false})
    @ParameterizedTest
    void unsupportedSignature(final boolean useBlockTransformer) {
        final var signatureMap = SignatureMap.newBuilder()
                .addSigPair(SignaturePair.newBuilder().build())
                .build();
        final var recordItem = insertScheduleSign(SIGN_TIMESTAMP, signatureMap, scheduleId, useBlockTransformer);

        // verify
        assertThat(transactionRepository.count()).isOne();
        assertThat(transactionSignatureRepository.count()).isZero();
        assertThat(scheduleRepository.count()).isZero();
        assertTransactionInRepository(recordItem.getCongestionPricingMultiplier(), SIGN_TIMESTAMP, false, SUCCESS);
    }

    @ValueSource(booleans = {true, false})
    @ParameterizedTest
    void scheduleExecuteOnSuccess(final boolean useBlockTransformer) {
        scheduleExecute(SUCCESS, useBlockTransformer);
    }

    @ValueSource(booleans = {true, false})
    @ParameterizedTest
    void scheduleExecuteOnFailure(final boolean useBlockTransformer) {
        scheduleExecute(ResponseCodeEnum.INVALID_CHUNK_TRANSACTION_ID, useBlockTransformer);
    }

    private ByteString byteString(int length) {
        return ByteString.copyFrom(domainBuilder.bytes(length));
    }

    void scheduleExecute(final ResponseCodeEnum responseCodeEnum, final boolean useBlockTransformer) {
        insertScheduleCreateTransaction(CREATE_TIMESTAMP, null, scheduleId, useBlockTransformer);

        // sign
        final var signatureMap = getSigMap(3, true);
        insertScheduleSign(SIGN_TIMESTAMP, signatureMap, scheduleId, useBlockTransformer);

        // scheduled transaction
        final var recordItem = insertScheduledTransaction(EXECUTE_TIMESTAMP, scheduleId, responseCodeEnum);

        // verify entity count
        final var expected = createEntity(
                EntityId.of(scheduleId),
                SCHEDULE,
                SCHEDULE_REF_KEY,
                null,
                null,
                false,
                null,
                SCHEDULE_CREATE_MEMO,
                CREATE_TIMESTAMP,
                CREATE_TIMESTAMP);
        assertEquals(1, entityRepository.count());
        assertEntity(expected);

        // verify schedule
        assertScheduleInRepository(scheduleId, CREATE_TIMESTAMP, PAYER, EXECUTE_TIMESTAMP);

        // verify schedule signatures
        List<TransactionSignature> expectedTransactionList = new ArrayList<>(defaultSignatureList);
        expectedTransactionList.addAll(toTransactionSignatureList(SIGN_TIMESTAMP, scheduleId, signatureMap));
        assertTransactionSignatureInRepository(expectedTransactionList);

        // verify transaction
        assertTransactionInRepository(
                recordItem.getCongestionPricingMultiplier(), EXECUTE_TIMESTAMP, true, responseCodeEnum);
    }

    private Transaction scheduleCreateTransaction(AccountID payer) {
        return buildTransaction(builder -> {
            ScheduleCreateTransactionBody.Builder scheduleCreateBuilder = builder.getScheduleCreateBuilder();
            scheduleCreateBuilder
                    .setAdminKey(SCHEDULE_REF_KEY)
                    .setMemo(SCHEDULE_CREATE_MEMO)
                    .setScheduledTransactionBody(SCHEDULED_TRANSACTION_BODY);
            if (payer != null) {
                scheduleCreateBuilder.setPayerAccountID(payer);
            } else {
                scheduleCreateBuilder.clearPayerAccountID();
            }
        });
    }

    private Transaction scheduleDeleteTransaction(ScheduleID scheduleId) {
        return buildTransaction(builder -> builder.setScheduleDelete(
                ScheduleDeleteTransactionBody.newBuilder().setScheduleID(scheduleId)));
    }

    private Transaction scheduleSignTransaction(ScheduleID scheduleID, SignatureMap signatureMap) {
        return buildTransaction(builder -> builder.getScheduleSignBuilder().setScheduleID(scheduleID), signatureMap);
    }

    private Transaction scheduledTransaction() {
        return buildTransaction(builder -> builder.getCryptoTransferBuilder()
                .getTransfersBuilder()
                .addAccountAmounts(accountAmount(PAYER.getAccountNum(), 1000))
                .addAccountAmounts(accountAmount(NODE.getAccountNum(), 2000)));
    }

    private RecordItem getRecordItem(
            final Function<RecordItem, BlockTransactionBuilder.Builder> buildBlockTransaction,
            final Transaction transaction,
            final TransactionRecord transactionRecord,
            final boolean useBlockTransformer) {
        final var builder = RecordItem.builder().transaction(transaction).transactionRecord(transactionRecord);
        if (useBlockTransformer) {
            final var recordItem = builder.congestionPricingMultiplier(
                            recordItemBuilder.accountId().getAccountNum())
                    .build();
            final var blockTransaction = buildBlockTransaction.apply(recordItem).build();
            return transformBlockItemToRecordItem(blockTransaction);
        } else {
            return builder.build();
        }
    }

    @SuppressWarnings("deprecation")
    private SignatureMap getSigMap(int signatureCount, boolean isEd25519) {
        SignatureMap.Builder builder = SignatureMap.newBuilder();
        String salt = RandomStringUtils.secure().nextAlphabetic(5);

        for (int i = 0; i < signatureCount; i++) {
            SignaturePair.Builder signaturePairBuilder = SignaturePair.newBuilder();
            signaturePairBuilder.setPubKeyPrefix(ByteString.copyFromUtf8("PubKeyPrefix-" + i + salt));

            ByteString byteString = ByteString.copyFromUtf8("Ed25519-" + i + salt);
            if (isEd25519) {
                signaturePairBuilder.setEd25519(byteString);
            } else {
                signaturePairBuilder.setRSA3072(byteString);
            }

            builder.addSigPair(signaturePairBuilder.build());
        }

        return builder.build();
    }

    private TransactionRecord createTransactionRecord(
            long consensusTimestamp,
            ScheduleID scheduleID,
            TransactionBody transactionBody,
            ResponseCodeEnum responseCode,
            boolean scheduledTransaction) {
        var receipt = TransactionReceipt.newBuilder().setStatus(responseCode).setScheduleID(scheduleID);

        return buildTransactionRecord(
                recordBuilder -> {
                    recordBuilder.setReceipt(receipt).setConsensusTimestamp(TestUtils.toTimestamp(consensusTimestamp));

                    if (scheduledTransaction) {
                        recordBuilder.setScheduleRef(scheduleID);
                    }

                    recordBuilder.getReceiptBuilder().setAccountID(PAYER);
                },
                transactionBody,
                responseCode.getNumber());
    }

    private RecordItem insertScheduleCreateTransaction(
            final long createdTimestamp,
            final AccountID payer,
            final ScheduleID scheduleId,
            final boolean useBlockTransformer) {
        final var createTransaction = scheduleCreateTransaction(payer);
        final var createTransactionBody = getTransactionBody(createTransaction);
        var createTransactionRecord =
                createTransactionRecord(createdTimestamp, scheduleId, createTransactionBody, SUCCESS, false);
        final var recordItem = getRecordItem(
                blockTransactionBuilder::scheduleCreate,
                createTransaction,
                createTransactionRecord,
                useBlockTransformer);

        parseRecordItemAndCommit(recordItem);
        return recordItem;
    }

    private RecordItem insertScheduleDeleteTransaction(
            final long timestamp, final ScheduleID scheduleId, final boolean useBlockTransformer) {
        final var transaction = scheduleDeleteTransaction(scheduleId);
        final var transactionBody = getTransactionBody(transaction);
        final var transactionRecord = createTransactionRecord(timestamp, scheduleId, transactionBody, SUCCESS, false);
        final var recordItem = getRecordItem(
                blockTransactionBuilder::scheduleDelete, transaction, transactionRecord, useBlockTransformer);

        parseRecordItemAndCommit(recordItem);
        return recordItem;
    }

    private RecordItem insertScheduleSign(
            final long signTimestamp,
            final SignatureMap signatureMap,
            final ScheduleID scheduleId,
            final boolean useBlockTransformer) {
        final var signTransaction = scheduleSignTransaction(scheduleId, signatureMap);
        final var signTransactionBody = getTransactionBody(signTransaction);
        final var signTransactionRecord =
                createTransactionRecord(signTimestamp, scheduleId, signTransactionBody, SUCCESS, false);
        final var recordItem = getRecordItem(
                blockTransactionBuilder::scheduleSign, signTransaction, signTransactionRecord, useBlockTransformer);

        parseRecordItemAndCommit(recordItem);
        return recordItem;
    }

    private RecordItem insertScheduledTransaction(
            final long signTimestamp, final ScheduleID scheduleId, final ResponseCodeEnum responseCodeEnum) {
        final var transaction = scheduledTransaction();
        final var transactionBody = getTransactionBody(transaction);
        final var txnRecord =
                createTransactionRecord(signTimestamp, scheduleId, transactionBody, responseCodeEnum, true);
        var recordItem = RecordItem.builder()
                .transactionRecord(txnRecord)
                .transaction(transaction)
                .build();
        parseRecordItemAndCommit(recordItem);
        return recordItem;
    }

    private void assertScheduleInRepository(
            ScheduleID scheduleID, long createdTimestamp, AccountID payer, Long executedTimestamp) {
        Long scheduleEntityId = EntityId.of(scheduleID).getId();
        assertThat(scheduleRepository.findById(scheduleEntityId))
                .get()
                .returns(createdTimestamp, from(Schedule::getConsensusTimestamp))
                .returns(executedTimestamp, from(Schedule::getExecutedTimestamp))
                .returns(scheduleEntityId, from(Schedule::getScheduleId))
                .returns(EntityId.of(PAYER), from(Schedule::getCreatorAccountId))
                .returns(EntityId.of(payer), from(Schedule::getPayerAccountId))
                .returns(SCHEDULED_TRANSACTION_BODY.toByteArray(), from(Schedule::getTransactionBody));
    }

    private void assertTransactionSignatureInRepository(List<TransactionSignature> expected) {
        assertThat(transactionSignatureRepository.findAll()).isNotNull().hasSameElementsAs(expected);
    }

    private void assertTransactionInRepository(
            Long congestionPricingMultiplier,
            long consensusTimestamp,
            boolean scheduled,
            ResponseCodeEnum responseCode) {
        assertThat(transactionRepository.findById(consensusTimestamp))
                .get()
                .returns(congestionPricingMultiplier, t -> t.getCongestionPricingMultiplier())
                .returns(scheduled, from(org.hiero.mirror.common.domain.transaction.Transaction::isScheduled))
                .returns(
                        responseCode.getNumber(),
                        from(org.hiero.mirror.common.domain.transaction.Transaction::getResult));
    }

    public RecordItem transformBlockItemToRecordItem(BlockTransaction blockTransaction) {
        var blockFile = blockFileBuilder.items(List.of(blockTransaction)).build();
        var blockRecordFile = blockFileTransformer.transform(blockFile);
        return blockRecordFile.getItems().iterator().next();
    }

    private List<TransactionSignature> toTransactionSignatureList(
            long timestamp, ScheduleID scheduleId, SignatureMap signatureMap) {
        return signatureMap.getSigPairList().stream()
                .map(pair -> {
                    TransactionSignature transactionSignature = new TransactionSignature();
                    transactionSignature.setConsensusTimestamp(timestamp);
                    transactionSignature.setEntityId(EntityId.of(scheduleId));
                    transactionSignature.setPublicKeyPrefix(
                            pair.getPubKeyPrefix().toByteArray());
                    transactionSignature.setSignature(pair.getEd25519().toByteArray());
                    transactionSignature.setType(SignaturePair.SignatureCase.ED25519.getNumber());
                    return transactionSignature;
                })
                .toList();
    }
}
