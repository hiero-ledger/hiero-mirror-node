// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.parser.record.receipt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hiero.mirror.importer.TestUtils.fill;

import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import java.io.ByteArrayOutputStream;
import java.math.BigInteger;
import java.util.List;
import java.util.Map;
import org.apache.tuweni.bytes.Bytes;
import org.bouncycastle.crypto.digests.KeccakDigest;
import org.hiero.mirror.common.domain.contract.ContractLog;
import org.hiero.mirror.common.domain.contract.ContractResult;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.hiero.mirror.common.util.DomainUtils;
import org.hiero.mirror.common.util.LogsBloomFilter;
import org.junit.jupiter.api.Test;

final class ReceiptRootTest {

    private static final long ALIASED_CONTRACT_A = 100L;
    private static final long ALIASED_CONTRACT_B = 200L;

    @Test
    void empty() {
        assertThat(new ReceiptRoot().getRootHash(Map.of(), Map.of())).isEqualTo(new byte[32]);
    }

    @Test
    void singleReceiptMatchesIndependentTrieRoot() {
        final var receiptRoot = new ReceiptRoot();
        receiptRoot.add(contractResult(0, true, 0L, new byte[256]));

        // reconstruct the single-leaf Merkle Patricia Trie root
        final var value = rlpList(
                rlpString(new byte[] {0x01}), // status = 1
                rlpString(new byte[0]), // cumulativeGasUsed = 0 -> canonical empty scalar (0x80)
                rlpString(new byte[256]), // 256-byte empty bloom
                rlpList()); // no logs

        final var leaf = rlpList(rlpString(new byte[] {0x20, (byte) 0x80}), rlpString(value));

        assertThat(receiptRoot.getRootHash(Map.of(), Map.of())).isEqualTo(keccak256(leaf));
    }

    @Test
    void syntheticReceiptUsesZeroFirstFieldAndEmptyScalarGas() {
        // a transaction with a log but no contract result becomes a synthetic receipt
        final var topic = fill((byte) 0x01, 32);
        final var contractLog = ContractLog.builder()
                .consensusTimestamp(1000L)
                .contractId(EntityId.of(0, 0, 600))
                .index(0)
                .topic0(topic)
                .transactionIndex(0)
                .build();
        final var receiptRoot = new ReceiptRoot();
        receiptRoot.add(contractLog);

        final var address = DomainUtils.toEvmAddress(EntityId.of(0, 0, 600));
        final var bloomFilter = new LogsBloomFilter();
        bloomFilter.insertAddress(address);
        bloomFilter.insertTopic(topic);

        final var value = rlpList(
                rlpString(new byte[32]), // synthetic receipt first field = 32 zero bytes in place of the status
                rlpString(new byte[0]), // cumulativeGasUsed = 0 -> canonical empty scalar (0x80)
                rlpString(bloomFilter.toArrayUnsafe()),
                rlpList(rlpList(rlpString(address), rlpList(rlpString(topic)), rlpString(new byte[0]))));
        final var leaf = rlpList(rlpString(new byte[] {0x20, (byte) 0x80}), rlpString(value));

        assertThat(receiptRoot.getRootHash(Map.of(), Map.of())).isEqualTo(keccak256(leaf));
    }

    @Test
    void nonZeroCumulativeGasUsesMinimalScalar() {
        final var receiptRoot = new ReceiptRoot();
        receiptRoot.add(contractResult(0, true, 256L, new byte[256]));

        final var value = rlpList(
                rlpString(new byte[] {0x01}), rlpString(new byte[] {0x01, 0x00}), rlpString(new byte[256]), rlpList());
        final var leaf = rlpList(rlpString(new byte[] {0x20, (byte) 0x80}), rlpString(value));

        assertThat(receiptRoot.getRootHash(Map.of(), Map.of())).isEqualTo(keccak256(leaf));
    }

    /**
     * Test against Hedera mainnet block 97775344 (0x5d3eef0). Both transactions are failed (WRONG_NONCE)
     * contract calls with gasUsed = 0, so the block pins the canonical RLP encoding of zero cumulative gas as the
     * empty string (0x80). The expected root was computed with an independent implementation (@ethereumjs/trie and
     * @ethereumjs/rlp with integer scalars) and intentionally differs from the 0xcd311260... root the relay served
     * for this block, whose legacy calculation encoded zero cumulative gas as a non-canonical 0x00 byte.
     */
    @Test
    void mainnetBlockWithZeroCumulativeGasUsesCanonicalScalar() {
        final var receiptRoot = new ReceiptRoot();
        receiptRoot.add(contractResult(4, false, 0L, new byte[256]));
        receiptRoot.add(contractResult(7, false, 0L, new byte[256]));

        assertThat(receiptRoot.getRootHash(Map.of(), Map.of()))
                .isEqualTo(bytes("46e3a8baf0e3de5cfce58e12dfa221b5d643f51fdd38e4f22dbf7866b85146b1"));
    }

