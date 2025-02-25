// SPDX-License-Identifier: Apache-2.0

package com.hedera.services.sigs.utils;

import static com.hedera.services.sigs.utils.ImmutableKeyUtils.signalsKeyRemoval;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.KeyList;
import com.hederahashgraph.api.proto.java.ThresholdKey;
import org.junit.jupiter.api.Test;

class ImmutableKeyUtilsTest {
    @Test
    void recognizesSentinelKey() {
        assertFalse(signalsKeyRemoval(Key.getDefaultInstance()));
        assertFalse(signalsKeyRemoval(Key.newBuilder()
                .setThresholdKey(ThresholdKey.getDefaultInstance())
                .build()));
        assertTrue(signalsKeyRemoval(
                Key.newBuilder().setKeyList(KeyList.getDefaultInstance()).build()));
    }
}
