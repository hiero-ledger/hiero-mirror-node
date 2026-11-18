// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.restjava.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.hiero.mirror.restjava.common.Constants.TIMESTAMP;
import static org.hiero.mirror.restjava.jooq.domain.Tables.HOOK_STORAGE_CHANGE;
import static org.springframework.data.domain.Sort.Direction.ASC;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.ArrayUtils;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.hiero.mirror.common.domain.hook.Hook;
import org.hiero.mirror.common.domain.hook.HookStorage;
import org.hiero.mirror.common.domain.hook.HookStorageChange;
import org.hiero.mirror.common.util.CommonUtils;
import org.hiero.mirror.common.util.RangeUtils;
import org.hiero.mirror.restjava.RestJavaIntegrationTest;
import org.hiero.mirror.restjava.common.RangeOperator;
import org.hiero.mirror.restjava.dto.HookStorageRequest;
import org.hiero.mirror.restjava.dto.HookStorageSlot;
import org.hiero.mirror.restjava.dto.HooksRequest;
import org.hiero.mirror.restjava.parameter.TimestampParameter;
import org.hiero.mirror.restjava.service.Bound;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.data.domain.Sort.Direction;

@RequiredArgsConstructor
final class HookRepositoryTest extends RestJavaIntegrationTest {

    private static final long HOOK_ID = 2000L;
    private static final byte[] KEY_MAX = filledKey((byte) 0xFF);
    private static final byte[] KEY_MIN = filledKey((byte) 0);
    private static final int LIMIT = 2;
    private static final long OWNER_ID_1 = 1000L;
    private static final long OWNER_ID_2 = 9999L;

    private final HookRepository hookRepository;

    @Test
    @DisplayName("findHooks returns the owner's hooks matching the explicit hook ids")
    void findHooksByExplicitIds() {
        // given
        final var hook1 = persistHook(OWNER_ID_1, 1L);
        persistHook(OWNER_ID_1, 2L);
        final var hook3 = persistHook(OWNER_ID_1, 3L);
        persistHook(OWNER_ID_2, 1L);

        final var request =
                HooksRequest.builder().hookIds(List.of(1L, 3L, 5L)).order(ASC).build();

        // when
        final var result = hookRepository.findHooks(request, OWNER_ID_1);

        // then
        assertThat(result).containsExactly(hook1, hook3);
    }

    @Test
    @DisplayName("findHooks returns deleted hooks")
    void findHooksIncludesDeleted() {
        // given
        persistHook(OWNER_ID_1, 1L);
        domainBuilder
                .hook()
                .customize(hook -> hook.ownerId(OWNER_ID_1).hookId(2L).deleted(true))
                .persist();

        final var request = HooksRequest.builder().order(ASC).build();

        // when
        final var result = hookRepository.findHooks(request, OWNER_ID_1);

        // then
        assertThat(result)
                .extracting(Hook::getHookId, Hook::getDeleted)
                .containsExactly(tuple(1L, false), tuple(2L, true));
    }

    @ParameterizedTest
    @EnumSource(Direction.class)
    @DisplayName("findHooks returns the owner's hooks within the inclusive hook id range")
    void findHooksByRange(Direction order) {
        // given
        persistHook(OWNER_ID_1, 1L);
        final var hook2 = persistHook(OWNER_ID_1, 2L);
        final var hook3 = persistHook(OWNER_ID_1, 3L);
        final var hook4 = persistHook(OWNER_ID_1, 4L);
        persistHook(OWNER_ID_1, 5L);
        persistHook(OWNER_ID_2, 3L);

        final var request = HooksRequest.builder()
                .lowerBound(2L)
                .order(order)
                .upperBound(4L)
                .build();
        final var expected = order.isAscending() ? List.of(hook2, hook3, hook4) : List.of(hook4, hook3, hook2);

        // when
        final var result = hookRepository.findHooks(request, OWNER_ID_1);

        // then
        assertThat(result).containsExactlyElementsOf(expected);
    }

    @Test
    @DisplayName("findHooks returns empty when the range excludes all hooks")
    void findHooksRangeEmpty() {
        // given
        persistHook(OWNER_ID_1, 1L);
        persistHook(OWNER_ID_1, 2L);

        final var request =
                HooksRequest.builder().lowerBound(100L).upperBound(200L).build();

        // when, then
        assertThat(hookRepository.findHooks(request, OWNER_ID_1)).isEmpty();
    }

