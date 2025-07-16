// SPDX-License-Identifier: Apache-2.0

package com.hedera.services.store.contracts.precompile.utils;

import com.hederahashgraph.api.proto.java.TokenID;
import lombok.experimental.UtilityClass;
import org.hiero.mirror.common.CommonProperties;

/**
 * Utility class to provide functionalities respecting non-zero shard and realm numbers.
 *
 * TODO can be deleted once mono code is removed from the codebase
 */
@UtilityClass
public class NonZeroProtoAndShardUtils {

    public static TokenID getDefaultTokenIDInstance() {
        final var commonProperties = CommonProperties.getInstance();
        return TokenID.newBuilder()
                .setShardNum(commonProperties.getShard())
                .setRealmNum(commonProperties.getRealm())
                .setTokenNum(TokenID.getDefaultInstance().getTokenNum())
                .build();
    }
}