    /**
     * Test against Hedera mainnet block 97775284 (0x5d3eeb4), whose receiptsRoot was returned by the
     * JSON-RPC relay (eth_getBlockByNumber via mainnet.hashio.io on 2026-07-17). The block contains a successful
     * contract call with four logs and a non-trivial bloom followed by a failed zero-gas transaction.
     */
    @Test
    void mainnetBlockWithLogsMatchesRelayReceiptsRoot() {
        final var bloom = bytes("0000000000000000000000000000000000000000000000008000000000000000"
                + "0000000020000000000000000000000000000000000000000000000020000a00"
                + "0000000000000000000000000004000000002000000001000000000000000001"
                + "0000000002000000000000000000080000000000000000000000000000000100"
                + "0000000000010000000000000000000000000000000000000000000000000001"
                + "0000000000000000000000000000000000000000000080000000000002000000"
                + "0000000000000004000000000000000000000000000000001000000020002000"
                + "0000000800000000000010000000000000000001000000008000000000000000");
        final var logs = List.of(
                contractLog(
                        0,
                        4,
                        ALIASED_CONTRACT_A,
                        "00000000000000000000000000000000000000000000000071a7432fce49c000"
                                + "000000000000000000000000000000000000000000000000000000006a59d60e",
                        "52f50aa6d1a95a4595361ecf953d095f125d442e4673716dede699e049de148a",
                        "0000000000000000000000007ce6bb2cc2d3fd45a974da6a0f29236cb9513a98"),
                contractLog(
                        1,
                        4,
                        ALIASED_CONTRACT_A,
                        "0000000000000000000000000000000000000000022373a84bf1629d14e00000"
                                + "000000000000000000000000000000000000000000000000000000006a59d60e",
                        "52f50aa6d1a95a4595361ecf953d095f125d442e4673716dede699e049de148a",
                        "000000000000000000000000b1f616b8134f602c3bb465fb5b5e6565ccad37ed"),
                contractLog(
                        2,
                        4,
                        ALIASED_CONTRACT_B,
                        "0000000000000000000000000000000000000000000000000000000000000060"
                                + "0000000000000000000000000000000000000000000000000000000000000080"
                                + "00000000000000000000000000000000000000000000000000000000000000a0"
                                + "0000000000000000000000000000000000000000000000000000000000000000"
                                + "0000000000000000000000000000000000000000000000000000000000000000"
                                + "0000000000000000000000000000000000000000000000000000000000000040"
                                + "00000000000000000000000000000000000000000000000000000000000000e0"
                                + "0000000000000000000000000000000000000000000000000000000000000002"
                                + "0000000000000000000000007ce6bb2cc2d3fd45a974da6a0f29236cb9513a98"
                                + "00000000000000000000000000000000000000000000000071a7432fce49c000"
                                + "000000000000000000000000b1f616b8134f602c3bb465fb5b5e6565ccad37ed"
                                + "0000000000000000000000000000000000000000022373a84bf1629d14e00000"
                                + "0000000000000000000000000000000000000000000000000000000000000000",
                        "b967c9b9e1b7af9a61ca71ff00e9f5b89ec6f2e268de8dacf12f0de8e51f3e47"),
                contractLog(
                        3,
                        4,
                        ALIASED_CONTRACT_B,
                        "000ad45374fd3ec68ae6c31741ec5c0f705c103f68ec76a31bf20101032a8e1c"
                                + "000000000000000000000000000000000000000000000000000000000003f86f",
                        "198d6990ef96613a9026203077e422916918b03ff47f0be6bee7b02d8e139ef0",
                        "0000000000000000000000000000000000000000000000000000000000000000"));
        final var evmAddresses = Map.of(
                ALIASED_CONTRACT_A, bytes("47cbb1f75fa2a98a87f861c5039fcb522b93a640"),
                ALIASED_CONTRACT_B, bytes("f09afe78d3c7d359b334d7cb88995751f7ec5e13"));

        final var receiptRoot = new ReceiptRoot();
        receiptRoot.add(contractResult(4, true, 0x1fd7dL, bloom));
        logs.forEach(receiptRoot::add);
        receiptRoot.add(contractResult(5, false, 0L, new byte[256]));

        assertThat(receiptRoot.getRootHash(Map.of(), evmAddresses))
                .isEqualTo(bytes("8fecfc53901017434f8a078ddc11119fa6117bbbf937a4ac3b2086d0b3b37c57"));
    }

