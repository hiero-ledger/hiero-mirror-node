// SPDX-License-Identifier: Apache-2.0

package com.hedera.services.store.models;

import com.hederahashgraph.api.proto.java.TokenID;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Comparator;

/**
 * Copied NftId type from hedera-services.
 * <p>
 * Represents the id of a UniqueToken model
 */
public record NftId(long shard, long realm, long num, long serialNo) implements Comparable<NftId> {
    private static final Comparator<NftId> NATURAL_ORDER = Comparator.comparingLong(NftId::num)
            .thenComparingLong(NftId::serialNo)
            .thenComparingLong(NftId::shard)
            .thenComparingLong(NftId::realm);

    public static NftId fromGrpc(final TokenID tokenId, final long serialNo) {
        return new NftId(tokenId.getShardNum(), tokenId.getRealmNum(), tokenId.getTokenNum(), serialNo);
    }

    public TokenID tokenId() {
        return TokenID.newBuilder()
                .setShardNum(shard)
                .setRealmNum(realm)
                .setTokenNum(num)
                .build();
    }

    @Override
    public int compareTo(final @NonNull NftId that) {
        return NATURAL_ORDER.compare(this, that);
    }
}