    @Test
    @DisplayName("findHooks intersects the explicit hook ids with the range")
    void findHooksIntersection() {
        // given
        persistHook(OWNER_ID_1, 5L);
        final var hook15 = persistHook(OWNER_ID_1, 15L);
        persistHook(OWNER_ID_1, 25L);

        final var request = HooksRequest.builder()
                .hookIds(List.of(5L, 15L, 25L))
                .lowerBound(10L)
                .upperBound(20L)
                .build();

        // when, then
        assertThat(hookRepository.findHooks(request, OWNER_ID_1)).containsExactly(hook15);
    }

    @Test
    @DisplayName("findHooks respects the limit")
    void findHooksLimit() {
        // given
        persistHook(OWNER_ID_1, 1L);
        final var hook2 = persistHook(OWNER_ID_1, 2L);
        final var hook3 = persistHook(OWNER_ID_1, 3L);

        final var request = HooksRequest.builder().limit(LIMIT).build();

        // when, then
        assertThat(hookRepository.findHooks(request, OWNER_ID_1)).containsExactly(hook3, hook2);
    }

    @Test
    @DisplayName("findHooks maps a null contract id")
    void findHooksNullContractId() {
        // given
        final var hook = domainBuilder
                .hook()
                .customize(h -> h.contractId(null).hookId(1L).ownerId(OWNER_ID_1))
                .persist();

        // when
        final var result = hookRepository.findHooks(HooksRequest.builder().build(), OWNER_ID_1);

        // then
        assertThat(result).containsExactly(hook);
        assertThat(result.getFirst().getContractId()).isNull();
    }

    @Test
    @DisplayName("findHooks maps an empty timestamp range")
    void findHooksEmptyTimestampRange() {
        // given
        final var hook = domainBuilder
                .hook()
                .customize(h -> h.hookId(1L).ownerId(OWNER_ID_1).timestampRange(RangeUtils.EMPTY_LONG_RANGE))
                .persist();

        // when, then
        assertThat(hookRepository.findHooks(HooksRequest.builder().build(), OWNER_ID_1))
                .containsExactly(hook);
    }

    @ParameterizedTest
    @EnumSource(Direction.class)
    void findHookStorageByKeys(Direction order) {
        // given
        final var keys = generateKeys(6);
        final var ownerId = domainBuilder.entityId();

        final var storage1 = persistHookStorage(ownerId, HOOK_ID, keys.get(0));
        persistHookStorage(ownerId, HOOK_ID, keys.get(1)); // not requested
        final var storage3 = persistHookStorage(ownerId, HOOK_ID, keys.get(2), ArrayUtils.EMPTY_BYTE_ARRAY); // deleted
        final var storage4 = persistHookStorage(ownerId, HOOK_ID, keys.get(3));
        final var storage5 = persistHookStorage(EntityId.of(ownerId.getId() + 1), HOOK_ID, keys.get(4));
        final var storage6 = persistHookStorage(ownerId, HOOK_ID + 1, keys.get(5));

        final var request = storageRequest()
                .keys(List.of(
                        storage1.getKey(),
                        storage3.getKey(), // deleted
                        storage4.getKey(),
                        CommonUtils.nextBytes(32), // non-existing
                        storage5.getKey(), // different owner id
                        storage6.getKey() // different hook id
                        ))
                .order(order)
                .build();
        final var expected = order.isAscending() ? List.of(storage1, storage4) : List.of(storage4, storage1);

        // when
        final var result = hookRepository.findHookStorage(request, ownerId.getId());

        // then
        assertSlots(result, expected.stream().map(HookRepositoryTest::toSlot));
    }

    @ParameterizedTest
    @EnumSource(Direction.class)
    void findHookStorageByKeyRange(Direction order) {
        // given
        final var keys = generateKeys(6);
        final var ownerId = domainBuilder.entityId();

        final var storage1 = persistHookStorage(ownerId, HOOK_ID, keys.get(0));
        final var storage2 = persistHookStorage(ownerId, HOOK_ID, keys.get(1));
        persistHookStorage(ownerId, HOOK_ID, keys.get(2), ArrayUtils.EMPTY_BYTE_ARRAY); // deleted
        final var storage4 = persistHookStorage(ownerId, HOOK_ID, keys.get(3));
        persistHookStorage(EntityId.of(ownerId.getId() + 1), HOOK_ID, keys.get(4)); // different owner id
        final var storage6 = persistHookStorage(ownerId, HOOK_ID + 1, keys.get(5)); // different hook id

        final var request = storageRequest()
                .keyLowerBound(storage1.getKey())
                .keyUpperBound(storage6.getKey())
                .order(order)
                .build();
        final var expected =
                order.isAscending() ? List.of(storage1, storage2, storage4) : List.of(storage4, storage2, storage1);

        // when
        final var result = hookRepository.findHookStorage(request, ownerId.getId());

        // then
        assertSlots(result, expected.stream().map(HookRepositoryTest::toSlot));
    }

