// SPDX-License-Identifier: Apache-2.0

package com.hedera.mirror.web3.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.common.collect.Range;
import com.hedera.mirror.common.CommonProperties;
import com.hedera.mirror.common.domain.entity.Entity;
import com.hedera.mirror.common.domain.entity.EntityHistory;
import com.hedera.mirror.web3.Web3IntegrationTest;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

@RequiredArgsConstructor
class EntityRepositoryTest extends Web3IntegrationTest {

    private static final CommonProperties COMMON_PROPERTIES = CommonProperties.getInstance();
    private static final long SHARD_ORIGINAL_VALUE = COMMON_PROPERTIES.getShard();
    private static final long REALM_ORIGINAL_VALUE = COMMON_PROPERTIES.getRealm();

    private final EntityRepository entityRepository;

    @AfterAll
    static void cleanup() {
        COMMON_PROPERTIES.setShard(SHARD_ORIGINAL_VALUE);
        COMMON_PROPERTIES.setRealm(REALM_ORIGINAL_VALUE);
    }

    @Test
    void findByIdAndDeletedIsFalseSuccessfulCall() {
        final var entity = domainBuilder.entity(-2, domainBuilder.timestamp()).persist();
        assertThat(entityRepository.findByIdAndDeletedIsFalse(-2L)).contains(entity);
    }

    @Test
    void findByIdAndDeletedIsFalseFailCall() {
        final var entity = domainBuilder.entity().persist();
        long id = entity.getId();
        assertThat(entityRepository.findByIdAndDeletedIsFalse(++id)).isEmpty();
    }

    @Test
    void findByIdAndDeletedTrueCall() {
        final var entity =
                domainBuilder.entity().customize(e -> e.deleted(true)).persist();
        assertThat(entityRepository.findByIdAndDeletedIsFalse(entity.getId())).isEmpty();
    }

    @ParameterizedTest
    @MethodSource("shardAndRealmData")
    void findByEvmAddressAndDeletedIsFalseSuccessfulCall(long shard, long realm) {
        setCommonProperties(shard, realm);
        final var entity1 = entityPersistWithShardAndRealm(shard, realm);
        final var entity2 = entityPersistWithShardAndRealm(shard, realm);
        assertThat(entityRepository.findByEvmAddressAndDeletedIsFalseAndShardAndRealm(
                        entity1.getEvmAddress(), shard, realm))
                .contains(entity1);

        // Validate entity1 is cached and entity2 can't be found since it's not cached
        entityRepository.deleteAll();
        assertThat(entityRepository.findByEvmAddressAndDeletedIsFalseAndShardAndRealm(
                        entity1.getEvmAddress(), shard, realm))
                .contains(entity1);
        assertThat(entityRepository.findByEvmAddressAndDeletedIsFalseAndShardAndRealm(
                        entity2.getEvmAddress(), shard, realm))
                .isEmpty();
    }

    @ParameterizedTest
    @MethodSource("shardAndRealmData")
    void findByEvmAddressAndDeletedIsFalseFailCall(long shard, long realm) {
        setCommonProperties(shard, realm);
        entityPersistWithShardAndRealm(shard, realm);

        assertThat(entityRepository.findByEvmAddressAndDeletedIsFalseAndShardAndRealm(new byte[32], shard, realm))
                .isEmpty();
    }

    @ParameterizedTest
    @MethodSource("shardAndRealmData")
    void findByEvmAddressAndDeletedTrueCall(long shard, long realm) {
        setCommonProperties(shard, realm);

        final var entity = entityPersistDeletedWithShardAndRealm(shard, realm);
        assertThat(entityRepository.findByEvmAddressAndDeletedIsFalseAndShardAndRealm(
                        entity.getEvmAddress(), shard, realm))
                .isEmpty();
    }

