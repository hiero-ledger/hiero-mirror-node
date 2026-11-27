// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.parser.contractlog;

import java.util.Arrays;
import java.util.HexFormat;
import lombok.experimental.UtilityClass;
import org.hiero.mirror.common.domain.contract.ContractResult;
import org.hiero.mirror.common.util.LogsBloomFilter;
import org.web3j.utils.Bloom;

@UtilityClass
public class SyntheticLogTestUtils {

    /** Alias whose first byte is 0x00, so trimming strips it to 19 bytes. */
    static final byte[] LEADING_ZERO_ALIAS = HexFormat.of().parseHex("00a1b2c3d4e5f60718293a4b5c6d7e8f90a1b2c3");

    /** Entity num 1000. Its long-zero address 0x…03e8 trims to the 2-byte topic value 0x03e8. */
    static final long LONG_ZERO_NUM = 1000L;

    private static final int TOPIC_SIZE_BYTES = 32;

    static byte[] aggregateExpectedContractResultBloom(
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

    /**
     * Builds the logs bloom an Ethereum client computes for a log: keccak256 of the canonical 20-byte address and of
     * each 32-byte topic, per Yellow Paper M3:2048. Uses web3j's implementation rather than LogsBloomFilter so the
     * expected value doesn't depend on the code under test. Mirror node stores topics trimmed, so each one is
     * left-padded back to 32 bytes; null topics are absent and skipped.
     */
    static byte[] ethereumLogBloom(final byte[] address, final byte[]... topics) {
        final var bloom = new Bloom();
        bloom.add(address);
        for (final var topic : topics) {
            if (topic == null) {
                continue;
            }
            if (topic.length > TOPIC_SIZE_BYTES) {
                throw new IllegalArgumentException("Ethereum topics are at most 32 bytes, got " + topic.length);
            }

            final var paddedTopic = new byte[TOPIC_SIZE_BYTES];
            System.arraycopy(topic, 0, paddedTopic, TOPIC_SIZE_BYTES - topic.length, topic.length);
            bloom.add(paddedTopic);
        }
        return bloom.getBytes();
    }
}