    @ParameterizedTest
    @EnumSource(Direction.class)
    void findHookStorageLimit(Direction order) {
        // given
        final var keys = generateKeys(4);
        final var ownerId = domainBuilder.entityId();

        final var storage1 = persistHookStorage(ownerId, HOOK_ID, keys.get(0));
        final var storage2 = persistHookStorage(ownerId, HOOK_ID, keys.get(1));
        final var storage3 = persistHookStorage(ownerId, HOOK_ID, keys.get(2));
        final var storage4 = persistHookStorage(ownerId, HOOK_ID, keys.get(3));

        final var request = storageRequest().limit(LIMIT).order(order).build();
        final var expected = order.isAscending() ? List.of(storage1, storage2) : List.of(storage4, storage3);

        // when
        final var result = hookRepository.findHookStorage(request, ownerId.getId());

        // then
        assertSlots(result, expected.stream().map(HookRepositoryTest::toSlot));
    }

    @Test
    @DisplayName("findHookStorage matches only the keys within the key range")
    void findHookStorageKeysWithRange() {
        // given
        final var keys = generateKeys(3);
        final var ownerId = domainBuilder.entityId();

        persistHookStorage(ownerId, HOOK_ID, keys.get(0));
        final var storage3 = persistHookStorage(ownerId, HOOK_ID, keys.get(2));

        final var request = storageRequest()
                .keys(List.of(keys.get(0), keys.get(2)))
                .keyLowerBound(keys.get(1))
                .build();

        // when
        final var result = hookRepository.findHookStorage(request, ownerId.getId());

        // then
        assertSlots(result, Stream.of(toSlot(storage3)));
    }

    @Test
    void findHookStorageKeysOutOfRange() {
        // given
        final var keys = generateKeys(3);
        final var ownerId = domainBuilder.entityId();

        persistHookStorage(ownerId, HOOK_ID, keys.get(0));
        persistHookStorage(ownerId, HOOK_ID, keys.get(2));

        final var request = storageRequest()
                .keys(List.of(keys.get(0), keys.get(2)))
                .keyLowerBound(keys.get(1))
                .keyUpperBound(keys.get(1))
                .build();

        // when, then
        assertThat(hookRepository.findHookStorage(request, ownerId.getId())).isEmpty();
    }

    @ParameterizedTest
    @EnumSource(Direction.class)
    void findHistoricalHookStorageByKeyRange(Direction order) {
        // given
        final var keys = generateKeys(5);
        final var ownerId = domainBuilder.entityId();

        final var change1 = persistChange(ownerId, HOOK_ID, keys.get(0));
        final var change2 = persistChange(ownerId, HOOK_ID, keys.get(1));
        persistChange(ownerId, HOOK_ID + 1, keys.get(2)); // different hook id
        final var change4 = persistChange(ownerId, HOOK_ID, keys.get(3));
        final var change5 = persistChange(ownerId, HOOK_ID, keys.get(4));
        persistChange(EntityId.of(ownerId.getId() + 1), HOOK_ID, keys.get(4)); // different owner id

        final var request = storageRequest()
                .keyLowerBound(change1.getKey())
                .keyUpperBound(change5.getKey())
                .order(order)
                .timestamp(timestampBound(change1.getConsensusTimestamp(), change5.getConsensusTimestamp()))
                .build();
        final var expected = order.isAscending()
                ? List.of(change1, change2, change4, change5)
                : List.of(change5, change4, change2, change1);

        // when
        final var result = hookRepository.findHookStorage(request, ownerId.getId());

        // then
        assertSlots(result, expected.stream().map(this::toSlot));
    }

