// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.parser.record.transactionhandler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hiero.mirror.common.domain.entity.EntityType.ACCOUNT;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.google.common.collect.Range;
import com.google.protobuf.ByteString;
import com.google.protobuf.Descriptors.FieldDescriptor;
import com.google.protobuf.Message;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.CryptoCreateTransactionBody;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionReceipt;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.assertj.core.api.ObjectAssert;
import org.hiero.mirror.common.domain.entity.AbstractEntity;
import org.hiero.mirror.common.domain.entity.Entity;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.hiero.mirror.common.domain.entity.EntityTransaction;
import org.hiero.mirror.common.domain.entity.EntityType;
import org.hiero.mirror.common.domain.transaction.RecordItem;
import org.hiero.mirror.common.domain.transaction.Transaction;
import org.hiero.mirror.common.util.DomainUtils;
import org.hiero.mirror.importer.TestUtils;
import org.hiero.mirror.importer.parser.domain.RecordItemBuilder;
import org.hiero.mirror.importer.util.Utility;
import org.hiero.mirror.importer.util.UtilityTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.data.util.Version;

class CryptoCreateTransactionHandlerTest extends AbstractTransactionHandlerTest {

    private final EVMHookHandler evmHookHandler = mock(EVMHookHandler.class);

    private static Stream<Arguments> provideAlias() {
        var validKey = Key.newBuilder()
                .setECDSASecp256K1(ByteString.copyFrom(TestUtils.generateRandomByteArray(20)))
                .build();
        var emptyKey = Key.getDefaultInstance();
        var validAliasForKey = ByteString.copyFrom(UtilityTest.ALIAS_ECDSA_SECP256K1);
        var invalidAliasForKey = ByteString.fromHex("1234");
        return Stream.of(
                Arguments.of(validAliasForKey, validKey, validKey.toByteArray()),
                Arguments.of(validAliasForKey, emptyKey, validAliasForKey.toByteArray()),
                Arguments.of(invalidAliasForKey, validKey, validKey.toByteArray()),
                Arguments.of(invalidAliasForKey, emptyKey, null));
    }

    private static Stream<Arguments> provideEvmAddresses() {
        var evmAddress = RecordItemBuilder.EVM_ADDRESS;
        return Stream.of(
                Arguments.of(ByteString.empty(), UtilityTest.EVM_ADDRESS),
                Arguments.of(evmAddress, evmAddress.toByteArray()));
    }

    @Override
    protected TransactionHandler getTransactionHandler() {
        return new CryptoCreateTransactionHandler(entityIdService, entityListener, evmHookHandler);
    }

    @Override
    protected TransactionBody.Builder getDefaultTransactionBody() {
        return TransactionBody.newBuilder().setCryptoCreateAccount(CryptoCreateTransactionBody.getDefaultInstance());
    }

    @Override
    protected TransactionReceipt.Builder getTransactionReceipt(ResponseCodeEnum responseCodeEnum) {
        return TransactionReceipt.newBuilder().setStatus(responseCodeEnum).setAccountID(defaultEntityId.toAccountID());
    }

    @Override
    protected EntityType getExpectedEntityIdType() {
        return EntityType.ACCOUNT;
    }

    @Override
    protected AbstractEntity getExpectedUpdatedEntity() {
        AbstractEntity entity = super.getExpectedUpdatedEntity();
        entity.setBalance(0L);
        entity.setDeclineReward(false);
        entity.setMaxAutomaticTokenAssociations(0);
        return entity;
    }

    @Override
    protected List<UpdateEntityTestSpec> getUpdateEntityTestSpecsForCreateTransaction(FieldDescriptor memoField) {
        List<UpdateEntityTestSpec> testSpecs = super.getUpdateEntityTestSpecsForCreateTransaction(memoField);
        testSpecs.stream().forEach(testSpec -> {
            var consensusTimestamp = testSpec.getRecordItem().getConsensusTimestamp();
            testSpec.getExpected().setBalanceTimestamp(consensusTimestamp);
        });

        TransactionBody body = getTransactionBodyForUpdateEntityWithoutMemo();
        Message innerBody = getInnerBody(body);
        FieldDescriptor field = getInnerBodyFieldDescriptorByName("max_automatic_token_associations");
        innerBody = innerBody.toBuilder().setField(field, 500).build();
        body = getTransactionBody(body, innerBody);
        var recordItem = getRecordItem(body, getDefaultTransactionRecord().build());

        AbstractEntity expected = getExpectedUpdatedEntity();
        expected.setBalanceTimestamp(recordItem.getConsensusTimestamp());
        expected.setMaxAutomaticTokenAssociations(500);
        expected.setMemo("");
        testSpecs.add(UpdateEntityTestSpec.builder()
                .description("create entity with non-zero max_automatic_token_associations")
                .expected(expected)
                .recordItem(recordItem)
                .build());

        return testSpecs;
    }

