// SPDX-License-Identifier: Apache-2.0

package com.hedera.mirror.web3.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.common.collect.Range;
import com.hedera.mirror.common.CommonProperties;
import com.hedera.mirror.web3.Web3IntegrationTest;
import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.Test;

@RequiredArgsConstructor
class EntityRepositoryTest extends Web3IntegrationTest {

    private static final CommonProperties COMMON_PROPERTIES = CommonProperties.getInstance();
    private static final long SHARD = COMMON_PROPERTIES.getShard();
    private static final long REALM = COMMON_PROPERTIES.getRealm();

    private final EntityRepository entityRepository;

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

    @Test
    void findByEvmAddressAndDeletedIsFalseSuccessfulCall() {
        final var entity1 = domainBuilder.entity().persist();
        final var entity2 = domainBuilder.entity().persist();
        assertThat(entityRepository.findByEvmAddressAndDeletedIsFalseAndShardAndRealm(
                        entity1.getEvmAddress(), SHARD, REALM))
                .contains(entity1);

        // Validate entity1 is cached and entity2 can't be found since it's not cached
        entityRepository.deleteAll();
        assertThat(entityRepository.findByEvmAddressAndDeletedIsFalseAndShardAndRealm(
                        entity1.getEvmAddress(), SHARD, REALM))
                .contains(entity1);
        assertThat(entityRepository.findByEvmAddressAndDeletedIsFalseAndShardAndRealm(
                        entity2.getEvmAddress(), SHARD, REALM))
                .isEmpty();
    }

    @Test
    void findByEvmAddressAndDeletedIsFalseFailCall() {
        domainBuilder.entity().persist();
        assertThat(entityRepository.findByEvmAddressAndDeletedIsFalseAndShardAndRealm(new byte[32], SHARD, REALM))
                .isEmpty();
    }

    @Test
    void findByEvmAddressAndDeletedTrueCall() {
        final var entity =
                domainBuilder.entity().customize(e -> e.deleted(true)).persist();
        assertThat(entityRepository.findByEvmAddressAndDeletedIsFalseAndShardAndRealm(
                        entity.getEvmAddress(), SHARD, REALM))
                .isEmpty();
    }

    @Test
    void findByEvmAddressAndTimestampRangeLessThanBlockTimestampAndDeletedIsFalseCall() {
        final var entity = domainBuilder.entity().persist();

        assertThat(entityRepository.findActiveByEvmAddressAndTimestampAndShardAndRealm(
                        entity.getEvmAddress(), entity.getTimestampLower() + 1, SHARD, REALM))
                .get()
                .isEqualTo(entity);
    }

    @Test
    void findByEvmAddressAndTimestampRangeEqualToBlockTimestampAndDeletedIsFalseCall() {
        final var entity = domainBuilder.entity().persist();

        assertThat(entityRepository.findActiveByEvmAddressAndTimestampAndShardAndRealm(
                        entity.getEvmAddress(), entity.getTimestampLower(), SHARD, REALM))
                .get()
                .isEqualTo(entity);
    }

    @Test
    void findByEvmAddressAndTimestampRangeGreaterThanBlockTimestampAndDeletedIsFalseCall() {
        final var entity = domainBuilder.entity().persist();

        assertThat(entityRepository.findActiveByEvmAddressAndTimestampAndShardAndRealm(
                        entity.getEvmAddress(), entity.getTimestampLower() - 1, SHARD, REALM))
                .isEmpty();
    }

    @Test
    void findByEvmAddressAndTimestampRangeLessThanBlockTimestampAndDeletedTrueCall() {
        final var entity =
                domainBuilder.entity().customize(e -> e.deleted(true)).persist();

        assertThat(entityRepository.findActiveByEvmAddressAndTimestampAndShardAndRealm(
                        entity.getEvmAddress(), entity.getTimestampLower() + 1, SHARD, REALM))
                .isEmpty();
    }

    @Test
    void findHistoricalEntityByEvmAddressAndTimestampRangeAndDeletedTrueCall() {
        final var entityHistory =
                domainBuilder.entityHistory().customize(e -> e.deleted(true)).persist();

        assertThat(entityRepository.findActiveByEvmAddressAndTimestampAndShardAndRealm(
                        entityHistory.getEvmAddress(), entityHistory.getTimestampLower() - 1, SHARD, REALM))
                .isEmpty();
    }

