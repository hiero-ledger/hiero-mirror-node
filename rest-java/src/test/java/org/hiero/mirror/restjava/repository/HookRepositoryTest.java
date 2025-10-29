// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.restjava.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.hiero.mirror.common.domain.hook.Hook;
import org.hiero.mirror.restjava.RestJavaIntegrationTest;
import org.hiero.mirror.restjava.common.EntityIdParameter;
import org.hiero.mirror.restjava.dto.HooksRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.data.domain.Sort.Direction;

@RequiredArgsConstructor
final class HookRepositoryTest extends RestJavaIntegrationTest {

    private final HookRepository hookRepository;

    private long ownerId;
    private List<Hook> hooks;

    @BeforeEach
    void setup() {
        setupHooks();
    }

    @ParameterizedTest
    @EnumSource(Direction.class)
    void findAllSorted(Direction direction) {
        // given
        final var request = HooksRequest.builder()
                .ownerId(EntityIdParameter.valueOf("0.0" + ownerId))
                .limit(5)
                .order(direction)
                .build();

        // when
        final var result = hookRepository.findAll(request, EntityId.of(ownerId));

        // then
        assertThat(result).isNotEmpty().hasSizeLessThanOrEqualTo(5);

        long previousId = direction == Direction.ASC ? -1 : Long.MAX_VALUE;
        for (Hook hook : result) {
            assertThat(hook.getOwnerId()).isEqualTo(ownerId);
            if (previousId != (direction == Direction.ASC ? -1 : Long.MAX_VALUE)) {
                if (direction == Direction.ASC) {
                    assertThat(hook.getHookId()).isGreaterThan(previousId);
                } else {
                    assertThat(hook.getHookId()).isLessThan(previousId);
                }
            }
            previousId = hook.getHookId();
        }
    }

    @Test
    void findAllNoMatch() {
        // Request with a non-existent account
        final var request = HooksRequest.builder()
                .ownerId(EntityIdParameter.valueOf("999999"))
                .limit(5)
                .order(Direction.ASC)
                .build();

        final var result = hookRepository.findAll(request, EntityId.of(999999L));
        assertThat(result).isEmpty();
    }

    private void setupHooks() {
        ownerId = domainBuilder.id();
        hooks = new ArrayList<>();

        for (int i = 0; i < 5; i++) {
            final var hook = domainBuilder
                    .hook()
                    .customize(h -> h.ownerId(ownerId).hookId(domainBuilder.id()))
                    .persist();
            hooks.add(hook);
        }
    }
}
