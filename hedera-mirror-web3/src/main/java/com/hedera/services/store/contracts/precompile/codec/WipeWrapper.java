// SPDX-License-Identifier: Apache-2.0

package com.hedera.services.store.contracts.precompile.codec;

import static com.hederahashgraph.api.proto.java.TokenType.FUNGIBLE_COMMON;
import static com.hederahashgraph.api.proto.java.TokenType.NON_FUNGIBLE_UNIQUE;

import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TokenType;
import java.util.Collections;
import java.util.List;

public record WipeWrapper(TokenID token, AccountID account, long amount, List<Long> serialNumbers) {

    private static final long NON_FUNGIBLE_WIPE_AMOUNT = -1;
    private static final List<Long> FUNGIBLE_WIPE_SERIAL_NUMBERS = Collections.emptyList();

    public static WipeWrapper forNonFungible(
            final TokenID token, final AccountID account, final List<Long> serialNumbers) {
        return new WipeWrapper(token, account, NON_FUNGIBLE_WIPE_AMOUNT, serialNumbers);
    }

    public static WipeWrapper forFungible(final TokenID token, final AccountID account, final long amount) {
        return new WipeWrapper(token, account, amount, FUNGIBLE_WIPE_SERIAL_NUMBERS);
    }

    public TokenType type() {
        return (serialNumbers != null && !serialNumbers.isEmpty() && amount <= 0)
                ? NON_FUNGIBLE_UNIQUE
                : FUNGIBLE_COMMON;
    }
}
