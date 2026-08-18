// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.restjava.service;

import static java.lang.Long.MAX_VALUE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.HexFormat;
import java.util.List;
import java.util.TreeSet;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.hiero.mirror.common.domain.hook.Hook;
import org.hiero.mirror.restjava.dto.HookStorageRequest;
import org.hiero.mirror.restjava.dto.HookStorageSlot;
import org.hiero.mirror.restjava.dto.HooksRequest;
import org.hiero.mirror.restjava.parameter.EntityIdParameter;
import org.hiero.mirror.restjava.repository.HookRepository;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Sort.Direction;

final class HookServiceTest {

    private static final long OWNER_NUM = 1001L;
    private static final String KEY_MIN = "0000000000000000000000000000000000000000000000000000000000000000";
    private static final String KEY_MAX = "FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFF";
    private static final byte[] KEY_MIN_BYTES = HexFormat.of().parseHex(KEY_MIN);
    private static final byte[] KEY_MAX_BYTES = HexFormat.of().parseHex(KEY_MAX);

    private final EntityService entityService = mock(EntityService.class);
    private final HookRepository hookRepository = mock(HookRepository.class);
    private final HookServiceImpl hookService = new HookServiceImpl(entityService, hookRepository);

    private final EntityId ownerId = EntityId.of(OWNER_NUM);

    @Test
    void getHooksDelegatesToRepository() {
        var hook = new Hook();
        var request = HooksRequest.builder()
                .hookIds(new TreeSet<>(List.of(1L, 2L)))
                .lowerBound(0L)
                .upperBound(MAX_VALUE)
                .limit(25)
                .order(Direction.DESC)
                .ownerId(EntityIdParameter.valueOf(String.valueOf(OWNER_NUM)))
                .build();

        when(entityService.lookup(any())).thenReturn(ownerId);
        when(hookRepository.findHooks(eq(request), eq(OWNER_NUM))).thenReturn(List.of(hook));

        assertThat(hookService.getHooks(request)).containsExactly(hook);
        verify(hookRepository).findHooks(eq(request), eq(OWNER_NUM));
    }

    @Test
    void getHookStorageWhenHookMissingThrows() {
        var request = HookStorageRequest.builder()
                .ownerId(EntityIdParameter.valueOf(String.valueOf(OWNER_NUM)))
                .hookId(1L)
                .keys(List.of())
                .keyLowerBound(KEY_MIN_BYTES)
                .keyUpperBound(KEY_MAX_BYTES)
                .limit(25)
                .order(Direction.ASC)
                .timestamp(Bound.EMPTY)
                .build();

        when(entityService.lookup(any())).thenReturn(ownerId);
        when(hookRepository.existsById(new Hook.Id(1L, OWNER_NUM))).thenReturn(false);

        assertThatThrownBy(() -> hookService.getHookStorage(request))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Hook not found");
    }

    @Test
    void getHookStorageReturnsRepositoryRows() {
        var slot = new HookStorageSlot(1L, KEY_MIN_BYTES, new byte[] {1, 2, 3});
        var request = HookStorageRequest.builder()
                .ownerId(EntityIdParameter.valueOf(String.valueOf(OWNER_NUM)))
                .hookId(1L)
                .keys(List.of())
                .keyLowerBound(KEY_MIN_BYTES)
                .keyUpperBound(KEY_MAX_BYTES)
                .limit(25)
                .order(Direction.ASC)
                .timestamp(Bound.EMPTY)
                .build();

        when(entityService.lookup(any())).thenReturn(ownerId);
        when(hookRepository.existsById(new Hook.Id(1L, OWNER_NUM))).thenReturn(true);
        when(hookRepository.findHookStorage(eq(request), eq(OWNER_NUM))).thenReturn(List.of(slot));

        var result = hookService.getHookStorage(request);
        assertThat(result.ownerId()).isEqualTo(ownerId);
        assertThat(result.storage()).containsExactly(slot);
    }
}
