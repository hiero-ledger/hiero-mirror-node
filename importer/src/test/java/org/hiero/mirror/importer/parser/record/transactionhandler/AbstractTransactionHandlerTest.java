// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.parser.record.transactionhandler;

import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INSUFFICIENT_PAYER_BALANCE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hiero.mirror.importer.TestUtils.toEntityTransactions;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mock.Strictness.LENIENT;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.google.common.collect.Lists;
import com.google.protobuf.BoolValue;
import com.google.protobuf.ByteString;
import com.google.protobuf.Descriptors.FieldDescriptor;
import com.google.protobuf.Int32Value;
import com.google.protobuf.Message;
import com.google.protobuf.StringValue;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ContractID;
import com.hederahashgraph.api.proto.java.Duration;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.KeyList;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.SignatureMap;
import com.hederahashgraph.api.proto.java.SignedTransaction;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionBody.DataCase;
import com.hederahashgraph.api.proto.java.TransactionReceipt;
import com.hederahashgraph.api.proto.java.TransactionRecord;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.stream.Stream;
import lombok.Builder;
import lombok.Value;
import org.hiero.mirror.common.CommonProperties;
import org.hiero.mirror.common.domain.DomainBuilder;
import org.hiero.mirror.common.domain.SystemEntity;
import org.hiero.mirror.common.domain.entity.AbstractEntity;
import org.hiero.mirror.common.domain.entity.Entity;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.hiero.mirror.common.domain.entity.EntityOperation;
import org.hiero.mirror.common.domain.entity.EntityTransaction;
import org.hiero.mirror.common.domain.entity.EntityType;
import org.hiero.mirror.common.domain.transaction.RecordItem;
import org.hiero.mirror.common.util.DomainUtils;
import org.hiero.mirror.importer.domain.EntityIdService;
import org.hiero.mirror.importer.parser.contractlog.SyntheticContractLogService;
import org.hiero.mirror.importer.parser.domain.RecordItemBuilder;
import org.hiero.mirror.importer.parser.record.entity.EntityListener;
import org.hiero.mirror.importer.parser.record.entity.EntityProperties;
import org.hiero.mirror.importer.repository.EntityRepository;
import org.hiero.mirror.importer.util.Utility;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.util.Version;

@ExtendWith(MockitoExtension.class)
abstract class AbstractTransactionHandlerTest {

    protected static final Duration DEFAULT_AUTO_RENEW_PERIOD =
            Duration.newBuilder().setSeconds(1).build();
    protected static final Long DEFAULT_ENTITY_NUM = 100L;
    protected static final Timestamp DEFAULT_EXPIRATION_TIME = Utility.instantToTimestamp(Instant.now());
    protected static final Key DEFAULT_KEY = getKey("4a5ad514f0957fa170a676210c9bdbddf3bc9519702cf915fa6767a40463b96f");
    protected static final String DEFAULT_MEMO = "default entity memo";
    protected static final boolean DEFAULT_RECEIVER_SIG_REQUIRED = false;
    protected static final Key DEFAULT_SUBMIT_KEY =
            getKey("5a5ad514f0957fa170a676210c9bdbddf3bc9519702cf915fa6767a40463b96G");
    protected static final KeyList DEFAULT_KEY_LIST = KeyList.newBuilder()
            .addAllKeys(Arrays.asList(DEFAULT_KEY, DEFAULT_SUBMIT_KEY))
            .build();
    protected static final String UPDATED_MEMO = "update memo";
    protected static final BoolValue UPDATED_RECEIVER_SIG_REQUIRED = BoolValue.of(true);
    protected static final Timestamp MODIFIED_TIMESTAMP =
            Timestamp.newBuilder().setSeconds(200).setNanos(2).build();
    private static final Timestamp CREATED_TIMESTAMP =
            Timestamp.newBuilder().setSeconds(100).setNanos(1).build();
    private static final Long CREATED_TIMESTAMP_NS = DomainUtils.timestampInNanosMax(CREATED_TIMESTAMP);
    private static final Long MODIFIED_TIMESTAMP_NS = DomainUtils.timestampInNanosMax(MODIFIED_TIMESTAMP);

