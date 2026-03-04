// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.state;

import static com.hedera.node.app.service.schedule.impl.schemas.V0570ScheduleSchema.SCHEDULE_ID_BY_EQUALITY_STATE_ID;
import static com.hedera.node.app.service.token.impl.schemas.V0490TokenSchema.STAKING_NETWORK_REWARDS_STATE_ID;
import static com.hedera.node.app.state.recordcache.schemas.V0490RecordCacheSchema.TRANSACTION_RECEIPTS_STATE_ID;
import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;

import com.hedera.node.app.service.schedule.ScheduleService;
import com.hedera.node.app.service.token.TokenService;
import com.hedera.node.app.state.recordcache.RecordCacheService;
import com.swirlds.state.spi.ReadableKVState;
import com.swirlds.state.spi.ReadableQueueState;
import com.swirlds.state.spi.ReadableSingletonState;
import com.swirlds.state.spi.WritableKVState;
import com.swirlds.state.spi.WritableQueueState;
import com.swirlds.state.spi.WritableSingletonState;
import java.util.LinkedList;
import org.hiero.mirror.web3.ContextExtension;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(ContextExtension.class)
@ExtendWith(MockitoExtension.class)
final class MirrorNodeStateTest {

    private final MirrorNodeState mirrorNodeState = new MirrorNodeState(new LinkedList<>(), new LinkedList<>());

    @Test
    void getReadableStatesWithSingleton() {
        final var tokenStates = mirrorNodeState.getReadableStates(TokenService.NAME);
        var state = tokenStates.getSingleton(STAKING_NETWORK_REWARDS_STATE_ID);
        assertThat(state).isInstanceOf(ReadableSingletonState.class);
    }

    @Test
    void testGetReadableStatesWithKV() {
        final var scheduleServiceState = mirrorNodeState.getReadableStates(ScheduleService.NAME);
        var state = scheduleServiceState.get(SCHEDULE_ID_BY_EQUALITY_STATE_ID);
        assertThat(state).isInstanceOf(ReadableKVState.class);
    }

    @Test
    void testGetReadableStatesWithQueue() {
        final var recordCacheService = mirrorNodeState.getReadableStates(RecordCacheService.NAME);
        var state = recordCacheService.getQueue(TRANSACTION_RECEIPTS_STATE_ID);
        assertThat(state).isInstanceOf(ReadableQueueState.class);
    }

    @Test
    void testGetReadableStateForUnsupportedService() {
        assertThat(mirrorNodeState.getReadableStates("").size()).isZero();
    }

    @Test
    void getWritableStatesWithSingleton() {
        final var tokenStates = mirrorNodeState.getWritableStates(TokenService.NAME);
        var state = tokenStates.getSingleton(STAKING_NETWORK_REWARDS_STATE_ID);
        assertThat(state).isInstanceOf(WritableSingletonState.class);
    }

    @Test
    void testGetWritableStatesWithKV() {
        final var scheduleServiceState = mirrorNodeState.getWritableStates(ScheduleService.NAME);
        var state = scheduleServiceState.get(SCHEDULE_ID_BY_EQUALITY_STATE_ID);
        assertThat(state).isInstanceOf(WritableKVState.class);
    }

    @Test
    void testGetWritableStatesWithQueue() {
        final var recordCacheService = mirrorNodeState.getWritableStates(RecordCacheService.NAME);
        var state = recordCacheService.getQueue(TRANSACTION_RECEIPTS_STATE_ID);
        assertThat(state).isInstanceOf(WritableQueueState.class);
    }

    @Test
    void testGetWritableStateForUnsupportedService() {
        assertThat(mirrorNodeState.getWritableStates("").size()).isZero();
    }

    @Test
    void testEqualsDifferentType() {
        assertThat(mirrorNodeState).isNotEqualTo("someString");
    }

    @Test
    void testEqualsWithNull() {
        assertThat(mirrorNodeState).isNotEqualTo(null);
    }

    @Test
    void testEqualsSameValues() {
        assertThat(mirrorNodeState).isEqualTo(mirrorNodeState);
    }

    @Test
    void testHashCode() {
        assertThat(mirrorNodeState).hasSameHashCodeAs(mirrorNodeState);
    }
}