    @Test
    void orderIndependentOfInputOrdering() {
        final var first = contractResult(0, true, 21000L, new byte[256]);
        final var second = contractResult(1, false, 42000L, new byte[256]);
        final var secondLog = ContractLog.builder()
                .consensusTimestamp(consensusTimestamp(1))
                .index(0)
                .topic0(new byte[32])
                .transactionIndex(1)
                .build();

        final var types = Map.of(consensusTimestamp(1), 2);
        final var forward = new ReceiptRoot();
        forward.add(first);
        forward.add(second);
        forward.add(secondLog);
        final var reverse = new ReceiptRoot();
        reverse.add(secondLog);
        reverse.add(second);
        reverse.add(first);

        assertThat(forward.getRootHash(types, Map.of()))
                .isEqualTo(reverse.getRootHash(types, Map.of()))
                .hasSize(32);
    }

    @Test
    void distinctReceiptsProduceDistinctRoots() {
        final var success = new ReceiptRoot();
        success.add(contractResult(0, true, 21000L, new byte[256]));
        final var failure = new ReceiptRoot();
        failure.add(contractResult(0, false, 21000L, new byte[256]));

        assertThat(success.getRootHash(Map.of(), Map.of())).isNotEqualTo(failure.getRootHash(Map.of(), Map.of()));
    }

    private static long consensusTimestamp(final int transactionIndex) {
        return 1000L + transactionIndex;
    }

    private static ContractResult contractResult(
            final int transactionIndex, final boolean success, final long gasUsed, final byte[] bloom) {
        return ContractResult.builder()
                .bloom(bloom)
                .consensusTimestamp(consensusTimestamp(transactionIndex))
                .gasUsed(gasUsed)
                .transactionIndex(transactionIndex)
                .transactionResult(success ? ResponseCodeEnum.SUCCESS_VALUE : ResponseCodeEnum.WRONG_NONCE_VALUE)
                .build();
    }

    private static ContractLog contractLog(
            final int index,
            final int transactionIndex,
            final long contractNum,
            final String data,
            final String... topics) {
        final var builder = ContractLog.builder()
                .consensusTimestamp(consensusTimestamp(transactionIndex))
                .contractId(EntityId.of(0, 0, contractNum))
                .data(bytes(data))
                .index(index)
                .transactionIndex(transactionIndex);
        if (topics.length > 0) {
            builder.topic0(bytes(topics[0]));
        }
        if (topics.length > 1) {
            builder.topic1(bytes(topics[1]));
        }
        return builder.build();
    }

    private static byte[] bytes(final String hex) {
        return Bytes.fromHexString(hex).toArray();
    }

    private static byte[] keccak256(final byte[] input) {
        final var digest = new KeccakDigest(256);
        digest.update(input, 0, input.length);
        final var out = new byte[32];
        digest.doFinal(out, 0);
        return out;
    }

    private static byte[] rlpString(final byte[] value) {
        if (value.length == 1 && (value[0] & 0xFF) < 0x80) {
            return value;
        }
        if (value.length <= 55) {
            return concat(new byte[] {(byte) (0x80 + value.length)}, value);
        }
        final var length = minimalBigEndian(value.length);
        return concat(new byte[] {(byte) (0xB7 + length.length)}, length, value);
    }

    private static byte[] rlpList(final byte[]... items) {
        final var payload = concat(items);
        if (payload.length <= 55) {
            return concat(new byte[] {(byte) (0xC0 + payload.length)}, payload);
        }
        final var length = minimalBigEndian(payload.length);
        return concat(new byte[] {(byte) (0xF7 + length.length)}, length, payload);
    }

    private static byte[] minimalBigEndian(final int value) {
        final var bytes = BigInteger.valueOf(value).toByteArray();
        return bytes.length > 1 && bytes[0] == 0 ? java.util.Arrays.copyOfRange(bytes, 1, bytes.length) : bytes;
    }

    private static byte[] concat(final byte[]... arrays) {
        final var out = new ByteArrayOutputStream();
        for (final var array : arrays) {
            out.writeBytes(array);
        }
        return out.toByteArray();
    }
}