    protected final DomainBuilder domainBuilder = new DomainBuilder();
    protected final RecordItemBuilder recordItemBuilder = new RecordItemBuilder();
    protected final Logger log = LoggerFactory.getLogger(getClass());

    protected final EntityId defaultEntityId = domainBuilder.entityNum(DEFAULT_ENTITY_NUM);
    protected final ContractID contractId = defaultEntityId.toContractID();
    protected final SystemEntity systemEntity = new SystemEntity(CommonProperties.getInstance());
    protected final EntityProperties entityProperties = new EntityProperties(systemEntity);

    protected TransactionHandler transactionHandler;

    @Mock(strictness = LENIENT)
    protected EntityIdService entityIdService;

    @Mock
    protected EntityListener entityListener;

    @Mock
    protected EntityRepository entityRepository;

    @Mock
    protected SyntheticContractLogService syntheticContractLogService;

    @Captor
    protected ArgumentCaptor<Entity> entityCaptor;

    protected static Key getKey(String keyString) {
        return Key.newBuilder().setEd25519(ByteString.copyFromUtf8(keyString)).build();
    }

    protected static Stream<EntityId> provideEntities() {
        return Stream.of(null, EntityId.EMPTY);
    }

    protected final <T> T assertArg(Consumer<T> asserter) {
        return argThat(t -> {
            asserter.accept(t);
            return true;
        });
    }

    protected abstract TransactionHandler getTransactionHandler();

    // All sub-classes need to implement this function and return a TransactionBody.Builder with valid 'oneof data' set.
    protected abstract TransactionBody.Builder getDefaultTransactionBody();

    protected SignatureMap.Builder getDefaultSigMap() {
        return SignatureMap.newBuilder();
    }

    protected TransactionReceipt.Builder getTransactionReceipt(ResponseCodeEnum responseCodeEnum) {
        return TransactionReceipt.newBuilder().setStatus(responseCodeEnum);
    }

    protected TransactionRecord.Builder getDefaultTransactionRecord() {
        TransactionRecord.Builder builder = TransactionRecord.newBuilder();
        if (isCrudTransactionHandler(transactionHandler)) {
            Timestamp consensusTimestamp = transactionHandler.getType().getEntityOperation() == EntityOperation.CREATE
                    ? CREATED_TIMESTAMP
                    : MODIFIED_TIMESTAMP;
            builder.setConsensusTimestamp(consensusTimestamp);
        }

        return builder.setReceipt(getTransactionReceipt(ResponseCodeEnum.SUCCESS));
    }

    // For testGetEntityId
    protected abstract EntityType getExpectedEntityIdType();

    protected Map<Long, EntityTransaction> getExpectedEntityTransactions(
            RecordItem recordItem,
            org.hiero.mirror.common.domain.transaction.Transaction transaction,
            EntityId... entityIds) {
        var entityIdList = Lists.newArrayList(entityIds);
        entityIdList.add(transaction.getEntityId());
        entityIdList.add(transaction.getNodeAccountId());
        entityIdList.add(transaction.getPayerAccountId());
        return toEntityTransactions(recordItem, entityIdList.toArray(EntityId[]::new));
    }

    protected List<UpdateEntityTestSpec> getUpdateEntityTestSpecs() {
        return null;
    }

    protected boolean isSkipMainEntityTransaction() {
        return false;
    }

    @BeforeEach
    void beforeEach(TestInfo testInfo) {
        log.info("Executing: {}", testInfo.getDisplayName());
        entityProperties.getPersist().setEntityTransactions(true);
        recordItemBuilder.getPersistProperties().setEntityTransactions(true);
        transactionHandler = getTransactionHandler();
        when(entityIdService.lookup(AccountID.getDefaultInstance())).thenReturn(Optional.of(EntityId.EMPTY));
        when(entityIdService.lookup(AccountID.newBuilder().setAccountNum(0).build()))
                .thenReturn(Optional.of(EntityId.EMPTY));
    }