    @Test
    void updateTransactionStakedAccountId() {
        // given
        var stakedAccountId = AccountID.newBuilder().setAccountNum(1L).build();
        var recordItem = recordItemBuilder
                .cryptoCreate()
                .recordItem(r -> r.hapiVersion(new Version(0, 28, 0)))
                .transactionBody(b -> b.setDeclineReward(false).setStakedAccountId(stakedAccountId))
                .build();
        var transaction = transaction(recordItem);
        var accountId =
                EntityId.of(recordItem.getTransactionRecord().getReceipt().getAccountID());

        // when
        transactionHandler.updateTransaction(transaction, recordItem);

        // then
        assertEntity(accountId, recordItem.getConsensusTimestamp())
                .returns(false, Entity::getDeclineReward)
                .returns(stakedAccountId.getAccountNum(), Entity::getStakedAccountId)
                .returns(Utility.getEpochDay(recordItem.getConsensusTimestamp()), Entity::getStakePeriodStart);
        assertThat(recordItem.getEntityTransactions())
                .containsExactlyInAnyOrderEntriesOf(getExpectedEntityTransactions(recordItem, transaction));
    }

    @ParameterizedTest
    @ValueSource(ints = {27, 28})
    void updateTransactionStakedNodeId(int minorVersion) {
        // given
        long nodeId = 1L;
        var recordItem = recordItemBuilder
                .cryptoCreate()
                .recordItem(r -> r.hapiVersion(new Version(0, minorVersion, 0)))
                .transactionBody(b -> b.setDeclineReward(true).setStakedNodeId(nodeId))
                .build();
        var transaction = transaction(recordItem);
        var accountId =
                EntityId.of(recordItem.getTransactionRecord().getReceipt().getAccountID());

        // when
        transactionHandler.updateTransaction(transaction, recordItem);

        // then
        assertEntity(accountId, recordItem.getConsensusTimestamp())
                .returns(true, Entity::getDeclineReward)
                .returns(nodeId, Entity::getStakedNodeId)
                .returns(null, Entity::getStakedAccountId)
                .returns(Utility.getEpochDay(recordItem.getConsensusTimestamp()), Entity::getStakePeriodStart);
        assertThat(recordItem.getEntityTransactions())
                .containsExactlyInAnyOrderEntriesOf(getExpectedEntityTransactions(recordItem, transaction));
    }

    @Test
    void doNotUpdateTransactionStakedAccountIdBeforeConsensusStaking() {
        // given
        var stakedAccountId = AccountID.newBuilder().setAccountNum(1L).build();
        var recordItem = recordItemBuilder
                .cryptoCreate()
                .recordItem(r -> r.hapiVersion(new Version(0, 26, 0)))
                .transactionBody(b -> b.setDeclineReward(false).setStakedAccountId(stakedAccountId))
                .build();
        var transaction = transaction(recordItem);
        var accountId =
                EntityId.of(recordItem.getTransactionRecord().getReceipt().getAccountID());

        // when
        transactionHandler.updateTransaction(transaction, recordItem);

        // then
        assertEntity(accountId, recordItem.getConsensusTimestamp())
                .returns(null, Entity::getDeclineReward)
                .returns(null, Entity::getStakedAccountId)
                .returns(null, Entity::getStakePeriodStart);
    }

    @Test
    void updateAlias() {
        var alias = UtilityTest.ALIAS_ECDSA_SECP256K1;
        var recordItem = recordItemBuilder
                .cryptoCreate()
                .record(r -> r.setAlias(DomainUtils.fromBytes(alias)))
                .build();
        var transaction = transaction(recordItem);
        var accountId =
                EntityId.of(recordItem.getTransactionRecord().getReceipt().getAccountID());

        transactionHandler.updateTransaction(transaction, recordItem);

        assertEntity(accountId, recordItem.getConsensusTimestamp()).returns(alias, Entity::getAlias);
        assertThat(recordItem.getEntityTransactions())
                .containsExactlyInAnyOrderEntriesOf(getExpectedEntityTransactions(recordItem, transaction));
    }

    @Test
    void updateAliasEvmKey() {
        var alias = UtilityTest.ALIAS_ECDSA_SECP256K1;
        var evmAddress = UtilityTest.EVM_ADDRESS;
        var recordItem = recordItemBuilder
                .cryptoCreate()
                .record(r -> r.setEvmAddress(DomainUtils.fromBytes(evmAddress)))
                .transactionBody(t -> t.setAlias(DomainUtils.fromBytes(alias)).setKey(Key.getDefaultInstance()))
                .build();
        var transaction = transaction(recordItem);
        var accountId =
                EntityId.of(recordItem.getTransactionRecord().getReceipt().getAccountID());

        transactionHandler.updateTransaction(transaction, recordItem);

        assertEntity(accountId, recordItem.getConsensusTimestamp())
                .returns(alias, Entity::getAlias)
                .returns(alias, Entity::getKey)
                .returns(evmAddress, Entity::getEvmAddress);
        assertThat(recordItem.getEntityTransactions())
                .containsExactlyInAnyOrderEntriesOf(getExpectedEntityTransactions(recordItem, transaction));
    }