    @ParameterizedTest
    @MethodSource("shardAndRealmData")
    void findByEvmAddressAndTimestampRangeLessThanBlockTimestampAndDeletedIsFalseCall(long shard, long realm) {
        setCommonProperties(shard, realm);

        final var entity = entityPersistWithShardAndRealm(shard, realm);

        assertThat(entityRepository.findActiveByEvmAddressAndTimestampAndShardAndRealm(
                        entity.getEvmAddress(), entity.getTimestampLower() + 1, shard, realm))
                .get()
                .isEqualTo(entity);
    }

    @ParameterizedTest
    @MethodSource("shardAndRealmData")
    void findByEvmAddressAndTimestampRangeEqualToBlockTimestampAndDeletedIsFalseCall(long shard, long realm) {
        setCommonProperties(shard, realm);

        final var entity = entityPersistWithShardAndRealm(shard, realm);

        assertThat(entityRepository.findActiveByEvmAddressAndTimestampAndShardAndRealm(
                        entity.getEvmAddress(), entity.getTimestampLower(), shard, realm))
                .get()
                .isEqualTo(entity);
    }

    @ParameterizedTest
    @MethodSource("shardAndRealmData")
    void findByEvmAddressAndTimestampRangeGreaterThanBlockTimestampAndDeletedIsFalseCall(long shard, long realm) {
        setCommonProperties(shard, realm);

        final var entity = entityPersistWithShardAndRealm(shard, realm);

        assertThat(entityRepository.findActiveByEvmAddressAndTimestampAndShardAndRealm(
                        entity.getEvmAddress(), entity.getTimestampLower() - 1, shard, realm))
                .isEmpty();
    }

    @ParameterizedTest
    @MethodSource("shardAndRealmData")
    void findByEvmAddressAndTimestampRangeLessThanBlockTimestampAndDeletedTrueCall(long shard, long realm) {
        setCommonProperties(shard, realm);

        final var entity = entityPersistDeletedWithShardAndRealm(shard, realm);

        assertThat(entityRepository.findActiveByEvmAddressAndTimestampAndShardAndRealm(
                        entity.getEvmAddress(), entity.getTimestampLower() + 1, shard, realm))
                .isEmpty();
    }

    @ParameterizedTest
    @MethodSource("shardAndRealmData")
    void findHistoricalEntityByEvmAddressAndTimestampRangeAndDeletedTrueCall(long shard, long realm) {
        setCommonProperties(shard, realm);

        final var entityHistory = entityHistoryPersistDeletedWithShardAndRealm(shard, realm);

        assertThat(entityRepository.findActiveByEvmAddressAndTimestampAndShardAndRealm(
                        entityHistory.getEvmAddress(), entityHistory.getTimestampLower() - 1, shard, realm))
                .isEmpty();
    }

    @ParameterizedTest
    @MethodSource("shardAndRealmData")
    void findHistoricalEntityByEvmAddressAndTimestampRangeGreaterThanBlockTimestampAndDeletedIsFalse(
            long shard, long realm) {
        setCommonProperties(shard, realm);

        final var entityHistory = entityHistoryPersistWithShardAndRealm(shard, realm);
        final var entity = entityPersistWithHistoryIdShardAndRealm(entityHistory.getId(), shard, realm);

        assertThat(entityRepository.findActiveByEvmAddressAndTimestampAndShardAndRealm(
                        entity.getEvmAddress(), entityHistory.getTimestampLower() - 1, shard, realm))
                .isEmpty();
    }

    @ParameterizedTest
    @MethodSource("shardAndRealmData")
    void findEntityByEvmAddressAndTimestampRangeEqualToBlockTimestampAndDeletedIsFalse(long shard, long realm) {
        setCommonProperties(shard, realm);

        final var entityHistory = entityHistoryPersistWithShardAndRealm(shard, realm);

        // Both entity and entity history will be queried in union but entity record is the latest valid
        final var entity = entityPersistWithHistoryIdShardAndRealm(entityHistory.getId(), shard, realm);

        assertThat(entityRepository.findActiveByEvmAddressAndTimestampAndShardAndRealm(
                        entity.getEvmAddress(), entity.getTimestampLower(), shard, realm))
                .get()
                .isEqualTo(entity);
    }

