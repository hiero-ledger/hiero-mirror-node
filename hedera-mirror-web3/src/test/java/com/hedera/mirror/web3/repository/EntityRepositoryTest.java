// SPDX-License-Identifier: Apache-2.0

package com.hedera.mirror.web3.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.common.collect.Range;
import com.hedera.mirror.common.CommonProperties;
import com.hedera.mirror.common.domain.entity.Entity;
import com.hedera.mirror.common.domain.entity.EntityHistory;
import com.hedera.mirror.web3.Web3IntegrationTest;
import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@RequiredArgsConstructor
class EntityRepositoryTest extends Web3IntegrationTest {

    private final EntityRepository entityRepository;
    private final CommonProperties commonProperties;

    private long shard;
    private long realm;


    @BeforeEach
    void setup() {
        shard = commonProperties.getShard();
        realm = commonProperties.getRealm();
    }

    @Test
    void findByIdAndDeletedIsFalseSuccessfulCall() {
        var entity = domainBuilder.entity(-2, domainBuilder.timestamp()).persist();
        assertThat(entityRepository.findByIdAndDeletedIsFalse(-2L)).contains(entity);
    }

    @Test
    void findByIdAndDeletedIsFalseFailCall() {
        Entity entity = domainBuilder.entity().persist();
        long id = entity.getId();
        assertThat(entityRepository.findByIdAndDeletedIsFalse(++id)).isEmpty();
    }

    @Test
    void findByIdAndDeletedTrueCall() {
        Entity entity = domainBuilder.entity().customize(e -> e.deleted(true)).persist();
        assertThat(entityRepository.findByIdAndDeletedIsFalse(entity.getId())).isEmpty();
    }

    @Test
    void findByEvmAddressAndDeletedIsFalseSuccessfulCall() {
        var entity1 = domainBuilder.entity().persist();
        var entity2 = domainBuilder.entity().persist();
        assertThat(entityRepository.findByEvmAddressAndDeletedIsFalse(entity1.getEvmAddress()))
                .contains(entity1);

        // Validate entity1 is cached and entity2 can't be found since it's not cached
        entityRepository.deleteAll();
        assertThat(entityRepository.findByEvmAddressAndDeletedIsFalse(entity1.getEvmAddress()))
                .contains(entity1);
        assertThat(entityRepository.findByEvmAddressAndDeletedIsFalse(entity2.getEvmAddress()))
                .isEmpty();
    }

    @Test
    void findByEvmAddressAndDeletedIsFalseFailCall() {
        domainBuilder.entity().persist();
        assertThat(entityRepository.findByEvmAddressAndDeletedIsFalse(new byte[32]))
                .isEmpty();
    }

    @Test
    void findByEvmAddressAndDeletedTrueCall() {
        Entity entity = domainBuilder.entity().customize(e -> e.deleted(true)).persist();
        assertThat(entityRepository.findByEvmAddressAndDeletedIsFalse(entity.getEvmAddress()))
                .isEmpty();
    }

    @Test
    void findByEvmAddressAndTimestampRangeLessThanBlockTimestampAndDeletedIsFalseCall() {
        Entity entity = domainBuilder.entity().persist();

        assertThat(entityRepository.findActiveByEvmAddressAndTimestamp(
                        entity.getEvmAddress(), entity.getTimestampLower() + 1, shard, realm))
                .get()
                .isEqualTo(entity);
    }

    @Test
    void findByEvmAddressAndTimestampRangeEqualToBlockTimestampAndDeletedIsFalseCall() {
        Entity entity = domainBuilder.entity().persist();

        assertThat(entityRepository.findActiveByEvmAddressAndTimestamp(
                        entity.getEvmAddress(), entity.getTimestampLower(), shard, realm))
                .get()
                .isEqualTo(entity);
    }

    @Test
    void findByEvmAddressAndTimestampRangeGreaterThanBlockTimestampAndDeletedIsFalseCall() {
        Entity entity = domainBuilder.entity().persist();

        assertThat(entityRepository.findActiveByEvmAddressAndTimestamp(
                        entity.getEvmAddress(), entity.getTimestampLower() - 1, shard, realm))
                .isEmpty();
    }

