// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.parser.record.entity;

import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INSUFFICIENT_ACCOUNT_BALANCE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.from;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.google.common.collect.Range;
import com.google.protobuf.ByteString;
import com.google.protobuf.StringValue;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ConsensusCreateTopicTransactionBody;
import com.hederahashgraph.api.proto.java.ConsensusDeleteTopicTransactionBody;
import com.hederahashgraph.api.proto.java.ConsensusMessageChunkInfo;
import com.hederahashgraph.api.proto.java.ConsensusSubmitMessageTransactionBody;
import com.hederahashgraph.api.proto.java.ConsensusSubmitMessageTransactionBody.Builder;
import com.hederahashgraph.api.proto.java.ConsensusUpdateTopicTransactionBody;
import com.hederahashgraph.api.proto.java.CustomFeeLimit;
import com.hederahashgraph.api.proto.java.Duration;
import com.hederahashgraph.api.proto.java.FeeExemptKeyList;
import com.hederahashgraph.api.proto.java.FixedCustomFee;
import com.hederahashgraph.api.proto.java.FixedCustomFeeList;
import com.hederahashgraph.api.proto.java.FixedFee;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.SignedTransaction;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.TopicID;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionID;
import com.hederahashgraph.api.proto.java.TransactionReceipt;
import com.hederahashgraph.api.proto.java.TransactionRecord;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.apache.commons.codec.digest.DigestUtils;
import org.hiero.mirror.common.domain.entity.AbstractEntity;
import org.hiero.mirror.common.domain.entity.Entity;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.hiero.mirror.common.domain.entity.EntityType;
import org.hiero.mirror.common.domain.token.CustomFee;
import org.hiero.mirror.common.domain.topic.Topic;
import org.hiero.mirror.common.domain.topic.TopicMessage;
import org.hiero.mirror.common.domain.transaction.AssessedCustomFee;
import org.hiero.mirror.common.domain.transaction.RecordItem;
import org.hiero.mirror.common.domain.transaction.Transaction;
import org.hiero.mirror.common.domain.transaction.TransactionType;
import org.hiero.mirror.common.util.DomainUtils;
import org.hiero.mirror.importer.TestUtils;
import org.hiero.mirror.importer.converter.KeyConverter;
import org.hiero.mirror.importer.converter.TopicIdArgumentConverter;
import org.hiero.mirror.importer.parser.CommonParserProperties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.converter.ConvertWith;
import org.junit.jupiter.params.provider.CsvSource;

@RequiredArgsConstructor
class EntityRecordItemListenerTopicTest extends AbstractEntityRecordItemListenerTest {

    private static final TopicID FILTERED_TOPIC_ID =
            DOMAIN_BUILDER.entityNum(999).toTopicID();
    private static final TopicID TOPIC_ID = DOMAIN_BUILDER.entityNum(200).toTopicID();
    private static final String TRANSACTION_MEMO = "transaction memo";
    private static final EntityId PAYER_ACCOUNT_ID = DOMAIN_BUILDER.entityNum(9999);
    private static final String TRANSACTION_ID = PAYER_ACCOUNT_ID.toString() + "-123456789";

    private final CommonParserProperties commonParserProperties;

    private Collection<EntityId> excludeEntities;

    @BeforeEach
    void setup() {
        if (commonProperties.getRealm() == 0 && commonProperties.getShard() == 0) {
            return;
        }

        // workaround filtering topics with non-zero realm, the filter is built lazily and cached. so we have to replace
        // the exclude entities in runtime
        var exclude = commonParserProperties.getExclude().iterator().next();
        excludeEntities = exclude.getEntity();
        exclude.setEntity(List.of(EntityId.of(FILTERED_TOPIC_ID)));
    }

    @AfterEach
    void teardown() {
        if (commonProperties.getRealm() == 0 && commonProperties.getShard() == 0) {
            return;
        }

        var exclude = commonParserProperties.getExclude().iterator().next();
        exclude.setEntity(excludeEntities);
    }

    @ParameterizedTest
    @CsvSource({
        "65537, admin-key, submit-key, '', 1000000, 1, 30",
        "2147483647, admin-key, '', memo, 1000001, 1, 30",
        "1, '', '', memo, 1000002, , ,",
        "55, admin-key, submit-key, memo, 1000003, 1, 30"
    })
    void createTopicTest(
            @ConvertWith(TopicIdArgumentConverter.class) TopicID topicId,
            @ConvertWith(KeyConverter.class) Key adminKey,
            @ConvertWith(KeyConverter.class) Key submitKey,
            String memo,
            long consensusTimestamp,
            Long autoRenewAccountNum,
            Long autoRenewPeriod) {
        var responseCode = SUCCESS;
        var transaction = createCreateTopicTransaction(adminKey, submitKey, memo, autoRenewAccountNum, autoRenewPeriod);
        var transactionRecord = createTransactionRecord(topicId, consensusTimestamp, responseCode);

        parseRecordItemAndCommit(RecordItem.builder()
                .transactionRecord(transactionRecord)
                .transaction(transaction)
                .build());

        var entity = getTopicEntity(topicId);

        assertTransactionInRepository(responseCode, consensusTimestamp, entity.getId());
        assertEquals(1L, entityRepository.count());
        var autoRenewAccountId = autoRenewAccountNum == null
                ? null
                : domainBuilder.entityNum(autoRenewAccountNum).getId();
        var expectedEntity = createTopicEntity(topicId, null, null, memo, autoRenewAccountId, autoRenewPeriod);
        expectedEntity.setCreatedTimestamp(consensusTimestamp);
        expectedEntity.setDeleted(false);
        expectedEntity.setTimestampLower(consensusTimestamp);

        assertThat(entity).isEqualTo(expectedEntity);

        var expectedTopic = Topic.builder()
                .adminKey(adminKey.toByteArray())
                .createdTimestamp(consensusTimestamp)
                .feeExemptKeyList(FeeExemptKeyList.getDefaultInstance().toByteArray())
                .id(entity.getId())
                .submitKey(submitKey.toByteArray())
                .timestampRange(Range.atLeast(consensusTimestamp))
                .build();
        assertThat(topicRepository.findAll()).containsExactly(expectedTopic);
        assertThat(findHistory(Topic.class)).isEmpty();
    }

    @Test
    void createTopicTestNulls() {
        var consensusTimestamp = 2_000_000L;
        var responseCode = SUCCESS;
        var transaction = createCreateTopicTransaction(null, null, "", null, null);
        var transactionRecord = createTransactionRecord(TOPIC_ID, null, null, 2, consensusTimestamp, responseCode);

        parseRecordItemAndCommit(RecordItem.builder()
                .transactionRecord(transactionRecord)
                .transaction(transaction)
                .build());

        var entity = getTopicEntity(TOPIC_ID);
        assertTransactionInRepository(responseCode, consensusTimestamp, entity.getId());
        assertEquals(1L, entityRepository.count());
        assertThat(entity)
                .returns(null, from(Entity::getKey))
                .returns("", from(Entity::getMemo))
                .returns(false, from(Entity::getDeleted))
                .returns(EntityType.TOPIC, from(Entity::getType));

        var expectedTopic = Topic.builder()
                .createdTimestamp(consensusTimestamp)
                .feeExemptKeyList(FeeExemptKeyList.getDefaultInstance().toByteArray())
                .id(entity.getId())
                .timestampRange(Range.atLeast(consensusTimestamp))
                .build();
        assertThat(topicRepository.findAll()).containsExactly(expectedTopic);
        assertThat(findHistory(Topic.class)).isEmpty();
    }

