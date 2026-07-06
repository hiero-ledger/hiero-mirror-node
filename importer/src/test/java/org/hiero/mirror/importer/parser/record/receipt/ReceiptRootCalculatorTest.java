// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.parser.record.receipt;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.math.BigInteger;
import java.util.List;
import org.bouncycastle.crypto.digests.KeccakDigest;
import org.hiero.mirror.importer.parser.record.receipt.Receipt.ReceiptLog;
import org.junit.jupiter.api.Test;

final class ReceiptRootCalculatorTest {

    private final ReceiptRootCalculator calculator = new ReceiptRootCalculator();

    @Test
    void empty() {
        assertThat(calculator.calculate(List.of())).isEqualTo(new byte[32]);
    }

    @Test
    void singleReceiptMatchesIndependentTrieRoot() {
        final var receipt = new Receipt(0, 0, true, null, 0L, new byte[256], List.of());

        // reconstruct the single-leaf Merkle Patricia Trie root
        final var value = rlpList(
                rlpString(new byte[] {0x01}), // status = 1
                rlpString(new byte[] {0x00}), // cumulativeGasUsed = 0 -> single 0x00 byte
                rlpString(new byte[256]), // 256-byte empty bloom
                rlpList()); // no logs

        final var leaf = rlpList(rlpString(new byte[] {0x20, (byte) 0x80}), rlpString(value));

        assertThat(calculator.calculate(List.of(receipt))).isEqualTo(keccak256(leaf));
    }

    @Test
    void syntheticReceiptUsesZeroRootAndSingleByteZeroGas() {
        final var receipt = new Receipt(0, 0, true, new byte[32], 0L, new byte[256], List.of());

        final var value = rlpList(
                rlpString(new byte[32]), // post-state root = 32 zero bytes
                rlpString(new byte[] {0x00}),
                rlpString(new byte[256]),
                rlpList());
        final var leaf = rlpList(rlpString(new byte[] {0x20, (byte) 0x80}), rlpString(value));

        assertThat(calculator.calculate(List.of(receipt))).isEqualTo(keccak256(leaf));
    }

    @Test
    void nonZeroCumulativeGasUsesMinimalScalar() {
        final var receipt = new Receipt(0, 0, true, null, 256L, new byte[256], List.of());

        final var value = rlpList(
                rlpString(new byte[] {0x01}), rlpString(new byte[] {0x01, 0x00}), rlpString(new byte[256]), rlpList());
        final var leaf = rlpList(rlpString(new byte[] {0x20, (byte) 0x80}), rlpString(value));

        assertThat(calculator.calculate(List.of(receipt))).isEqualTo(keccak256(leaf));
    }

    @Test
    void orderIndependentOfInputOrdering() {
        final var first = new Receipt(0, 0, true, null, 21000L, new byte[256], List.of());
        final var second = new Receipt(
                1,
                2,
                false,
                null,
                42000L,
                new byte[256],
                List.of(new ReceiptLog(new byte[20], List.of(new byte[32]), new byte[0])));

        assertThat(calculator.calculate(List.of(first, second)))
                .isEqualTo(calculator.calculate(List.of(second, first)))
                .hasSize(32);
    }

    @Test
    void distinctReceiptsProduceDistinctRoots() {
        final var success = new Receipt(0, 0, true, null, 21000L, new byte[256], List.of());
        final var failure = new Receipt(0, 0, false, null, 21000L, new byte[256], List.of());

        assertThat(calculator.calculate(List.of(success))).isNotEqualTo(calculator.calculate(List.of(failure)));
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
