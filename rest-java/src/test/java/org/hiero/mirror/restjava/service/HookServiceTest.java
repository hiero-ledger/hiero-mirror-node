// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.restjava.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyList;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.hiero.mirror.common.domain.hook.Hook;
import org.hiero.mirror.common.domain.hook.HookStorage;
import org.hiero.mirror.restjava.RestJavaIntegrationTest;
import org.hiero.mirror.restjava.common.EntityIdParameter;
import org.hiero.mirror.restjava.dto.HookStorageRequest;
import org.hiero.mirror.restjava.dto.HooksRequest;
import org.hiero.mirror.restjava.repository.HookRepository;
import org.hiero.mirror.restjava.repository.HookStorageChangeRepository;
import org.hiero.mirror.restjava.repository.HookStorageRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.web3j.utils.Numeric;

@RequiredArgsConstructor
final class HookServiceTest extends RestJavaIntegrationTest {

    private static final String KEY_MIN = "0000000000000000000000000000000000000000000000000000000000000000";
    private static final String KEY_MAX = "FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFF";
    private static final byte[] KEY_MIN_BYTES = Numeric.hexStringToByteArray(KEY_MIN);
    private static final byte[] KEY_MAX_BYTES = Numeric.hexStringToByteArray(KEY_MAX);
    public static final int DEFAULT_LIMIT = 25;
    private static final long OWNER_ID = 1001L;
    private static final long TIMESTAMP_MIN = 1000L;
    private static final long TIMESTAMP_MAX = 5000L;

    private HookRepository hookRepository;
    private HookStorageRepository hookStorageRepository;
    private HookStorageChangeRepository hookStorageChangeRepository;
    private EntityService entityService;
    private HookService hookService;

    @BeforeEach
    void setup() {
        hookRepository = mock(HookRepository.class);
        hookStorageRepository = mock(HookStorageRepository.class);
        hookStorageChangeRepository = mock(HookStorageChangeRepository.class);
        entityService = mock(EntityService.class);
        hookService =
                new HookServiceImpl(hookRepository, hookStorageRepository, hookStorageChangeRepository, entityService);
    }

    @Test
    void getHooksEqFiltersCallsFindByOwnerIdAndHookIdIn() {
        // given
        final var ownerId = EntityId.of(OWNER_ID);
        final var hook = new Hook();
        when(entityService.lookup(any())).thenReturn(ownerId);
        when(hookRepository.findByOwnerIdAndHookIdIn(eq(ownerId.getId()), anyList(), any()))
                .thenReturn(List.of(hook));

        final var request = HooksRequest.builder()
                .ownerId(EntityIdParameter.valueOf(String.valueOf(OWNER_ID)))
                .hookIds(List.of(1L, 2L))
                .limit(DEFAULT_LIMIT)
                .order(Sort.Direction.DESC)
                .build();

        // when
        final var result = hookService.getHooks(request);

        // then
        assertThat(result).containsExactly(hook);
        verify(hookRepository).findByOwnerIdAndHookIdIn(eq(ownerId.getId()), eq(List.of(1L, 2L)), any());
    }

    @Test
    void getHooksRangeFiltersCallsFindByOwnerIdAndHookIdBetween() {
        // given
        final var ownerId = EntityId.of(OWNER_ID);
        final var hook = new Hook();
        when(entityService.lookup(any())).thenReturn(ownerId);
        when(hookRepository.findByOwnerIdAndHookIdBetween(eq(ownerId.getId()), eq(10L), eq(20L), any()))
                .thenReturn(List.of(hook));

        final var request = HooksRequest.builder()
                .ownerId(EntityIdParameter.valueOf(String.valueOf(OWNER_ID)))
                .lowerBound(10L)
                .upperBound(20L)
                .limit(DEFAULT_LIMIT)
                .order(Sort.Direction.ASC)
                .build();

        // when
        final var result = hookService.getHooks(request);

        // then
        assertThat(result).containsExactly(hook);
        verify(hookRepository).findByOwnerIdAndHookIdBetween(eq(ownerId.getId()), eq(10L), eq(20L), any());
    }

