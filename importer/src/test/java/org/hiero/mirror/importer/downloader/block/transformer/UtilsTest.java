// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.downloader.block.transformer;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.protobuf.ByteString;
import com.hedera.hapi.block.stream.trace.protoc.EvmTransactionLog;
import com.hederahashgraph.api.proto.java.ContractID;
import java.util.BitSet;
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;
import org.apache.commons.lang3.StringUtils;
import org.bouncycastle.util.encoders.Hex;
import org.hiero.mirror.common.util.CommonUtils;
import org.hiero.mirror.common.util.DomainUtils;
import org.hyperledger.besu.evm.log.LogsBloomFilter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;

final class UtilsTest {

    @ParameterizedTest
    @CsvSource(
            textBlock =
                    """
            0, 0, 158, 0x000000000000000000000000000000000000009e
            259, 1027, 3493, 0x0000000000000000000000000000000000000da5
            """)
    void asAddress(long shard, long realm, long num, String expected) {
        var contractId = ContractID.newBuilder()
                .setShardNum(shard)
                .setRealmNum(realm)
                .setContractNum(num)
                .build();
        assertThat(Utils.asAddress(contractId).toHexString()).isEqualTo(expected);
    }

    @ParameterizedTest
    @MethodSource("provideBloomForTestParameters")
    void bloomFor(ContractID contractId, List<ByteString> topics, byte[] expected) {
        var evmTransactionLog = EvmTransactionLog.newBuilder()
                .setContractId(contractId)
                // data is not included in bloom filter
                .setData(DomainUtils.fromBytes(CommonUtils.nextBytes(32)))
                .addAllTopics(topics)
                .build();
        assertThat(Utils.bloomFor(evmTransactionLog).toArray()).isEqualTo(expected);
    }

    @Test
    void bloomForAll() {
        assertThat(Utils.bloomForAll(Collections.emptyList())).isEqualTo(LogsBloomFilter.empty());

        var bloom1 = LogsBloomFilter.fromHexString(logsBloomHexStr("ab"));
        assertThat(Utils.bloomForAll(List.of(bloom1))).isEqualTo(bloom1);

        var bloom2 = LogsBloomFilter.fromHexString(logsBloomHexStr("ab95"));
        var expected = LogsBloomFilter.fromHexString(logsBloomHexStr("abbf"));
        assertThat(Utils.bloomForAll(List.of(bloom1, bloom2))).isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource(
            textBlock =
                    """
            '',0000000000000000000000000000000000000000000000000000000000000000
            3728e591097635310e6341af53db8b7ee42da9b3a8d918f9463ce9cca886dfbd,3728e591097635310e6341af53db8b7ee42da9b3a8d918f9463ce9cca886dfbd
            628669,0000000000000000000000000000000000000000000000000000000000628669
            """)
    void leftPad32(String topicHex, String expectedHex) {
        var topic = Hex.decode(topicHex);
        var expected = Hex.decode(expectedHex);
        assertThat(Utils.leftPad32(DomainUtils.fromBytes(topic))).isEqualTo(expected);
    }

    private String logsBloomHexStr(String str) {
        return StringUtils.leftPad(str, LogsBloomFilter.BYTE_SIZE * 2, '0');
    }

    private static Stream<Arguments> provideBloomForTestParameters() {
        // data extracted from testnet, the expected bloom filter byte array is made of the list of bit indices set to 1
        return Stream.of(
                Arguments.of(
                        ContractID.newBuilder().setContractNum(3042133).build(),
                        List.of(
                                DomainUtils.fromBytes(
                                        Hex.decode("d06a6b7f4918494b3719217d1802786c1f5112a6c1d88fe2cfec00b4584f6aec")),
                                DomainUtils.fromBytes(Hex.decode(
                                        "3728e591097635310e6341af53db8b7ee42da9b3a8d918f9463ce9cca886dfbd"))),
                        bloomFilterFromBits(List.of(93, 179, 263, 375, 745, 1333, 1464, 1478, 1538))),
                Arguments.of(
                        ContractID.newBuilder().setContractNum(6739121).build(),
                        List.of(
                                DomainUtils.fromBytes(
                                        Hex.decode("ddf252ad1be2c89b69c2b068fc378daa952ba7f163c4a11628f55a4df523b3ef")),
                                DomainUtils.fromBytes(Hex.decode("62866b")),
                                DomainUtils.fromBytes(Hex.decode("628669"))),
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
}
