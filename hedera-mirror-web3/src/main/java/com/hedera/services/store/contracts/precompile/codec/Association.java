// SPDX-License-Identifier: Apache-2.0

package com.hedera.services.store.contracts.precompile.codec;

import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.TokenID;
import java.util.List;

public record Association(AccountID accountId, List<TokenID> tokenIds) {
    public static Association singleAssociation(final AccountID accountId, final TokenID tokenId) {
        return new Association(accountId, List.of(tokenId));
    }

    public static Association multiAssociation(final AccountID accountId, final List<TokenID> tokenIds) {
        return new Association(accountId, tokenIds);
    }
}
