// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.restjava.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hiero.mirror.restjava.common.Constants.CONSENSUS_TIMESTAMP;
import static org.hiero.mirror.restjava.common.Constants.KEY;
import static org.springframework.data.domain.Sort.Direction.ASC;
import static org.springframework.data.domain.Sort.Direction.DESC;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.hiero.mirror.common.domain.hook.HookStorageChange;
import org.hiero.mirror.restjava.RestJavaIntegrationTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.domain.Sort.Direction;

@RequiredArgsConstructor
final class HookStorageChangeRepositoryTest extends RestJavaIntegrationTest {

    private static final long HOOK_ID = 2000;
    private static final int LIMIT = 2;

    private final HookStorageChangeRepository repository;

    @ParameterizedTest
    @EnumSource(Direction.class)
    void findByKeyBetweenAndTimestampBetweenInRange(Direction order) {
        // given
        final var ownerId = domainBuilder.entityId();
        final var keys = generateKeys(5);

        final var change1 = persistChange(ownerId, HOOK_ID, keys.get(0));
        final var change2 = persistChange(ownerId, HOOK_ID, keys.get(1));
        persistChange(ownerId, HOOK_ID + 1, keys.get(2)); // different hookId
        final var change4 = persistChange(ownerId, HOOK_ID, keys.get(3));
        final var change5 = persistChange(ownerId, HOOK_ID, keys.get(4));

        final var sort = sort(order);

        final var expectedChanges = order.isAscending()
                ? List.of(change1, change2, change4, change5)
                : List.of(change5, change4, change2, change1);

        final var changes = expectedChanges.stream().map(this::hookStorage).toList();

        final var expectedPage0 = changes.subList(0, LIMIT);
        final var expectedPage1 = changes.subList(LIMIT, changes.size());

        // when
        final var page0Result = repository.findByKeyBetweenAndTimestampBetween(
                ownerId.getId(),
                HOOK_ID,
                change1.getKey(),
                change5.getKey(),
                change1.getConsensusTimestamp(),
                change5.getConsensusTimestamp(),
                PageRequest.of(0, LIMIT, sort));

        final var page1Result = repository.findByKeyBetweenAndTimestampBetween(
                ownerId.getId(),
                HOOK_ID,
                change1.getKey(),
                change5.getKey(),
                change1.getConsensusTimestamp(),
                change5.getConsensusTimestamp(),
                PageRequest.of(1, LIMIT, sort));

        // then
        assertThat(page0Result).containsExactlyElementsOf(expectedPage0);
        assertThat(page1Result).containsExactlyElementsOf(expectedPage1);
    }

    @Test
    void findByKeyBetweenAndTimestampBetweenInRangePartialOverlap() {
        // given
        final var keys = generateKeys(5);
        final var ownerId = domainBuilder.entityId();

        final var change1 = persistChange(ownerId, HOOK_ID, keys.get(0));
        final var change2 = persistChange(ownerId, HOOK_ID, keys.get(1));
        final var change3 = persistChange(ownerId, HOOK_ID, keys.get(2));
        final var change4 = persistChange(ownerId, HOOK_ID, keys.get(3));
        final var change5 = persistChange(ownerId, HOOK_ID, keys.get(4));

        final var sort = sort(ASC);

        final var expectedChanges =
                Stream.of(change2, change3, change4).map(this::hookStorage).toList();

        // when
        final var keyPartialOverlap = repository.findByKeyBetweenAndTimestampBetween(
                ownerId.getId(),
                HOOK_ID,
                change2.getKey(),
                change4.getKey(),
                change1.getConsensusTimestamp(),
                change4.getConsensusTimestamp(),
                PageRequest.of(0, 10, sort));

        final var timestampPartialOverlap = repository.findByKeyBetweenAndTimestampBetween(
                ownerId.getId(),
                HOOK_ID,
                change1.getKey(),
                change5.getKey(),
                change2.getConsensusTimestamp(),
                change4.getConsensusTimestamp(),
                PageRequest.of(0, 10, sort));

        // then
        assertThat(keyPartialOverlap).containsExactlyElementsOf(expectedChanges);
        assertThat(timestampPartialOverlap).containsExactlyElementsOf(expectedChanges);
    }

