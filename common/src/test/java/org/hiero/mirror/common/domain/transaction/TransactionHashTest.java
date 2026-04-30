// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.common.domain.transaction;

import static org.assertj.core.api.Assertions.assertThat;

import org.hiero.mirror.common.domain.DomainBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class TransactionHashTest {

    private final DomainBuilder domainBuilder = new DomainBuilder();

    @ParameterizedTest
    @ValueSource(ints = {32, 48})
    void testHashIsValid(int hashSize) {
        byte[] bytes = new byte[hashSize];
        bytes[0] = (byte) 0x81;
        bytes[1] = 0x22;
        bytes[2] = 0x25;
        var nonEmptyHash = TransactionHash.builder().hash(bytes).build();
        assertThat(nonEmptyHash.hashIsValid()).isTrue();
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 31, 33, 47, 49, 50})
    void testHashIsInValid(int hashSize) {
        var nullHash = TransactionHash.builder().build();
        assertThat(nullHash.hashIsValid()).isFalse();

        byte[] bytes = new byte[hashSize];
        var nonEmptyHash = TransactionHash.builder().hash(bytes).build();
        assertThat(nonEmptyHash.hashIsValid()).isFalse();
    }

    @Test
    void testShardCalculation() {
        byte[] bytes = new byte[32];
        bytes[0] = (byte) 0x81;
        bytes[1] = 0x22;
        bytes[2] = 0x25;

        var builder = TransactionHash.builder()
                .consensusTimestamp(1)
                .payerAccountId(5)
                .hash(bytes);

        assertThat(builder.build().calculateV1Shard()).isEqualTo(1);

        bytes[0] = 0x25;
        assertThat(builder.build().calculateV1Shard()).isEqualTo(5);
    }

    @Test
    void hashInvalidSize() {
        var transactionHash = new TransactionHash();
        transactionHash.setHash(new byte[31]);
        assertThat(transactionHash.getHash()).isNull();
    }
}
