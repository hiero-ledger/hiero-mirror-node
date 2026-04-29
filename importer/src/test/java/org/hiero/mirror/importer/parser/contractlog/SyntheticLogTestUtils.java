// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.parser.contractlog;

import java.util.Arrays;
import lombok.experimental.UtilityClass;
import org.hiero.mirror.common.domain.contract.ContractResult;
import org.hiero.mirror.common.util.LogsBloomFilter;

@UtilityClass
public class SyntheticLogTestUtils {

    static byte[] aggregateExpectedContractResultBloomForTest(
            final byte[] recordFileLogsBloom, final ContractResult contractResult) {
        final var aggregatedBloom = new LogsBloomFilter();
        if (recordFileLogsBloom != null && !Arrays.equals(recordFileLogsBloom, new byte[LogsBloomFilter.BYTE_SIZE])) {
            aggregatedBloom.or(recordFileLogsBloom);
        }
        final var syntheticBloom = contractResult.getBloom();
        if (syntheticBloom != null && syntheticBloom.length == LogsBloomFilter.BYTE_SIZE) {
            aggregatedBloom.or(syntheticBloom);
        }
        return aggregatedBloom.toArrayUnsafe();
    }
}