    @AfterEach
    void afterEach() {
        entityProperties.getPersist().setEntityTransactions(false);
        recordItemBuilder.getPersistProperties().setEntityTransactions(false);
    }

    @Test
    void testGetEntityId() {
        final var entityType = getExpectedEntityIdType();
        EntityId expectedEntityId = entityType != null ? defaultEntityId : null;
        testGetEntityIdHelper(
                getDefaultTransactionBody().build(),
                getDefaultTransactionRecord().build(),
                expectedEntityId);
    }

    @TestFactory
    Stream<DynamicTest> testUpdateEntity() {
        if (!isCrudTransactionHandler(transactionHandler)) {
            // empty test if the handler does not update entity
            return Stream.empty();
        }

        FieldDescriptor memoField = getInnerBodyFieldDescriptorByName("memo");
        FieldDescriptor memoWrapperField = getInnerBodyFieldDescriptorByName("memoWrapper");
        FieldDescriptor maxAutomaticTokenAssociationsField =
                getInnerBodyFieldDescriptorByName("max_automatic_token_associations");
        FieldDescriptor receiverSigRequiredField = getInnerBodyFieldDescriptorByName("receiverSigRequired");
        FieldDescriptor receiverSigRequiredWrapperField =
                getInnerBodyFieldDescriptorByName("receiverSigRequiredWrapper");
        List<UpdateEntityTestSpec> testSpecs;

        if (memoField != null) {
            // it's either an entity create transaction or entity update transaction when memo field is present
            boolean isMemoString = memoField.getType() == FieldDescriptor.Type.STRING;
            boolean isReceiverSigRequiredBool =
                    receiverSigRequiredField != null && receiverSigRequiredField.getType() == FieldDescriptor.Type.BOOL;

            if ((isMemoString && memoWrapperField == null)
                    || (isReceiverSigRequiredBool && receiverSigRequiredWrapperField == null)) {
                testSpecs = getUpdateEntityTestSpecsForCreateTransaction(memoField);
            } else {
                testSpecs = getUpdateEntityTestSpecsForUpdateTransaction(
                        memoField,
                        memoWrapperField,
                        maxAutomaticTokenAssociationsField,
                        receiverSigRequiredWrapperField);
            }
        } else {
            // no memo field, either delete or undelete transaction, leave it to the test class
            testSpecs = getUpdateEntityTestSpecs();
        }

        return DynamicTest.stream(testSpecs.iterator(), UpdateEntityTestSpec::getDescription, testSpec -> {
            // when
            var transaction = new org.hiero.mirror.common.domain.transaction.Transaction();
            transaction.setEntityId(testSpec.getExpected().toEntityId());
            transaction.setConsensusTimestamp(CREATED_TIMESTAMP_NS);
            Mockito.reset(entityListener);
            transactionHandler.updateTransaction(transaction, testSpec.getRecordItem());
            verify(entityListener).onEntity(entityCaptor.capture());

            // then
            assertThat(entityCaptor.getValue()).isEqualTo(testSpec.getExpected());
        });
    }

    @Test
    void updateTransactionUnsuccessful() {
        // Given
        var transactionRecord = getDefaultTransactionRecord();
        transactionRecord.getReceiptBuilder().setStatus(INSUFFICIENT_PAYER_BALANCE);
        var recordItem = getRecordItem(getDefaultTransactionBody().build(), transactionRecord.build());
        var transaction = domainBuilder.transaction().get();
        var expectedEntityTransactions = toEntityTransactions(
                recordItem,
                isSkipMainEntityTransaction() ? EntityId.EMPTY : transaction.getEntityId(),
                transaction.getNodeAccountId(),
                transaction.getPayerAccountId());

        // When
        transactionHandler.updateTransaction(transaction, recordItem);

        // Then
        verifyNoInteractions(entityListener);
        assertThat(recordItem.getEntityTransactions()).containsExactlyInAnyOrderEntriesOf(expectedEntityTransactions);
    }