    @ParameterizedTest
    @MethodSource("shardAndRealmData")
    void findHistoricalEntityByEvmAddressAndTimestampRangeEqualToBlockTimestampAndDeletedIsFalse(
            long shard, long realm) {
        setCommonProperties(shard, realm);

        final var entity = entityPersistWithShardAndRealm(shard, realm);
        // Both entity and entity history will be queried in union but entity history record is the latest valid
        final var entityHistory = entityHistoryPersistWithEntityIdShardAndRealm(entity.getId(), shard, realm);

        assertThat(entityRepository.findActiveByEvmAddressAndTimestampAndShardAndRealm(
                        entity.getEvmAddress(), entityHistory.getTimestampLower(), shard, realm))
                .get()
                .usingRecursiveComparison()
                .isEqualTo(entityHistory);
    }

    @Test
    void findByIdAndTimestampRangeLessThanBlockTimestampAndDeletedIsFalseCall() {
        final var entity = domainBuilder.entity().persist();

        assertThat(entityRepository.findActiveByIdAndTimestamp(entity.getId(), entity.getTimestampLower() + 1))
                .get()
                .isEqualTo(entity);
    }

    @Test
    void findByIdAndTimestampRangeEqualToBlockTimestampAndDeletedIsFalseCall() {
        final var entity = domainBuilder.entity().persist();

        assertThat(entityRepository.findActiveByIdAndTimestamp(entity.getId(), entity.getTimestampLower()))
                .get()
                .isEqualTo(entity);
    }

    @Test
    void findByIdAndTimestampRangeGreaterThanBlockTimestampAndDeletedIsFalseCall() {
        final var entity = domainBuilder.entity().persist();

        assertThat(entityRepository.findActiveByIdAndTimestamp(entity.getId(), entity.getTimestampLower() - 1))
                .isEmpty();
    }

    @Test
    void findByIdAndTimestampRangeAndDeletedTrueCall() {
        final var entity =
                domainBuilder.entity().customize(e -> e.deleted(true)).persist();

        assertThat(entityRepository.findActiveByIdAndTimestamp(entity.getId(), entity.getTimestampLower()))
                .isEmpty();
    }

    @Test
    void findHistoricalEntityByIdAndTimestampRangeLessThanBlockTimestampAndDeletedIsFalseCall() {
        final var entityHistory = domainBuilder.entityHistory().persist();

        // persist older entity in entity history
        domainBuilder
                .entityHistory()
                .customize(e -> e.timestampRange(
                        Range.closedOpen(entityHistory.getTimestampLower() - 10, entityHistory.getTimestampLower())))
                .persist();

        // verify that we get the latest valid entity from entity history
        assertThat(entityRepository.findActiveByIdAndTimestamp(
                        entityHistory.getId(), entityHistory.getTimestampLower() + 1))
                .get()
                .usingRecursiveComparison()
                .isEqualTo(entityHistory);
    }

    @Test
    void findHistoricalEntityByIdAndTimestampRangeEqualToBlockTimestampAndDeletedIsFalseCall() {
        final var entityHistory = domainBuilder.entityHistory().persist();

        assertThat(entityRepository.findActiveByIdAndTimestamp(
                        entityHistory.getId(), entityHistory.getTimestampLower()))
                .get()
                .usingRecursiveComparison()
                .isEqualTo(entityHistory);
    }

    @Test
    void findHistoricalEntityByIdAndTimestampRangeGreaterThanBlockTimestampAndDeletedIsFalseCall() {
        final var entityHistory = domainBuilder.entityHistory().persist();

        assertThat(entityRepository.findActiveByIdAndTimestamp(
                        entityHistory.getId(), entityHistory.getTimestampLower() - 1))
                .isEmpty();
    }

