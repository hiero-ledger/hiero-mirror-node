// SPDX-License-Identifier: Apache-2.0

package com.hedera.services.hapi.fees.usage.crypto;

import com.google.common.base.MoreObjects;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.TokenID;
import edu.umd.cs.findbugs.annotations.NonNull;
import org.apache.commons.lang3.builder.CompareToBuilder;

public record AllowanceId(TokenID tokenId, AccountID spenderId) implements Comparable<AllowanceId> {
    @Override
    public int compareTo(@NonNull final AllowanceId that) {
        return new CompareToBuilder()
                .append(tokenId, that.tokenId)
                .append(spenderId, that.spenderId)
                .toComparison();
    }

    @Override
    public String toString() {
        var tokenString =
                String.format("%d.%d.%d", tokenId.getShardNum(), tokenId.getRealmNum(), tokenId.getTokenNum());
        var spenderString =
                String.format("%d.%d.%d", spenderId.getShardNum(), spenderId.getRealmNum(), spenderId.getAccountNum());

        return MoreObjects.toStringHelper(this)
                .add("tokenId", tokenString)
                .add("spenderId", spenderString)
                .toString();
    }
}