    protected void testGetEntityIdHelper(
            TransactionBody transactionBody, TransactionRecord transactionRecord, EntityId expectedEntity) {
        RecordItem recordItem = RecordItem.builder()
                .transaction(Transaction.newBuilder()
                        .setSignedTransactionBytes(SignedTransaction.newBuilder()
                                .setBodyBytes(transactionBody.toByteString())
                                .setSigMap(getDefaultSigMap())
                                .build()
                                .toByteString())
                        .build())
                .transactionRecord(transactionRecord)
                .build();
        assertThat(transactionHandler.getEntity(recordItem)).isEqualTo(expectedEntity);
    }

    protected Entity getEntity() {
        var entityId = domainBuilder.entityNum(DEFAULT_ENTITY_NUM);
        var entity = entityId.toEntity();
        entity.setType(getExpectedEntityIdType());
        return entity;
    }

    protected Entity getExpectedEntityWithTimestamp() {
        Entity entity = getEntity();
        EntityOperation entityOperation = transactionHandler.getType().getEntityOperation();

        if (entityOperation == EntityOperation.CREATE) {
            entity.setCreatedTimestamp(CREATED_TIMESTAMP_NS);
            entity.setDeleted(false);
            entity.setTimestampLower(CREATED_TIMESTAMP_NS);
        } else if (entityOperation == EntityOperation.UPDATE) {
            entity.setDeleted(false);
            entity.setTimestampLower(MODIFIED_TIMESTAMP_NS);
        } else {
            entity.setTimestampLower(MODIFIED_TIMESTAMP_NS);
        }

        return entity;
    }

    protected List<UpdateEntityTestSpec> getUpdateEntityTestSpecsForCreateTransaction(FieldDescriptor memoField) {
        TransactionBody body = getTransactionBodyForUpdateEntityWithoutMemo();
        Message innerBody = getInnerBody(body);
        List<UpdateEntityTestSpec> testSpecs = new ArrayList<>();
        AbstractEntity expected = getExpectedUpdatedEntity();
        expected.setMemo(""); // Proto defaults to empty string

        // no memo set, expect empty memo
        testSpecs.add(UpdateEntityTestSpec.builder()
                .description("create entity without memo, expect empty memo")
                .expected(expected)
                .recordItem(getRecordItem(body, innerBody))
                .build());

        expected = getExpectedUpdatedEntity();
        expected.setMemo("");
        // memo set to empty string, expect empty memo
        Message updatedInnerBody = innerBody.toBuilder().setField(memoField, "").build();
        testSpecs.add(UpdateEntityTestSpec.builder()
                .description("create entity with empty memo, expect empty memo")
                .expected(expected)
                .recordItem(getRecordItem(body, updatedInnerBody))
                .build());

        // memo set to non-empty string, expect memo set
        expected = getExpectedUpdatedEntity();
        expected.setMemo(DEFAULT_MEMO);
        updatedInnerBody =
                innerBody.toBuilder().setField(memoField, DEFAULT_MEMO).build();
        testSpecs.add(UpdateEntityTestSpec.builder()
                .description("create entity with non-empty memo, expect memo set")
                .expected(expected)
                .recordItem(getRecordItem(body, updatedInnerBody))
                .build());

        return testSpecs;
    }