    @Test
    void getHooksEqAndRangeFiltersCallsFindByOwnerIdAndHookIdInFilteredIds() {
        // given
        final var ownerId = EntityId.of(OWNER_ID);
        final var hook = new Hook();
        when(entityService.lookup(any())).thenReturn(ownerId);
        when(hookRepository.findByOwnerIdAndHookIdIn(eq(ownerId.getId()), eq(List.of(15L)), any()))
                .thenReturn(List.of(hook));

        final var request = HooksRequest.builder()
                .ownerId(EntityIdParameter.valueOf(String.valueOf(OWNER_ID)))
                .hookIds(List.of(5L, 15L, 25L))
                .lowerBound(10L)
                .upperBound(20L)
                .limit(DEFAULT_LIMIT)
                .order(Sort.Direction.ASC)
                .build();

        // when
        final var result = hookService.getHooks(request);

        // then
        assertThat(result).containsExactly(hook);
        verify(hookRepository).findByOwnerIdAndHookIdIn(eq(ownerId.getId()), eq(List.of(15L)), any());
    }

    @Test
    void getHooksEqAndRangeFiltersNoIdsInRangeReturnsEmpty() {
        // given
        final var ownerId = EntityId.of(OWNER_ID);
        when(entityService.lookup(any())).thenReturn(ownerId);

        final var request = HooksRequest.builder()
                .ownerId(EntityIdParameter.valueOf(String.valueOf(OWNER_ID)))
                .hookIds(List.of(1L, 2L))
                .lowerBound(10L)
                .upperBound(20L)
                .limit(DEFAULT_LIMIT)
                .build();

        // when
        final var result = hookService.getHooks(request);

        // then
        assertThat(result).isEmpty();
        verifyNoInteractions(hookRepository);
    }

    @Test
    void getHookStorageEmptyKeysCallsKeyBetween() {
        // given
        final var ownerId = EntityId.of(OWNER_ID);
        final var hookStorage = new HookStorage();
        when(hookStorageRepository.findByOwnerIdAndHookIdAndKeyBetweenAndDeletedIsFalse(
                        eq(ownerId.getId()), eq(1L), any(byte[].class), any(byte[].class), any()))
                .thenReturn(List.of(hookStorage));

        final var request = HookStorageRequest.builder()
                .ownerId(ownerId)
                .hookId(1L)
                .keys(List.of()) // empty keys
                .keyLowerBound(KEY_MIN_BYTES)
                .keyUpperBound(KEY_MAX_BYTES)
                .limit(DEFAULT_LIMIT)
                .order(Sort.Direction.ASC)
                .build();

        // when
        final var result = hookService.getHookStorage(request);

        // then
        assertThat(result).containsExactly(hookStorage);
        verify(hookStorageRepository)
                .findByOwnerIdAndHookIdAndKeyBetweenAndDeletedIsFalse(
                        eq(ownerId.getId()),
                        eq(1L),
                        eq(request.getKeyLowerBound()),
                        eq(request.getKeyUpperBound()),
                        any(PageRequest.class));
    }

    @Test
    void getHookStorageNonEmptyKeysCallsKeyIn() {
        // given
        final var ownerId = EntityId.of(OWNER_ID);

        final var hookStorage = new HookStorage();
        when(hookStorageRepository.findByOwnerIdAndHookIdAndKeyInAndDeletedIsFalse(
                        eq(ownerId.getId()), eq(1L), anyList(), any()))
                .thenReturn(List.of(hookStorage));

        final var request = HookStorageRequest.builder()
                .ownerId(ownerId)
                .hookId(1L)
                .keys(List.of(KEY_MIN, KEY_MAX))
                .keyLowerBound(KEY_MIN_BYTES)
                .keyUpperBound(KEY_MAX_BYTES)
                .limit(DEFAULT_LIMIT)
                .order(Sort.Direction.ASC)
                .build();

        // when
        final var result = hookService.getHookStorage(request);

        // then
        assertThat(result).containsExactly(hookStorage);
        verify(hookStorageRepository)
                .findByOwnerIdAndHookIdAndKeyInAndDeletedIsFalse(
                        eq(ownerId.getId()),
                        eq(1L),
                        argThat(actual -> {
                            if (actual.size() != 2) return false;
                            return Arrays.equals(actual.get(0), KEY_MIN_BYTES)
                                    && Arrays.equals(actual.get(1), KEY_MAX_BYTES);
                        }),
                        any(PageRequest.class));
    }