    @Test
    void findByEvmAddressAndTimestampRangeLessThanBlockTimestampAndDeletedTrueCall() {
        Entity entity = domainBuilder.entity().customize(e -> e.deleted(true)).persist();

        assertThat(entityRepository.findActiveByEvmAddressAndTimestamp(
                        entity.getEvmAddress(), entity.getTimestampLower() + 1, shard, realm))
                .isEmpty();
    }

    @Test
    void findHistoricalEntityByEvmAddressAndTimestampRangeAndDeletedTrueCall() {
        EntityHistory entityHistory =
                domainBuilder.entityHistory().customize(e -> e.deleted(true)).persist();

        assertThat(entityRepository.findActiveByEvmAddressAndTimestamp(
                        entityHistory.getEvmAddress(), entityHistory.getTimestampLower() - 1, shard, realm))
                .isEmpty();
    }

    @Test
    void findHistoricalEntityByEvmAddressAndTimestampRangeGreaterThanBlockTimestampAndDeletedIsFalse() {
        EntityHistory entityHistory = domainBuilder.entityHistory().persist();
        Entity entity = domainBuilder
                .entity()
                .customize(e -> e.id(entityHistory.getId()))
                .persist();

        assertThat(entityRepository.findActiveByEvmAddressAndTimestamp(
                        entity.getEvmAddress(), entityHistory.getTimestampLower() - 1, shard, realm))
                .isEmpty();
    }

    @Test
    void findEntityByEvmAddressAndTimestampRangeEqualToBlockTimestampAndDeletedIsFalse() {
        EntityHistory entityHistory = domainBuilder.entityHistory().persist();

        // Both entity and entity history will be queried in union but entity record is the latest valid
        Entity entity = domainBuilder
                .entity()
                .customize(e -> e.id(entityHistory.getId()))
                .persist();

        assertThat(entityRepository.findActiveByEvmAddressAndTimestamp(
                        entity.getEvmAddress(), entity.getTimestampLower(), shard, realm))
                .get()
                .isEqualTo(entity);
    }

    @Test
    void findHistoricalEntityByEvmAddressAndTimestampRangeEqualToBlockTimestampAndDeletedIsFalse() {
        Entity entity = domainBuilder.entity().persist();
        // Both entity and entity history will be queried in union but entity history record is the latest valid
        EntityHistory entityHistory = domainBuilder
                .entityHistory()
                .customize(e -> e.id(entity.getId()))
                .persist();

        assertThat(entityRepository.findActiveByEvmAddressAndTimestamp(
                        entity.getEvmAddress(), entityHistory.getTimestampLower(), shard, realm))
                .get()
                .usingRecursiveComparison()
                .isEqualTo(entityHistory);
    }

    @Test
    void findByIdAndTimestampRangeLessThanBlockTimestampAndDeletedIsFalseCall() {
        Entity entity = domainBuilder.entity().persist();

        assertThat(entityRepository.findActiveByIdAndTimestamp(entity.getId(), entity.getTimestampLower() + 1))
                .get()
                .isEqualTo(entity);
    }

    @Test
    void findByIdAndTimestampRangeEqualToBlockTimestampAndDeletedIsFalseCall() {
        Entity entity = domainBuilder.entity().persist();

        assertThat(entityRepository.findActiveByIdAndTimestamp(entity.getId(), entity.getTimestampLower()))
                .get()
                .isEqualTo(entity);
    }

    @Test
    void findByIdAndTimestampRangeGreaterThanBlockTimestampAndDeletedIsFalseCall() {
        Entity entity = domainBuilder.entity().persist();

        assertThat(entityRepository.findActiveByIdAndTimestamp(entity.getId(), entity.getTimestampLower() - 1))
                .isEmpty();
    }

    @Test
    void findByIdAndTimestampRangeAndDeletedTrueCall() {
        Entity entity = domainBuilder.entity().customize(e -> e.deleted(true)).persist();

        assertThat(entityRepository.findActiveByIdAndTimestamp(entity.getId(), entity.getTimestampLower()))
                .isEmpty();
    }

    @Test
    void findHistoricalEntityByIdAndTimestampRangeLessThanBlockTimestampAndDeletedIsFalseCall() {
        EntityHistory entityHistory = domainBuilder.entityHistory().persist();

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
        EntityHistory entityHistory = domainBuilder.entityHistory().persist();

        assertThat(entityRepository.findActiveByIdAndTimestamp(
                        entityHistory.getId(), entityHistory.getTimestampLower()))
                .get()
                .usingRecursiveComparison()
                .isEqualTo(entityHistory);
    }