    @Test
    void findHistoricalHookStoragePartialOverlap() {
        // given
        final var keys = generateKeys(5);
        final var ownerId = domainBuilder.entityId();

        final var change1 = persistChange(ownerId, HOOK_ID, keys.get(0));
        final var change2 = persistChange(ownerId, HOOK_ID, keys.get(1));
        final var change3 = persistChange(ownerId, HOOK_ID, keys.get(2));
        final var change4 = persistChange(ownerId, HOOK_ID, keys.get(3));
        final var change5 = persistChange(ownerId, HOOK_ID, keys.get(4));

        final var keyOverlap = storageRequest()
                .keyLowerBound(change2.getKey())
                .keyUpperBound(change4.getKey())
                .timestamp(timestampBound(change1.getConsensusTimestamp(), change4.getConsensusTimestamp()))
                .build();
        final var timestampOverlap = storageRequest()
                .keyLowerBound(change1.getKey())
                .keyUpperBound(change5.getKey())
                .timestamp(timestampBound(change2.getConsensusTimestamp(), change4.getConsensusTimestamp()))
                .build();
        final var expected = List.of(change2, change3, change4);

        // when
        final var keyOverlapResult = hookRepository.findHookStorage(keyOverlap, ownerId.getId());
        final var timestampOverlapResult = hookRepository.findHookStorage(timestampOverlap, ownerId.getId());

        // then
        assertSlots(keyOverlapResult, expected.stream().map(this::toSlot));
        assertSlots(timestampOverlapResult, expected.stream().map(this::toSlot));
    }

    @ParameterizedTest
    @EnumSource(Direction.class)
    void findHistoricalHookStorageByKeys(Direction order) {
        // given
        final var keys = generateKeys(5);
        final var ownerId = domainBuilder.entityId();

        final var change1 = persistChange(ownerId, HOOK_ID, keys.get(0));
        final var change2 = persistChange(ownerId, HOOK_ID, keys.get(1));
        final var change3 = persistChange(ownerId, HOOK_ID + 1, keys.get(2)); // different hook id
        final var change4 = persistChange(ownerId, HOOK_ID, keys.get(3));
        persistChange(ownerId, HOOK_ID, keys.get(4)); // not requested

        final var request = storageRequest()
                .keys(List.of(change1.getKey(), change2.getKey(), change3.getKey(), change4.getKey()))
                .limit(LIMIT)
                .order(order)
                .timestamp(timestampBound(change1.getConsensusTimestamp(), Long.MAX_VALUE))
                .build();
        final var expected = order.isAscending() ? List.of(change1, change2) : List.of(change4, change2);

        // when
        final var result = hookRepository.findHookStorage(request, ownerId.getId());

        // then
        assertSlots(result, expected.stream().map(this::toSlot));
    }

    @Test
    @DisplayName("findHookStorage matches all keys once any key is within the key range")
    void findHistoricalHookStorageKeysWithRange() {
        // given
        final var keys = generateKeys(3);
        final var ownerId = domainBuilder.entityId();

        final var change1 = persistChange(ownerId, HOOK_ID, keys.get(0));
        final var change3 = persistChange(ownerId, HOOK_ID, keys.get(2));

        final var request = storageRequest()
                .keys(List.of(keys.get(0), keys.get(2)))
                .keyLowerBound(keys.get(1))
                .timestamp(timestampBound(change1.getConsensusTimestamp(), change3.getConsensusTimestamp()))
                .build();

        // when
        final var result = hookRepository.findHookStorage(request, ownerId.getId());

        // then
        assertSlots(result, Stream.of(change1, change3).map(this::toSlot));
    }

    @Test
    void findHistoricalHookStorageOutOfRange() {
        // given
        final var keys = generateKeys(5);
        final var ownerId = domainBuilder.entityId();

        final var change1 = persistChange(ownerId, HOOK_ID, keys.get(0));
        persistChange(ownerId, HOOK_ID, keys.get(1));
        final var change3 = persistChange(ownerId, HOOK_ID, keys.get(2));

        final var timestampsOutOfRange = storageRequest()
                .timestamp(timestampBound(change3.getConsensusTimestamp() + 1, Long.MAX_VALUE))
                .build();
        final var keysOutOfRange = storageRequest()
                .keyLowerBound(keys.get(3))
                .timestamp(timestampBound(change1.getConsensusTimestamp(), change3.getConsensusTimestamp()))
                .build();
        final var keysNotFound = storageRequest()
                .keys(List.of(keys.get(3)))
                .timestamp(timestampBound(change1.getConsensusTimestamp(), change3.getConsensusTimestamp()))
                .build();

        // when, then
        assertThat(hookRepository.findHookStorage(timestampsOutOfRange, ownerId.getId()))
                .isEmpty();
        assertThat(hookRepository.findHookStorage(keysOutOfRange, ownerId.getId()))
                .isEmpty();
        assertThat(hookRepository.findHookStorage(keysNotFound, ownerId.getId()))
                .isEmpty();
    }

