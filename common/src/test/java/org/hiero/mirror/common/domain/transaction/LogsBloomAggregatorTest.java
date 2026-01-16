// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.common.domain.transaction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

import com.google.protobuf.ByteString;
import org.apache.tuweni.bytes.Bytes;
import org.hiero.mirror.common.aggregator.LogsBloomAggregator;
import org.hyperledger.besu.evm.log.LogsBloomFilter;
import org.junit.jupiter.api.Test;

class LogsBloomAggregatorTest {

    @Test
    void getLogsBloomInsertBytesTest() {
        byte[] bytes1 = {127, -128, 78, -1, -19, -26, 125, 15, -14, -127, -75, 3, -62, -57, -35, 14, -69, -80, 43, 113};
        byte[] bytes2 = {-127, 1, 99, -54, -4, 126, -64, -78, -115, -70, -122, 127, 127, 54, -95, -40, -25, 84, 11, 59};
        byte[] bytes3 = {127, 127, -17, 3, -55, -10, -13, 127, -50, -61, -97, 19, -9, -2, 38, -121, -104, 103, -34, -52
        };

        LogsBloomAggregator bloomAggregator = new LogsBloomAggregator();
        bloomAggregator.aggregate(bytes1);
        byte[] expectedResult = new byte[] {
            -1, -1, -17, -1, -3, -2, -1, -1, -1, -5, -65, 127, -1, -1, -1, -33, -1, -9, -1, -1, 0, 0, 0, 0, 0, 0, 0, 0,
            0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
            0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
            0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
            0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
            0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
            0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
            0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0
        };
        assertThat(bloomAggregator.getBloom()).isNotEqualTo(expectedResult);
        bloomAggregator.aggregate(bytes2);
        assertThat(bloomAggregator.getBloom()).isNotEqualTo(expectedResult);
        bloomAggregator.aggregate(bytes3);
        assertThat(bloomAggregator.getBloom()).isEqualTo(expectedResult);

        // Already inserted bytes should not change the filter
        bloomAggregator.aggregate(bytes3);
        assertThat(bloomAggregator.getBloom()).isEqualTo(expectedResult);
    }

    @Test
    void nullIsConsideredToBeInTheBloom() {
        LogsBloomAggregator bloomAggregator = new LogsBloomAggregator();

        String bloom1 = "000000040000000010";
        bloomAggregator.aggregate(ByteString.fromHex(bloom1).toByteArray());
        assertTrue(bloomAggregator.couldContain(null));
    }

    @Test
    void byteArrayMustHaveCorrectLength() {
        LogsBloomAggregator bloomAggregator = new LogsBloomAggregator();

        String bloom1 = "00000004000000000100";
        bloomAggregator.aggregate(ByteString.fromHex(bloom1).toByteArray());
        assertFalse(bloomAggregator.couldContain(new byte[] {1, 2, 3}));
    }

    @Test
    void topicsMustBeFoundInsideAggregatedBloom() {
        LogsBloomAggregator bloomAggregator = new LogsBloomAggregator();

        String bloom1 =
                "00000004000000001000000000000000000000000000000000000000000000000000000000000000000000000000004000000000000100000000000000000000000000000000000000000000000000000000100000000000000000000000000000000000000000000000000000000000000000000000000000000000001000000000000000000000000000000000000004000000000000000000000000000000000000000000000040000000000000000000000000000000001000000000000000000000000000000000000000000000001000000000000100000080000000000000000000000000000000000000000000000000000000000000000000000000";
        bloomAggregator.aggregate(ByteString.fromHex(bloom1).toByteArray());

        String bloom2 =
                "00000004000000001000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000100000000000000000000000000000000000000000000000000000000100000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000008004000000000000000000000000000000000000000000000040000000000000000000000000000000001000000000000000000000000000000000000000000000000000000000000100002080000000000000000000000000000000000000000000000400000000000000000000000000";
        bloomAggregator.aggregate(ByteString.fromHex(bloom2).toByteArray());

        String[] topics = {
            // topic0 from bloom1
            "2F8788117E7EFF1D82E926EC794901D17C78024A50270940304540A733656F0D",

            // topic1 from bloom1
            "9F2DF0FED2C77648DE5860A4CC508CD0818C85B8B8A1AB4CEEEF8D981C8956A6",

            // evm address from bloom1
            "00000000000000000000000000000000000D98C7",

            // topic0 from bloom2
            "2F8788117E7EFF1D82E926EC794901D17C78024A50270940304540A733656F0D",

            // topic1 from bloom2
            "65D7A28E3265B37A6474929F336521B332C1681B933F6CB9F3376673440D862A",

            // evm address from bloom2
            "00000000000000000000000000000000000D98C7"
        };

        for (var topic : topics) {
            LogsBloomFilter topicBloom = LogsBloomFilter.builder()
                    .insertBytes(Bytes.fromHexString(topic))
                    .build();
            assertTrue(bloomAggregator.couldContain(topicBloom.toArray()), topic);
        }

        String[] stringsNotPresentInAnyBloom = {
            "FF2F8788117E7EFF1D82E926EC794901D17C78024A50270940304540A733656F0D",
            "AA9F2DF0FED2C77648DE5860A4CC508CD0818C85B8B8A1AB4CEEEF8D981C8956A6"
        };
        for (var str : stringsNotPresentInAnyBloom) {
            LogsBloomFilter bloom = LogsBloomFilter.builder()
                    .insertBytes(Bytes.fromHexString(str))
                    .build();
            assertFalse(bloomAggregator.couldContain(bloom.toArray()), str);
        }
    }

    @Test
    void resultingBloomMustBeEmpty() {
        LogsBloomAggregator bloomAggregator = new LogsBloomAggregator();
        assertArrayEquals(new byte[0], bloomAggregator.getBloom());
    }

    @Test
    void mustReturnFalseWhenNotInitialized() {
        LogsBloomAggregator bloomAggregator = new LogsBloomAggregator();
        assertFalse(bloomAggregator.couldContain(new byte[] {}));
    }
}
