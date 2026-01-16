// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.common.aggregator;

import lombok.NoArgsConstructor;
import org.apache.commons.lang3.ArrayUtils;

@NoArgsConstructor
public class LogsBloomAggregator {

    public static final int BYTE_SIZE = 256;
    private byte[] aggregatedBlooms = ArrayUtils.EMPTY_BYTE_ARRAY;

    public LogsBloomAggregator aggregate(byte[] bloom) {
        if (bloom == null) {
            return this;
        }
        if (aggregatedBlooms.length == 0) {
            aggregatedBlooms = new byte[BYTE_SIZE];
        }
        for (int i = 0; i < bloom.length; i++) {
            aggregatedBlooms[i] |= bloom[i];
        }
        return this;
    }

    public byte[] getBloom() {
        return aggregatedBlooms;
    }

    public boolean couldContain(byte[] bloom) {
        if (bloom == null) {
            // other implementations accept null values as positive matches.
            return true;
        }
        if (aggregatedBlooms.length == 0) {
            return false;
        }
        if (bloom.length != BYTE_SIZE) {
            return false;
        }

        for (int i = 0; i < bloom.length; i++) {
            if ((bloom[i] & aggregatedBlooms[i]) != bloom[i]) {
                return false;
            }
        }
        return true;
    }
}
