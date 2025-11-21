// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.restjava.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hiero.mirror.restjava.common.Constants.CONSENSUS_TIMESTAMP;

import java.util.Arrays;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.hiero.mirror.common.domain.hook.HookStorageChange;
import org.hiero.mirror.restjava.RestJavaIntegrationTest;
import org.hiero.mirror.restjava.common.Constants;
import org.hiero.mirror.restjava.util.BytesUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.domain.Sort.Direction;

@RequiredArgsConstructor
final class HookStorageChangeRepositoryTest extends RestJavaIntegrationTest {

    private final HookStorageChangeRepository repository;

    private static final EntityId OWNER_ID = EntityId.of(1000L);

    private static final int LIMIT = 2;

    public static final String ASC = "asc";
    public static final String DESC = "desc";

    @ParameterizedTest
    @ValueSource(strings = {ASC, DESC})
    void findByKeyBetweenAndTimestampBetweenInRange(String order) {
        // given
        final var change1 = persistChange(OWNER_ID);
        final var hookId1 = change1.getHookId();
        final var key1 = change1.getKey();

        final var change2 = persistChange(OWNER_ID, hookId1, BytesUtil.incrementByteArray(key1));
        final var key2 = change2.getKey();

        persistChange(OWNER_ID, hookId1 + 1, BytesUtil.incrementByteArray(key1)); // different hookId

        final var change3 = persistChange(OWNER_ID, hookId1, BytesUtil.incrementByteArray(key2));
        final var key3 = change3.getKey();

        final var change4 = persistChange(OWNER_ID, hookId1, BytesUtil.incrementByteArray(key3));
        final var key4 = change4.getKey();

        final var sort = Sort.by(
                new Sort.Order(Direction.fromString(order), Constants.KEY),
                new Sort.Order(Direction.DESC, CONSENSUS_TIMESTAMP));

        final var orderedAll = ASC.equalsIgnoreCase(order)
                ? List.of(change1, change2, change3, change4)
                : List.of(change4, change3, change2, change1);

        final var expectedPage0 = orderedAll.subList(0, LIMIT);
        final var expectedPage1 = orderedAll.subList(LIMIT, orderedAll.size());

        // when
        final var timestamp1 = change1.getConsensusTimestamp();
        final var timestamp4 = change4.getConsensusTimestamp();

        final var page0Result = repository.findByKeyBetweenAndTimestampBetween(
                OWNER_ID.getId(), hookId1, key1, key4, timestamp1, timestamp4, PageRequest.of(0, LIMIT, sort));

        final var page1Result = repository.findByKeyBetweenAndTimestampBetween(
                OWNER_ID.getId(), hookId1, key1, key4, timestamp1, timestamp4, PageRequest.of(1, LIMIT, sort));

        // then
        assertThat(page0Result).isNotNull().hasSize(expectedPage0.size()).containsExactlyElementsOf(expectedPage0);
        assertThat(page1Result).isNotNull().hasSize(expectedPage1.size()).containsExactlyElementsOf(expectedPage1);
    }

    @Test
    void findByKeyBetweenAndTimestampBetweenInRangePartialOverlap() {
        // given
        final var change1 = persistChange(OWNER_ID);
        final var hookId1 = change1.getHookId();
        final var key1 = change1.getKey();

        final var change2 = persistChange(OWNER_ID, hookId1, BytesUtil.incrementByteArray(key1));
        final var key2 = change2.getKey();

        final var change3 = persistChange(OWNER_ID, hookId1, BytesUtil.incrementByteArray(key2));
        final var key3 = change3.getKey();

        final var change4 = persistChange(OWNER_ID, hookId1, BytesUtil.incrementByteArray(key3));
        final var key4 = change4.getKey();

        final var change5 = persistChange(OWNER_ID, hookId1, BytesUtil.incrementByteArray(key4));

        final var sort = Sort.by(
                new Sort.Order(Direction.ASC, Constants.KEY), new Sort.Order(Direction.DESC, CONSENSUS_TIMESTAMP));

        final var expectedChanges = List.of(change2, change3, change4);

        // when
        final var timestamp1 = change1.getConsensusTimestamp();
        final var timestamp2 = change2.getConsensusTimestamp();
        final var timestamp4 = change4.getConsensusTimestamp();

        final var keyPartialOverlap = repository.findByKeyBetweenAndTimestampBetween(
                OWNER_ID.getId(), hookId1, key2, key4, timestamp1, timestamp4, PageRequest.of(0, 10, sort));

        final var timestampPartialOverlap = repository.findByKeyBetweenAndTimestampBetween(
                OWNER_ID.getId(), hookId1, key1, change5.getKey(), timestamp2, timestamp4, PageRequest.of(0, 10, sort));

        // then
        assertThat(keyPartialOverlap)
                .isNotNull()
                .hasSize(expectedChanges.size())
                .containsExactlyElementsOf(expectedChanges);
        assertThat(timestampPartialOverlap)
                .isNotNull()
                .hasSize(expectedChanges.size())
                .containsExactlyElementsOf(expectedChanges);
    }

