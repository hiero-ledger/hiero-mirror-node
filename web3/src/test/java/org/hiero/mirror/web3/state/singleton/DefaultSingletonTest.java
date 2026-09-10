// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.state.singleton;

import static com.hedera.node.app.service.token.impl.schemas.V0490TokenSchema.STAKING_NETWORK_REWARDS_STATE_ID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import com.hedera.hapi.node.state.token.NetworkStakingRewards;
import com.hedera.node.app.service.token.TokenService;
import org.junit.jupiter.api.Test;

class DefaultSingletonTest {

    private final DefaultSingleton singleton =
            new DefaultSingleton(TokenService.NAME, STAKING_NETWORK_REWARDS_STATE_ID, NetworkStakingRewards.DEFAULT);

    @Test
    void get() {
        assertThat(singleton.get()).isEqualTo(NetworkStakingRewards.DEFAULT);
    }

    @Test
    void key() {
        assertThat(singleton.getStateId()).isEqualTo(STAKING_NETWORK_REWARDS_STATE_ID);
    }

    @Test
    void requiresDefaultValue() {
        assertThatNullPointerException()
                .isThrownBy(() -> new DefaultSingleton(TokenService.NAME, STAKING_NETWORK_REWARDS_STATE_ID, null));
    }
}
