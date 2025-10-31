// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.restjava.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.hiero.mirror.common.domain.hook.Hook;
import org.hiero.mirror.restjava.RestJavaIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

@RequiredArgsConstructor
class HookRepositoryTest extends RestJavaIntegrationTest {

    private final HookRepository hookRepository;

    private static final long OWNER_ID_1 = 1000L;
    private static final long OWNER_ID_2 = 9999L;

    private Hook persistHook(long ownerId, long hookId) {
        return domainBuilder
                .hook()
                .customize(hook -> hook.ownerId(ownerId).hookId(hookId))
                .persist();
    }

    @DisplayName("findByOwnerId should respect sorting")
    @ParameterizedTest
    @CsvSource({
        "DESC, 3, 2", // Test descending order
        "ASC,  1, 2" // Test ascending order
    })
    void findByOwnerIdRespectsSorting(Sort.Direction direction, long firstHookId, long secondHookId) {
        // given
        final var hooks = Map.of(
                1L, persistHook(OWNER_ID_1, 1L),
                2L, persistHook(OWNER_ID_1, 2L),
                3L, persistHook(OWNER_ID_1, 3L));

        final var expectedHooks = List.of(hooks.get(firstHookId), hooks.get(secondHookId));

        final var pageable = PageRequest.of(0, 2, Sort.by(direction, "hookId"));

        // when
        final var result = hookRepository.findByOwnerId(OWNER_ID_1, pageable);

        // then
        assertThat(result).hasSize(2).containsExactlyElementsOf(expectedHooks);
    }

    @Test
    @DisplayName("findByOwnerId should fetch the second page correctly")
    void findByOwnerIdRespectsPaging() {
        // given
        persistHook(OWNER_ID_1, 1L);
        persistHook(OWNER_ID_1, 2L);
        final var hook3 = persistHook(OWNER_ID_1, 3L);
        persistHook(OWNER_ID_2, 4L);

        // Request page 1, size 2, sorted by hookId ASC
        final var pageable = PageRequest.of(1, 2, Sort.by("hookId").ascending());

        // when
        final var result = hookRepository.findByOwnerId(OWNER_ID_1, pageable);

        // then
        assertThat(result).hasSize(1).containsExactly(hook3);
    }

    @Test
    @DisplayName("findByOwnerIdAndHookIdIn should find matching IDs for the correct owner")
    void findByOwnerIdAndHookIdInMatchingIds() {
        // given
        final var hook1 = persistHook(OWNER_ID_1, 1L);
        persistHook(OWNER_ID_1, 2L);
        final var hook3 = persistHook(OWNER_ID_1, 3L);
        persistHook(OWNER_ID_2, 1L);

        final var hookIdsToFind = List.of(1L, 3L, 5L);
        final var pageable = PageRequest.of(0, 10, Sort.by("hookId").ascending());

        // when
        final var result = hookRepository.findByOwnerIdAndHookIdIn(OWNER_ID_1, hookIdsToFind, pageable);

        // then
        assertThat(result).hasSize(2).containsExactly(hook1, hook3);
    }

    @Test
    @DisplayName("findByOwnerIdAndHookIdIn should return empty list for empty ID collection")
    void findByOwnerIdAndHookIdInEmptyList() {
        // given
        persistHook(OWNER_ID_1, 1L);
        final var pageable = PageRequest.of(0, 10, Sort.by("hookId").ascending());

        // when
        final var result = hookRepository.findByOwnerIdAndHookIdIn(OWNER_ID_1, List.of(), pageable);

        // then
        assertThat(result).isNotNull().isEmpty();
    }

    @Test
    @DisplayName("findByOwnerIdAndHookIdBetween should find hooks within the inclusive range")
    void findByOwnerIdAndHookIdBetweenInRange() {
        // given
        persistHook(OWNER_ID_1, 1L);
        final var hook2 = persistHook(OWNER_ID_1, 2L);
        final var hook3 = persistHook(OWNER_ID_1, 3L);
        final var hook4 = persistHook(OWNER_ID_1, 4L);
        persistHook(OWNER_ID_1, 5L);
        persistHook(OWNER_ID_2, 3L);

        final var pageable = PageRequest.of(0, 10, Sort.by("hookId").ascending());

        // when
        final var result = hookRepository.findByOwnerIdAndHookIdBetween(OWNER_ID_1, 2L, 4L, pageable);

        // then
        assertThat(result).hasSize(3).containsExactly(hook2, hook3, hook4);
    }

    @Test
    @DisplayName("findByOwnerIdAndHookIdBetween should return empty list when out of range")
    void findByOwnerIdAndHookIdBetweenOutOfRange() {
        // given
        persistHook(OWNER_ID_1, 1L);
        persistHook(OWNER_ID_1, 2L);
        persistHook(OWNER_ID_1, 3L);
        final var pageable = Pageable.unpaged();

        // when
        final var result = hookRepository.findByOwnerIdAndHookIdBetween(OWNER_ID_1, 100L, 200L, pageable);

        // then
        assertThat(result).isNotNull().isEmpty();
    }
}