    // Issue #501
    @Test
    void createTopicTestExistingAutoRenewAccount() {
        Long autoRenewAccountNum = 100L;
        var consensusTimestamp = 2_000_000L;
        var responseCode = SUCCESS;
        var transaction = createCreateTopicTransaction(null, null, "", autoRenewAccountNum, null);
        var transactionRecord = createTransactionRecord(TOPIC_ID, null, null, 1, consensusTimestamp, responseCode);

        parseRecordItemAndCommit(RecordItem.builder()
                .transactionRecord(transactionRecord)
                .transaction(transaction)
                .build());

        var entity = getTopicEntity(TOPIC_ID);

        assertTransactionInRepository(responseCode, consensusTimestamp, entity.getId());
        assertEquals(1L, entityRepository.count());
        assertThat(entity)
                .returns(null, from(Entity::getKey))
                .returns("", from(Entity::getMemo))
                .returns(false, from(Entity::getDeleted))
                .returns(EntityType.TOPIC, from(Entity::getType))
                .returns(domainBuilder.entityNum(autoRenewAccountNum).getId(), AbstractEntity::getAutoRenewAccountId);
        var expectedTopic = Topic.builder()
                .createdTimestamp(consensusTimestamp)
                .feeExemptKeyList(FeeExemptKeyList.getDefaultInstance().toByteArray())
                .id(entity.getId())
                .timestampRange(Range.atLeast(consensusTimestamp))
                .build();
        assertThat(topicRepository.findAll()).containsExactly(expectedTopic);
        assertThat(findHistory(Topic.class)).isEmpty();
    }

    @Test
    void createTopicTestFiltered() {
        var consensusTimestamp = 2_000_000L;
        var transaction = createCreateTopicTransaction(null, null, null, null, null);
        var transactionRecord = createTransactionRecord(FILTERED_TOPIC_ID, null, null, 1, consensusTimestamp, SUCCESS);

        parseRecordItemAndCommit(RecordItem.builder()
                .transactionRecord(transactionRecord)
                .transaction(transaction)
                .build());

        assertThat(entityRepository.findAll()).isEmpty();
        assertThat(transactionRepository.findAll()).isEmpty();
        assertThat(topicRepository.findAll()).isEmpty();
        assertThat(findHistory(Topic.class)).isEmpty();
    }

    @Test
    void createTopicTestError() {
        var consensusTimestamp = 3_000_000L;
        var responseCode = INSUFFICIENT_ACCOUNT_BALANCE;
        var transaction = createCreateTopicTransaction(null, null, "memo", null, null);
        var transactionRecord = createTransactionRecord(null, consensusTimestamp, responseCode);

        parseRecordItemAndCommit(RecordItem.builder()
                .transactionRecord(transactionRecord)
                .transaction(transaction)
                .build());

        assertTransactionInRepository(responseCode, consensusTimestamp, null);
        assertThat(entityRepository.findAll()).isEmpty();
        assertThat(topicRepository.findAll()).isEmpty();
        assertThat(findHistory(Topic.class)).isEmpty();
    }

    @Test
    void createUpdateTopicWithCustomFees() {
        var expectedCustomFeeHistory = new ArrayList<CustomFee>();
        var expectedEntityHistory = new ArrayList<Entity>();
        var expectedTopicHistory = new ArrayList<Topic>();

        // create topic
        var create = recordItemBuilder
                .consensusCreateTopic()
                .transactionBody(
                        b -> b.clearAutoRenewAccount().clearAutoRenewPeriod().clearCustomFees())
                .build();
        var body = create.getTransactionBody().getConsensusCreateTopic();
        var topicId = create.getTransactionRecord().getReceipt().getTopicID();
        var expectedEntity = EntityId.of(
                        create.getTransactionRecord().getReceipt().getTopicID())
                .toEntity();
        var range = Range.atLeast(create.getConsensusTimestamp());
        expectedEntity.setCreatedTimestamp(create.getConsensusTimestamp());
        expectedEntity.setDeclineReward(false);
        expectedEntity.setDeleted(false);
        expectedEntity.setMemo(body.getMemo());
        expectedEntity.setStakedNodeId(-1L);
        expectedEntity.setStakePeriodStart(-1L);
        expectedEntity.setTimestampRange(range);
        expectedEntity.setType(EntityType.TOPIC);

        var expectedTopic = Topic.builder()
                .adminKey(body.getAdminKey().toByteArray())
                .createdTimestamp(create.getConsensusTimestamp())
                .feeExemptKeyList(FeeExemptKeyList.newBuilder()
                        .addAllKeys(body.getFeeExemptKeyListList())
                        .build()
                        .toByteArray())
                .feeScheduleKey(body.getFeeScheduleKey().toByteArray())
                .id(expectedEntity.getId())
                .submitKey(body.getSubmitKey().toByteArray())
                .timestampRange(range)
                .build();

        var expectedCustomFee = CustomFee.builder()
                .fixedFees(Collections.emptyList())
                .entityId(expectedEntity.getId())
                .timestampRange(range)
                .build();

        // update custom fee
        var tokenId = recordItemBuilder.tokenId();
        var collector = recordItemBuilder.accountId();
        var updateCustomFee = recordItemBuilder
                .consensusUpdateTopic()
                .transactionBody(b -> b.clear()
                        .setCustomFees(FixedCustomFeeList.newBuilder()
                                .addFees(FixedCustomFee.newBuilder()
                                        .setFixedFee(FixedFee.newBuilder()
                                                .setAmount(100L)
                                                .setDenominatingTokenId(tokenId))
                                        .setFeeCollectorAccountId(collector)))
                        .setTopicID(topicId))
                .build();
        range = Range.closedOpen(expectedEntity.getTimestampLower(), updateCustomFee.getConsensusTimestamp());
        expectedCustomFeeHistory.add(
                expectedCustomFee.toBuilder().timestampRange(range).build());
        expectedEntityHistory.add(
                expectedEntity.toBuilder().timestampRange(range).build());
        expectedTopicHistory.add(expectedTopic.toBuilder().timestampRange(range).build());

        range = Range.atLeast(updateCustomFee.getConsensusTimestamp());
        expectedCustomFee.setFixedFees(List.of(org.hiero.mirror.common.domain.token.FixedFee.builder()
                .amount(100L)
                .collectorAccountId(EntityId.of(collector))
                .denominatingTokenId(EntityId.of(tokenId))
                .build()));
        expectedCustomFee.setTimestampRange(range);
        expectedEntity.setTimestampRange(range);
        expectedTopic.setTimestampRange(range);

        // clear fee exempt key list
        var clearFeeExemptKeyList = recordItemBuilder
                .consensusUpdateTopic()
                .transactionBody(b -> b.clear()
                        .setFeeExemptKeyList(FeeExemptKeyList.getDefaultInstance())
                        .setTopicID(topicId))
                .build();
        range = Range.closedOpen(expectedEntity.getTimestampLower(), clearFeeExemptKeyList.getConsensusTimestamp());
        expectedEntityHistory.add(
                expectedEntity.toBuilder().timestampRange(range).build());
        expectedTopicHistory.add(expectedTopic.toBuilder().timestampRange(range).build());

        range = Range.atLeast(clearFeeExemptKeyList.getConsensusTimestamp());
        expectedEntity.setTimestampRange(range);
        expectedTopic.setFeeExemptKeyList(FeeExemptKeyList.getDefaultInstance().toByteArray());
        expectedTopic.setTimestampRange(range);

        // update fee schedule key
        var newKey = recordItemBuilder.key();
        var updateFeeScheduleKey = recordItemBuilder
                .consensusUpdateTopic()
                .transactionBody(b -> b.clear().setFeeScheduleKey(newKey).setTopicID(topicId))
                .build();
        range = Range.closedOpen(expectedEntity.getTimestampLower(), updateFeeScheduleKey.getConsensusTimestamp());
        expectedEntityHistory.add(
                expectedEntity.toBuilder().timestampRange(range).build());
        expectedTopicHistory.add(expectedTopic.toBuilder().timestampRange(range).build());

        range = Range.atLeast(updateFeeScheduleKey.getConsensusTimestamp());
        expectedEntity.setTimestampRange(range);
        expectedTopic.setFeeScheduleKey(newKey.toByteArray());
        expectedTopic.setTimestampRange(range);

        // when
        parseRecordItemsAndCommit(List.of(create, updateCustomFee, clearFeeExemptKeyList, updateFeeScheduleKey));

        // then
        assertThat(customFeeRepository.findAll()).containsExactly(expectedCustomFee);
        assertThat(findHistory(CustomFee.class)).containsExactlyInAnyOrderElementsOf(expectedCustomFeeHistory);
        assertThat(entityRepository.findAll()).containsExactly(expectedEntity);
        assertThat(findHistory(Entity.class)).containsExactlyInAnyOrderElementsOf(expectedEntityHistory);
        assertThat(topicRepository.findAll()).containsExactly(expectedTopic);
        assertThat(findHistory(Topic.class)).containsExactlyInAnyOrderElementsOf(expectedTopicHistory);
    }