    private List<UpdateEntityTestSpec> getUpdateEntityTestSpecsForUpdateTransaction(
            FieldDescriptor memoField,
            FieldDescriptor memoWrapperField,
            FieldDescriptor maxAutomaticTokenAssociationsField,
            FieldDescriptor receiverSigRequiredWrapperField) {
        TransactionBody body = getTransactionBodyForUpdateEntityWithoutMemo();
        Message innerBody = getInnerBody(body);
        List<UpdateEntityTestSpec> testSpecs = new ArrayList<>();

        if (receiverSigRequiredWrapperField != null) {
            innerBody = innerBody.toBuilder()
                    .setField(receiverSigRequiredWrapperField, UPDATED_RECEIVER_SIG_REQUIRED)
                    .build();
        }

        AbstractEntity expected = getExpectedUpdatedEntity();

        Message unchangedMemoInnerBody = innerBody;
        if (receiverSigRequiredWrapperField != null) {
            expected.setReceiverSigRequired(UPDATED_RECEIVER_SIG_REQUIRED.getValue());
        }

        testSpecs.add(UpdateEntityTestSpec.builder()
                .description("update entity without memo")
                .expected(expected)
                .recordItem(getRecordItem(body, unchangedMemoInnerBody))
                .build());

        Message updatedMemoInnerBody = innerBody;
        if (memoWrapperField != null) {
            // memo is of string type
            // non-empty string, expect memo set to non-empty string
            expected = getExpectedUpdatedEntity();
            expected.setMemo(UPDATED_MEMO);
            updatedMemoInnerBody =
                    innerBody.toBuilder().setField(memoField, UPDATED_MEMO).build();
        }

        if (receiverSigRequiredWrapperField != null) {
            expected.setReceiverSigRequired(UPDATED_RECEIVER_SIG_REQUIRED.getValue());
        }

        testSpecs.add(UpdateEntityTestSpec.builder()
                .description("update entity with non-empty String")
                .expected(expected)
                .recordItem(getRecordItem(body, updatedMemoInnerBody))
                .build());

        // memo is set through the StringValue field
        // there is always a StringValue field, either "memo" or "memoWrapper"
        FieldDescriptor field = memoWrapperField;
        if (field == null) {
            field = memoField;
        }

        expected = getExpectedUpdatedEntity();
        expected.setMemo("");
        Message clearedMemoInnerBody =
                innerBody.toBuilder().setField(field, StringValue.of("")).build();

        if (receiverSigRequiredWrapperField != null) {
            expected.setReceiverSigRequired(UPDATED_RECEIVER_SIG_REQUIRED.getValue());
        }

        testSpecs.add(UpdateEntityTestSpec.builder()
                .description("update entity with empty StringValue memo, expect memo cleared")
                .expected(expected)
                .recordItem(getRecordItem(body, clearedMemoInnerBody))
                .build());

        // non-empty StringValue, expect memo in entity updated
        expected = getExpectedUpdatedEntity();
        expected.setMemo(UPDATED_MEMO);
        Message memoStringValueUpdatedInnerBody = innerBody.toBuilder()
                .setField(field, StringValue.of(UPDATED_MEMO))
                .build();

        if (receiverSigRequiredWrapperField != null) {
            expected.setReceiverSigRequired(UPDATED_RECEIVER_SIG_REQUIRED.getValue());
        }

        testSpecs.add(UpdateEntityTestSpec.builder()
                .description("update entity with non-empty StringValue memo, expect memo updated")
                .expected(expected)
                .recordItem(getRecordItem(body, memoStringValueUpdatedInnerBody))
                .build());

        if (maxAutomaticTokenAssociationsField != null) {
            expected = getExpectedUpdatedEntity();
            expected.setMaxAutomaticTokenAssociations(500);
            if (expected.getType() != EntityType.CONTRACT) {
                expected.setReceiverSigRequired(true);
            }

            Message updatedInnerBody = innerBody.toBuilder()
                    .setField(maxAutomaticTokenAssociationsField, Int32Value.of(500))
                    .build();
            testSpecs.add(UpdateEntityTestSpec.builder()
                    .description("update entity with max_automatic_token_associations")
                    .expected(expected)
                    .recordItem(getRecordItem(body, updatedInnerBody))
                    .build());
        }

        return testSpecs;
    }