    @ParameterizedTest
    @ValueSource(strings = {ASC, DESC})
    void findByKeyInAndTimestampBetweenInRange(String order) {
        // given
        final var change1 = persistChange(OWNER_ID);
        final var hookId1 = change1.getHookId();
        final var key1 = change1.getKey();

        final var change2 = persistChange(OWNER_ID, hookId1, BytesUtil.incrementByteArray(key1));
        final var key2 = change2.getKey();

        persistChange(OWNER_ID, hookId1 + 1, BytesUtil.incrementByteArray(key1)); // different hookId

        final var change3 = persistChange(OWNER_ID, hookId1, BytesUtil.incrementByteArray(key2));
        final var key3 = change3.getKey();

        final var keys = List.of(key1, key2, key3);

        final var orderedChanges =
                ASC.equalsIgnoreCase(order) ? List.of(change1, change2, change3) : List.of(change3, change2, change1);

        final var expectedPage0 = orderedChanges.subList(0, LIMIT);
        final var expectedPage1 = orderedChanges.subList(LIMIT, orderedChanges.size());

        final var sort = Sort.by(
                new Sort.Order(Direction.fromString(order), Constants.KEY),
                new Sort.Order(Direction.DESC, CONSENSUS_TIMESTAMP));
        // when
        final var timestamp1 = change1.getConsensusTimestamp();
        final var timestamp3 = change3.getConsensusTimestamp();

        final var page0Result = repository.findByKeyInAndTimestampBetween(
                OWNER_ID.getId(), hookId1, keys, timestamp1, timestamp3, PageRequest.of(0, LIMIT, sort));

        final var page1Result = repository.findByKeyInAndTimestampBetween(
                OWNER_ID.getId(), hookId1, keys, timestamp1, timestamp3, PageRequest.of(1, LIMIT, sort));

        // then
        assertThat(page0Result).isNotNull().hasSize(expectedPage0.size()).containsExactlyElementsOf(expectedPage0);
        assertThat(page1Result).isNotNull().hasSize(expectedPage1.size()).containsExactlyElementsOf(expectedPage1);
    }

    @Test
    void findByKeyBetweenAndTimestampBetweenDifferentOwnerAndHookIdEmptyResults() {
        // given
        final var change1 = persistChange(OWNER_ID);
        final var hookId1 = change1.getHookId();
        final var key1 = change1.getKey();

        final var change2 = persistChange(OWNER_ID, hookId1, BytesUtil.incrementByteArray(key1));
        final var key2 = change2.getKey();

        final var change3 = persistChange(OWNER_ID, hookId1, BytesUtil.incrementByteArray(key2));
        final var key3 = change3.getKey();

        final var change4 = persistChange(OWNER_ID, hookId1, BytesUtil.incrementByteArray(key3));
        final var key4 = change4.getKey();

        final var sort = Sort.by(
                new Sort.Order(Direction.ASC, Constants.KEY), new Sort.Order(Direction.DESC, CONSENSUS_TIMESTAMP));

        // when
        final var timestamp1 = change1.getConsensusTimestamp();
        final var timestamp4 = change4.getConsensusTimestamp();

        final var differentOwnerQueryResult = repository.findByKeyBetweenAndTimestampBetween(
                OWNER_ID.getId() + 1, hookId1, key1, key4, timestamp1, timestamp4, PageRequest.of(0, LIMIT, sort));

        final var differentHookIdQueryResults = repository.findByKeyBetweenAndTimestampBetween(
                OWNER_ID.getId(), hookId1 + 1, key1, key4, timestamp1, timestamp4, PageRequest.of(0, LIMIT, sort));

        // then
        assertThat(differentOwnerQueryResult).isEmpty();
        assertThat(differentHookIdQueryResults).isEmpty();
    }

