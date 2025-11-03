// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.restjava.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.hiero.mirror.common.domain.hook.Hook;
import org.hiero.mirror.restjava.RestJavaIntegrationTest;
import org.hiero.mirror.restjava.common.EntityIdParameter;
import org.hiero.mirror.restjava.dto.HooksRequest;
import org.hiero.mirror.restjava.repository.HookRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Sort;

@RequiredArgsConstructor
class HookServiceTest extends RestJavaIntegrationTest {

    private static final long OWNER_ID = 1001L;

    private HookRepository hookRepository;
    private EntityService entityService;
    private HookService hookService;

    @BeforeEach
    void setup() {
        hookRepository = mock(HookRepository.class);
        entityService = mock(EntityService.class);
        hookService = new HookServiceImpl(hookRepository, entityService);
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
                .hookIdEqualsFilters(List.of(1L, 2L))
                .limit(5)
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
                .hookIdLowerBoundInclusive(10L)
                .hookIdUpperBoundInclusive(20L)
                .limit(10)
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
                .hookIdEqualsFilters(List.of(5L, 15L, 25L))
                .hookIdLowerBoundInclusive(10L)
                .hookIdUpperBoundInclusive(20L)
                .limit(10)
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
                .hookIdEqualsFilters(List.of(1L, 2L))
                .hookIdLowerBoundInclusive(10L)
                .hookIdUpperBoundInclusive(20L)
                .limit(5)
                .build();

        // when
        final var result = hookService.getHooks(request);

        // then
        assertThat(result).isEmpty();
        verifyNoInteractions(hookRepository);
    }
}