    @ParameterizedTest
    @CsvSource({
        "11, 21, updated-admin-key, updated-submit-key, updated-memo, 1, 30",
        "11, 21, '', '', '', 0, 30",
        "11, 21, updated-admin-key, updated-submit-key, updated-memo, ,"
    })
    void updateTopicTest(
            long updatedExpirationTimeSeconds,
            int updatedExpirationTimeNanos,
            @ConvertWith(KeyConverter.class) Key updatedAdminKey,
            @ConvertWith(KeyConverter.class) Key updatedSubmitKey,
            String updatedMemo,
            Long autoRenewAccountNum,
            Long autoRenewPeriod) {
        var topicEntity = domainBuilder
                .topicEntity()
                .customize(t -> t.permanentRemoval(null).obtainerId(null))
                .persist();
        var topic = domainBuilder
                .topic()
                .customize(t -> t.createdTimestamp(topicEntity.getCreatedTimestamp())
                        .id(topicEntity.getId())
                        .timestampRange(topicEntity.getTimestampRange()))
                .persist();
        var updateTimestamp = topicEntity.getCreatedTimestamp() + 100L;

        var topicId = EntityId.of(topicEntity.getId()).toTopicID();
        var transaction = createUpdateTopicTransaction(
                topicId,
                updatedExpirationTimeSeconds,
                updatedExpirationTimeNanos,
                updatedAdminKey,
                updatedSubmitKey,
                updatedMemo,
                autoRenewAccountNum,
                autoRenewPeriod);
        var transactionRecord = createTransactionRecord(topicId, updateTimestamp, SUCCESS);

        var expectedAutoRenewAccountId = autoRenewAccountNum == null
                ? topicEntity.getAutoRenewAccountId()
                : domainBuilder.entityNum(autoRenewAccountNum).getId();
        var expectedAutoRenewPeriod = autoRenewPeriod == null ? topicEntity.getAutoRenewPeriod() : autoRenewPeriod;
        var expected = createTopicEntity(
                topicId,
                updatedExpirationTimeSeconds,
                updatedExpirationTimeNanos,
                updatedMemo,
                expectedAutoRenewAccountId,
                expectedAutoRenewPeriod);
        expected.setCreatedTimestamp(topicEntity.getCreatedTimestamp());
        expected.setDeleted(false);
        expected.setTimestampLower(updateTimestamp);

        parseRecordItemAndCommit(RecordItem.builder()
                .transactionRecord(transactionRecord)
                .transaction(transaction)
                .build());

        assertTransactionInRepository(SUCCESS, updateTimestamp, topicEntity.getId());
        assertEntity(expected);
        assertEquals(1L, entityRepository.count());

        var expectedTopic = topic.toBuilder()
                .adminKey(updatedAdminKey.toByteArray())
                .submitKey(updatedSubmitKey.toByteArray())
                .timestampRange(Range.atLeast(updateTimestamp))
                .build();
        topic.setTimestampUpper(updateTimestamp);
        assertThat(topicRepository.findAll()).containsExactly(expectedTopic);
        assertThat(findHistory(Topic.class)).containsExactly(topic);
    }

    @Test
    void updateTopicTestError() {
        var topicId = TopicID.newBuilder().setTopicNum(1600).build();
        var updatedAdminKey = keyFromString(KEY);
        var updatedSubmitKey = keyFromString(KEY2);
        var consensusTimestamp = 6_000_000L;
        var responseCode = ResponseCodeEnum.INVALID_TOPIC_ID;

        // Store topic to be updated.
        var topicEntity = createTopicEntity(topicId, 10L, 20, "memo", null, 30L);
        entityRepository.save(topicEntity);
        var topic = domainBuilder
                .topic()
                .customize(t -> t.createdTimestamp(topicEntity.getCreatedTimestamp())
                        .id(topicEntity.getId())
                        .timestampRange(topicEntity.getTimestampRange()))
                .persist();

        var transaction = createUpdateTopicTransaction(
                topicId, 11L, 21, updatedAdminKey, updatedSubmitKey, "updated-memo", null, 30L);
        var transactionRecord = createTransactionRecord(topicId, consensusTimestamp, responseCode);

        parseRecordItemAndCommit(RecordItem.builder()
                .transactionRecord(transactionRecord)
                .transaction(transaction)
                .build());
        var entity = getTopicEntity(topicId);
        assertTransactionInRepository(responseCode, consensusTimestamp, entity.getId());
        assertEquals(1L, entityRepository.count());
        assertThat(entity).isEqualTo(topicEntity);
        assertThat(topicRepository.findAll()).containsExactly(topic);
        assertThat(findHistory(Topic.class)).isEmpty();
    }