    @Test
    void findHistoricalEntityByIdAndTimestampRangeAndDeletedTrueCall() {
        final var entityHistory =
                domainBuilder.entityHistory().customize(e -> e.deleted(true)).persist();

        assertThat(entityRepository.findActiveByIdAndTimestamp(
                        entityHistory.getId(), entityHistory.getCreatedTimestamp()))
                .isEmpty();
    }

    @ParameterizedTest
    @MethodSource("shardAndRealmData")
    void findByEvmAddressOrAliasSuccessWithAlias(long shard, long realm) {
        setCommonProperties(shard, realm);
        final var alias = domainBuilder.key();
        final var entity = domainBuilder
                .entity()
                .customize(e -> e.alias(alias).shard(shard).realm(realm))
                .persist();
        assertThat(entityRepository.findByEvmAddressOrAliasAndShardAndRealm(alias, shard, realm))
                .get()
                .isEqualTo(entity);
    }

    @ParameterizedTest
    @MethodSource("shardAndRealmData")
    void findByEvmAddressOrAliasSuccessWithEvmAddress(long shard, long realm) {
        setCommonProperties(shard, realm);
        final var evmAddress = domainBuilder.evmAddress();
        final var entity = domainBuilder
                .entity()
                .customize(e -> e.evmAddress(evmAddress).shard(shard).realm(realm))
                .persist();
        assertThat(entityRepository.findByEvmAddressOrAliasAndShardAndRealm(evmAddress, shard, realm))
                .get()
                .isEqualTo(entity);
    }

    @ParameterizedTest
    @MethodSource("shardAndRealmData")
    void findByEvmAddressOrAliasReturnsEmptyWhenDeletedIsTrueWithAlias(long shard, long realm) {
        setCommonProperties(shard, realm);
        final var alias = domainBuilder.key();
        domainBuilder
                .entity()
                .customize(e -> e.alias(alias).deleted(true).shard(shard).realm(realm))
                .persist();
        assertThat(entityRepository.findByEvmAddressOrAliasAndShardAndRealm(alias, shard, realm))
                .isEmpty();
    }

    @ParameterizedTest
    @MethodSource("shardAndRealmData")
    void findByEvmAddressOrAliasReturnsEmptyWhenDeletedIsTrueWithEvmAddress(long shard, long realm) {
        setCommonProperties(shard, realm);
        final var evmAddress = domainBuilder.evmAddress();
        domainBuilder
                .entity()
                .customize(
                        e -> e.evmAddress(evmAddress).deleted(true).shard(shard).realm(realm))
                .persist();
        assertThat(entityRepository.findByEvmAddressOrAliasAndShardAndRealm(evmAddress, shard, realm))
                .isEmpty();
    }

    @ParameterizedTest
    @MethodSource("shardAndRealmData")
    void findByEvmAddressOrAliasAndTimestampRangeLessThanBlockTimestampAndDeletedIsFalseCallWithAlias(
            long shard, long realm) {
        setCommonProperties(shard, realm);
        final var entity = entityPersistWithShardAndRealm(shard, realm);

        assertThat(entityRepository.findActiveByEvmAddressOrAliasAndTimestampAndShardAndRealm(
                        entity.getAlias(), entity.getTimestampLower() + 1, shard, realm))
                .get()
                .isEqualTo(entity);
    }

    @ParameterizedTest
    @MethodSource("shardAndRealmData")
    void findByEvmAddressOrAliasAndTimestampRangeLessThanBlockTimestampAndDeletedIsFalseCallWithEvmAddress(
            long shard, long realm) {
        setCommonProperties(shard, realm);
        final var entity = entityPersistWithShardAndRealm(shard, realm);

        assertThat(entityRepository.findActiveByEvmAddressOrAliasAndTimestampAndShardAndRealm(
                        entity.getEvmAddress(), entity.getTimestampLower() + 1, shard, realm))
                .get()
                .isEqualTo(entity);
    }

