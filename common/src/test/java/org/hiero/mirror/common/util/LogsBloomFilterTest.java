// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.common.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.protobuf.ByteString;
import com.hederahashgraph.api.proto.java.ContractID;
import java.util.BitSet;
import java.util.List;
import java.util.stream.Stream;
import org.apache.commons.codec.DecoderException;
import org.apache.commons.codec.binary.Hex;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

final class LogsBloomFilterTest {

    // Pre-computed bloom filters from real transactions (512 hex chars = 256 bytes each)
    private static final String BLOOM1 =
            "00000004000000001000000000000000000000000000000000000000000000000000000000000000000000000000004000000000000100000000000000000000000000000000000000000000000000000000100000000000000000000000000000000000000000000000000000000000000000000000000000000000001000000000000000000000000000000000000004000000000000000000000000000000000000000000000040000000000000000000000000000000001000000000000000000000000000000000000000000000001000000000000100000080000000000000000000000000000000000000000000000000000000000000000000000000";

    private static final String BLOOM2 =
            "00000004000000001000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000100000000000000000000000000000000000000000000000000000000100000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000008004000000000000000000000000000000000000000000000040000000000000000000000000000000001000000000000000000000000000000000000000000000000000000000000100002080000000000000000000000000000000000000000000000400000000000000000000000000";

    private final LogsBloomFilter logsBloomFilter = new LogsBloomFilter();

    private static byte[] hex(String hex) {
        try {
            return Hex.decodeHex(hex);
        } catch (DecoderException e) {
            throw new RuntimeException(e);
        }
    }

    private static Stream<Arguments> knownTopics() {
        return Stream.of(
                Arguments.of("2F8788117E7EFF1D82E926EC794901D17C78024A50270940304540A733656F0D", BLOOM1),
                Arguments.of("9F2DF0FED2C77648DE5860A4CC508CD0818C85B8B8A1AB4CEEEF8D981C8956A6", BLOOM1),
                Arguments.of("2F8788117E7EFF1D82E926EC794901D17C78024A50270940304540A733656F0D", BLOOM2),
                Arguments.of("65D7A28E3265B37A6474929F336521B332C1681B933F6CB9F3376673440D862A", BLOOM2));
    }

    private static Stream<Arguments> absentTopics() {
        return Stream.of(
                Arguments.of("FF2F8788117E7EFF1D82E926EC794901D17C78024A50270940304540A733656F0D"),
                Arguments.of("AA9F2DF0FED2C77648DE5860A4CC508CD0818C85B8B8A1AB4CEEEF8D981C8956A6"));
    }

    private static Stream<Arguments> provideBloomForTestParameters() {
        // data extracted from testnet, the expected bloom filter byte array is made of the list of bit indices set to 1
        return Stream.of(
                Arguments.of(
                        ContractID.newBuilder().setContractNum(3042133).build(),
                        List.of(
                                DomainUtils.fromBytes(org.bouncycastle.util.encoders.Hex.decode(
                                        "d06a6b7f4918494b3719217d1802786c1f5112a6c1d88fe2cfec00b4584f6aec")),
                                DomainUtils.fromBytes(org.bouncycastle.util.encoders.Hex.decode(
                                        "3728e591097635310e6341af53db8b7ee42da9b3a8d918f9463ce9cca886dfbd"))),
                        bloomFilterFromBits(List.of(93, 179, 263, 375, 745, 1333, 1464, 1478, 1538))),
                Arguments.of(
                        ContractID.newBuilder().setContractNum(6739121).build(),
                        List.of(
                                DomainUtils.fromBytes(org.bouncycastle.util.encoders.Hex.decode(
                                        "ddf252ad1be2c89b69c2b068fc378daa952ba7f163c4a11628f55a4df523b3ef")),
                                DomainUtils.fromBytes(org.bouncycastle.util.encoders.Hex.decode("62866b")),
                                DomainUtils.fromBytes(org.bouncycastle.util.encoders.Hex.decode("628669"))),
                        bloomFilterFromBits(List.of(217, 307, 603, 642, 657, 697, 988, 1029, 1156, 1232, 1303, 1561))));
    }

    private static byte[] bloomFilterFromBits(List<Integer> bits) {
        var bitSet = new BitSet(LogsBloomFilter.BYTE_SIZE * 8);
        bits.forEach(bitSet::set);
        var bloom = bitSet.toByteArray();
        // the byte array from the BitSet will only contain bytes up to the last set bit
        byte[] padded = new byte[LogsBloomFilter.BYTE_SIZE];
        System.arraycopy(bloom, 0, padded, 0, bloom.length);
        return padded;
    }