    @Test
    void updateTopicTestTopicNotFound() {
        var adminKey = keyFromString(KEY);
        var submitKey = keyFromString(KEY2);
        var consensusTimestamp = 6_000_000L;
        var responseCode = SUCCESS;
        var memo = "updated-memo";
        var autoRenewAccount = domainBuilder.entityNum(1);
        // Topic does not get stored in the repository beforehand.

        var transaction = createUpdateTopicTransaction(
                TOPIC_ID, 11L, 0, adminKey, submitKey, memo, autoRenewAccount.getNum(), 30L);
        var transactionRecord = createTransactionRecord(TOPIC_ID, consensusTimestamp, responseCode);

        parseRecordItemAndCommit(RecordItem.builder()
                .transactionRecord(transactionRecord)
                .transaction(transaction)
                .build());
        var entity = getTopicEntity(TOPIC_ID);
        assertTransactionInRepository(responseCode, consensusTimestamp, entity.getId());
        assertEquals(1L, entityRepository.count());

        var expectedTopicEntity = createTopicEntity(TOPIC_ID, 11L, 0, memo, autoRenewAccount.getId(), 30L);
        expectedTopicEntity.setDeleted(false);
        expectedTopicEntity.setTimestampLower(consensusTimestamp);
        assertThat(entity).isEqualTo(expectedTopicEntity);

        var expectedTopic = Topic.builder()
                .adminKey(adminKey.toByteArray())
                .id(expectedTopicEntity.getId())
                .submitKey(submitKey.toByteArray())
                .timestampRange(expectedTopicEntity.getTimestampRange())
                .build();
        assertThat(topicRepository.findAll()).containsExactly(expectedTopic);
        assertThat(findHistory(Topic.class)).isEmpty();
    }

    @ParameterizedTest
    @CsvSource({
        "1500, 10, 20, admin-key, submit-key, memo, 5000000, 0, 0, , , , 1, 30, , 0",
        "1500, 10, 20, admin-key, submit-key, memo, 5000000, , , , , , 1, 30, , ",
        "1500, 10, 20, admin-key, submit-key, memo, 5000000, , , , , '', 1, 30, , ",
        "1501, 0, 0, '', '', '', 5000001, 0, 0, , , , , , ,",
        "1502, , , admin-key, submit-key, memo, 5000002, 10, 20, updated-admin-key, updated-submit-key, "
                + "updated-memo, 1, 30, 11, 31",
        "1503, , , , , '', 5000003, 11, 21, admin-key, submit-key, memo, , , 1, 30"
    })
    void updateTopicTestPartialUpdates(
            @ConvertWith(TopicIdArgumentConverter.class) TopicID topicId,
            Long expirationTimeSeconds,
            Integer expirationTimeNanos,
            @ConvertWith(KeyConverter.class) Key adminKey,
            @ConvertWith(KeyConverter.class) Key submitKey,
            String memo,
            long consensusTimestamp,
            Long updatedExpirationTimeSeconds,
            Integer updatedExpirationTimeNanos,
            @ConvertWith(KeyConverter.class) Key updatedAdminKey,
            @ConvertWith(KeyConverter.class) Key updatedSubmitKey,
            String updatedMemo,
            Long autoRenewAccountNum,
            Long autoRenewPeriod,
            Long updatedAutoRenewAccountNum,
            Long updatedAutoRenewPeriod) {

        var autoRenewAccountId = autoRenewAccountNum == null
                ? null
                : domainBuilder.entityNum(autoRenewAccountNum).getId();
        // Store topic to be updated.
        var topicEntity = createTopicEntity(
                topicId, expirationTimeSeconds, expirationTimeNanos, memo, autoRenewAccountId, autoRenewPeriod);
        entityRepository.save(topicEntity);
        var topic = domainBuilder
                .topic()
                .customize(t -> t.adminKey(adminKey != null ? adminKey.toByteArray() : null)
                        .createdTimestamp(topicEntity.getCreatedTimestamp())
                        .id(topicEntity.getId())
                        .submitKey(submitKey != null ? submitKey.toByteArray() : null)
                        .timestampRange(topicEntity.getTimestampRange()))
                .persist();

        if (updatedAutoRenewAccountNum != null) {
            topicEntity.setAutoRenewAccountId(
                    domainBuilder.entityNum(updatedAutoRenewAccountNum).getId());
        }
        if (updatedAutoRenewPeriod != null) {
            topicEntity.setAutoRenewPeriod(updatedAutoRenewPeriod);
        }
        if (updatedExpirationTimeSeconds != null && updatedExpirationTimeNanos != null) {
            topicEntity.setExpirationTimestamp(
                    DomainUtils.convertToNanosMax(updatedExpirationTimeSeconds, updatedExpirationTimeNanos));
        }
        if (updatedMemo != null) {
            topicEntity.setMemo(updatedMemo);
        }

        var responseCode = SUCCESS;
        var transaction = createUpdateTopicTransaction(
                topicId,
                updatedExpirationTimeSeconds,
                updatedExpirationTimeNanos,
                updatedAdminKey,
                updatedSubmitKey,
                updatedMemo,
                updatedAutoRenewAccountNum,
                updatedAutoRenewPeriod);
        var transactionRecord = createTransactionRecord(topicId, consensusTimestamp, responseCode);

        parseRecordItemAndCommit(RecordItem.builder()
                .transactionRecord(transactionRecord)
                .transaction(transaction)
                .build());

        if (updatedAutoRenewAccountNum != null) {
            topicEntity.setAutoRenewAccountId(
                    domainBuilder.entityNum(updatedAutoRenewAccountNum).getId());
        }
        topicEntity.setDeleted(false);
        topicEntity.setTimestampLower(consensusTimestamp);

        var actual = getTopicEntity(topicId);
        assertTransactionInRepository(responseCode, consensusTimestamp, actual.getId());
        assertEquals(1L, entityRepository.count());
        assertThat(actual).isEqualTo(topicEntity);

        var expectedTopic = topic.toBuilder()
                .timestampRange(topicEntity.getTimestampRange())
                .build();
        if (updatedAdminKey != null) {
            expectedTopic.setAdminKey(updatedAdminKey.toByteArray());
        }
        if (updatedSubmitKey != null) {
            expectedTopic.setSubmitKey(updatedSubmitKey.toByteArray());
        }
        topic.setTimestampUpper(expectedTopic.getTimestampLower());
        assertThat(topicRepository.findAll()).containsExactly(expectedTopic);
        assertThat(findHistory(Topic.class)).containsExactly(topic);
    }

    @Test
    void deleteTopicTest() {
        var consensusTimestamp = 7_000_000L;
        var responseCode = SUCCESS;

        // Store topic to be deleted.
        var topic = createTopicEntity(TOPIC_ID, null, null, "", null, null);
        entityRepository.save(topic);

        // Setup expected data
        topic.setDeleted(true);
        topic.setTimestampLower(consensusTimestamp);

        var transaction = createDeleteTopicTransaction(TOPIC_ID);
        var transactionRecord = createTransactionRecord(TOPIC_ID, consensusTimestamp, responseCode);

        parseRecordItemAndCommit(RecordItem.builder()
                .transactionRecord(transactionRecord)
                .transaction(transaction)
                .build());

        var entity = getTopicEntity(TOPIC_ID);
        assertTransactionInRepository(SUCCESS, consensusTimestamp, entity.getId());
        assertEquals(1L, entityRepository.count());
        assertThat(entity).isEqualTo(topic);
    }