    @Test
    void getHookStorageKeysOutOfRangeDoesNotCallRepositoryReturnsEmptyList() {
        // given
        final var ownerId = EntityId.of(OWNER_ID);

        // keys are outside the [lower, upper] range
        final var outOfRangeKey1 = "FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFE";
        final var outOfRangeKey2 = "FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFF";

        final var request = HookStorageRequest.builder()
                .ownerId(ownerId)
                .hookId(1L)
                .keys(List.of(outOfRangeKey1, outOfRangeKey2))
                .keyLowerBound(Numeric.hexStringToByteArray(
                        "0000000000000000000000000000000000000000000000000000000000000000"))
                .keyUpperBound(Numeric.hexStringToByteArray(
                        "00000000000000000000000000000000000000000000000000000000000000FF"))
                .limit(DEFAULT_LIMIT)
                .order(Sort.Direction.ASC)
                .build();

        // when
        final var result = hookService.getHookStorage(request);

        // then
        assertThat(result).isEmpty();

        // verify repository was never called
        verify(hookStorageRepository, never())
                .findByOwnerIdAndHookIdAndKeyInAndDeletedIsFalse(
                        anyLong(), anyLong(), anyList(), any(PageRequest.class));
        verify(hookStorageRepository, never())
                .findByOwnerIdAndHookIdAndKeyBetweenAndDeletedIsFalse(
                        anyLong(), anyLong(), any(), any(), any(PageRequest.class));
    }

    @Test
    void getHookStorageChangeEmptyKeysAndTimestamps() {
        final var ownerId = EntityId.of(OWNER_ID);

        final var request = HookStorageRequest.builder()
                .ownerId(ownerId)
                .hookId(1L)
                .keys(List.of())
                .timestamp(List.of())
                .keyLowerBound(KEY_MIN_BYTES)
                .keyUpperBound(KEY_MAX_BYTES)
                .timestampLowerBound(TIMESTAMP_MIN)
                .timestampUpperBound(TIMESTAMP_MAX)
                .limit(DEFAULT_LIMIT)
                .order(Sort.Direction.ASC)
                .build();

        hookService.getHookStorageChange(request);

        verify(hookStorageChangeRepository)
                .findLatestChangePerKeyInTimestampRangeForKeyRangeOrderByKeyAsc(
                        eq(ownerId.getId()),
                        eq(1L),
                        eq(KEY_MIN_BYTES),
                        eq(KEY_MAX_BYTES),
                        eq(TIMESTAMP_MIN),
                        eq(TIMESTAMP_MAX),
                        any());
    }

    @Test
    void getHookStorageChangeNoTimestampsKeysInRange() {
        final var ownerId = EntityId.of(OWNER_ID);

        final var request = HookStorageRequest.builder()
                .ownerId(ownerId)
                .hookId(1L)
                .keys(List.of(KEY_MIN, KEY_MAX))
                .timestamp(List.of())
                .keyLowerBound(KEY_MIN_BYTES)
                .keyUpperBound(KEY_MAX_BYTES)
                .timestampLowerBound(TIMESTAMP_MIN)
                .timestampUpperBound(TIMESTAMP_MAX)
                .limit(DEFAULT_LIMIT)
                .order(Sort.Direction.ASC)
                .build();

        hookService.getHookStorageChange(request);

        verify(hookStorageChangeRepository)
                .findLatestChangePerKeyInTimestampRangeForKeysOrderByKeyAsc(
                        eq(ownerId.getId()),
                        eq(1L),
                        argThat(list -> list.size() == 2),
                        eq(TIMESTAMP_MIN),
                        eq(TIMESTAMP_MAX),
                        any());
    }

