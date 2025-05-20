// SPDX-License-Identifier: Apache-2.0

package com.hedera.services.hapi.utils.fees;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.google.protobuf.ByteString;
import com.hederahashgraph.api.proto.java.*;
import org.junit.jupiter.api.Test;

class FeeBuilderTest {

    @Test
    void assertGetTinybarsFromTinyCents() {
        var exchangeRate =
                ExchangeRate.newBuilder().setCentEquiv(10).setHbarEquiv(100).build();
        assertEquals(100, FeeBuilder.getTinybarsFromTinyCents(exchangeRate, 10));
    }

    @Test
    void assertCalculateKeysMetadata() {
        int[] countKeyMetatData = {0, 0};
        Key validKey = Key.newBuilder()
                .setEd25519(
                        ByteString.copyFromUtf8(
                                "a479462fba67674b5a41acfb16cb6828626b61d3f389fa611005a45754130e5c749073c0b1b791596430f4a54649cc8a3f6d28147dd4099070a5c3c4811d1771"))
                .build();

        Key validKey1 = Key.newBuilder()
                .setEd25519(
                        ByteString.copyFromUtf8(
                                "a479462fba67674b5a41acfb16cb6828626b61d3f389fa611005a45754130e5c749073c0b1b791596430f4a54649cc8a3f6d28147dd4099070a5c3c4811d1771"))
                .build();
        Key validED25519Keys = Key.newBuilder()
                .setKeyList(KeyList.newBuilder()
                        .addKeys(validKey)
                        .addKeys(validKey1)
                        .build())
                .build();
        assertEquals(
                countKeyMetatData.length, FeeBuilder.calculateKeysMetadata(validED25519Keys, countKeyMetatData).length);
        assertEquals(4, FeeBuilder.calculateKeysMetadata(validED25519Keys, countKeyMetatData)[0]);
    }

    @Test
    void assertCalculateKeysMetadataThresholdKey() {
        int[] countKeyMetatData = {0, 0};
        KeyList thresholdKeyList = KeyList.newBuilder()
                .addKeys(Key.newBuilder()
                        .setEd25519(ByteString.copyFromUtf8("aaaaaaaa"))
                        .build())
                .addKeys(Key.newBuilder()
                        .setEd25519(ByteString.copyFromUtf8("bbbbbbbbbbbbbbbbbbbbb"))
                        .build())
                .build();
        ThresholdKey thresholdKey = ThresholdKey.newBuilder()
                .setKeys(thresholdKeyList)
                .setThreshold(2)
                .build();
        Key validED25519Keys = Key.newBuilder().setThresholdKey(thresholdKey).build();
        assertEquals(
                countKeyMetatData.length, FeeBuilder.calculateKeysMetadata(validED25519Keys, countKeyMetatData).length);
        assertEquals(2, FeeBuilder.calculateKeysMetadata(validED25519Keys, countKeyMetatData)[1]);
    }
}