    @Test
    void deleteTopicTestTopicNotFound() {
        var consensusTimestamp = 10_000_000L;
        var responseCode = SUCCESS;

        // Setup expected data
        var topic = createTopicEntity(TOPIC_ID, null, null, "", null, null);
        topic.setDeleted(true);
        topic.setTimestampLower(consensusTimestamp);
        // Topic not saved to the repository.

        var transaction = createDeleteTopicTransaction(TOPIC_ID);
        var transactionRecord = createTransactionRecord(TOPIC_ID, consensusTimestamp, responseCode);

        parseRecordItemAndCommit(RecordItem.builder()
                .transactionRecord(transactionRecord)
                .transaction(transaction)
                .build());

        var entity = getTopicEntity(TOPIC_ID);
        assertTransactionInRepository(responseCode, consensusTimestamp, entity.getId());
        assertEquals(1L, entityRepository.count());
        assertThat(entity).isEqualTo(topic);
    }

    @Test
    void deleteTopicTestError() {
        var consensusTimestamp = 9_000_000L;
        var responseCode = INSUFFICIENT_ACCOUNT_BALANCE;

        // Store topic to be deleted.
        var topic = createTopicEntity(TOPIC_ID, 10L, 20, "", null, null);
        entityRepository.save(topic);

        var transaction = createDeleteTopicTransaction(TOPIC_ID);
        var transactionRecord = createTransactionRecord(TOPIC_ID, consensusTimestamp, responseCode);

        parseRecordItemAndCommit(RecordItem.builder()
                .transactionRecord(transactionRecord)
                .transaction(transaction)
                .build());

        var entity = getTopicEntity(TOPIC_ID);
        assertTransactionInRepository(responseCode, consensusTimestamp, entity.getId());
        assertEquals(1L, entityRepository.count());
        assertThat(entity).isEqualTo(topic);
    }

    @Test
    void submitMessageMaxCustomFeeTest() {
        // given
        var accountId = recordItemBuilder.accountId();
        var feeCollector = recordItemBuilder.accountId();
        var tokenId = recordItemBuilder.tokenId();
        var customFeeLimit = CustomFeeLimit.newBuilder()
                .addFees(FixedFee.newBuilder().setAmount(50_000L))
                .addFees(FixedFee.newBuilder().setAmount(60_000L).setDenominatingTokenId(tokenId))
                .setAccountId(accountId)
                .build();
        var assessedCustomFees = List.of(
                com.hederahashgraph.api.proto.java.AssessedCustomFee.newBuilder()
                        .setAmount(40_000L)
                        .setFeeCollectorAccountId(feeCollector)
                        .addEffectivePayerAccountId(accountId)
                        .build(),
                com.hederahashgraph.api.proto.java.AssessedCustomFee.newBuilder()
                        .setAmount(55_000L)
                        .setFeeCollectorAccountId(feeCollector)
                        .addEffectivePayerAccountId(accountId)
                        .setTokenId(tokenId)
                        .build());
        var recordItem = recordItemBuilder
                .consensusSubmitMessage()
                .transactionBody(Builder::clearChunkInfo)
                .transactionBodyWrapper(w -> w.clearMaxCustomFees().addMaxCustomFees(customFeeLimit))
                .record(r -> r.addAllAssessedCustomFees(assessedCustomFees))
                .build();

        // when
        parseRecordItemAndCommit(recordItem);

        // then
        var body = recordItem.getTransactionBody().getConsensusSubmitMessage();
        long consensusTimestamp = recordItem.getConsensusTimestamp();
        var transactionRecord = recordItem.getTransactionRecord();
        var receipt = transactionRecord.getReceipt();
        var topicId = EntityId.of(body.getTopicID());
        var expectedAssessedCustomFees = List.of(
                AssessedCustomFee.builder()
                        .amount(40_000L)
                        .collectorAccountId(EntityId.of(feeCollector).getId())
                        .consensusTimestamp(consensusTimestamp)
                        .effectivePayerAccountIds(List.of(EntityId.of(accountId).getId()))
                        .payerAccountId(recordItem.getPayerAccountId())
                        .build(),
                AssessedCustomFee.builder()
                        .amount(55_000L)
                        .collectorAccountId(EntityId.of(feeCollector).getId())
                        .consensusTimestamp(consensusTimestamp)
                        .effectivePayerAccountIds(List.of(EntityId.of(accountId).getId()))
                        .payerAccountId(recordItem.getPayerAccountId())
                        .tokenId(EntityId.of(tokenId))
                        .build());
        assertThat(getAllAssessedCustomFees()).containsExactlyInAnyOrderElementsOf(expectedAssessedCustomFees);
        var expectedTopicMessage = TopicMessage.builder()
                .consensusTimestamp(consensusTimestamp)
                .message(DomainUtils.toBytes(body.getMessage()))
                .payerAccountId(recordItem.getPayerAccountId())
                .runningHash(DomainUtils.toBytes(receipt.getTopicRunningHash()))
                .sequenceNumber(receipt.getTopicSequenceNumber())
                .topicId(topicId)
                .build();
        assertThat(topicMessageRepository.findAll()).containsExactly(expectedTopicMessage);
        var expectedMaxCustomFees = new byte[][] {customFeeLimit.toByteArray()};
        assertThat(transactionRepository.findAll())
                .hasSize(1)
                .first()
                .returns(consensusTimestamp, Transaction::getConsensusTimestamp)
                .returns(topicId, Transaction::getEntityId)
                .returns(expectedMaxCustomFees, Transaction::getMaxCustomFees)
                .returns(recordItem.getPayerAccountId(), Transaction::getPayerAccountId)
                .returns(TransactionType.CONSENSUSSUBMITMESSAGE.getProtoId(), Transaction::getType);
    }