    @ParameterizedTest
    @MethodSource("provideAlias")
    void updateKeyFromTransactionBody(ByteString alias, Key key, byte[] expectedKey) {
        var recordItem = recordItemBuilder
                .cryptoCreate()
                .record(r -> r.setAlias(alias))
                .transactionBody(t -> t.setKey(key))
                .build();
        var accountId =
                EntityId.of(recordItem.getTransactionRecord().getReceipt().getAccountID());

        transactionHandler.updateTransaction(transaction(recordItem), recordItem);

        assertEntity(accountId, recordItem.getConsensusTimestamp()).returns(expectedKey, Entity::getKey);
    }

    @ParameterizedTest
    @MethodSource("provideEvmAddresses")
    void updateEvmAddress(ByteString recordEvmAddress, byte[] expected) {
        var recordItem = recordItemBuilder
                .cryptoCreate()
                .record(r -> r.setEvmAddress(recordEvmAddress))
                .transactionBody(t -> t.setAlias(ByteString.copyFrom(UtilityTest.ALIAS_ECDSA_SECP256K1)))
                .build();
        var accountId =
                EntityId.of(recordItem.getTransactionRecord().getReceipt().getAccountID());

        transactionHandler.updateTransaction(transaction(recordItem), recordItem);

        assertEntity(accountId, recordItem.getConsensusTimestamp()).returns(expected, Entity::getEvmAddress);
    }

    @Test
    void evmHookHandlerCalledWithHookCreationDetails() {
        // given
        var recordItem = recordItemBuilder.cryptoCreate().build();
        var transaction = transaction(recordItem);
        var accountId =
                EntityId.of(recordItem.getTransactionRecord().getReceipt().getAccountID());
        var transactionBody = recordItem.getTransactionBody().getCryptoCreateAccount();

        // when
        transactionHandler.updateTransaction(transaction, recordItem);

        // then
        verify(evmHookHandler)
                .process(
                        eq(recordItem),
                        eq(accountId.getId()),
                        eq(transactionBody.getHookCreationDetailsList()),
                        eq(List.of()));

        // Verify entity was created
        assertEntity(accountId, recordItem.getConsensusTimestamp());
        assertThat(recordItem.getEntityTransactions())
                .containsExactlyInAnyOrderEntriesOf(getExpectedEntityTransactions(recordItem, transaction));
    }

    @Test
    void evmHookHandlerNotCalledWhenNoHooks() {
        // given
        var recordItem = recordItemBuilder
                .cryptoCreate()
                .transactionBody(b -> b.clearHookCreationDetails())
                .build();
        var transaction = transaction(recordItem);

        // when
        transactionHandler.updateTransaction(transaction, recordItem);

        // then
        verify(evmHookHandler).process(eq(recordItem), anyLong(), eq(List.of()), eq(List.of()));
    }

    private ObjectAssert<Entity> assertEntity(EntityId accountId, long timestamp) {
        verify(entityListener).onEntity(entityCaptor.capture());
        return assertThat(entityCaptor.getValue())
                .isNotNull()
                .satisfies(c -> assertThat(c.getAutoRenewPeriod()).isPositive())
                .returns(timestamp, Entity::getCreatedTimestamp)
                .returns(0L, Entity::getBalance)
                .returns(timestamp, Entity::getBalanceTimestamp)
                .returns(false, Entity::getDeleted)
                .returns(null, Entity::getExpirationTimestamp)
                .returns(accountId.getId(), Entity::getId)
                .satisfies(c -> assertThat(c.getMaxAutomaticTokenAssociations()).isPositive())
                .satisfies(c -> assertThat(c.getMemo()).isNotEmpty())
                .returns(accountId.getNum(), Entity::getNum)
                .satisfies(
                        c -> assertThat(EntityId.isEmpty(c.getProxyAccountId())).isFalse())
                .returns(accountId.getRealm(), Entity::getRealm)
                .returns(accountId.getShard(), Entity::getShard)
                .returns(ACCOUNT, Entity::getType)
                .returns(Range.atLeast(timestamp), Entity::getTimestampRange)
                .returns(null, Entity::getObtainerId);
    }

    @SuppressWarnings("deprecation")
    private Map<Long, EntityTransaction> getExpectedEntityTransactions(RecordItem recordItem, Transaction transaction) {
        var body = recordItem.getTransactionBody().getCryptoCreateAccount();
        return getExpectedEntityTransactions(
                recordItem, transaction, EntityId.of(body.getStakedAccountId()), EntityId.of(body.getProxyAccountID()));
    }

    private Transaction transaction(RecordItem recordItem) {
        var entityId =
                EntityId.of(recordItem.getTransactionRecord().getReceipt().getAccountID());
        var consensusTimestamp = recordItem.getConsensusTimestamp();
        return domainBuilder
                .transaction()
                .customize(t -> t.consensusTimestamp(consensusTimestamp).entityId(entityId))
                .get();
    }
}