    @ParameterizedTest
    @EnumSource(Direction.class)
    void findByKeyInAndTimestampBetweenInRange(Direction order) {
        // given
        final var keys = generateKeys(5);
        final var ownerId = domainBuilder.entityId();

        final var change1 = persistChange(ownerId, HOOK_ID, keys.get(0));
        final var change2 = persistChange(ownerId, HOOK_ID, keys.get(1));
        persistChange(ownerId, HOOK_ID + 1, keys.get(2)); // different hookId
        final var change4 = persistChange(ownerId, HOOK_ID, keys.get(3));

        final var queryKeys = List.of(change1.getKey(), change2.getKey(), change4.getKey());

        final var expectedChanges =
                order.isAscending() ? List.of(change1, change2, change4) : List.of(change4, change2, change1);

        final var changes = expectedChanges.stream().map(this::hookStorage).toList();

        final var expectedPage0 = changes.subList(0, LIMIT);
        final var expectedPage1 = changes.subList(LIMIT, changes.size());

        final var sort = sort(order);

        // when
        final var page0Result = repository.findByKeyInAndTimestampBetween(
                ownerId.getId(),
                HOOK_ID,
                queryKeys,
                change1.getConsensusTimestamp(),
                change4.getConsensusTimestamp(),
                PageRequest.of(0, LIMIT, sort));

        final var page1Result = repository.findByKeyInAndTimestampBetween(
                ownerId.getId(),
                HOOK_ID,
                queryKeys,
                change1.getConsensusTimestamp(),
                change4.getConsensusTimestamp(),
                PageRequest.of(1, LIMIT, sort));

        // then
        assertThat(page0Result).containsExactlyElementsOf(expectedPage0);
        assertThat(page1Result).containsExactlyElementsOf(expectedPage1);
    }

    @Test
    void findByKeyBetweenAndTimestampBetweenDifferentOwnerAndHookIdEmptyResults() {
        // given
        final var keys = generateKeys(5);
        final var ownerId = domainBuilder.entityId();

        final var change1 = persistChange(ownerId, HOOK_ID, keys.get(0));
        persistChange(ownerId, HOOK_ID, keys.get(1));
        persistChange(ownerId, HOOK_ID, keys.get(2));
        final var change4 = persistChange(ownerId, HOOK_ID, keys.get(3));

        final var sort = sort(ASC);

        // when
        final var differentOwnerChanges = repository.findByKeyBetweenAndTimestampBetween(
                ownerId.getId() + 1,
                HOOK_ID,
                change1.getKey(),
                change4.getKey(),
                change1.getConsensusTimestamp(),
                change4.getConsensusTimestamp(),
                PageRequest.of(0, LIMIT, sort));

        final var differentHookIdChanges = repository.findByKeyBetweenAndTimestampBetween(
                ownerId.getId(),
                HOOK_ID + 1,
                change1.getKey(),
                change4.getKey(),
                change1.getConsensusTimestamp(),
                change4.getConsensusTimestamp(),
                PageRequest.of(0, LIMIT, sort));

        // then
        assertThat(differentOwnerChanges).isEmpty();
        assertThat(differentHookIdChanges).isEmpty();
    }