    @ParameterizedTest
    @CsvSource({
        "9000, test-message0, 9000000, runninghash, 1, 1, , , , false, 0",
        "9001, test-message1, 9000001, runninghash1, 9223372036854775807, 2, 1, 1, 89999999, false, 0",
        "9001, test-message2, 9000001, runninghash2, 9223372036854775807, 2, 2, 4, 89999999, false, 0",
        "9001, test-message3, 9000001, runninghash3, 9223372036854775807, 2, 4, 4, 89999999, false, 0",
        "9001, test-message4, 9000001, runninghash3, 9223372036854775807, 2, 4, 4, 89999999, true, 7",
    })
    void submitMessageTest(
            @ConvertWith(TopicIdArgumentConverter.class) TopicID topicId,
            String message,
            long consensusTimestamp,
            String runningHash,
            long sequenceNumber,
            int runningHashVersion,
            Integer chunkNum,
            Integer chunkTotal,
            Long validStartNs,
            Boolean scheduled,
            Integer nonce) {
        var responseCode = SUCCESS;

        TransactionID initialTransactionId = null;
        if (chunkNum != null) {
            initialTransactionId = createTransactionID(
                    PAYER_ACCOUNT_ID.toAccountID(), TestUtils.toTimestamp(validStartNs), scheduled, nonce);
        }
        var topicMessage = createTopicMessage(
                topicId,
                message,
                sequenceNumber,
                runningHash,
                consensusTimestamp,
                runningHashVersion,
                chunkNum,
                chunkTotal,
                PAYER_ACCOUNT_ID,
                initialTransactionId);
        var transaction = createSubmitMessageTransaction(topicId, message, chunkNum, chunkTotal, initialTransactionId);
        var transactionRecord = createTransactionRecord(
                topicId, sequenceNumber, runningHash.getBytes(), runningHashVersion, consensusTimestamp, responseCode);

        parseRecordItemAndCommit(RecordItem.builder()
                .transactionRecord(transactionRecord)
                .transaction(transaction)
                .build());

        assertTransactionInRepository(
                responseCode, consensusTimestamp, EntityId.of(topicId).getId());
        assertEquals(0L, entityRepository.count());
        assertThat(topicMessageRepository.findById(consensusTimestamp)).get().isEqualTo(topicMessage);
    }

    @Test
    void submitMessageTestTopicNotFound() {
        var responseCode = SUCCESS;
        var consensusTimestamp = 10_000_000L;
        var message = "message";
        var sequenceNumber = 10_000L;
        var runningHash = "running-hash";
        var runningHashVersion = 2;
        var chunkNum = 3;
        var chunkTotal = 5;
        var validStartNs = 7L;
        var scheduled = false;
        var nonce = 0;

        TransactionID initialTransactionId = createTransactionID(
                PAYER_ACCOUNT_ID.toAccountID(), TestUtils.toTimestamp(validStartNs), scheduled, nonce);

        var topicMessage = createTopicMessage(
                TOPIC_ID,
                message,
                sequenceNumber,
                runningHash,
                consensusTimestamp,
                runningHashVersion,
                chunkNum,
                chunkTotal,
                PAYER_ACCOUNT_ID,
                initialTransactionId);
        var transaction = createSubmitMessageTransaction(TOPIC_ID, message, chunkNum, chunkTotal, initialTransactionId);
        var transactionRecord = createTransactionRecord(
                TOPIC_ID, sequenceNumber, runningHash.getBytes(), runningHashVersion, consensusTimestamp, responseCode);

        parseRecordItemAndCommit(RecordItem.builder()
                .transactionRecord(transactionRecord)
                .transaction(transaction)
                .build());

        assertTransactionInRepository(
                responseCode, consensusTimestamp, EntityId.of(TOPIC_ID).getId());
        assertEquals(0L, entityRepository.count());
        assertEquals(1L, topicMessageRepository.count());
        assertThat(topicMessageRepository.findById(consensusTimestamp)).get().isEqualTo(topicMessage);
    }

    @Test
    void submitMessageDisabled() {
        // given
        entityProperties.getPersist().setTopics(false);
        var responseCode = SUCCESS;
        var topicId = TOPIC_ID;
        var consensusTimestamp = 10_000_000L;
        var message = "message";
        var sequenceNumber = 10_000L;
        var runningHash = "running-hash";
        var runningHashVersion = 1;
        var chunkNum = 3;
        var chunkTotal = 5;
        var validStartNs = 7L;
        var scheduled = false;
        var nonce = 0;

        TransactionID initialTransactionId = createTransactionID(
                PAYER_ACCOUNT_ID.toAccountID(), TestUtils.toTimestamp(validStartNs), scheduled, nonce);

        var transaction = createSubmitMessageTransaction(topicId, message, chunkNum, chunkTotal, initialTransactionId);
        var transactionRecord = createTransactionRecord(
                topicId, sequenceNumber, runningHash.getBytes(), runningHashVersion, consensusTimestamp, responseCode);

        // when
        parseRecordItemAndCommit(RecordItem.builder()
                .transactionRecord(transactionRecord)
                .transaction(transaction)
                .build());

        // then
        assertEquals(0L, entityRepository.count());
        assertEquals(0L, topicMessageRepository.count());
        assertTransactionInRepository(
                responseCode, consensusTimestamp, EntityId.of(TOPIC_ID).getId());
        entityProperties.getPersist().setTopics(true);
    }

    @Test
    void submitMessageTestFiltered() {
        // given
        var consensusTimestamp = 10_000_000L;
        var message = "message";
        var sequenceNumber = 10_000L;
        var runningHash = "running-hash";
        var runningHashVersion = 1;
        var chunkNum = 3;
        var chunkTotal = 5;
        var validStartNs = 7L;
        var scheduled = false;
        var nonce = 0;

        TransactionID initialTransactionId = createTransactionID(
                PAYER_ACCOUNT_ID.toAccountID(), TestUtils.toTimestamp(validStartNs), scheduled, nonce);

        var transaction =
                createSubmitMessageTransaction(FILTERED_TOPIC_ID, message, chunkNum, chunkTotal, initialTransactionId);
        var transactionRecord = createTransactionRecord(
                FILTERED_TOPIC_ID,
                sequenceNumber,
                runningHash.getBytes(),
                runningHashVersion,
                consensusTimestamp,
                SUCCESS);

        // when
        parseRecordItemAndCommit(RecordItem.builder()
                .transactionRecord(transactionRecord)
                .transaction(transaction)
                .build());

        // then
        // if the transaction is filtered out, nothing in it should affect the state
        assertEquals(0L, entityRepository.count());
        assertEquals(0L, topicMessageRepository.count());
    }

    @Test
    void submitMessageTestInvalidChunkInfo() {
        // given
        var id = 10_000_000L;
        var topicId = domainBuilder.entityNum(9000).toTopicID();
        var scheduled = false;
        var nonce = 0;

        TransactionID initialTransactionId =
                createTransactionID(null, TestUtils.toTimestamp(Long.MAX_VALUE, Integer.MAX_VALUE), scheduled, nonce);
        var transaction = createSubmitMessageTransaction(topicId, "message", 3, 5, initialTransactionId);
        var transactionRecord = createTransactionRecord(topicId, 10_000L, "running-hash".getBytes(), 2, id, SUCCESS);

        // when
        parseRecordItemAndCommit(RecordItem.builder()
                .transactionRecord(transactionRecord)
                .transaction(transaction)
                .build());

        // then
        assertEquals(0L, entityRepository.count());
        assertEquals(1L, topicMessageRepository.count());
        assertThat(topicMessageRepository.findById(id))
                .get()
                .extracting(TopicMessage::getValidStartTimestamp, TopicMessage::getPayerAccountId)
                .containsExactly(null, PAYER_ACCOUNT_ID);
    }