    @Test
    void getHookStorageChangeNoKeysTimestampsInRange() {
        final var ownerId = EntityId.of(OWNER_ID);

        final var request = HookStorageRequest.builder()
                .ownerId(ownerId)
                .hookId(1L)
                .keys(List.of())
                .timestamp(List.of(TIMESTAMP_MIN, TIMESTAMP_MAX))
                .keyLowerBound(KEY_MIN_BYTES)
                .keyUpperBound(KEY_MAX_BYTES)
                .timestampLowerBound(TIMESTAMP_MIN)
                .timestampUpperBound(TIMESTAMP_MAX)
                .limit(DEFAULT_LIMIT)
                .order(Sort.Direction.ASC)
                .build();

        hookService.getHookStorageChange(request);

        verify(hookStorageChangeRepository)
                .findLatestChangePerKeyForTimestampListAndKeyRangeOrderByKeyAsc(
                        eq(ownerId.getId()),
                        eq(1L),
                        eq(KEY_MIN_BYTES),
                        eq(KEY_MAX_BYTES),
                        argThat(list -> list.size() == 2),
                        any());
    }

    @Test
    void getHookStorageChangeKeysAndTimestampsInRange() {
        final var ownerId = EntityId.of(OWNER_ID);

        final var request = HookStorageRequest.builder()
                .ownerId(ownerId)
                .hookId(1L)
                .keys(List.of(KEY_MIN, KEY_MAX))
                .timestamp(List.of(TIMESTAMP_MIN, TIMESTAMP_MAX))
                .keyLowerBound(KEY_MIN_BYTES)
                .keyUpperBound(KEY_MAX_BYTES)
                .timestampLowerBound(TIMESTAMP_MIN)
                .timestampUpperBound(TIMESTAMP_MAX)
                .limit(DEFAULT_LIMIT)
                .order(Sort.Direction.ASC)
                .build();

        hookService.getHookStorageChange(request);

        verify(hookStorageChangeRepository)
                .findLatestChangePerKeyForKeyListAndTimestampListOrderByKeyAsc(
                        eq(ownerId.getId()),
                        eq(1L),
                        argThat(list -> list.size() == 2),
                        argThat(list -> list.size() == 2),
                        any());
    }

    @Test
    void getHookStorageChangeKeysOutOfRangeReturnsEmpty() {
        final var ownerId = EntityId.of(OWNER_ID);

        final var request = HookStorageRequest.builder()
                .ownerId(ownerId)
                .hookId(1L)
                .keys(List.of(KEY_MAX)) // out of key range
                .timestamp(List.of())
                .keyLowerBound(KEY_MIN_BYTES)
                .keyUpperBound(KEY_MIN_BYTES) // exclusive range
                .timestampLowerBound(TIMESTAMP_MIN)
                .timestampUpperBound(TIMESTAMP_MAX)
                .limit(DEFAULT_LIMIT)
                .order(Sort.Direction.ASC)
                .build();

        final var result = hookService.getHookStorageChange(request);

        assertThat(result).isEmpty();
        verifyNoInteractions(hookStorageChangeRepository);
    }

    @Test
    void getHookStorageChangeTimestampsOutOfRangeReturnsEmpty() {
        final var ownerId = EntityId.of(OWNER_ID);

        final var request = HookStorageRequest.builder()
                .ownerId(ownerId)
                .hookId(1L)
                .keys(List.of())
                .timestamp(List.of(TIMESTAMP_MAX + 1)) // out of timestamp range
                .keyLowerBound(KEY_MIN_BYTES)
                .keyUpperBound(KEY_MAX_BYTES)
                .timestampLowerBound(TIMESTAMP_MIN)
                .timestampUpperBound(TIMESTAMP_MAX)
                .limit(DEFAULT_LIMIT)
                .order(Sort.Direction.ASC)
                .build();

        final var result = hookService.getHookStorageChange(request);

        assertThat(result).isEmpty();
        verifyNoInteractions(hookStorageChangeRepository);
    }
}
