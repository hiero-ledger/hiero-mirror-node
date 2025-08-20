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
import jakarta.annotation.Resource;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.apache.commons.lang3.RandomStringUtils;
import org.hiero.mirror.common.domain.entity.Entity;
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

class EntityRecordItemListenerScheduleTest extends AbstractEntityRecordItemListenerTest {

    private static final long CREATE_TIMESTAMP = 1L;
    private static final long EXECUTE_TIMESTAMP = 500L;
    private static final String SCHEDULE_CREATE_MEMO = "ScheduleCreate memo";
    private static final SchedulableTransactionBody SCHEDULED_TRANSACTION_BODY =
            SchedulableTransactionBody.getDefaultInstance();
    private static final ScheduleID SCHEDULE_ID = ScheduleID.newBuilder()
            .setShardNum(0)
            .setRealmNum(0)
            .setScheduleNum(2)
            .build();
    private static final Key SCHEDULE_REF_KEY = keyFromString(KEY);
    private static final long SIGN_TIMESTAMP = 10L;

    @Resource
    private BlockFileBuilder blockFileBuilder;

    @Resource
    private BlockFileTransformer blockFileTransformer;

    @Resource
    private BlockTransactionBuilder blockTransactionBuilder;

    @Resource
    protected ScheduleRepository scheduleRepository;

    @Resource
    protected TransactionSignatureRepository transactionSignatureRepository;

    @Resource
    protected TransactionRepository transactionRepository;