    @ParameterizedTest
    @MethodSource("shardAndRealmData")
    void findByEvmAddressOrAliasAndTimestampRangeEqualToBlockTimestampAndDeletedIsFalseCallWithAlias(
            long shard, long realm) {
        setCommonProperties(shard, realm);
        final var entity = entityPersistWithShardAndRealm(shard, realm);

        assertThat(entityRepository.findActiveByEvmAddressOrAliasAndTimestampAndShardAndRealm(
                        entity.getAlias(), entity.getTimestampLower(), shard, realm))
                .get()
                .isEqualTo(entity);
    }

    @ParameterizedTest
    @MethodSource("shardAndRealmData")
    void findByEvmAddressOrAliasAndTimestampRangeEqualToBlockTimestampAndDeletedIsFalseCallWithEvmAddress(
            long shard, long realm) {
        setCommonProperties(shard, realm);
        final var entity = entityPersistWithShardAndRealm(shard, realm);

        assertThat(entityRepository.findActiveByEvmAddressOrAliasAndTimestampAndShardAndRealm(
                        entity.getEvmAddress(), entity.getTimestampLower(), shard, realm))
                .get()
                .isEqualTo(entity);
    }

    @ParameterizedTest
    @MethodSource("shardAndRealmData")
    void findByEvmAddressOrAliasAndTimestampRangeGreaterThanBlockTimestampAndDeletedIsFalseCallWithAlias(
            long shard, long realm) {
        setCommonProperties(shard, realm);
        final var entity = entityPersistWithShardAndRealm(shard, realm);

        assertThat(entityRepository.findActiveByEvmAddressOrAliasAndTimestampAndShardAndRealm(
                        entity.getAlias(), entity.getTimestampLower() - 1, shard, realm))
                .isEmpty();
    }

    @ParameterizedTest
    @MethodSource("shardAndRealmData")
    void findByEvmAddressOrAliasAndTimestampRangeGreaterThanBlockTimestampAndDeletedIsFalseCallWithEvmAddress(
            long shard, long realm) {
        setCommonProperties(shard, realm);
        final var entity = entityPersistWithShardAndRealm(shard, realm);

        assertThat(entityRepository.findActiveByEvmAddressOrAliasAndTimestampAndShardAndRealm(
                        entity.getEvmAddress(), entity.getTimestampLower() - 1, shard, realm))
                .isEmpty();
    }

    @ParameterizedTest
    @MethodSource("shardAndRealmData")
    void findByEvmAddressOrAliasAndTimestampRangeLessThanBlockTimestampAndDeletedTrueCallWithAlias(
            long shard, long realm) {
        setCommonProperties(shard, realm);
        final var entity = entityPersistDeletedWithShardAndRealm(shard, realm);

        assertThat(entityRepository.findActiveByEvmAddressOrAliasAndTimestampAndShardAndRealm(
                        entity.getAlias(), entity.getTimestampLower() + 1, shard, realm))
                .isEmpty();
    }

    @ParameterizedTest
    @MethodSource("shardAndRealmData")
    void findByEvmAddressOrAliasAndTimestampRangeLessThanBlockTimestampAndDeletedTrueCallWithEvmAddress(
            long shard, long realm) {
        setCommonProperties(shard, realm);
        final var entity = entityPersistDeletedWithShardAndRealm(shard, realm);

        assertThat(entityRepository.findActiveByEvmAddressOrAliasAndTimestampAndShardAndRealm(
                        entity.getEvmAddress(), entity.getTimestampLower() + 1, shard, realm))
                .isEmpty();
    }

    @ParameterizedTest
    @MethodSource("shardAndRealmData")
    void findHistoricalEntityByEvmAddressOrAliasAndTimestampRangeAndDeletedTrueCallWithAlias(long shard, long realm) {
        setCommonProperties(shard, realm);
        final var entityHistory = entityHistoryPersistDeletedWithShardAndRealm(shard, realm);

        assertThat(entityRepository.findActiveByEvmAddressOrAliasAndTimestampAndShardAndRealm(
                        entityHistory.getAlias(), entityHistory.getTimestampLower() - 1, shard, realm))
                .isEmpty();
    }