    protected AbstractEntity getExpectedUpdatedEntity() {
        Entity entity = getExpectedEntityWithTimestamp();

        TransactionBody defaultBody = getDefaultTransactionBody().build();
        Message innerBody = getInnerBody(defaultBody);
        List<String> fieldNames = innerBody.getDescriptorForType().getFields().stream()
                .map(FieldDescriptor::getName)
                .toList();
        boolean isTopicCreateOrUpdate = defaultBody.getDataCase() == DataCase.CONSENSUSCREATETOPIC
                || defaultBody.getDataCase() == DataCase.CONSENSUSUPDATETOPIC;

        for (String fieldName : fieldNames) {
            switch (fieldName) {
                case "adminKey":
                    if (!isTopicCreateOrUpdate) {
                        entity.setKey(DEFAULT_KEY.toByteArray());
                    }
                    break;
                case "autoRenewPeriod":
                    entity.setAutoRenewPeriod(DEFAULT_AUTO_RENEW_PERIOD.getSeconds());
                    break;
                case "expiry", "expirationTime":
                    entity.setExpirationTimestamp(DomainUtils.timestampInNanosMax(DEFAULT_EXPIRATION_TIME));
                    break;
                case "keys":
                    entity.setKey(DEFAULT_KEY_LIST.toByteArray());
                    break;
                case "receiverSigRequired":
                    entity.setReceiverSigRequired(DEFAULT_RECEIVER_SIG_REQUIRED);
                    break;
                default:
                    break;
            }
        }

        return entity;
    }

    protected TransactionBody getTransactionBodyForUpdateEntityWithoutMemo() {
        TransactionBody defaultBody = getDefaultTransactionBody().build();
        Message innerBody = getInnerBody(defaultBody);
        Message.Builder builder = innerBody.toBuilder().clear();

        for (FieldDescriptor field : innerBody.getDescriptorForType().getFields()) {
            switch (field.getName()) {
                case "adminKey":
                    builder.setField(field, DEFAULT_KEY);
                    break;
                case "autoRenewPeriod":
                    builder.setField(field, DEFAULT_AUTO_RENEW_PERIOD);
                    break;
                case "expiry", "expirationTime":
                    builder.setField(field, DEFAULT_EXPIRATION_TIME);
                    break;
                case "keys":
                    builder.setField(field, DEFAULT_KEY_LIST);
                    break;
                case "receiverSigRequired":
                    builder.setField(field, DEFAULT_RECEIVER_SIG_REQUIRED);
                    break;
                case "submitKey":
                    builder.setField(field, DEFAULT_SUBMIT_KEY);
                    break;
                default:
                    break;
            }
        }

        return getTransactionBody(defaultBody, builder.build());
    }

    protected FieldDescriptor getInnerBodyFieldDescriptorByName(String name) {
        TransactionBody body = getDefaultTransactionBody().build();
        return getInnerBody(body).getDescriptorForType().findFieldByName(name);
    }

    protected Message getInnerBody(TransactionBody body) {
        FieldDescriptor innerBodyField =
                body.getDescriptorForType().findFieldByNumber(body.getDataCase().getNumber());
        return (Message) body.getField(innerBodyField);
    }

    protected TransactionBody getTransactionBody(TransactionBody body, Message innerBody) {
        FieldDescriptor innerBodyField =
                body.getDescriptorForType().findFieldByNumber(body.getDataCase().getNumber());
        return body.toBuilder().setField(innerBodyField, innerBody).build();
    }

    private RecordItem getRecordItem(TransactionBody body, Message innerBody) {
        return getRecordItem(
                getTransactionBody(body, innerBody),
                getDefaultTransactionRecord().build());
    }

    protected RecordItem getRecordItem(TransactionBody body, TransactionRecord txnRecord) {
        Transaction transaction = Transaction.newBuilder()
                .setSignedTransactionBytes(SignedTransaction.newBuilder()
                        .setBodyBytes(body.toByteString())
                        .setSigMap(getDefaultSigMap())
                        .build()
                        .toByteString())
                .build();

        return RecordItem.builder()
                .entityTransactionPredicate(entityProperties.getPersist()::shouldPersistEntityTransaction)
                .hapiVersion(new Version(0, 28, 0))
                .transactionRecord(txnRecord)
                .transaction(transaction)
                .build();
    }

    private boolean isCrudTransactionHandler(TransactionHandler transactionHandler) {
        return transactionHandler instanceof AbstractEntityCrudTransactionHandler;
    }

    @Builder
    @Value
    static class UpdateEntityTestSpec {
        String description;
        AbstractEntity expected;
        RecordItem recordItem;
    }
}