    private List<TransactionSignature> defaultSignatureList;

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
        defaultSignatureList = toTransactionSignatureList(CREATE_TIMESTAMP, SCHEDULE_ID, DEFAULT_SIG_MAP);
    }

    @ParameterizedTest(name = "{2}")
    @MethodSource("provideScheduleCreatePayer")
    void scheduleCreate(AccountID payer, AccountID expectedPayer, String name, boolean useBlockTransformer) {
        insertScheduleCreateTransaction(CREATE_TIMESTAMP, payer, SCHEDULE_ID, useBlockTransformer);

        // verify entity count
        Entity expectedEntity = createEntity(
                EntityId.of(SCHEDULE_ID),
                SCHEDULE,
                SCHEDULE_REF_KEY,
                null,
                null,
                false,
                null,
                SCHEDULE_CREATE_MEMO,
                CREATE_TIMESTAMP,
                CREATE_TIMESTAMP);

        Schedule expectedSchedule = Schedule.builder()
                .consensusTimestamp(CREATE_TIMESTAMP)
                .creatorAccountId(EntityId.of(PAYER))
                .payerAccountId(EntityId.of(expectedPayer))
                .scheduleId(EntityId.of(SCHEDULE_ID).getId())
                .transactionBody(SCHEDULED_TRANSACTION_BODY.toByteArray())
                .build();

        assertEquals(1, entityRepository.count());
        assertEntity(expectedEntity);

        // verify schedule and signatures
        assertThat(scheduleRepository.findAll()).containsOnly(expectedSchedule);

        assertTransactionSignatureInRepository(defaultSignatureList);

        // verify transaction
        assertTransactionInRepository(CREATE_TIMESTAMP, false, SUCCESS);
    }

    @Test
    void scheduleCreateLongTermScheduledTransaction() {
        var expirationTime = recordItemBuilder.timestamp();
        var pubKeyPrefix = recordItemBuilder.bytes(16);
        var signature = recordItemBuilder.bytes(32);
        var recordItem = recordItemBuilder
                .scheduleCreate()
                .receipt(r -> r.setScheduleID(SCHEDULE_ID))
                .signatureMap(s -> s.clear()
                        .addSigPair(SignaturePair.newBuilder()
                                .setPubKeyPrefix(pubKeyPrefix)
                                .setEd25519(signature)))
                .transactionBody(b -> b.setExpirationTime(expirationTime).setWaitForExpiry(true))
                .build();
        var scheduleCreate = recordItem.getTransactionBody().getScheduleCreate();
        var timestamp = recordItem.getConsensusTimestamp();
        var expectedEntity = createEntity(
                EntityId.of(SCHEDULE_ID),
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
                .scheduleId(SCHEDULE_ID.getScheduleNum())
                .transactionBody(scheduleCreate.getScheduledTransactionBody().toByteArray())
                .waitForExpiry(true)
                .build();
        var expectedTransactionSignature = TransactionSignature.builder()
                .consensusTimestamp(timestamp)
                .entityId(EntityId.of(SCHEDULE_ID))
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
        assertTransactionInRepository(timestamp, false, SUCCESS);
    }

    @ValueSource(booleans = {true, false})
    @ParameterizedTest
    void scheduleDelete(boolean useBlockTransformer) {
        // given
        insertScheduleCreateTransaction(CREATE_TIMESTAMP, null, SCHEDULE_ID, useBlockTransformer);

        // when
        long deletedTimestamp = CREATE_TIMESTAMP + 10;
        insertScheduleDeleteTransaction(deletedTimestamp, SCHEDULE_ID, useBlockTransformer);

        // then
        Entity expected = createEntity(
                EntityId.of(SCHEDULE_ID),
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
        assertScheduleInRepository(SCHEDULE_ID, CREATE_TIMESTAMP, PAYER, null);

        // verify transaction
        assertTransactionInRepository(deletedTimestamp, false, SUCCESS);
    }

    @ValueSource(booleans = {true, false})
    @ParameterizedTest
    void scheduleSign(boolean useBlockTransformer) {
        insertScheduleCreateTransaction(CREATE_TIMESTAMP, null, SCHEDULE_ID, useBlockTransformer);

        // sign
        SignatureMap signatureMap = getSigMap(3, true);
        insertScheduleSign(SIGN_TIMESTAMP, signatureMap, SCHEDULE_ID, useBlockTransformer);

        // verify entity count
        assertEquals(1, entityRepository.count());

        // verify schedule
        assertThat(scheduleRepository.count()).isOne();
        assertScheduleInRepository(SCHEDULE_ID, CREATE_TIMESTAMP, PAYER, null);

        // verify schedule signatures
        List<TransactionSignature> expectedTransactionSignatureList = new ArrayList<>(defaultSignatureList);
        expectedTransactionSignatureList.addAll(toTransactionSignatureList(SIGN_TIMESTAMP, SCHEDULE_ID, signatureMap));
        assertTransactionSignatureInRepository(expectedTransactionSignatureList);

        // verify transaction
        assertTransactionInRepository(SIGN_TIMESTAMP, false, SUCCESS);
    }

    @ValueSource(booleans = {true, false})
    @ParameterizedTest
    void scheduleSignTwoBatches(boolean useBlockTransformer) {
        insertScheduleCreateTransaction(CREATE_TIMESTAMP, null, SCHEDULE_ID, useBlockTransformer);

        // first sign
        SignatureMap firstSignatureMap = getSigMap(2, true);
        insertScheduleSign(SIGN_TIMESTAMP, firstSignatureMap, SCHEDULE_ID, useBlockTransformer);

        // verify schedule signatures
        List<TransactionSignature> expectedTransactionSignatureList = new ArrayList<>(defaultSignatureList);
        expectedTransactionSignatureList.addAll(
                toTransactionSignatureList(SIGN_TIMESTAMP, SCHEDULE_ID, firstSignatureMap));
        assertTransactionSignatureInRepository(expectedTransactionSignatureList);

        // second sign
        long timestamp = SIGN_TIMESTAMP + 10;
        SignatureMap secondSignatureMap = getSigMap(3, true);
        insertScheduleSign(timestamp, secondSignatureMap, SCHEDULE_ID, useBlockTransformer);

        expectedTransactionSignatureList.addAll(toTransactionSignatureList(timestamp, SCHEDULE_ID, secondSignatureMap));
        assertTransactionSignatureInRepository(expectedTransactionSignatureList);

        // verify entity count
        Entity expected = createEntity(
                EntityId.of(SCHEDULE_ID),
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
        assertScheduleInRepository(SCHEDULE_ID, CREATE_TIMESTAMP, PAYER, null);

        // verify transaction
        assertTransactionInRepository(SIGN_TIMESTAMP, false, SUCCESS);
    }

    @ValueSource(booleans = {true, false})
    @ParameterizedTest
    void scheduleSignDuplicateEd25519Signatures(boolean useBlockTransformer) {
        SignatureMap signatureMap = getSigMap(3, true);
        SignaturePair first = signatureMap.getSigPair(0);
        SignaturePair third = signatureMap.getSigPair(2);
        SignatureMap signatureMapWithDuplicate =
                signatureMap.toBuilder().addSigPair(first).addSigPair(third).build();

        insertScheduleSign(SIGN_TIMESTAMP, signatureMapWithDuplicate, SCHEDULE_ID, useBlockTransformer);

        // verify lack of schedule data and transaction
        assertTransactionSignatureInRepository(toTransactionSignatureList(SIGN_TIMESTAMP, SCHEDULE_ID, signatureMap));
        assertThat(transactionRepository.count()).isEqualTo(1);
    }

    @SuppressWarnings("deprecation")
    @ValueSource(booleans = {true, false})
    @ParameterizedTest
    void unknownSignatureType(boolean useBlockTransformer) {
        int unknownType = 999;
        ByteString sig = ByteString.copyFromUtf8("123");
        UnknownFieldSet.Field unknownField =
                UnknownFieldSet.Field.newBuilder().addLengthDelimited(sig).build();

        SignatureMap.Builder signatureMap = SignatureMap.newBuilder()
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

        insertScheduleSign(SIGN_TIMESTAMP, signatureMap.build(), SCHEDULE_ID, useBlockTransformer);

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
    void unsupportedSignature(boolean useBlockTransformer) {
        SignatureMap signatureMap = SignatureMap.newBuilder()
                .addSigPair(SignaturePair.newBuilder().build())
                .build();
        insertScheduleSign(SIGN_TIMESTAMP, signatureMap, SCHEDULE_ID, useBlockTransformer);

        // verify
        assertThat(transactionRepository.count()).isOne();
        assertThat(transactionSignatureRepository.count()).isZero();
        assertThat(scheduleRepository.count()).isZero();
        assertTransactionInRepository(SIGN_TIMESTAMP, false, SUCCESS);
    }

    @ValueSource(booleans = {true, false})
    @ParameterizedTest
    void scheduleExecuteOnSuccess(boolean useBlockTransformer) {
        scheduleExecute(SUCCESS, useBlockTransformer);
    }

    @ValueSource(booleans = {true, false})
    @ParameterizedTest
    void scheduleExecuteOnFailure(boolean useBlockTransformer) {
        scheduleExecute(ResponseCodeEnum.INVALID_CHUNK_TRANSACTION_ID, useBlockTransformer);
    }

    private ByteString byteString(int length) {
        return ByteString.copyFrom(domainBuilder.bytes(length));
    }

    void scheduleExecute(ResponseCodeEnum responseCodeEnum, boolean useBlockTransformer) {
        insertScheduleCreateTransaction(CREATE_TIMESTAMP, null, SCHEDULE_ID, useBlockTransformer);

        // sign
        SignatureMap signatureMap = getSigMap(3, true);
        insertScheduleSign(SIGN_TIMESTAMP, signatureMap, SCHEDULE_ID, useBlockTransformer);

        // scheduled transaction
        insertScheduledTransaction(EXECUTE_TIMESTAMP, SCHEDULE_ID, responseCodeEnum);

        // verify entity count
        Entity expected = createEntity(
                EntityId.of(SCHEDULE_ID),
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
        assertScheduleInRepository(SCHEDULE_ID, CREATE_TIMESTAMP, PAYER, EXECUTE_TIMESTAMP);

        // verify schedule signatures
        List<TransactionSignature> expectedTransactionList = new ArrayList<>(defaultSignatureList);
        expectedTransactionList.addAll(toTransactionSignatureList(SIGN_TIMESTAMP, SCHEDULE_ID, signatureMap));
        assertTransactionSignatureInRepository(expectedTransactionList);

        // verify transaction
        assertTransactionInRepository(EXECUTE_TIMESTAMP, true, responseCodeEnum);
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

    private void insertScheduleCreateTransaction(
            long createdTimestamp, AccountID payer, ScheduleID scheduleID, boolean useBlockTransformer) {
        Transaction createTransaction = scheduleCreateTransaction(payer);
        TransactionBody createTransactionBody = getTransactionBody(createTransaction);
        var createTransactionRecord =
                createTransactionRecord(createdTimestamp, scheduleID, createTransactionBody, SUCCESS, false);

        var recordItem = RecordItem.builder()
                .transactionRecord(createTransactionRecord)
                .transaction(createTransaction)
                .build();

        if (useBlockTransformer) {
            var blockItem = blockTransactionBuilder.scheduleCreate(recordItem).build();
            recordItem = transformBlockItemToRecordItem(blockItem);
        }

        parseRecordItemAndCommit(recordItem);
    }

    private void insertScheduleDeleteTransaction(long timestamp, ScheduleID scheduleId, boolean useBlockTransformer) {
        var transaction = scheduleDeleteTransaction(scheduleId);
        var transactionBody = getTransactionBody(transaction);
        var transactionRecord = createTransactionRecord(timestamp, scheduleId, transactionBody, SUCCESS, false);

        var recordItem = RecordItem.builder()
                .transactionRecord(transactionRecord)
                .transaction(transaction)
                .build();

        if (useBlockTransformer) {
            var blockItem = blockTransactionBuilder.scheduleDelete(recordItem).build();
            recordItem = transformBlockItemToRecordItem(blockItem);
        }

        parseRecordItemAndCommit(recordItem);
    }

    private void insertScheduleSign(
            long signTimestamp, SignatureMap signatureMap, ScheduleID scheduleID, boolean useBlockTransformer) {
        Transaction signTransaction = scheduleSignTransaction(scheduleID, signatureMap);
        TransactionBody signTransactionBody = getTransactionBody(signTransaction);
        var signTransactionRecord =
                createTransactionRecord(signTimestamp, scheduleID, signTransactionBody, SUCCESS, false);

        var recordItem = RecordItem.builder()
                .transaction(signTransaction)
                .transactionRecord(signTransactionRecord)
                .build();

        if (useBlockTransformer) {
            var blockItem = blockTransactionBuilder.scheduleSign(recordItem).build();
            recordItem = transformBlockItemToRecordItem(blockItem);
        }

        parseRecordItemAndCommit(recordItem);
    }

    private void insertScheduledTransaction(
            long signTimestamp, ScheduleID scheduleID, ResponseCodeEnum responseCodeEnum) {
        var transaction = scheduledTransaction();
        var transactionBody = getTransactionBody(transaction);
        var txnRecord = createTransactionRecord(signTimestamp, scheduleID, transactionBody, responseCodeEnum, true);
        var recordItem = RecordItem.builder()
                .transactionRecord(txnRecord)
                .transaction(transaction)
                .build();
        parseRecordItemAndCommit(recordItem);
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
            long consensusTimestamp, boolean scheduled, ResponseCodeEnum responseCode) {
        assertThat(transactionRepository.findById(consensusTimestamp))
                .get()
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
