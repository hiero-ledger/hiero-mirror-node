// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.restjava.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hiero.mirror.restjava.common.Constants.TIMESTAMP;
import static org.hiero.mirror.restjava.jooq.domain.Tables.HOOK_STORAGE_CHANGE;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import java.util.List;
import org.hiero.mirror.restjava.common.RangeOperator;
import org.hiero.mirror.restjava.dto.HookStorageRequest;
import org.hiero.mirror.restjava.dto.HooksRequest;
import org.hiero.mirror.restjava.parameter.TimestampParameter;
import org.hiero.mirror.restjava.service.Bound;
import org.jooq.DSLContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

final class HookRepositoryCustomImplTest {

    private static final long OWNER_ID = 1000L;

    private final DSLContext dslContext = mock(DSLContext.class);
    private final HookRepositoryCustomImpl repository = new HookRepositoryCustomImpl(dslContext);

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void findHookStorageSkipsQueryWhenNoKeyInRange(boolean historical) {
        // given
        final var timestamp = historical
                ? Bound.of(
                        new TimestampParameter[] {new TimestampParameter(RangeOperator.LTE, 100L)},
                        TIMESTAMP,
                        HOOK_STORAGE_CHANGE.CONSENSUS_TIMESTAMP)
                : Bound.EMPTY;
        final var request = HookStorageRequest.builder()
                .hookId(1L)
                .keys(List.of(new byte[] {1}, new byte[] {3}))
                .keyLowerBound(new byte[] {2})
                .keyUpperBound(new byte[] {2})
                .timestamp(timestamp)
                .build();

        // when, then
        assertThat(request.isHistorical()).isEqualTo(historical);
        assertThat(repository.findHookStorage(request, OWNER_ID)).isEmpty();
        verifyNoInteractions(dslContext);
    }

    @Test
    void findHooksSkipsQueryWhenNoHookIdInRange() {
        // given
        final var request = HooksRequest.builder()
                .hookIds(List.of(5L, 25L))
                .lowerBound(10L)
                .upperBound(20L)
                .build();

        // when, then
        assertThat(repository.findHooks(request, OWNER_ID)).isEmpty();
        verifyNoInteractions(dslContext);
    }
}