    @Test
    void newFilterHasAllZeroBytes() {
        assertThat(logsBloomFilter.toArrayUnsafe()).isEmpty();
    }

    @Test
    void toArrayUnsafeHasNonZeroBytesAfterInsert() {
        logsBloomFilter.insertTopic(hex("2F8788117E7EFF1D82E926EC794901D17C78024A50270940304540A733656F0D"));
        assertThat(logsBloomFilter.toArrayUnsafe())
                .hasSize(LogsBloomFilter.BYTE_SIZE)
                .isNotEqualTo(new byte[LogsBloomFilter.BYTE_SIZE]);
    }

    @Test
    void toByteStringMatchesToArrayUnsafe() {
        logsBloomFilter.insertTopic(hex("2F8788117E7EFF1D82E926EC794901D17C78024A50270940304540A733656F0D"));
        assertThat(logsBloomFilter.toByteString().toByteArray()).isEqualTo(logsBloomFilter.toArrayUnsafe());
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "00000000000000000000000000000000000D98C7", // 20-byte Hedera EVM address in BLOOM1
            })
    void insertAddressIsFoundInAggregatedBloom(String addressHex) {
        logsBloomFilter.or(hex(BLOOM1));

        var filter = new LogsBloomFilter();
        filter.insertAddress(hex(addressHex));

        assertThat(logsBloomFilter.couldContain(filter.toArrayUnsafe())).isTrue();
    }

    @Test
    void insertAddressEmptyInputIsIgnored() {
        logsBloomFilter.insertAddress(new byte[0]);
        assertThat(logsBloomFilter.toArrayUnsafe()).isEmpty();
    }

    @Test
    void insertAddressNullInputIsIgnored() {
        logsBloomFilter.insertAddress(null);
        assertThat(logsBloomFilter.toArrayUnsafe()).isEmpty();
    }

    @ParameterizedTest
    @MethodSource("knownTopics")
    void insertTopicIsFoundInAggregatedBloom(String topicHex, String bloomHex) {
        logsBloomFilter.or(hex(bloomHex));

        var logsBloomFilter2 = new LogsBloomFilter();
        logsBloomFilter2.insertTopic(hex(topicHex));

        assertThat(logsBloomFilter.couldContain(logsBloomFilter2.toArrayUnsafe()))
                .isTrue();
    }

    @Test
    void insertTopicPadsShortInputToMatchZeroPaddedEquivalent() {
        // insertTopic left-pads inputs shorter than 32 bytes with zeros;
        // "000D98C7" (4 bytes) should produce the same bloom as its 32-byte zero-padded form
        var shortFilter = new LogsBloomFilter();
        shortFilter.insertTopic(hex("000D98C7")); // 4 bytes → padded to 32

        var paddedFilter = new LogsBloomFilter();
        paddedFilter.insertTopic(hex("00000000000000000000000000000000000000000000000000000000000D98C7"));

        assertThat(shortFilter.toArrayUnsafe()).isEqualTo(paddedFilter.toArrayUnsafe());
    }

    @Test
    void insertTopicEmptyInputTreatedAsZeroPadded() {
        // Empty input is left-padded to 32 zero bytes, which IS inserted (not ignored)
        var emptyFilter = new LogsBloomFilter();
        emptyFilter.insertTopic(new byte[0]);

        var zeroFilter = new LogsBloomFilter();
        zeroFilter.insertTopic(new byte[32]);

        assertThat(emptyFilter.toArrayUnsafe()).isEqualTo(zeroFilter.toArrayUnsafe());
    }

    @Test
    void insertTopicNullInputIsIgnored() {
        var filter = new LogsBloomFilter();
        filter.insertTopic((byte[]) null);
        assertThat(filter.toArrayUnsafe()).isEmpty();
    }

    @Test
    void insertTopicByteStringEquivalentToByteArray() {
        var topic = hex("2F8788117E7EFF1D82E926EC794901D17C78024A50270940304540A733656F0D");

        var byteArrayFilter = new LogsBloomFilter();
        byteArrayFilter.insertTopic(topic);

        var byteStringFilter = new LogsBloomFilter();
        byteStringFilter.insertTopic(ByteString.copyFrom(topic));

        assertThat(byteStringFilter.toArrayUnsafe()).isEqualTo(byteArrayFilter.toArrayUnsafe());
    }

    @Test
    void couldContainNullReturnsTrue() {
        logsBloomFilter.insertTopic(hex("2F8788117E7EFF1D82E926EC794901D17C78024A50270940304540A733656F0D"));
        assertThat(logsBloomFilter.couldContain(null)).isTrue();
    }

    @Test
    void couldContainAllZeroBloomReturnsTrueVacuously() {
        // An all-zero bloom encodes no items; it is trivially a subset of any filter
        logsBloomFilter.insertTopic(hex("2F8788117E7EFF1D82E926EC794901D17C78024A50270940304540A733656F0D"));
        assertThat(logsBloomFilter.couldContain(new byte[LogsBloomFilter.BYTE_SIZE]))
                .isTrue();
    }

    @ParameterizedTest
    @MethodSource("absentTopics")
    void couldContainReturnsFalseForAbsentTopics(String topicHex) {
        logsBloomFilter.or(hex(BLOOM1));
        logsBloomFilter.or(hex(BLOOM2));

        var filter = new LogsBloomFilter();
        filter.insertTopic(hex(topicHex));

        assertThat(logsBloomFilter.couldContain(filter.toArrayUnsafe())).isFalse();
    }

    // --- or(byte[]) ---

    @Test
    void orNullFilterThrowsIllegalArgumentException() {
        assertThatThrownBy(() -> logsBloomFilter.or((LogsBloomFilter) null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void orFilterMergesTopicsFromBothFilters() {
        var topic1Hex = "2F8788117E7EFF1D82E926EC794901D17C78024A50270940304540A733656F0D";
        var topic2Hex = "65D7A28E3265B37A6474929F336521B332C1681B933F6CB9F3376673440D862A";

        var filter1 = new LogsBloomFilter();
        filter1.insertTopic(hex(topic1Hex));

        var filter2 = new LogsBloomFilter();
        filter2.insertTopic(hex(topic2Hex));

        filter1.or(filter2);

        var topic1Filter = new LogsBloomFilter();
        topic1Filter.insertTopic(hex(topic1Hex));
        assertThat(filter1.couldContain(topic1Filter.toArrayUnsafe())).isTrue();

        var topic2Filter = new LogsBloomFilter();
        topic2Filter.insertTopic(hex(topic2Hex));
        assertThat(filter1.couldContain(topic2Filter.toArrayUnsafe())).isTrue();
    }

    @Test
    void orNullArrayThrowsIllegalArgumentException() {
        assertThatThrownBy(() -> logsBloomFilter.or((byte[]) null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void orArrayAccumulatesBloomValues() {
        logsBloomFilter.or(hex(BLOOM1));
        logsBloomFilter.or(hex(BLOOM2));

        // A topic unique to BLOOM2 should be found after ORing both
        var filter = new LogsBloomFilter();
        filter.insertTopic(hex("65D7A28E3265B37A6474929F336521B332C1681B933F6CB9F3376673440D862A"));
        assertThat(logsBloomFilter.couldContain(filter.toArrayUnsafe())).isTrue();
    }

    @Test
    void orArrayIsIdempotent() {
        logsBloomFilter.or(hex(BLOOM1));
        byte[] afterFirst = logsBloomFilter.toArrayUnsafe().clone();

        logsBloomFilter.or(hex(BLOOM1));

        assertThat(logsBloomFilter.toArrayUnsafe()).isEqualTo(afterFirst);
    }

    @Test
    void getLogsBloomInsertBytesTest() {
        byte[] bytes1 = {127, -128, 78, -1, -19, -26, 125, 15, -14, -127, -75, 3, -62, -57, -35, 14, -69, -80, 43, 113};
        byte[] bytes2 = {-127, 1, 99, -54, -4, 126, -64, -78, -115, -70, -122, 127, 127, 54, -95, -40, -25, 84, 11, 59};
        byte[] bytes3 = {127, 127, -17, 3, -55, -10, -13, 127, -50, -61, -97, 19, -9, -2, 38, -121, -104, 103, -34, -52
        };

        logsBloomFilter.or(bytes1);
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
        assertThat(logsBloomFilter.toArrayUnsafe()).isNotEqualTo(expectedResult);
        logsBloomFilter.or(bytes2);
        assertThat(logsBloomFilter.toArrayUnsafe()).isNotEqualTo(expectedResult);
        logsBloomFilter.or(bytes3);
        assertThat(logsBloomFilter.toArrayUnsafe()).isEqualTo(expectedResult);

        // Already inserted bytes should not change the filter
        logsBloomFilter.or(bytes3);
        assertThat(logsBloomFilter.toArrayUnsafe()).isEqualTo(expectedResult);
    }

    @Test
    void nullIsConsideredToBeInTheBloom() {
        assertTrue(logsBloomFilter.couldContain(null));
    }

    @Test
    void byteArrayMustHaveCorrectLength() {
        String bloom1 = "00000004000000000100";
        logsBloomFilter.or(
                DomainUtils.leftPadBytes(ByteString.fromHex(bloom1).toByteArray(), LogsBloomFilter.BYTE_SIZE));
        assertFalse(logsBloomFilter.couldContain(new byte[] {1, 2, 3}));
    }

    @Test
    void topicsMustBeFoundInsideAggregatedBloom() throws Exception {
        String bloom1 =
                "00000004000000001000000000000000000000000000000000000000000000000000000000000000000000000000004000000000000100000000000000000000000000000000000000000000000000000000100000000000000000000000000000000000000000000000000000000000000000000000000000000000001000000000000000000000000000000000000004000000000000000000000000000000000000000000000040000000000000000000000000000000001000000000000000000000000000000000000000000000001000000000000100000080000000000000000000000000000000000000000000000000000000000000000000000000";
        logsBloomFilter.or(ByteString.fromHex(bloom1).toByteArray());

        String bloom2 =
                "00000004000000001000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000100000000000000000000000000000000000000000000000000000000100000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000008004000000000000000000000000000000000000000000000040000000000000000000000000000000001000000000000000000000000000000000000000000000000000000000000100002080000000000000000000000000000000000000000000000400000000000000000000000000";
        logsBloomFilter.or(ByteString.fromHex(bloom2).toByteArray());

        String[] addresses = {
            // evm address from bloom1
            "00000000000000000000000000000000000D98C7",
            // evm address from bloom2
            "00000000000000000000000000000000000D98C7"
        };
        String[] topics = {
            // topic0 from bloom1
            "2F8788117E7EFF1D82E926EC794901D17C78024A50270940304540A733656F0D",

            // topic1 from bloom1
            "9F2DF0FED2C77648DE5860A4CC508CD0818C85B8B8A1AB4CEEEF8D981C8956A6",

            // topic0 from bloom2
            "2F8788117E7EFF1D82E926EC794901D17C78024A50270940304540A733656F0D",

            // topic1 from bloom2
            "65D7A28E3265B37A6474929F336521B332C1681B933F6CB9F3376673440D862A",
        };

        for (var address : addresses) {
            final var topicBloom = new LogsBloomFilter();
            topicBloom.insertAddress(Hex.decodeHex(address));
            assertTrue(logsBloomFilter.couldContain(topicBloom.toArrayUnsafe()), address);
        }

        for (var topic : topics) {
            final var topicBloom = new LogsBloomFilter();
            topicBloom.insertTopic(Hex.decodeHex(topic));
            assertTrue(logsBloomFilter.couldContain(topicBloom.toArrayUnsafe()), topic);
        }

        String[] stringsNotPresentInAnyBloom = {
            "FF2F8788117E7EFF1D82E926EC794901D17C78024A50270940304540A733656F0D",
            "AA9F2DF0FED2C77648DE5860A4CC508CD0818C85B8B8A1AB4CEEEF8D981C8956A6"
        };
        for (var str : stringsNotPresentInAnyBloom) {
            final var topicBloom = new LogsBloomFilter();
            topicBloom.insertTopic(Hex.decodeHex(str));
            assertFalse(logsBloomFilter.couldContain(topicBloom.toArrayUnsafe()), str);
        }
    }

    @ParameterizedTest
    @MethodSource("provideBloomForTestParameters")
    void bloomFor(ContractID contractId, List<ByteString> topics, byte[] expected) {
        final var logsBloomFilter = new LogsBloomFilter();
        logsBloomFilter.insertAddress(DomainUtils.toEvmAddress(EntityId.of(contractId)));
        topics.forEach(logsBloomFilter::insertTopic);

        assertThat(logsBloomFilter.toArrayUnsafe()).isEqualTo(expected);
    }
}
