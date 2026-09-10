// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.restjava.repository;

import static java.lang.Long.MAX_VALUE;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.TreeSet;
import lombok.RequiredArgsConstructor;
import org.hiero.mirror.RestJavaIntegrationTest;
import org.hiero.mirror.common.domain.hook.Hook;
import org.hiero.mirror.restjava.dto.HooksRequest;
import org.hiero.mirror.restjava.parameter.EntityIdParameter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Sort.Direction;

@RequiredArgsConstructor
final class HookRepositoryTest extends RestJavaIntegrationTest {

    private final HookRepository hookRepository;

    private static final long OWNER_ID_1 = 1000L;
    private static final long OWNER_ID_2 = 9999L;
    private static final int LIMIT = 50;

    private Hook persistHook(long ownerId, long hookId) {
        return domainBuilder
                .hook()
                .customize(hook -> hook.ownerId(ownerId).hookId(hookId))
                .persist();
    }

    @Test
    @DisplayName("findHooks returns hooks matching explicit hook ids for owner")
    void findHooksByExplicitIds() {
        persistHook(OWNER_ID_1, 1L);
        persistHook(OWNER_ID_1, 2L);
        persistHook(OWNER_ID_1, 3L);
        persistHook(OWNER_ID_2, 1L);

        var request = HooksRequest.builder()
                .hookIds(new TreeSet<>(List.of(1L, 3L)))
                .lowerBound(0L)
                .upperBound(MAX_VALUE)
                .limit(LIMIT)
                .order(Direction.ASC)
                .ownerId(EntityIdParameter.valueOf(String.valueOf(OWNER_ID_1)))
                .build();

        final var result = hookRepository.findHooks(request, OWNER_ID_1);
        assertThat(result).extracting(Hook::getHookId).containsExactly(1L, 3L);
    }

    @Test
    @DisplayName("findHooks returns hooks within hook id range")
    void findHooksByRange() {
        persistHook(OWNER_ID_1, 1L);
        persistHook(OWNER_ID_1, 2L);
        persistHook(OWNER_ID_1, 3L);
        persistHook(OWNER_ID_1, 4L);
        persistHook(OWNER_ID_1, 5L);
        persistHook(OWNER_ID_2, 3L);

        var request = HooksRequest.builder()
                .hookIds(new TreeSet<>())
                .lowerBound(2L)
                .upperBound(4L)
                .limit(LIMIT)
                .order(Direction.ASC)
                .ownerId(EntityIdParameter.valueOf(String.valueOf(OWNER_ID_1)))
                .build();

        final var result = hookRepository.findHooks(request, OWNER_ID_1);
        assertThat(result).extracting(Hook::getHookId).containsExactly(2L, 3L, 4L);
    }

    @Test
    @DisplayName("findHooks returns empty when range excludes all hooks")
    void findHooksRangeEmpty() {
        persistHook(OWNER_ID_1, 1L);
        persistHook(OWNER_ID_1, 2L);
        var request = HooksRequest.builder()
                .hookIds(new TreeSet<>())
                .lowerBound(100L)
                .upperBound(200L)
                .limit(LIMIT)
                .order(Direction.ASC)
                .ownerId(EntityIdParameter.valueOf(String.valueOf(OWNER_ID_1)))
                .build();

        assertThat(hookRepository.findHooks(request, OWNER_ID_1)).isEmpty();
    }

    @Test
    @DisplayName("findHooks intersects explicit ids with range")
    void findHooksIntersection() {
        persistHook(OWNER_ID_1, 5L);
        persistHook(OWNER_ID_1, 15L);
        persistHook(OWNER_ID_1, 25L);

        var request = HooksRequest.builder()
                .hookIds(new TreeSet<>(List.of(5L, 15L, 25L)))
                .lowerBound(10L)
                .upperBound(20L)
                .limit(LIMIT)
                .order(Direction.ASC)
                .ownerId(EntityIdParameter.valueOf(String.valueOf(OWNER_ID_1)))
                .build();

        assertThat(hookRepository.findHooks(request, OWNER_ID_1))
                .extracting(Hook::getHookId)
                .containsExactly(15L);
    }
}