    @ParameterizedTest
    @MethodSource("shardAndRealmData")
    void findHistoricalEntityByEvmAddressOrAliasAndTimestampRangeAndDeletedTrueCallWithEvmAddress(
            long shard, long realm) {
        setCommonProperties(shard, realm);
        final var entityHistory = entityHistoryPersistDeletedWithShardAndRealm(shard, realm);

        assertThat(entityRepository.findActiveByEvmAddressOrAliasAndTimestampAndShardAndRealm(
                        entityHistory.getEvmAddress(), entityHistory.getTimestampLower() - 1, shard, realm))
                .isEmpty();
    }

    @ParameterizedTest
    @MethodSource("shardAndRealmData")
    void findHistoricalEntityByEvmAddressOrAliasAndTimestampRangeGreaterThanBlockTimestampAndDeletedIsFalseWithAlias(
            long shard, long realm) {
        setCommonProperties(shard, realm);
        final var entityHistory = entityHistoryPersistWithShardAndRealm(shard, realm);
        final var entity = entityPersistWithHistoryIdShardAndRealm(entityHistory.getId(), shard, realm);

        assertThat(entityRepository.findActiveByEvmAddressOrAliasAndTimestampAndShardAndRealm(
                        entity.getAlias(), entityHistory.getTimestampLower() - 1, shard, realm))
                .isEmpty();
    }

    @ParameterizedTest
    @MethodSource("shardAndRealmData")
    void
            findHistoricalEntityByEvmAddressOrAliasAndTimestampRangeGreaterThanBlockTimestampAndDeletedIsFalseWithEvmAddress(
                    long shard, long realm) {
        setCommonProperties(shard, realm);
        final var entityHistory = entityHistoryPersistWithShardAndRealm(shard, realm);
        final var entity = entityPersistWithHistoryIdShardAndRealm(entityHistory.getId(), shard, realm);

        assertThat(entityRepository.findActiveByEvmAddressOrAliasAndTimestampAndShardAndRealm(
                        entity.getEvmAddress(), entityHistory.getTimestampLower() - 1, shard, realm))
                .isEmpty();
    }

    @ParameterizedTest
    @MethodSource("shardAndRealmData")
    void findEntityByEvmAddressOrAliasAndTimestampRangeEqualToBlockTimestampAndDeletedIsFalseWithAlias(
            long shard, long realm) {
        setCommonProperties(shard, realm);
        final var entityHistory = entityHistoryPersistWithShardAndRealm(shard, realm);

        // Both entity and entity history will be queried in union but entity record is the latest valid
        final var entity = entityPersistWithHistoryIdShardAndRealm(entityHistory.getId(), shard, realm);

        assertThat(entityRepository.findActiveByEvmAddressOrAliasAndTimestampAndShardAndRealm(
                        entity.getAlias(), entity.getTimestampLower(), shard, realm))
                .get()
                .isEqualTo(entity);
    }

    @ParameterizedTest
    @MethodSource("shardAndRealmData")
    void findEntityByEvmAddressOrAliasAndTimestampRangeEqualToBlockTimestampAndDeletedIsFalseWithEvmAddress(
            long shard, long realm) {
        setCommonProperties(shard, realm);
        final var entityHistory = entityHistoryPersistWithShardAndRealm(shard, realm);

        // Both entity and entity history will be queried in union but entity record is the latest valid
        final var entity = entityPersistWithHistoryIdShardAndRealm(entityHistory.getId(), shard, realm);

        assertThat(entityRepository.findActiveByEvmAddressOrAliasAndTimestampAndShardAndRealm(
                        entity.getEvmAddress(), entity.getTimestampLower(), shard, realm))
                .get()
                .isEqualTo(entity);
    }