    @Test
    void findHistoricalEntityByIdAndTimestampRangeGreaterThanBlockTimestampAndDeletedIsFalseCall() {
        EntityHistory entityHistory = domainBuilder.entityHistory().persist();

        assertThat(entityRepository.findActiveByIdAndTimestamp(
                        entityHistory.getId(), entityHistory.getTimestampLower() - 1))
                .isEmpty();
    }

    @Test
    void findHistoricalEntityByIdAndTimestampRangeAndDeletedTrueCall() {
        EntityHistory entityHistory =
                domainBuilder.entityHistory().customize(e -> e.deleted(true)).persist();

        assertThat(entityRepository.findActiveByIdAndTimestamp(
                        entityHistory.getId(), entityHistory.getCreatedTimestamp()))
                .isEmpty();
    }

    @Test
    void findByEvmAddressOrAliasSuccessWithAlias() {
        final var alias = domainBuilder.key();
        final var entity = domainBuilder.entity().customize(e -> e.alias(alias).shard(shard).realm(realm)).persist();
        assertThat(entityRepository.findByEvmAddressOrAlias(alias, shard, realm)).get().isEqualTo(entity);
    }

    @Test
    void findByEvmAddressOrAliasSuccessWithEvmAddress() {
        final var evmAddress = domainBuilder.evmAddress();
        final var entity =
                domainBuilder.entity().customize(e -> e.evmAddress(evmAddress).shard(shard).realm(realm)).persist();
        assertThat(entityRepository.findByEvmAddressOrAlias(evmAddress, shard, realm)).get().isEqualTo(entity);
    }

    @Test
    void findByEvmAddressOrAliasReturnsEmptyWhenDeletedIsTrueWithAlias() {
        final var alias = domainBuilder.key();
        domainBuilder.entity().customize(e -> e.alias(alias).deleted(true).shard(shard).realm(realm)).persist();
        assertThat(entityRepository.findByEvmAddressOrAlias(alias, shard, realm)).isEmpty();
    }

    @Test
    void findByEvmAddressOrAliasReturnsEmptyWhenDeletedIsTrueWithEvmAddress() {
        final var evmAddress = domainBuilder.evmAddress();
        domainBuilder
                .entity()
                .customize(e -> e.evmAddress(evmAddress).deleted(true).shard(shard).realm(realm))
                .persist();
        assertThat(entityRepository.findByEvmAddressOrAlias(evmAddress, shard, realm)).isEmpty();
    }

    @Test
    void findByEvmAddressOrAliasAndTimestampRangeLessThanBlockTimestampAndDeletedIsFalseCallWithAlias() {
        final var entity = domainBuilder.entity().persist();

        assertThat(entityRepository.findActiveByEvmAddressOrAliasAndTimestamp(
                        entity.getAlias(), entity.getTimestampLower() + 1))
                .get()
                .isEqualTo(entity);
    }

    @Test
    void findByEvmAddressOrAliasAndTimestampRangeLessThanBlockTimestampAndDeletedIsFalseCallWithEvmAddress() {
        final var entity = domainBuilder.entity().persist();

        assertThat(entityRepository.findActiveByEvmAddressOrAliasAndTimestamp(
                        entity.getEvmAddress(), entity.getTimestampLower() + 1))
                .get()
                .isEqualTo(entity);
    }

    @Test
    void findByEvmAddressOrAliasAndTimestampRangeEqualToBlockTimestampAndDeletedIsFalseCallWithAlias() {
        final var entity = domainBuilder.entity().persist();

        assertThat(entityRepository.findActiveByEvmAddressOrAliasAndTimestamp(
                        entity.getAlias(), entity.getTimestampLower()))
                .get()
                .isEqualTo(entity);
    }

    @Test
    void findByEvmAddressOrAliasAndTimestampRangeEqualToBlockTimestampAndDeletedIsFalseCallWithEvmAddress() {
        final var entity = domainBuilder.entity().persist();

        assertThat(entityRepository.findActiveByEvmAddressOrAliasAndTimestamp(
                        entity.getEvmAddress(), entity.getTimestampLower()))
                .get()
                .isEqualTo(entity);
    }

