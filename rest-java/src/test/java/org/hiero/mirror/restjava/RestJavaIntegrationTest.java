// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.restjava;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.apache.commons.lang3.StringUtils;
import org.bouncycastle.util.encoders.Hex;
import org.hiero.mirror.common.config.CommonIntegrationTest;
import org.hiero.mirror.common.domain.hook.HookStorage;
import org.hiero.mirror.common.domain.hook.HookStorageChange;
import org.hiero.mirror.restjava.parameter.EntityIdRangeParameter;

public abstract class RestJavaIntegrationTest extends CommonIntegrationTest {

    protected EntityIdRangeParameter[] paramToArray(EntityIdRangeParameter... param) {
        return Arrays.copyOf(param, param.length);
    }

    protected HookStorage hookStorage(HookStorageChange change) {
        return new HookStorage()
                .toBuilder()
                        .hookId(change.getHookId())
                        .key(change.getKey())
                        .ownerId(change.getOwnerId())
                        .value(change.getValueWritten() != null ? change.getValueWritten() : change.getValueRead())
                        .modifiedTimestamp(change.getConsensusTimestamp())
                        .build();
    }

    /**
     *  Generates a list of consecutive byte arrays based on 64 char hex strings
     */
    protected static List<byte[]> generateKeys(int count) {
        var consecutiveKeys = new ArrayList<byte[]>(count);

        for (int i = 1; i <= count; i++) {
            final var hex = Integer.toHexString(i);
            final var paddedHex = StringUtils.leftPad(hex, 64, '0');
            final var bytes = Hex.decode(paddedHex);
            consecutiveKeys.add(bytes);
        }

        return consecutiveKeys;
    }
}