    @Test
    void submitMessageTestTopicError() {
        var responseCode = ResponseCodeEnum.INVALID_TOPIC_ID;
        var consensusTimestamp = 11_000_000L;
        var message = "message";
        var sequenceNumber = 11_000L;
        var runningHash = "running-hash";
        var runningHashVersion = 2;
        var chunkNum = 3;
        var chunkTotal = 5;
        var validStartNs = 7L;
        var scheduled = false;
        var nonce = 0;

        TransactionID initialTransactionId = createTransactionID(
                PAYER_ACCOUNT_ID.toAccountID(), TestUtils.toTimestamp(validStartNs), scheduled, nonce);

        var transaction = createSubmitMessageTransaction(TOPIC_ID, message, chunkNum, chunkTotal, initialTransactionId);
        var transactionRecord = createTransactionRecord(
                TOPIC_ID, sequenceNumber, runningHash.getBytes(), runningHashVersion, consensusTimestamp, responseCode);

        parseRecordItemAndCommit(RecordItem.builder()
                .transactionRecord(transactionRecord)
                .transaction(transaction)
                .build());

        assertTransactionInRepository(
                responseCode, consensusTimestamp, EntityId.of(TOPIC_ID).getId());
        assertEquals(0, entityRepository.count());
        assertEquals(0L, topicMessageRepository.count());
    }

    @Test
    void submitMessageToInvalidTopicEntityNotCreated() {
        var responseCode = ResponseCodeEnum.INVALID_TOPIC_ID;
        var consensusTimestamp = 10_000_000L;
        var message = "message";
        var sequenceNumber = 10_000L;
        var runningHash = "running-hash";
        var runningHashVersion = 2;
        var chunkNum = 3;
        var chunkTotal = 5;
        var validStartNs = 7L;
        var scheduled = false;
        var nonce = 0;

        createTopicEntity(TOPIC_ID, null, null, "", null, null);
        // Topic NOT saved in the repository.

        TransactionID initialTransactionId = createTransactionID(
                PAYER_ACCOUNT_ID.toAccountID(), TestUtils.toTimestamp(validStartNs), scheduled, nonce);

        createTopicMessage(
                TOPIC_ID,
                message,
                sequenceNumber,
                runningHash,
                consensusTimestamp,
                runningHashVersion,
                chunkNum,
                chunkTotal,
                PAYER_ACCOUNT_ID,
                initialTransactionId);
        var transaction = createSubmitMessageTransaction(TOPIC_ID, message, chunkNum, chunkTotal, initialTransactionId);
        var transactionRecord = createTransactionRecord(
                TOPIC_ID, sequenceNumber, runningHash.getBytes(), runningHashVersion, consensusTimestamp, responseCode);

        parseRecordItemAndCommit(RecordItem.builder()
                .transactionRecord(transactionRecord)
                .transaction(transaction)
                .build());

        assertTransactionInRepository(
                responseCode, consensusTimestamp, EntityId.of(TOPIC_ID).getId());
        assertEquals(0, entityRepository.count());
        assertEquals(0, topicMessageRepository.count());
    }

    private com.hederahashgraph.api.proto.java.Transaction createCreateTopicTransaction(
            Key adminKey, Key submitKey, String memo, Long autoRenewAccount, Long autoRenewPeriod) {
        var innerBody = ConsensusCreateTopicTransactionBody.newBuilder();
        if (autoRenewAccount != null) {
            innerBody.setAutoRenewAccount(
                    domainBuilder.entityNum(autoRenewAccount).toAccountID());
        }
        if (autoRenewPeriod != null) {
            innerBody.setAutoRenewPeriod(
                    Duration.newBuilder().setSeconds(autoRenewPeriod).build());
        }
        if (adminKey != null) {
            innerBody.setAdminKey(adminKey);
        }
        if (submitKey != null) {
            innerBody.setSubmitKey(submitKey);
        }
        if (memo != null) {
            innerBody.setMemo(memo);
        }
        var body = createTransactionBody().setConsensusCreateTopic(innerBody).build();
        return com.hederahashgraph.api.proto.java.Transaction.newBuilder()
                .setSignedTransactionBytes(SignedTransaction.newBuilder()
                        .setBodyBytes(body.toByteString())
                        .build()
                        .toByteString())
                .build();
    }

    @Test
    void submitMessageTestNoInitialTransactionId() {
        var responseCode = SUCCESS;
        var message = "test-message1-no-initial-transaction";
        var consensusTimestamp = 10_000_000L;
        var sequenceNumber = 10_000L;
        var runningHash = "running-hash";
        var runningHashVersion = 2;
        var chunkNum = 3;
        var chunkTotal = 5;

        var topicMessage = createTopicMessage(
                TOPIC_ID,
                message,
                sequenceNumber,
                runningHash,
                consensusTimestamp,
                runningHashVersion,
                chunkNum,
                chunkTotal,
                PAYER_ACCOUNT_ID,
                null);
        var transaction = createSubmitMessageTransaction(TOPIC_ID, message, chunkNum, chunkTotal, null);
        var transactionRecord = createTransactionRecord(
                TOPIC_ID, sequenceNumber, runningHash.getBytes(), runningHashVersion, consensusTimestamp, responseCode);

        parseRecordItemAndCommit(RecordItem.builder()
                .transactionRecord(transactionRecord)
                .transaction(transaction)
                .build());

        assertTransactionInRepository(
                responseCode, consensusTimestamp, EntityId.of(TOPIC_ID).getId());
        assertEquals(0L, entityRepository.count());
        assertThat(topicMessageRepository.findById(consensusTimestamp)).get().isEqualTo(topicMessage);
    }

    private TransactionRecord createTransactionRecord(
            TopicID topicId, long consensusTimestamp, ResponseCodeEnum responseCode) {
        return createTransactionRecord(
                topicId, 10_000L, "running_hash".getBytes(), 1, consensusTimestamp, responseCode);
    }

    private TransactionRecord createTransactionRecord(
            TopicID topicId,
            Long topicSequenceNumber,
            byte[] topicRunningHash,
            int runningHashVersion,
            long consensusTimestamp,
            ResponseCodeEnum responseCode) {
        var receipt = TransactionReceipt.newBuilder().setStatus(responseCode);
        if (null != topicId) {
            receipt.setTopicID(topicId);
        }
        if (null != topicSequenceNumber) {
            receipt.setTopicSequenceNumber(topicSequenceNumber);
        }
        if (null != topicRunningHash) {
            receipt.setTopicRunningHash(ByteString.copyFrom(topicRunningHash));
            receipt.setTopicRunningHashVersion(runningHashVersion);
        }
        var consensusTimestampProto = TestUtils.toTimestamp(consensusTimestamp);
        var transactionRecord = TransactionRecord.newBuilder()
                .setReceipt(receipt)
                .setConsensusTimestamp(consensusTimestampProto)
                .setTransactionHash(ByteString.copyFrom(DigestUtils.sha384(consensusTimestampProto.toByteArray())));
        return transactionRecord.build();
    }

    private TransactionBody.Builder createTransactionBody() {
        return TransactionBody.newBuilder()
                .setTransactionValidDuration(
                        Duration.newBuilder().setSeconds(10).build())
                .setNodeAccountID(NODE)
                .setTransactionID(TestUtils.toTransactionId(TRANSACTION_ID))
                .setMemo(TRANSACTION_MEMO);
    }

