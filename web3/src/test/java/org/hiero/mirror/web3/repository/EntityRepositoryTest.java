// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.common.collect.Range;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.hiero.mirror.common.domain.entity.Entity;
import org.hiero.mirror.common.domain.entity.EntityHistory;
import org.hiero.mirror.web3.Web3IntegrationTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.provider.Arguments;

@RequiredArgsConstructor
class EntityRepositoryTest extends Web3IntegrationTest {
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
        final var entity1 = persistEntity();
        final var entity2 = persistEntity();
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
        persistEntity();

        assertThat(entityRepository.findByEvmAddressAndDeletedIsFalse(new byte[32]))
                .isEmpty();
    }

    @Test
    void findByEvmAddressAndDeletedTrueCall() {
        final var entity = entityPersistDeleted();
        assertThat(entityRepository.findByEvmAddressAndDeletedIsFalse(entity.getEvmAddress()))
                .isEmpty();
    }

    @Test
    void findByEvmAddressAndTimestampRangeLessThanBlockTimestampAndDeletedIsFalseCall() {
        final var entity = persistEntity();

        assertThat(entityRepository.findActiveByEvmAddressAndTimestamp(
                        entity.getEvmAddress(), entity.getTimestampLower() + 1))
                .get()
                .isEqualTo(entity);
    }

    @Test
    void findByEvmAddressAndTimestampRangeEqualToBlockTimestampAndDeletedIsFalseCall() {
        final var entity = persistEntity();

        assertThat(entityRepository.findActiveByEvmAddressAndTimestamp(
                        entity.getEvmAddress(), entity.getTimestampLower()))
                .get()
                .isEqualTo(entity);
    }

    @Test
    void findByEvmAddressAndTimestampRangeGreaterThanBlockTimestampAndDeletedIsFalseCall() {
        final var entity = persistEntity();

        assertThat(entityRepository.findActiveByEvmAddressAndTimestamp(
                        entity.getEvmAddress(), entity.getTimestampLower() - 1))
                .isEmpty();
    }

    @Test
    void findByEvmAddressAndTimestampRangeLessThanBlockTimestampAndDeletedTrueCall() {
        final var entity = entityPersistDeleted();

        assertThat(entityRepository.findActiveByEvmAddressAndTimestamp(
                        entity.getEvmAddress(), entity.getTimestampLower() + 1))
                .isEmpty();
    }

    @Test
    void findHistoricalEntityByEvmAddressAndTimestampRangeAndDeletedTrueCall() {
        final var entityHistory = persistEntityHistoryWithDeleted();

        assertThat(entityRepository.findActiveByEvmAddressAndTimestamp(
                        entityHistory.getEvmAddress(), entityHistory.getTimestampLower() - 1))
                .isEmpty();
    }

    @Test
    void findHistoricalEntityByEvmAddressAndTimestampRangeGreaterThanBlockTimestampAndDeletedIsFalse() {
        final var entityHistory = persistEntityHistory();
        final var entity = persistEntityHistoryWithId(entityHistory.getId());

        assertThat(entityRepository.findActiveByEvmAddressAndTimestamp(
                        entity.getEvmAddress(), entityHistory.getTimestampLower() - 1))
                .isEmpty();
    }

    @Test
    void findEntityByEvmAddressAndTimestampRangeEqualToBlockTimestampAndDeletedIsFalse() {
        final var entityHistory = persistEntityHistory();

        // Both entity and entity history will be queried in union but entity record is the latest valid
        final var entity = persistEntityWithId(entityHistory.getId());

        assertThat(entityRepository.findActiveByEvmAddressAndTimestamp(
                        entity.getEvmAddress(), entity.getTimestampLower()))
                .get()
                .isEqualTo(entity);
    }

    @Test
    void findHistoricalEntityByEvmAddressAndTimestampRangeEqualToBlockTimestampAndDeletedIsFalse() {
        final var entity = persistEntity();
        // Both entity and entity history will be queried in union but entity history record is the latest valid
        final var entityHistory = persistEntityHistoryWithId(entity.getId());

        assertThat(entityRepository.findActiveByEvmAddressAndTimestamp(
                        entity.getEvmAddress(), entityHistory.getTimestampLower()))
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
        final var entity = domainBuilder.entity().customize(e -> e.alias(alias)).persist();
        assertThat(entityRepository.findByEvmAddressOrAliasAndDeletedIsFalse(alias))
                .get()
                .isEqualTo(entity);
    }

    @Test
    void findByEvmAddressOrAliasSuccessWithEvmAddress() {
        final var evmAddress = domainBuilder.evmAddress();
        final var entity =
                domainBuilder.entity().customize(e -> e.evmAddress(evmAddress)).persist();
        assertThat(entityRepository.findByEvmAddressOrAliasAndDeletedIsFalse(evmAddress))
                .get()
                .isEqualTo(entity);
    }

    @Test
    void findByEvmAddressOrAliasReturnsEmptyWhenDeletedIsTrueWithAlias() {
        final var alias = domainBuilder.key();
        domainBuilder.entity().customize(e -> e.alias(alias).deleted(true)).persist();
        assertThat(entityRepository.findByEvmAddressOrAliasAndDeletedIsFalse(alias))
                .isEmpty();
    }

    @Test
    void findByEvmAddressOrAliasReturnsEmptyWhenDeletedIsTrueWithEvmAddress() {
        final var evmAddress = domainBuilder.evmAddress();
        domainBuilder
                .entity()
                .customize(e -> e.evmAddress(evmAddress).deleted(true))
                .persist();
        assertThat(entityRepository.findByEvmAddressOrAliasAndDeletedIsFalse(evmAddress))
                .isEmpty();
    }

    @Test
    void findByEvmAddressOrAliasAndTimestampRangeLessThanBlockTimestampAndDeletedIsFalseCallWithAlias() {
        final var entity = persistEntity();

        assertThat(entityRepository.findActiveByEvmAddressOrAliasAndTimestamp(
                        entity.getAlias(), entity.getTimestampLower() + 1))
                .get()
                .isEqualTo(entity);
    }

    @Test
    void findByEvmAddressOrAliasAndTimestampRangeLessThanBlockTimestampAndDeletedIsFalseCallWithEvmAddress() {
        final var entity = persistEntity();

        assertThat(entityRepository.findActiveByEvmAddressOrAliasAndTimestamp(
                        entity.getEvmAddress(), entity.getTimestampLower() + 1))
                .get()
                .isEqualTo(entity);
    }

    @Test
    void findByEvmAddressOrAliasAndTimestampRangeEqualToBlockTimestampAndDeletedIsFalseCallWithAlias() {
        final var entity = persistEntity();

        assertThat(entityRepository.findActiveByEvmAddressOrAliasAndTimestamp(
                        entity.getAlias(), entity.getTimestampLower()))
                .get()
                .isEqualTo(entity);
    }

    @Test
    void findByEvmAddressOrAliasAndTimestampRangeEqualToBlockTimestampAndDeletedIsFalseCallWithEvmAddress() {
        final var entity = persistEntity();

        assertThat(entityRepository.findActiveByEvmAddressOrAliasAndTimestamp(
                        entity.getEvmAddress(), entity.getTimestampLower()))
                .get()
                .isEqualTo(entity);
    }

    @Test
    void findByEvmAddressOrAliasAndTimestampRangeGreaterThanBlockTimestampAndDeletedIsFalseCallWithAlias() {
        final var entity = persistEntity();

        assertThat(entityRepository.findActiveByEvmAddressOrAliasAndTimestamp(
                        entity.getAlias(), entity.getTimestampLower() - 1))
                .isEmpty();
    }

    @Test
    void findByEvmAddressOrAliasAndTimestampRangeGreaterThanBlockTimestampAndDeletedIsFalseCallWithEvmAddress() {
        final var entity = persistEntity();

        assertThat(entityRepository.findActiveByEvmAddressOrAliasAndTimestamp(
                        entity.getEvmAddress(), entity.getTimestampLower() - 1))
                .isEmpty();
    }

    @Test
    void findByEvmAddressOrAliasAndTimestampRangeLessThanBlockTimestampAndDeletedTrueCallWithAlias() {
        final var entity = entityPersistDeleted();

        assertThat(entityRepository.findActiveByEvmAddressOrAliasAndTimestamp(
                        entity.getAlias(), entity.getTimestampLower() + 1))
                .isEmpty();
    }

    @Test
    void findByEvmAddressOrAliasAndTimestampRangeLessThanBlockTimestampAndDeletedTrueCallWithEvmAddress() {
        final var entity = entityPersistDeleted();

        assertThat(entityRepository.findActiveByEvmAddressOrAliasAndTimestamp(
                        entity.getEvmAddress(), entity.getTimestampLower() + 1))
                .isEmpty();
    }

    @Test
    void findHistoricalEntityByEvmAddressOrAliasAndTimestampRangeAndDeletedTrueCallWithAlias() {
        final var entityHistory = persistEntityHistoryWithDeleted();

        assertThat(entityRepository.findActiveByEvmAddressOrAliasAndTimestamp(
                        entityHistory.getAlias(), entityHistory.getTimestampLower() - 1))
                .isEmpty();
    }

    @Test
    void findHistoricalEntityByEvmAddressOrAliasAndTimestampRangeAndDeletedTrueCallWithEvmAddress() {
        final var entityHistory = persistEntityHistoryWithDeleted();

        assertThat(entityRepository.findActiveByEvmAddressOrAliasAndTimestamp(
                        entityHistory.getEvmAddress(), entityHistory.getTimestampLower() - 1))
                .isEmpty();
    }

    @Test
    void findHistoricalEntityByEvmAddressOrAliasAndTimestampRangeGreaterThanBlockTimestampAndDeletedIsFalseWithAlias() {
        final var entityHistory = persistEntityHistory();
        final var entity = persistEntityWithId(entityHistory.getId());

        assertThat(entityRepository.findActiveByEvmAddressOrAliasAndTimestamp(
                        entity.getAlias(), entityHistory.getTimestampLower() - 1))
                .isEmpty();
    }

    @Test
    void
            findHistoricalEntityByEvmAddressOrAliasAndTimestampRangeGreaterThanBlockTimestampAndDeletedIsFalseWithEvmAddress() {
        final var entityHistory = persistEntityHistory();
        final var entity = persistEntityWithId(entityHistory.getId());

        assertThat(entityRepository.findActiveByEvmAddressOrAliasAndTimestamp(
                        entity.getEvmAddress(), entityHistory.getTimestampLower() - 1))
                .isEmpty();
    }

    @Test
    void findEntityByEvmAddressOrAliasAndTimestampRangeEqualToBlockTimestampAndDeletedIsFalseWithAlias() {
        final var entityHistory = persistEntityHistory();

        // Both entity and entity history will be queried in union but entity record is the latest valid
        final var entity = persistEntityWithId(entityHistory.getId());

        assertThat(entityRepository.findActiveByEvmAddressOrAliasAndTimestamp(
                        entity.getAlias(), entity.getTimestampLower()))
                .get()
                .isEqualTo(entity);
    }

    @Test
    void findEntityByEvmAddressOrAliasAndTimestampRangeEqualToBlockTimestampAndDeletedIsFalseWithEvmAddress() {
        final var entityHistory = persistEntityHistory();

        // Both entity and entity history will be queried in union but entity record is the latest valid
        final var entity = persistEntityWithId(entityHistory.getId());

        assertThat(entityRepository.findActiveByEvmAddressOrAliasAndTimestamp(
                        entity.getEvmAddress(), entity.getTimestampLower()))
                .get()
                .isEqualTo(entity);
    }

    @Test
    void findHistoricalEntityByEvmAddressOrAliasAndTimestampRangeEqualToBlockTimestampAndDeletedIsFalseWithAlias() {
        final var entity = persistEntity();
        // Both entity and entity history will be queried in union but entity history record is the latest valid
        final var entityHistory = persistEntityHistoryWithId(entity.getId());

        assertThat(entityRepository.findActiveByEvmAddressOrAliasAndTimestamp(
                        entity.getAlias(), entityHistory.getTimestampLower()))
                .get()
                .usingRecursiveComparison()
                .isEqualTo(entityHistory);
    }

    @Test
    void
            findHistoricalEntityByEvmAddressOrAliasAndTimestampRangeEqualToBlockTimestampAndDeletedIsFalseWithEvmAddress() {
        final var entity = persistEntity();
        // Both entity and entity history will be queried in union but entity history record is the latest valid
        final var entityHistory = persistEntityHistoryWithId(entity.getId());

        assertThat(entityRepository.findActiveByEvmAddressOrAliasAndTimestamp(
                        entity.getEvmAddress(), entityHistory.getTimestampLower()))
                .get()
                .usingRecursiveComparison()
                .isEqualTo(entityHistory);
    }

    @Test
    void findMaxIdEmptyDb() {
        assertThat(entityRepository.findMaxId()).isNull();
    }

    @Test
    void findMaxId() {
        final long lastId = 1111;
        domainBuilder.entity().customize(e -> e.id(lastId)).persist();
        assertThat(entityRepository.findMaxId()).isEqualTo(lastId);
    }

    // Method that provides the test data
    private static Stream<Arguments> shardAndRealmData() {
        return Stream.of(Arguments.of(0L, 0L), Arguments.of(1L, 2L));
    }

    private Entity entityPersistDeleted() {
        return domainBuilder.entity().customize(e -> e.deleted(true)).persist();
    }

    private Entity persistEntity() {
        return domainBuilder.entity().persist();
    }

    private Entity persistEntityWithId(long entityHistoryId) {
        return domainBuilder.entity().customize(e -> e.id(entityHistoryId)).persist();
    }

    private EntityHistory persistEntityHistory() {
        return domainBuilder.entityHistory().persist();
    }

    private EntityHistory persistEntityHistoryWithDeleted() {
        return domainBuilder.entityHistory().customize(e -> e.deleted(true)).persist();
    }

    private EntityHistory persistEntityHistoryWithId(long entityId) {
        return domainBuilder.entityHistory().customize(e -> e.id(entityId)).persist();
    }
}