    @Test
    void findHistoricalEntityByEvmAddressAndTimestampRangeGreaterThanBlockTimestampAndDeletedIsFalse() {
        final var entityHistory = domainBuilder.entityHistory().persist();
        final var entity = domainBuilder
                .entity()
                .customize(e -> e.id(entityHistory.getId()))
                .persist();

        assertThat(entityRepository.findActiveByEvmAddressAndTimestampAndShardAndRealm(
                        entity.getEvmAddress(), entityHistory.getTimestampLower() - 1, SHARD, REALM))
                .isEmpty();
    }

    @Test
    void findEntityByEvmAddressAndTimestampRangeEqualToBlockTimestampAndDeletedIsFalse() {
        final var entityHistory = domainBuilder.entityHistory().persist();

        // Both entity and entity history will be queried in union but entity record is the latest valid
        final var entity = domainBuilder
                .entity()
                .customize(e -> e.id(entityHistory.getId()))
                .persist();

        assertThat(entityRepository.findActiveByEvmAddressAndTimestampAndShardAndRealm(
                        entity.getEvmAddress(), entity.getTimestampLower(), SHARD, REALM))
                .get()
                .isEqualTo(entity);
    }

    @Test
    void findHistoricalEntityByEvmAddressAndTimestampRangeEqualToBlockTimestampAndDeletedIsFalse() {
        final var entity = domainBuilder.entity().persist();
        // Both entity and entity history will be queried in union but entity history record is the latest valid
        final var entityHistory = domainBuilder
                .entityHistory()
                .customize(e -> e.id(entity.getId()))
                .persist();

        assertThat(entityRepository.findActiveByEvmAddressAndTimestampAndShardAndRealm(
                        entity.getEvmAddress(), entityHistory.getTimestampLower(), SHARD, REALM))
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

    @Test
    void findByEvmAddressOrAliasSuccessWithAlias() {
        final var alias = domainBuilder.key();
        final var entity = domainBuilder
                .entity()
                .customize(e -> e.alias(alias))
                .persist();
        assertThat(entityRepository.findByEvmAddressOrAliasAndShardAndRealm(alias, SHARD, REALM))
                .get()
                .isEqualTo(entity);
    }

    @Test
    void findByEvmAddressOrAliasSuccessWithEvmAddress() {
        final var evmAddress = domainBuilder.evmAddress();
        final var entity = domainBuilder
                .entity()
                .customize(e -> e.evmAddress(evmAddress))
                .persist();
        assertThat(entityRepository.findByEvmAddressOrAliasAndShardAndRealm(evmAddress, SHARD, REALM))
                .get()
                .isEqualTo(entity);
    }

    @Test
    void findByEvmAddressOrAliasReturnsEmptyWhenDeletedIsTrueWithAlias() {
        final var alias = domainBuilder.key();
        domainBuilder.entity().customize(e -> e.alias(alias).deleted(true)).persist();
        assertThat(entityRepository.findByEvmAddressOrAliasAndShardAndRealm(alias, SHARD, REALM))
                .isEmpty();
    }

    @Test
    void findByEvmAddressOrAliasReturnsEmptyWhenDeletedIsTrueWithEvmAddress() {
        final var evmAddress = domainBuilder.evmAddress();
        domainBuilder
                .entity()
                .customize(e -> e.evmAddress(evmAddress).deleted(true))
                .persist();
        assertThat(entityRepository.findByEvmAddressOrAliasAndShardAndRealm(evmAddress, SHARD, REALM))
                .isEmpty();
    }

    @Test
    void findByEvmAddressOrAliasAndTimestampRangeLessThanBlockTimestampAndDeletedIsFalseCallWithAlias() {
        final var entity = domainBuilder.entity().persist();

        assertThat(entityRepository.findActiveByEvmAddressOrAliasAndTimestampAndShardAndRealm(
                        entity.getAlias(), entity.getTimestampLower() + 1, SHARD, REALM))
                .get()
                .isEqualTo(entity);
    }

    @Test
    void findByEvmAddressOrAliasAndTimestampRangeLessThanBlockTimestampAndDeletedIsFalseCallWithEvmAddress() {
        final var entity = domainBuilder.entity().persist();

        assertThat(entityRepository.findActiveByEvmAddressOrAliasAndTimestampAndShardAndRealm(
                        entity.getEvmAddress(), entity.getTimestampLower() + 1, SHARD, REALM))
                .get()
                .isEqualTo(entity);
    }

    @Test
    void findByEvmAddressOrAliasAndTimestampRangeEqualToBlockTimestampAndDeletedIsFalseCallWithAlias() {
        final var entity = domainBuilder.entity().persist();

        assertThat(entityRepository.findActiveByEvmAddressOrAliasAndTimestampAndShardAndRealm(
                        entity.getAlias(), entity.getTimestampLower(), SHARD, REALM))
                .get()
                .isEqualTo(entity);
    }

    @Test
    void findByEvmAddressOrAliasAndTimestampRangeEqualToBlockTimestampAndDeletedIsFalseCallWithEvmAddress() {
        final var entity = domainBuilder.entity().persist();

        assertThat(entityRepository.findActiveByEvmAddressOrAliasAndTimestampAndShardAndRealm(
                        entity.getEvmAddress(), entity.getTimestampLower(), SHARD, REALM))
                .get()
                .isEqualTo(entity);
    }

    @Test
    void findByEvmAddressOrAliasAndTimestampRangeGreaterThanBlockTimestampAndDeletedIsFalseCallWithAlias() {
        final var entity = domainBuilder.entity().persist();

        assertThat(entityRepository.findActiveByEvmAddressOrAliasAndTimestampAndShardAndRealm(
                        entity.getAlias(), entity.getTimestampLower() - 1, SHARD, REALM))
                .isEmpty();
    }

    @Test
    void findByEvmAddressOrAliasAndTimestampRangeGreaterThanBlockTimestampAndDeletedIsFalseCallWithEvmAddress() {
        final var entity = domainBuilder.entity().persist();

        assertThat(entityRepository.findActiveByEvmAddressOrAliasAndTimestampAndShardAndRealm(
                        entity.getEvmAddress(), entity.getTimestampLower() - 1, SHARD, REALM))
                .isEmpty();
    }

    @Test
    void findByEvmAddressOrAliasAndTimestampRangeLessThanBlockTimestampAndDeletedTrueCallWithAlias() {
        final var entity =
                domainBuilder.entity().customize(e -> e.deleted(true)).persist();

        assertThat(entityRepository.findActiveByEvmAddressOrAliasAndTimestampAndShardAndRealm(
                        entity.getAlias(), entity.getTimestampLower() + 1, SHARD, REALM))
                .isEmpty();
    }

    @Test
    void findByEvmAddressOrAliasAndTimestampRangeLessThanBlockTimestampAndDeletedTrueCallWithEvmAddress() {
        final var entity =
                domainBuilder.entity().customize(e -> e.deleted(true)).persist();

        assertThat(entityRepository.findActiveByEvmAddressOrAliasAndTimestampAndShardAndRealm(
                        entity.getEvmAddress(), entity.getTimestampLower() + 1, SHARD, REALM))
                .isEmpty();
    }

    @Test
    void findHistoricalEntityByEvmAddressOrAliasAndTimestampRangeAndDeletedTrueCallWithAlias() {
        final var entityHistory =
                domainBuilder.entityHistory().customize(e -> e.deleted(true)).persist();

        assertThat(entityRepository.findActiveByEvmAddressOrAliasAndTimestampAndShardAndRealm(
                        entityHistory.getAlias(), entityHistory.getTimestampLower() - 1, SHARD, REALM))
                .isEmpty();
    }

    @Test
    void findHistoricalEntityByEvmAddressOrAliasAndTimestampRangeAndDeletedTrueCallWithEvmAddress() {
        final var entityHistory =
                domainBuilder.entityHistory().customize(e -> e.deleted(true)).persist();

        assertThat(entityRepository.findActiveByEvmAddressOrAliasAndTimestampAndShardAndRealm(
                        entityHistory.getEvmAddress(), entityHistory.getTimestampLower() - 1, SHARD, REALM))
                .isEmpty();
    }

    @Test
    void findHistoricalEntityByEvmAddressOrAliasAndTimestampRangeGreaterThanBlockTimestampAndDeletedIsFalseWithAlias() {
        final var entityHistory = domainBuilder.entityHistory().persist();
        final var entity = domainBuilder
                .entity()
                .customize(e -> e.id(entityHistory.getId()))
                .persist();

        assertThat(entityRepository.findActiveByEvmAddressOrAliasAndTimestampAndShardAndRealm(
                        entity.getAlias(), entityHistory.getTimestampLower() - 1, SHARD, REALM))
                .isEmpty();
    }

    @Test
    void
            findHistoricalEntityByEvmAddressOrAliasAndTimestampRangeGreaterThanBlockTimestampAndDeletedIsFalseWithEvmAddress() {
        final var entityHistory = domainBuilder.entityHistory().persist();
        final var entity = domainBuilder
                .entity()
                .customize(e -> e.id(entityHistory.getId()))
                .persist();

        assertThat(entityRepository.findActiveByEvmAddressOrAliasAndTimestampAndShardAndRealm(
                        entity.getEvmAddress(), entityHistory.getTimestampLower() - 1, SHARD, REALM))
                .isEmpty();
    }

    @Test
    void findEntityByEvmAddressOrAliasAndTimestampRangeEqualToBlockTimestampAndDeletedIsFalseWithAlias() {
        final var entityHistory = domainBuilder.entityHistory().persist();

        // Both entity and entity history will be queried in union but entity record is the latest valid
        final var entity = domainBuilder
                .entity()
                .customize(e -> e.id(entityHistory.getId()))
                .persist();

        assertThat(entityRepository.findActiveByEvmAddressOrAliasAndTimestampAndShardAndRealm(
                        entity.getAlias(), entity.getTimestampLower(), SHARD, REALM))
                .get()
                .isEqualTo(entity);
    }

    @Test
    void findEntityByEvmAddressOrAliasAndTimestampRangeEqualToBlockTimestampAndDeletedIsFalseWithEvmAddress() {
        final var entityHistory = domainBuilder.entityHistory().persist();

        // Both entity and entity history will be queried in union but entity record is the latest valid
        final var entity = domainBuilder
                .entity()
                .customize(e -> e.id(entityHistory.getId()))
                .persist();

        assertThat(entityRepository.findActiveByEvmAddressOrAliasAndTimestampAndShardAndRealm(
                        entity.getEvmAddress(), entity.getTimestampLower(), SHARD, REALM))
                .get()
                .isEqualTo(entity);
    }

    @Test
    void findHistoricalEntityByEvmAddressOrAliasAndTimestampRangeEqualToBlockTimestampAndDeletedIsFalseWithAlias() {
        final var entity = domainBuilder.entity().persist();
        // Both entity and entity history will be queried in union but entity history record is the latest valid
        final var entityHistory = domainBuilder
                .entityHistory()
                .customize(e -> e.id(entity.getId()))
                .persist();

        assertThat(entityRepository.findActiveByEvmAddressOrAliasAndTimestampAndShardAndRealm(
                        entity.getAlias(), entityHistory.getTimestampLower(), SHARD, REALM))
                .get()
                .usingRecursiveComparison()
                .isEqualTo(entityHistory);
    }

    @Test
    void
            findHistoricalEntityByEvmAddressOrAliasAndTimestampRangeEqualToBlockTimestampAndDeletedIsFalseWithEvmAddress() {
        final var entity = domainBuilder.entity().persist();
        // Both entity and entity history will be queried in union but entity history record is the latest valid
        final var entityHistory = domainBuilder
                .entityHistory()
                .customize(e -> e.id(entity.getId()))
                .persist();

        assertThat(entityRepository.findActiveByEvmAddressOrAliasAndTimestampAndShardAndRealm(
                        entity.getEvmAddress(), entityHistory.getTimestampLower(), SHARD, REALM))
                .get()
                .usingRecursiveComparison()
                .isEqualTo(entityHistory);
    }

    @Test
    void findMaxIdEmptyDb() {
        assertThat(entityRepository.findMaxId(SHARD, REALM)).isNull();
    }

    @Test
    void findMaxId() {
        final long lastId = 1111;
        domainBuilder.entity().customize(e -> e.id(lastId)).persist();
        assertThat(entityRepository.findMaxId(SHARD, REALM)).isEqualTo(lastId);
    }
}
