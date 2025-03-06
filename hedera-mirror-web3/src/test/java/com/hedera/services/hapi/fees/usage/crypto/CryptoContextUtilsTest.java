// SPDX-License-Identifier: Apache-2.0

package com.hedera.services.hapi.fees.usage.crypto;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.TokenID;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class CryptoContextUtilsTest {
    @Test
    void getsChangedKeys() {
        final Map<Long, Long> newMap = new HashMap<>();
        final Map<Long, Long> existingMap = new HashMap<>();

        newMap.put(1L, 2L);
        newMap.put(3L, 2L);
        newMap.put(4L, 2L);

        existingMap.put(1L, 2L);
        existingMap.put(4L, 2L);
        existingMap.put(5L, 2L);

        assertEquals(1, CryptoContextUtils.getChangedCryptoKeys(newMap.keySet(), existingMap.keySet()));
    }

    @Test
    void getsChangedTokenKeys() {
        final Map<AllowanceId, Long> newMap = new HashMap<>();
        final Map<AllowanceId, Long> existingMap = new HashMap<>();

        final var token1 = TokenID.newBuilder()
                .setShardNum(0L)
                .setRealmNum(0L)
                .setTokenNum(1L)
                .build();

        final var token2 = TokenID.newBuilder()
                .setShardNum(0L)
                .setRealmNum(0L)
                .setTokenNum(2L)
                .build();

        final var token3 = TokenID.newBuilder()
                .setShardNum(0L)
                .setRealmNum(0L)
                .setTokenNum(3L)
                .build();

        final var token4 = TokenID.newBuilder()
                .setShardNum(0L)
                .setRealmNum(0L)
                .setTokenNum(4L)
                .build();

        final var spender1 = AccountID.newBuilder()
                .setShardNum(0L)
                .setRealmNum(0L)
                .setAccountNum(2L)
                .build();

        final var spender2 = AccountID.newBuilder()
                .setShardNum(0L)
                .setRealmNum(0L)
                .setAccountNum(5L)
                .build();

        newMap.put(new AllowanceId(token1, spender1), 2L);
        newMap.put(new AllowanceId(token2, spender1), 2L);
        newMap.put(new AllowanceId(token3, spender1), 2L);

        existingMap.put(new AllowanceId(token1, spender1), 2L);
        existingMap.put(new AllowanceId(token4, spender1), 2L);
        existingMap.put(new AllowanceId(token3, spender2), 2L);

        assertEquals(2, CryptoContextUtils.getChangedTokenKeys(newMap.keySet(), existingMap.keySet()));
    }
}