    @Test
    void findByEvmAddressOrAliasAndTimestampRangeGreaterThanBlockTimestampAndDeletedIsFalseCallWithAlias() {
        final var entity = domainBuilder.entity().persist();

        assertThat(entityRepository.findActiveByEvmAddressOrAliasAndTimestamp(
                        entity.getAlias(), entity.getTimestampLower() - 1))
                .isEmpty();
    }

    @Test
    void findByEvmAddressOrAliasAndTimestampRangeGreaterThanBlockTimestampAndDeletedIsFalseCallWithEvmAddress() {
        final var entity = domainBuilder.entity().persist();

        assertThat(entityRepository.findActiveByEvmAddressOrAliasAndTimestamp(
                        entity.getEvmAddress(), entity.getTimestampLower() - 1))
                .isEmpty();
    }

    @Test
    void findByEvmAddressOrAliasAndTimestampRangeLessThanBlockTimestampAndDeletedTrueCallWithAlias() {
        final var entity =
                domainBuilder.entity().customize(e -> e.deleted(true)).persist();

        assertThat(entityRepository.findActiveByEvmAddressOrAliasAndTimestamp(
                        entity.getAlias(), entity.getTimestampLower() + 1))
                .isEmpty();
    }

    @Test
    void findByEvmAddressOrAliasAndTimestampRangeLessThanBlockTimestampAndDeletedTrueCallWithEvmAddress() {
        final var entity =
                domainBuilder.entity().customize(e -> e.deleted(true)).persist();

        assertThat(entityRepository.findActiveByEvmAddressOrAliasAndTimestamp(
                        entity.getEvmAddress(), entity.getTimestampLower() + 1))
                .isEmpty();
    }

    @Test
    void findHistoricalEntityByEvmAddressOrAliasAndTimestampRangeAndDeletedTrueCallWithAlias() {
        final var entityHistory =
                domainBuilder.entityHistory().customize(e -> e.deleted(true)).persist();

        assertThat(entityRepository.findActiveByEvmAddressOrAliasAndTimestamp(
                        entityHistory.getAlias(), entityHistory.getTimestampLower() - 1))
                .isEmpty();
    }

    @Test
    void findHistoricalEntityByEvmAddressOrAliasAndTimestampRangeAndDeletedTrueCallWithEvmAddress() {
        final var entityHistory =
                domainBuilder.entityHistory().customize(e -> e.deleted(true)).persist();

        assertThat(entityRepository.findActiveByEvmAddressOrAliasAndTimestamp(
                        entityHistory.getEvmAddress(), entityHistory.getTimestampLower() - 1))
                .isEmpty();
    }

    @Test
    void findHistoricalEntityByEvmAddressOrAliasAndTimestampRangeGreaterThanBlockTimestampAndDeletedIsFalseWithAlias() {
        final var entityHistory = domainBuilder.entityHistory().persist();
        final var entity = domainBuilder
                .entity()
                .customize(e -> e.id(entityHistory.getId()))
                .persist();

        assertThat(entityRepository.findActiveByEvmAddressOrAliasAndTimestamp(
                        entity.getAlias(), entityHistory.getTimestampLower() - 1))
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

        assertThat(entityRepository.findActiveByEvmAddressOrAliasAndTimestamp(
                        entity.getEvmAddress(), entityHistory.getTimestampLower() - 1))
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

        assertThat(entityRepository.findActiveByEvmAddressOrAliasAndTimestamp(
                        entity.getAlias(), entity.getTimestampLower()))
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

        assertThat(entityRepository.findActiveByEvmAddressOrAliasAndTimestamp(
                        entity.getEvmAddress(), entity.getTimestampLower()))
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

        assertThat(entityRepository.findActiveByEvmAddressOrAliasAndTimestamp(
                        entity.getAlias(), entityHistory.getTimestampLower()))
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

        assertThat(entityRepository.findActiveByEvmAddressOrAliasAndTimestamp(
                        entity.getEvmAddress(), entityHistory.getTimestampLower()))
                .get()
                .usingRecursiveComparison()
                .isEqualTo(entityHistory);
    }

    @Test
    void findMaxIdEmptyDb() {
        assertThat(entityRepository.findMaxId(0, 0)).isNull();
    }

    @Test
    void findMaxId() {
        final long lastId = 1111;
        domainBuilder.entity().customize(e -> e.id(lastId)).persist();
        assertThat(entityRepository.findMaxId(0, 0)).isEqualTo(lastId);
    }
}