    @Test
    void findByKeyInAndTimestampBetweenDifferentOwnerAndHookIdEmptyResult() {
        // given
        final var keys = generateKeys(5);
        final var ownerId = domainBuilder.entityId();

        final var change1 = persistChange(ownerId, HOOK_ID, keys.get(0));
        final var change2 = persistChange(ownerId, HOOK_ID, keys.get(1));
        final var change3 = persistChange(ownerId, HOOK_ID, keys.get(2));

        final var keysDifferentOwner = List.of(change1.getKey(), change2.getKey(), change3.getKey());

        final var sort = sort(ASC);

        // when
        final var differentOwnerChanges = repository.findByKeyInAndTimestampBetween(
                ownerId.getId() + 1,
                HOOK_ID,
                keysDifferentOwner,
                change1.getConsensusTimestamp(),
                change3.getConsensusTimestamp(),
                PageRequest.of(0, LIMIT, sort));

        final var differentHookIdChanges = repository.findByKeyInAndTimestampBetween(
                ownerId.getId(),
                HOOK_ID + 1,
                keysDifferentOwner,
                change1.getConsensusTimestamp(),
                change3.getConsensusTimestamp(),
                PageRequest.of(1, LIMIT, sort));

        // then
        assertThat(differentOwnerChanges).isEmpty();
        assertThat(differentHookIdChanges).isEmpty();
    }

    @Test
    void findByKeyInAndTimestampBetweenOutOfRange() {
        // given
        final var keys = generateKeys(5);
        final var ownerId = domainBuilder.entityId();

        final var change1 = persistChange(ownerId, HOOK_ID, keys.get(0));
        persistChange(ownerId, HOOK_ID, keys.get(1));
        final var change3 = persistChange(ownerId, HOOK_ID, keys.get(2));

        final var sort = sort(ASC);

        // when
        final var keysOutOfRangeQueryResult = repository.findByKeyInAndTimestampBetween(
                ownerId.getId(),
                HOOK_ID,
                List.of(keys.get(3)),
                change1.getConsensusTimestamp(),
                change3.getConsensusTimestamp(),
                PageRequest.of(0, LIMIT, sort));

        // then
        assertThat(keysOutOfRangeQueryResult).isEmpty();
    }

    @Test
    void findByKeyBetweenAndTimestampBetweenOutOfRange() {
        // given
        final var keys = generateKeys(5);
        final var ownerId = domainBuilder.entityId();

        final var change1 = persistChange(ownerId, HOOK_ID, keys.get(0));
        persistChange(ownerId, HOOK_ID, keys.get(1));
        persistChange(ownerId, HOOK_ID, keys.get(2));
        final var change4 = persistChange(ownerId, HOOK_ID, keys.get(3));

        final var sort = sort(ASC);

        // when
        final var timestampsOutOfRange = repository.findByKeyBetweenAndTimestampBetween(
                ownerId.getId(),
                HOOK_ID,
                change1.getKey(),
                change4.getKey(),
                change4.getConsensusTimestamp() + 1,
                Long.MAX_VALUE,
                PageRequest.of(0, LIMIT, sort));

        final var maxBytes = new byte[32];
        Arrays.fill(maxBytes, (byte) 0xFF);

        final var keysOutOfRange = repository.findByKeyBetweenAndTimestampBetween(
                ownerId.getId(),
                HOOK_ID,
                keys.get(4),
                maxBytes,
                change1.getConsensusTimestamp(),
                change4.getConsensusTimestamp(),
                PageRequest.of(0, LIMIT, sort));

        // then
        assertThat(timestampsOutOfRange).isEmpty();
        assertThat(keysOutOfRange).isEmpty();
    }

    private HookStorageChange persistChange(EntityId ownerId, long hookId) {
        return domainBuilder
                .hookStorageChange()
                .customize(change -> change.hookId(hookId).ownerId(ownerId.getId()))
                .persist();
    }

    private HookStorageChange persistChange(EntityId ownerId, long hookId, byte[] key) {
        return domainBuilder
                .hookStorageChange()
                .customize(change -> change.hookId(hookId).key(key).ownerId(ownerId.getId()))
                .persist();
    }

    private Sort sort(Direction order) {
        return Sort.by(new Sort.Order(order, KEY), new Sort.Order(DESC, CONSENSUS_TIMESTAMP));
    }
}
