// SPDX-License-Identifier: Apache-2.0

package com.hedera.services.store.contracts.precompile.codec;

import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.TokenID;
import java.util.List;

public record Dissociation(AccountID accountId, List<TokenID> tokenIds) {

    public static Dissociation singleDissociation(AccountID accountId, TokenID tokenID) {
        return new Dissociation(accountId, List.of(tokenID));
    }

    public static Dissociation multiDissociation(AccountID accountId, List<TokenID> tokenIds) {
        return new Dissociation(accountId, tokenIds);
    }
}
