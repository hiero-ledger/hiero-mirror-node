// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.restjava.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.hiero.mirror.common.domain.hook.Hook;
import org.hiero.mirror.restjava.RestJavaIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.data.domain.Sort.Direction;

@RequiredArgsConstructor
final class HookRepositoryTest extends RestJavaIntegrationTest {

    private final HookRepository hookRepository;

    private static final long OWNER_ID_1 = 1000L;
    private static final long OWNER_ID_2 = 9999L;

    private Hook persistHook(long ownerId, long hookId) {
        return domainBuilder
                .hook()
                .customize(hook -> hook.ownerId(ownerId).hookId(hookId))
                .persist();
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

        // when
        final var result = hookRepository.findByOwnerIdAndHookIdIn(OWNER_ID_1, hookIdsToFind, "ASC", 10);

        // then
        assertThat(result).hasSize(2).containsExactly(hook1, hook3);
    }

    @Test
    @DisplayName("findByOwnerIdAndHookIdIn should return empty list for empty ID collection")
    void findByOwnerIdAndHookIdInEmptyList() {
        // given
        persistHook(OWNER_ID_1, 1L);

        // when
        final var result = hookRepository.findByOwnerIdAndHookIdIn(OWNER_ID_1, List.of(), "ASC", 10);

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

        // when
        final var result = hookRepository.findByOwnerIdAndHookIdBetween(OWNER_ID_1, 2L, 4L, "ASC", 10);

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

        // when
        final var result = hookRepository.findByOwnerIdAndHookIdBetween(OWNER_ID_1, 100L, 200L, "ASC", 10);

        // then
        assertThat(result).isNotNull().isEmpty();
    }

    @ParameterizedTest
    @EnumSource(Direction.class)
    @DisplayName("findByOwnerIdAndHookIdBetween should respect the order and limit")
    void findByOwnerIdAndHookIdBetweenRespectsOrderAndLimit(Direction order) {
        // given
        final var hook1 = persistHook(OWNER_ID_1, 1L);
        final var hook2 = persistHook(OWNER_ID_1, 2L);
        final var hook3 = persistHook(OWNER_ID_1, 3L);
        final var hook4 = persistHook(OWNER_ID_1, 4L);

        final var expected = order.isAscending() ? List.of(hook1, hook2) : List.of(hook4, hook3);

        // when
        final var result = hookRepository.findByOwnerIdAndHookIdBetween(OWNER_ID_1, 0L, 10L, order.name(), 2);

        // then
        assertThat(result).containsExactlyElementsOf(expected);
    }

    @ParameterizedTest
    @EnumSource(Direction.class)
    @DisplayName("findByOwnerIdAndHookIdIn should respect the order and limit")
    void findByOwnerIdAndHookIdInRespectsOrderAndLimit(Direction order) {
        // given
        final var hook1 = persistHook(OWNER_ID_1, 1L);
        final var hook2 = persistHook(OWNER_ID_1, 2L);
        final var hook3 = persistHook(OWNER_ID_1, 3L);
        final var hook4 = persistHook(OWNER_ID_1, 4L);

        final var expected = order.isAscending() ? List.of(hook1, hook2) : List.of(hook4, hook3);

        // when
        final var result =
                hookRepository.findByOwnerIdAndHookIdIn(OWNER_ID_1, List.of(1L, 2L, 3L, 4L), order.name(), 2);

        // then
        assertThat(result).containsExactlyElementsOf(expected);
    }
}
