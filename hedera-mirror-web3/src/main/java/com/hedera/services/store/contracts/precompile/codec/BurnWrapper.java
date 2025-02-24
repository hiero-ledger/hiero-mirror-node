// SPDX-License-Identifier: Apache-2.0

package com.hedera.services.store.contracts.precompile.codec;

import static com.hederahashgraph.api.proto.java.TokenType.FUNGIBLE_COMMON;
import static com.hederahashgraph.api.proto.java.TokenType.NON_FUNGIBLE_UNIQUE;

import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TokenType;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Collections;
import java.util.List;

public record BurnWrapper(long amount, TokenID tokenType, List<Long> serialNos) {
    private static final long NONFUNGIBLE_BURN_AMOUNT = -1;
    private static final List<Long> FUNGIBLE_BURN_SERIAL_NOS = Collections.emptyList();

    @NonNull
    public static BurnWrapper forNonFungible(final TokenID tokenType, final List<Long> serialNos) {
        return new BurnWrapper(NONFUNGIBLE_BURN_AMOUNT, tokenType, serialNos);
    }

    @NonNull
    public static BurnWrapper forFungible(final TokenID tokenType, final long amount) {
        return new BurnWrapper(amount, tokenType, FUNGIBLE_BURN_SERIAL_NOS);
    }

    public TokenType type() {
        return (amount == NONFUNGIBLE_BURN_AMOUNT) ? NON_FUNGIBLE_UNIQUE : FUNGIBLE_COMMON;
    }
}
