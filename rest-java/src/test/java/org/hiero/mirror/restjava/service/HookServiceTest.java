// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.restjava.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.hiero.mirror.common.domain.hook.Hook;
import org.hiero.mirror.restjava.dto.HookStorageRequest;
import org.hiero.mirror.restjava.dto.HookStorageSlot;
import org.hiero.mirror.restjava.dto.HooksRequest;
import org.hiero.mirror.restjava.parameter.EntityIdParameter;
import org.hiero.mirror.restjava.repository.HookRepository;
import org.junit.jupiter.api.Test;

final class HookServiceTest {

    private static final long OWNER_ID = 1001L;

    private final EntityService entityService = mock(EntityService.class);
    private final HookRepository hookRepository = mock(HookRepository.class);
    private final HookService hookService = new HookServiceImpl(entityService, hookRepository);
    private final EntityId ownerId = EntityId.of(OWNER_ID);

    @Test
    void getHooks() {
        // given
        final var hook = new Hook();
        final var request = HooksRequest.builder()
                .ownerId(EntityIdParameter.valueOf(String.valueOf(OWNER_ID)))
                .hookIds(List.of(1L, 2L))
                .build();

        when(entityService.lookup(any())).thenReturn(ownerId);
        when(hookRepository.findHooks(request, OWNER_ID)).thenReturn(List.of(hook));

        // when
        final var result = hookService.getHooks(request);

        // then
        assertThat(result).containsExactly(hook);
    }

    @Test
    void getHookStorage() {
        // given
        final var slot = new HookStorageSlot(new byte[] {1}, 1L, new byte[] {2});
        final var request = HookStorageRequest.builder()
                .ownerId(EntityIdParameter.valueOf(String.valueOf(OWNER_ID)))
                .hookId(1L)
                .build();

        when(entityService.lookup(any())).thenReturn(ownerId);
        when(hookRepository.findHookStorage(request, OWNER_ID)).thenReturn(List.of(slot));

        // when
        final var result = hookService.getHookStorage(request);

        // then
        assertThat(result.ownerId()).isEqualTo(ownerId);
        assertThat(result.storage()).containsExactly(slot);
    }
}