    @Test
    @DisplayName("findHookStorage returns only the latest change per key within the timestamp range")
    void findHistoricalHookStorageLatestChangePerKey() {
        // given
        final var key = generateKeys(1).getFirst();
        final var ownerId = domainBuilder.entityId();

        final var first = persistChange(ownerId, HOOK_ID, key);
        final var second = persistChange(ownerId, HOOK_ID, key);
        persistChange(ownerId, HOOK_ID, key); // after the timestamp range

        final var request = storageRequest()
                .timestamp(timestampBound(first.getConsensusTimestamp(), second.getConsensusTimestamp()))
                .build();

        // when
        final var result = hookRepository.findHookStorage(request, ownerId.getId());

        // then
        assertSlots(result, Stream.of(toSlot(second)));
    }

    @Test
    @DisplayName("findHookStorage returns a null value for a read-only change")
    void findHistoricalHookStorageReadOnlyChange() {
        // given
        final var ownerId = domainBuilder.entityId();
        final var change = domainBuilder
                .hookStorageChange()
                .customize(c -> c.hookId(HOOK_ID).ownerId(ownerId.getId()).valueWritten(null))
                .persist();

        final var request = storageRequest()
                .timestamp(timestampBound(change.getConsensusTimestamp(), change.getConsensusTimestamp()))
                .build();

        // when
        final var result = hookRepository.findHookStorage(request, ownerId.getId());

        // then
        assertSlots(result, Stream.of(new HookStorageSlot(change.getKey(), change.getConsensusTimestamp(), null)));
    }

    private static void assertSlots(Collection<HookStorageSlot> actual, Stream<HookStorageSlot> expected) {
        assertThat(actual).usingRecursiveFieldByFieldElementComparator().containsExactlyElementsOf(expected.toList());
    }

    private static byte[] filledKey(byte value) {
        final var key = new byte[32];
        Arrays.fill(key, value);
        return key;
    }

    private static HookStorageRequest.HookStorageRequestBuilder storageRequest() {
        return HookStorageRequest.builder()
                .hookId(HOOK_ID)
                .keyLowerBound(KEY_MIN)
                .keyUpperBound(KEY_MAX)
                .order(ASC);
    }

    private static Bound timestampBound(long lower, long upper) {
        final var timestamps = new TimestampParameter[] {
            new TimestampParameter(RangeOperator.GTE, lower), new TimestampParameter(RangeOperator.LTE, upper)
        };
        return Bound.of(timestamps, TIMESTAMP, HOOK_STORAGE_CHANGE.CONSENSUS_TIMESTAMP);
    }

    private static HookStorageSlot toSlot(HookStorage storage) {
        return new HookStorageSlot(storage.getKey(), storage.getModifiedTimestamp(), storage.getValue());
    }

    private Hook persistHook(long ownerId, long hookId) {
        return domainBuilder
                .hook()
                .customize(hook -> hook.ownerId(ownerId).hookId(hookId))
                .persist();
    }

    private HookStorageChange persistChange(EntityId ownerId, long hookId, byte[] key) {
        return domainBuilder
                .hookStorageChange()
                .customize(change -> change.hookId(hookId).key(key).ownerId(ownerId.getId()))
                .persist();
    }

    private HookStorage persistHookStorage(EntityId ownerId, long hookId, byte[] key) {
        return domainBuilder
                .hookStorage()
                .customize(storage -> storage.hookId(hookId).key(key).ownerId(ownerId.getId()))
                .persist();
    }

    private HookStorage persistHookStorage(EntityId ownerId, long hookId, byte[] key, byte[] value) {
        return domainBuilder
                .hookStorage()
                .customize(storage ->
                        storage.hookId(hookId).key(key).ownerId(ownerId.getId()).value(value))
                .persist();
    }

    private HookStorageSlot toSlot(HookStorageChange change) {
        return toSlot(hookStorage(change));
    }
}