    private Entity createTopicEntity(
            TopicID topicId,
            Long expirationTimeSeconds,
            Integer expirationTimeNanos,
            String memo,
            Long autoRenewAccountId,
            Long autoRenewPeriod) {
        var topic = EntityId.of(topicId).toEntity();

        if (autoRenewAccountId != null) {
            topic.setAutoRenewAccountId(autoRenewAccountId);
        }
        if (autoRenewPeriod != null) {
            topic.setAutoRenewPeriod(autoRenewPeriod);
        }
        if (expirationTimeSeconds != null && expirationTimeNanos != null) {
            topic.setExpirationTimestamp(DomainUtils.convertToNanosMax(expirationTimeSeconds, expirationTimeNanos));
        }

        topic.setDeclineReward(false);
        topic.setMemo(memo);
        topic.setStakedNodeId(-1L);
        topic.setStakePeriodStart(-1L);
        topic.setType(EntityType.TOPIC);
        return topic;
    }

    private TopicMessage createTopicMessage(
            TopicID topicId,
            String message,
            long sequenceNumber,
            String runningHash,
            long consensusTimestamp,
            int runningHashVersion,
            Integer chunkNum,
            Integer chunkTotal,
            EntityId payerAccountId,
            TransactionID initialTransactionID) {

        var topicMessage = new TopicMessage();
        topicMessage.setConsensusTimestamp(consensusTimestamp);
        if (initialTransactionID != null) {
            topicMessage.setInitialTransactionId(initialTransactionID.toByteArray());
        }
        topicMessage.setTopicId(EntityId.of(topicId));
        topicMessage.setMessage(message.getBytes());
        topicMessage.setPayerAccountId(payerAccountId);
        topicMessage.setSequenceNumber(sequenceNumber);
        topicMessage.setRunningHash(runningHash.getBytes());
        topicMessage.setRunningHashVersion(runningHashVersion);
        topicMessage.setChunkNum(chunkNum);
        topicMessage.setChunkTotal(chunkTotal);

        return topicMessage;
    }

    private com.hederahashgraph.api.proto.java.Transaction createUpdateTopicTransaction(
            TopicID topicId,
            Long expirationTimeSeconds,
            Integer expirationTimeNanos,
            Key adminKey,
            Key submitKey,
            String memo,
            Long autoRenewAccount,
            Long autoRenewPeriod) {
        var innerBody = ConsensusUpdateTopicTransactionBody.newBuilder().setTopicID(topicId);

        if (expirationTimeSeconds != null && expirationTimeNanos != null) {
            innerBody.setExpirationTime(TestUtils.toTimestamp(expirationTimeSeconds, expirationTimeNanos));
        }
        if (autoRenewAccount != null) {
            innerBody.setAutoRenewAccount(AccountID.newBuilder()
                    .setShardNum(topicId.getShardNum())
                    .setRealmNum(topicId.getRealmNum())
                    .setAccountNum(autoRenewAccount)
                    .build());
        }
        if (autoRenewPeriod != null) {
            innerBody.setAutoRenewPeriod(
                    Duration.newBuilder().setSeconds(autoRenewPeriod).build());
        }
        if (adminKey != null) {
            innerBody.setAdminKey(adminKey);
        }
        if (submitKey != null) {
            innerBody.setSubmitKey(submitKey);
        }
        if (memo != null) {
            innerBody.setMemo(StringValue.of(memo));
        }
        var body = createTransactionBody().setConsensusUpdateTopic(innerBody).build();
        return com.hederahashgraph.api.proto.java.Transaction.newBuilder()
                .setSignedTransactionBytes(SignedTransaction.newBuilder()
                        .setBodyBytes(body.toByteString())
                        .build()
                        .toByteString())
                .build();
    }

    private com.hederahashgraph.api.proto.java.Transaction createDeleteTopicTransaction(TopicID topicId) {
        var innerBody = ConsensusDeleteTopicTransactionBody.newBuilder().setTopicID(topicId);
        var body = createTransactionBody().setConsensusDeleteTopic(innerBody).build();
        return com.hederahashgraph.api.proto.java.Transaction.newBuilder()
                .setSignedTransactionBytes(SignedTransaction.newBuilder()
                        .setBodyBytes(body.toByteString())
                        .build()
                        .toByteString())
                .build();
    }

    private com.hederahashgraph.api.proto.java.Transaction createSubmitMessageTransaction(
            TopicID topicId, String message, Integer chunkNum, Integer chunkTotal, TransactionID initialTransactionID) {
        var submitMessageTransactionBodyBuilder = ConsensusSubmitMessageTransactionBody.newBuilder()
                .setTopicID(topicId)
                .setMessage(ByteString.copyFrom(message.getBytes()));

        if (chunkNum != null) {
            ConsensusMessageChunkInfo.Builder chunkInfoBuilder =
                    ConsensusMessageChunkInfo.newBuilder().setNumber(chunkNum).setTotal(chunkTotal);
            if (initialTransactionID != null) {
                chunkInfoBuilder.setInitialTransactionID(initialTransactionID);
            }
            submitMessageTransactionBodyBuilder.setChunkInfo(chunkInfoBuilder);
        }

        var innerBody = submitMessageTransactionBodyBuilder.build();

        var body = createTransactionBody().setConsensusSubmitMessage(innerBody).build();
        return com.hederahashgraph.api.proto.java.Transaction.newBuilder()
                .setSignedTransactionBytes(SignedTransaction.newBuilder()
                        .setBodyBytes(body.toByteString())
                        .build()
                        .toByteString())
                .build();
    }

    private TransactionID createTransactionID(
            AccountID payerAccountId, Timestamp validStartNs, Boolean scheduled, Integer nonce) {
        TransactionID.Builder transactionIdBuilder = TransactionID.newBuilder();
        if (payerAccountId != null) {
            transactionIdBuilder.setAccountID(payerAccountId);
        }
        if (validStartNs != null) {
            transactionIdBuilder.setTransactionValidStart(validStartNs);
        }
        if (scheduled != null) {
            transactionIdBuilder.setScheduled(scheduled);
        }
        if (nonce != null) {
            transactionIdBuilder.setNonce(nonce);
        }
        return transactionIdBuilder.build();
    }

    private List<AssessedCustomFee> getAllAssessedCustomFees() {
        return jdbcOperations.query("select * from assessed_custom_fee", (rs, rowNum) -> {
            var array = rs.getArray("effective_payer_account_ids");
            var ids = Arrays.asList((Long[]) array.getArray());
            long tokenId = rs.getLong("token_id");
            return AssessedCustomFee.builder()
                    .amount(rs.getLong("amount"))
                    .collectorAccountId(rs.getLong("collector_account_id"))
                    .consensusTimestamp(rs.getLong("consensus_timestamp"))
                    .effectivePayerAccountIds(ids)
                    .tokenId(tokenId == 0 ? null : EntityId.of(tokenId))
                    .payerAccountId(EntityId.of(rs.getLong("payer_account_id")))
                    .build();
        });
    }

    private Entity getTopicEntity(TopicID topicId) {
        return getEntity(EntityId.of(topicId));
    }
}
