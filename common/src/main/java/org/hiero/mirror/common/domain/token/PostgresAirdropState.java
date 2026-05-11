// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.common.domain.token;

import java.io.Serializable;
import lombok.EqualsAndHashCode;
import lombok.Getter;

/**
 * JDBC wrapper for Postgres {@code airdrop_state}. Avoid mapping {@code TokenAirdropStateEnum} directly — Spring Data JDBC sends varchar.
 */
@Getter
@EqualsAndHashCode
public final class PostgresAirdropState implements Serializable {

    private final TokenAirdropStateEnum state;

    private PostgresAirdropState(TokenAirdropStateEnum state) {
        this.state = state;
    }

    public static PostgresAirdropState of(TokenAirdropStateEnum state) {
        return state == null ? null : new PostgresAirdropState(state);
    }
}
