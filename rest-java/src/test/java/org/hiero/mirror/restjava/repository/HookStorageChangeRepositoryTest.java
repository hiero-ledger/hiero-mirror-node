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
import org.hiero.mirror.restjava.util.BytesUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.domain.Sort.Direction;

@RequiredArgsConstructor
final class HookStorageChangeRepositoryTest extends RestJavaIntegrationTest {

    private static final EntityId OWNER_ID = EntityId.of(0, 0, 1000);
    private static final int LIMIT = 2;

    private final HookStorageChangeRepository repository;

    @ParameterizedTest
    @EnumSource(Direction.class)
    void findByKeyBetweenAndTimestampBetweenInRange(Direction order) {
        // given
        final var change1 = persistChange(OWNER_ID);
        final var hookId1 = change1.getHookId();

        final var change2 = persistChange(OWNER_ID, hookId1, BytesUtil.incrementByteArray(change1.getKey()));
        final var change3 = persistChange(
                OWNER_ID, hookId1 + 1, BytesUtil.incrementByteArray(change2.getKey())); // different hookId
        final var change4 = persistChange(OWNER_ID, hookId1, BytesUtil.incrementByteArray(change3.getKey()));
        final var change5 = persistChange(OWNER_ID, hookId1, BytesUtil.incrementByteArray(change4.getKey()));

        final var sort = sort(order);

        final var expectedChanges = order.isAscending()
                ? List.of(change1, change2, change4, change5)
                : List.of(change5, change4, change2, change1);

        final var changes = expectedChanges.stream().map(this::hookStorage).toList();

        final var expectedPage0 = changes.subList(0, LIMIT);
        final var expectedPage1 = changes.subList(LIMIT, changes.size());

        // when
        final var page0Result = repository.findByKeyBetweenAndTimestampBetween(
                OWNER_ID.getId(),
                hookId1,
                change1.getKey(),
                change5.getKey(),
                change1.getConsensusTimestamp(),
                change5.getConsensusTimestamp(),
                PageRequest.of(0, LIMIT, sort));

        final var page1Result = repository.findByKeyBetweenAndTimestampBetween(
                OWNER_ID.getId(),
                hookId1,
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
        final var change1 = persistChange(OWNER_ID);
        final var hookId1 = change1.getHookId();

        final var change2 = persistChange(OWNER_ID, hookId1, BytesUtil.incrementByteArray(change1.getKey()));
        final var change3 = persistChange(OWNER_ID, hookId1, BytesUtil.incrementByteArray(change2.getKey()));
        final var change4 = persistChange(OWNER_ID, hookId1, BytesUtil.incrementByteArray(change3.getKey()));
        final var change5 = persistChange(OWNER_ID, hookId1, BytesUtil.incrementByteArray(change4.getKey()));

        final var sort = sort(ASC);

        final var expectedChanges =
                Stream.of(change2, change3, change4).map(this::hookStorage).toList();

        // when
        final var keyPartialOverlap = repository.findByKeyBetweenAndTimestampBetween(
                OWNER_ID.getId(),
                hookId1,
                change2.getKey(),
                change4.getKey(),
                change1.getConsensusTimestamp(),
                change4.getConsensusTimestamp(),
                PageRequest.of(0, 10, sort));

        final var timestampPartialOverlap = repository.findByKeyBetweenAndTimestampBetween(
                OWNER_ID.getId(),
                hookId1,
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
        final var change1 = persistChange(OWNER_ID);
        final var hookId1 = change1.getHookId();

        final var change2 = persistChange(OWNER_ID, hookId1, BytesUtil.incrementByteArray(change1.getKey()));
        final var change3 = persistChange(
                OWNER_ID, hookId1 + 1, BytesUtil.incrementByteArray(change2.getKey())); // different hookId
        final var change4 = persistChange(OWNER_ID, hookId1, BytesUtil.incrementByteArray(change3.getKey()));

        final var keys = List.of(change1.getKey(), change2.getKey(), change4.getKey());

        final var expectedChanges =
                order.isAscending() ? List.of(change1, change2, change4) : List.of(change4, change2, change1);

        final var changes = expectedChanges.stream().map(this::hookStorage).toList();

        final var expectedPage0 = changes.subList(0, LIMIT);
        final var expectedPage1 = changes.subList(LIMIT, changes.size());

        final var sort = sort(order);

        // when
        final var page0Result = repository.findByKeyInAndTimestampBetween(
                OWNER_ID.getId(),
                hookId1,
                keys,
                change1.getConsensusTimestamp(),
                change4.getConsensusTimestamp(),
                PageRequest.of(0, LIMIT, sort));

        final var page1Result = repository.findByKeyInAndTimestampBetween(
                OWNER_ID.getId(),
                hookId1,
                keys,
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
        final var change1 = persistChange(OWNER_ID);
        final var hookId1 = change1.getHookId();

        final var change2 = persistChange(OWNER_ID, hookId1, BytesUtil.incrementByteArray(change1.getKey()));
        final var change3 = persistChange(OWNER_ID, hookId1, BytesUtil.incrementByteArray(change2.getKey()));
        final var change4 = persistChange(OWNER_ID, hookId1, BytesUtil.incrementByteArray(change3.getKey()));

        final var sort = sort(ASC);

        // when
        final var differentOwnerChanges = repository.findByKeyBetweenAndTimestampBetween(
                OWNER_ID.getId() + 1,
                hookId1,
                change1.getKey(),
                change4.getKey(),
                change1.getConsensusTimestamp(),
                change4.getConsensusTimestamp(),
                PageRequest.of(0, LIMIT, sort));

        final var differentHookIdChanges = repository.findByKeyBetweenAndTimestampBetween(
                OWNER_ID.getId(),
                hookId1 + 1,
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
        final var change1 = persistChange(OWNER_ID);
        final var hookId1 = change1.getHookId();

        final var change2 = persistChange(OWNER_ID, hookId1, BytesUtil.incrementByteArray(change1.getKey()));
        final var change3 = persistChange(OWNER_ID, hookId1, BytesUtil.incrementByteArray(change2.getKey()));

        final var keys = List.of(change1.getKey(), change2.getKey(), change3.getKey());

        final var sort = sort(ASC);

        // when
        final var differentOwnerChanges = repository.findByKeyInAndTimestampBetween(
                OWNER_ID.getId() + 1,
                hookId1,
                keys,
                change1.getConsensusTimestamp(),
                change3.getConsensusTimestamp(),
                PageRequest.of(0, LIMIT, sort));

        final var differentHookIdChanges = repository.findByKeyInAndTimestampBetween(
                OWNER_ID.getId(),
                hookId1 + 1,
                keys,
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
        final var change1 = persistChange(OWNER_ID);
        final var hookId1 = change1.getHookId();

        final var change2 = persistChange(OWNER_ID, hookId1, BytesUtil.incrementByteArray(change1.getKey()));
        final var change3 = persistChange(OWNER_ID, hookId1, BytesUtil.incrementByteArray(change2.getKey()));

        final var sort = sort(ASC);

        // when
        final var keysOutOfRangeQueryResult = repository.findByKeyInAndTimestampBetween(
                OWNER_ID.getId(),
                hookId1,
                List.of(BytesUtil.incrementByteArray(change3.getKey())),
                change1.getConsensusTimestamp(),
                change3.getConsensusTimestamp(),
                PageRequest.of(0, LIMIT, sort));

        // then
        assertThat(keysOutOfRangeQueryResult).isEmpty();
    }

    @Test
    void findByKeyBetweenAndTimestampBetweenOutOfRange() {
        // given
        final var change1 = persistChange(OWNER_ID);
        final var hookId1 = change1.getHookId();

        final var change2 = persistChange(OWNER_ID, hookId1, BytesUtil.incrementByteArray(change1.getKey()));
        final var change3 = persistChange(OWNER_ID, hookId1, BytesUtil.incrementByteArray(change2.getKey()));
        final var change4 = persistChange(OWNER_ID, hookId1, BytesUtil.incrementByteArray(change3.getKey()));

        final var sort = sort(ASC);

        // when
        final var timestampsOutOfRange = repository.findByKeyBetweenAndTimestampBetween(
                OWNER_ID.getId(),
                hookId1,
                change1.getKey(),
                change4.getKey(),
                change4.getConsensusTimestamp() + 1,
                Long.MAX_VALUE,
                PageRequest.of(0, LIMIT, sort));

        final var maxBytes = new byte[32];
        Arrays.fill(maxBytes, (byte) 0xFF);

        final var keysOutOfRange = repository.findByKeyBetweenAndTimestampBetween(
                OWNER_ID.getId(),
                hookId1,
                BytesUtil.incrementByteArray(change4.getKey()),
                maxBytes,
                change1.getConsensusTimestamp(),
                change4.getConsensusTimestamp(),
                PageRequest.of(0, LIMIT, sort));

        // then
        assertThat(timestampsOutOfRange).isEmpty();
        assertThat(keysOutOfRange).isEmpty();
    }

    private HookStorageChange persistChange(EntityId ownerId, long hookId, byte[] key) {
        return domainBuilder
                .hookStorageChange()
                .customize(
                        change -> change.ownerId(ownerId.getId()).hookId(hookId).key(key))
                .persist();
    }

    private HookStorageChange persistChange(EntityId ownerId) {
        return domainBuilder
                .hookStorageChange()
                .customize(change -> change.ownerId(ownerId.getId()))
                .persist();
    }

    private Sort sort(Direction order) {
        return Sort.by(new Sort.Order(order, KEY), new Sort.Order(DESC, CONSENSUS_TIMESTAMP));
    }
}