    @Test
    void findByKeyInAndTimestampBetweenDifferentOwnerAndHookIdEmptyResult() {
        // given
        final var change1 = persistChange(OWNER_ID);
        final var hookId1 = change1.getHookId();
        final var key1 = change1.getKey();

        final var change2 = persistChange(OWNER_ID, hookId1, BytesUtil.incrementByteArray(key1));
        final var key2 = change2.getKey();

        final var change3 = persistChange(OWNER_ID, hookId1, BytesUtil.incrementByteArray(key2));
        final var key3 = change3.getKey();

        final var keys = List.of(key1, key2, key3);

        final var sort = Sort.by(
                new Sort.Order(Direction.ASC, Constants.KEY), new Sort.Order(Direction.DESC, CONSENSUS_TIMESTAMP));
        // when
        final var timestamp1 = change1.getConsensusTimestamp();
        final var timestamp3 = change3.getConsensusTimestamp();

        final var differentOwnerQueryResult = repository.findByKeyInAndTimestampBetween(
                OWNER_ID.getId() + 1, hookId1, keys, timestamp1, timestamp3, PageRequest.of(0, LIMIT, sort));

        final var differentHookIdQueryResult = repository.findByKeyInAndTimestampBetween(
                OWNER_ID.getId(), hookId1 + 1, keys, timestamp1, timestamp3, PageRequest.of(1, LIMIT, sort));

        // then
        assertThat(differentOwnerQueryResult).isEmpty();
        assertThat(differentHookIdQueryResult).isEmpty();
    }

    @Test
    void findByKeyInAndTimestampBetweenOutOfRange() {
        // given
        final var change1 = persistChange(OWNER_ID);
        final var hookId1 = change1.getHookId();
        final var key1 = change1.getKey();

        final var change2 = persistChange(OWNER_ID, hookId1, BytesUtil.incrementByteArray(key1));
        final var key2 = change2.getKey();

        final var change3 = persistChange(OWNER_ID, hookId1, BytesUtil.incrementByteArray(key2));
        final var key3 = change3.getKey();

        final var sort = Sort.by(
                new Sort.Order(Direction.ASC, Constants.KEY), new Sort.Order(Direction.DESC, CONSENSUS_TIMESTAMP));
        // when
        final var timestamp1 = change1.getConsensusTimestamp();
        final var timestamp3 = change3.getConsensusTimestamp();

        final var keysOutOfRangeQueryResult = repository.findByKeyInAndTimestampBetween(
                OWNER_ID.getId(),
                hookId1,
                List.of(BytesUtil.incrementByteArray(key3)),
                timestamp1,
                timestamp3,
                PageRequest.of(0, LIMIT, sort));

        // then
        assertThat(keysOutOfRangeQueryResult).isEmpty();
    }

    @Test
    void findByKeyBetweenAndTimestampBetweenOutOfRange() {
        // given
        final var change1 = persistChange(OWNER_ID);
        final var hookId1 = change1.getHookId();
        final var key1 = change1.getKey();

        final var change2 = persistChange(OWNER_ID, hookId1, BytesUtil.incrementByteArray(key1));
        final var key2 = change2.getKey();

        final var change3 = persistChange(OWNER_ID, hookId1, BytesUtil.incrementByteArray(key2));
        final var key3 = change3.getKey();

        final var change4 = persistChange(OWNER_ID, hookId1, BytesUtil.incrementByteArray(key3));
        final var key4 = change4.getKey();

        final var sort = Sort.by(
                new Sort.Order(Direction.ASC, Constants.KEY), new Sort.Order(Direction.DESC, CONSENSUS_TIMESTAMP));

        // when
        final var timestamp1 = change1.getConsensusTimestamp();
        final var timestamp4 = change4.getConsensusTimestamp();

        final var timestampsOutOfRange = repository.findByKeyBetweenAndTimestampBetween(
                OWNER_ID.getId(), hookId1, key1, key4, timestamp4 + 1, Long.MAX_VALUE, PageRequest.of(0, LIMIT, sort));

        final var maxBytes = new byte[32];
        Arrays.fill(maxBytes, (byte) 0xFF);

        final var keysOutOfRange = repository.findByKeyBetweenAndTimestampBetween(
                OWNER_ID.getId(),
                hookId1,
                BytesUtil.incrementByteArray(key4),
                maxBytes,
                timestamp1,
                timestamp4,
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
}
