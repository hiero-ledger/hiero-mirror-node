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
import org.bouncycastle.util.encoders.Hex;
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
        final var ownerId = String.valueOf(OWNER_ID);
        final var hookStorage = new HookStorage();

        when(entityService.lookup(any())).thenReturn(EntityId.of(OWNER_ID));
        when(hookStorageRepository.findByOwnerIdAndHookIdAndKeyBetweenAndDeletedIsFalse(
                        eq(OWNER_ID), eq(1L), any(byte[].class), any(byte[].class), any()))
                .thenReturn(List.of(hookStorage));

        final var request = HookStorageRequest.builder()
                .ownerId(EntityIdParameter.valueOf(ownerId))
                .hookId(1L)
                .keys(List.of())
                .keyLowerBound(KEY_MIN_BYTES)
                .keyUpperBound(KEY_MAX_BYTES)
                .limit(DEFAULT_LIMIT)
                .order(Sort.Direction.ASC)
                .build();

        // when
        final var result = hookService.getHookStorage(request).storage();

        // then
        assertThat(result).containsExactly(hookStorage);
        verify(hookStorageRepository)
                .findByOwnerIdAndHookIdAndKeyBetweenAndDeletedIsFalse(
                        eq(OWNER_ID),
                        eq(1L),
                        eq(request.getKeyLowerBound()),
                        eq(request.getKeyUpperBound()),
                        any(PageRequest.class));
    }

    @Test
    void getHookStorageNonEmptyKeysCallsKeyIn() {
        // given
        final var hookStorage = new HookStorage();

        when(entityService.lookup(any())).thenReturn(EntityId.of(OWNER_ID));
        when(hookStorageRepository.findByOwnerIdAndHookIdAndKeyInAndDeletedIsFalse(
                        eq(OWNER_ID), eq(1L), anyList(), any()))
                .thenReturn(List.of(hookStorage));

        final var request = HookStorageRequest.builder()
                .ownerId(EntityIdParameter.valueOf(String.valueOf(OWNER_ID)))
                .hookId(1L)
                .keys(List.of(KEY_MIN_BYTES, KEY_MAX_BYTES))
                .keyLowerBound(KEY_MIN_BYTES)
                .keyUpperBound(KEY_MAX_BYTES)
                .limit(DEFAULT_LIMIT)
                .order(Sort.Direction.ASC)
                .build();

        // when
        final var result = hookService.getHookStorage(request).storage();

        // then
        assertThat(result).containsExactly(hookStorage);
        verify(hookStorageRepository)
                .findByOwnerIdAndHookIdAndKeyInAndDeletedIsFalse(
                        eq(OWNER_ID),
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

        when(entityService.lookup(any())).thenReturn(ownerId);

        final var outOfRangeKey1 = "FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFE";
        final var outOfRangeKey2 = "FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFF";

        final var request = HookStorageRequest.builder()
                .ownerId(EntityIdParameter.valueOf(String.valueOf(OWNER_ID)))
                .hookId(1L)
                .keys(List.of(Hex.decode(outOfRangeKey1), Hex.decode(outOfRangeKey2)))
                .keyLowerBound(Numeric.hexStringToByteArray(KEY_MIN))
                .keyUpperBound(Numeric.hexStringToByteArray(
                        "00000000000000000000000000000000000000000000000000000000000000FF"))
                .limit(DEFAULT_LIMIT)
                .order(Sort.Direction.ASC)
                .build();

        // when
        final var result = hookService.getHookStorage(request).storage();

        // then
        assertThat(result).isEmpty();
        verify(hookStorageRepository, never())
                .findByOwnerIdAndHookIdAndKeyInAndDeletedIsFalse(anyLong(), anyLong(), anyList(), any());
        verify(hookStorageRepository, never())
                .findByOwnerIdAndHookIdAndKeyBetweenAndDeletedIsFalse(anyLong(), anyLong(), any(), any(), any());
    }

    @Test
    void getHookStorageChangeKeysOutOfRangeReturnsEmpty() {
        // given
        final var ownerId = EntityId.of(OWNER_ID);
        when(entityService.lookup(any())).thenReturn(ownerId);

        final var request = HookStorageRequest.builder()
                .ownerId(EntityIdParameter.valueOf(String.valueOf(OWNER_ID)))
                .hookId(1L)
                .keys(List.of(KEY_MAX_BYTES)) // out of key range
                .keyLowerBound(KEY_MIN_BYTES)
                .keyUpperBound(KEY_MIN_BYTES) // exclusive
                .limit(DEFAULT_LIMIT)
                .order(Sort.Direction.ASC)
                .build();

        // when
        final var result = hookService.getHookStorage(request).storage();

        // then
        assertThat(result).isEmpty();
        verifyNoInteractions(hookStorageChangeRepository);
    }
}
