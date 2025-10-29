// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.restjava.service;

import static org.assertj.core.api.Assertions.assertThat;

import lombok.RequiredArgsConstructor;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.hiero.mirror.restjava.RestJavaIntegrationTest;
import org.hiero.mirror.restjava.common.EntityIdNumParameter;
import org.hiero.mirror.restjava.dto.HooksRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Sort.Direction;

@RequiredArgsConstructor
final class HookServiceTest extends RestJavaIntegrationTest {

    private final HookService hookService;

    private EntityId ownerId;

    @BeforeEach
    void setup() {
        // Persist a test entity to the database
        final var entity = domainBuilder.entity().persist();
        ownerId = entity.toEntityId();

        // Create a few hooks for that entity
        for (int i = 0; i < 3; i++) {
            domainBuilder
                    .hook()
                    .customize(h -> h.ownerId(ownerId.getId()).hookId(domainBuilder.id()))
                    .persist();
        }
    }

    @Test
    void getHooks() {
        // given
        final var request = HooksRequest.builder()
                .ownerId(new EntityIdNumParameter(ownerId))
                .limit(5)
                .order(Direction.ASC)
                .build();

        // when
        final var result = hookService.getHooks(request);

        // then
        assertThat(result).isNotEmpty().allMatch(h -> h.getOwnerId() == ownerId.getId());
    }

    @Test
    void getHooksEmptyResult() {
        // given – no hooks for this account
        final var nonexistent = EntityId.of(domainBuilder.id());
        final var request = HooksRequest.builder()
                .ownerId(new EntityIdNumParameter(nonexistent))
                .limit(5)
                .order(Direction.ASC)
                .build();

        // when
        final var result = hookService.getHooks(request);

        // then
        assertThat(result).isEmpty();
    }
}