    @ParameterizedTest
    @MethodSource("shardAndRealmData")
    void findHistoricalEntityByEvmAddressOrAliasAndTimestampRangeEqualToBlockTimestampAndDeletedIsFalseWithAlias(
            long shard, long realm) {
        setCommonProperties(shard, realm);
        final var entity = entityPersistWithShardAndRealm(shard, realm);
        // Both entity and entity history will be queried in union but entity history record is the latest valid
        final var entityHistory = entityHistoryPersistWithEntityIdShardAndRealm(entity.getId(), shard, realm);

        assertThat(entityRepository.findActiveByEvmAddressOrAliasAndTimestampAndShardAndRealm(
                        entity.getAlias(), entityHistory.getTimestampLower(), shard, realm))
                .get()
                .usingRecursiveComparison()
                .isEqualTo(entityHistory);
    }

    @ParameterizedTest
    @MethodSource("shardAndRealmData")
    void findHistoricalEntityByEvmAddressOrAliasAndTimestampRangeEqualToBlockTimestampAndDeletedIsFalseWithEvmAddress(
            long shard, long realm) {
        setCommonProperties(shard, realm);
        final var entity = entityPersistWithShardAndRealm(shard, realm);
        // Both entity and entity history will be queried in union but entity history record is the latest valid
        final var entityHistory = entityHistoryPersistWithEntityIdShardAndRealm(entity.getId(), shard, realm);

        assertThat(entityRepository.findActiveByEvmAddressOrAliasAndTimestampAndShardAndRealm(
                        entity.getEvmAddress(), entityHistory.getTimestampLower(), shard, realm))
                .get()
                .usingRecursiveComparison()
                .isEqualTo(entityHistory);
    }

    @ParameterizedTest
    @MethodSource("shardAndRealmData")
    void findMaxIdEmptyDb(long shard, long realm) {
        setCommonProperties(shard, realm);
        assertThat(entityRepository.findMaxId(shard, realm)).isNull();
    }

    @ParameterizedTest
    @MethodSource("shardAndRealmData")
    void findMaxId(long shard, long realm) {
        setCommonProperties(shard, realm);
        final long lastId = 1111;
        domainBuilder
                .entity()
                .customize(e -> e.id(lastId).shard(shard).realm(realm))
                .persist();
        assertThat(entityRepository.findMaxId(shard, realm)).isEqualTo(lastId);
    }

    // Method that provides the test data
    private static Stream<Arguments> shardAndRealmData() {
        return Stream.of(Arguments.of(0L, 0L), Arguments.of(1L, 2L));
    }

    private void setCommonProperties(long shard, long realm) {
        COMMON_PROPERTIES.setShard(shard);
        COMMON_PROPERTIES.setRealm(realm);
    }

    private Entity entityPersistDeletedWithShardAndRealm(long shard, long realm) {
        return domainBuilder
                .entity()
                .customize(e -> e.deleted(true).shard(shard).realm(realm))
                .persist();
    }

    private Entity entityPersistWithShardAndRealm(long shard, long realm) {
        return domainBuilder
                .entity()
                .customize(e -> e.shard(shard).realm(realm))
                .persist();
    }

    private Entity entityPersistWithHistoryIdShardAndRealm(long entityHistoryId, long shard, long realm) {
        return domainBuilder
                .entity()
                .customize(e -> e.id(entityHistoryId).shard(shard).realm(realm))
                .persist();
    }

    private EntityHistory entityHistoryPersistWithShardAndRealm(long shard, long realm) {
        return domainBuilder
                .entityHistory()
                .customize(e -> e.shard(shard).realm(realm))
                .persist();
    }

    private EntityHistory entityHistoryPersistDeletedWithShardAndRealm(long shard, long realm) {
        return domainBuilder
                .entityHistory()
                .customize(e -> e.deleted(true).shard(shard).realm(realm))
                .persist();
    }

    private EntityHistory entityHistoryPersistWithEntityIdShardAndRealm(long entityId, long shard, long realm) {
        return domainBuilder
                .entityHistory()
                .customize(e -> e.id(entityId).shard(shard).realm(realm))
                .persist();
    }
}
