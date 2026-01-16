// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import java.time.Instant;
import java.util.stream.Stream;
import org.apache.tuweni.bytes.Bytes;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.hiero.mirror.web3.exception.InvalidParametersException;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.test.context.junit.jupiter.SpringExtension;

@ExtendWith(SpringExtension.class)
class TransactionIdOrHashParameterTest {

    private static Stream<Arguments> provideEthHashes() {
        return Stream.of(
                Arguments.of("0x1234567890abcdef1234567890abcdef1234567890abcdef1234567890abcdef", true),
                Arguments.of("1234567890abcdef1234567890abcdef1234567890abcdef1234567890abcdef", true),
                Arguments.of("0xGHIJKL", false),
                Arguments.of(null, false));
    }

    private static Stream<Arguments> provideTransactionIds() {
        return Stream.of(
                Arguments.of("0.0.3-1234567890-123", true),
                Arguments.of("0.0.3-1234567890-1234567890", false),
                Arguments.of("0.0.3-12345678901234567890-123", false),
                Arguments.of(null, false));
    }

    @ParameterizedTest
    @MethodSource("provideEthHashes")
    void testParseTransactionHash(String hash, boolean isValidHash) {
        if (!isValidHash) {
            assertThatExceptionOfType(InvalidParametersException.class)
                    .isThrownBy(() -> TransactionIdOrHashParameter.valueOf(hash));
            return;
        }

        final var parameter = assertDoesNotThrow(() -> TransactionIdOrHashParameter.valueOf(hash));
        assertThat(parameter)
                .isNotNull()
                .isInstanceOf(TransactionHashParameter.class)
                .isEqualTo(new TransactionHashParameter(Bytes.fromHexString(hash)));
    }

    @ParameterizedTest
    @MethodSource("provideTransactionIds")
    void testParseTransactionId(String transactionId, boolean isValidTransactionId) {
        if (!isValidTransactionId) {
            assertThatExceptionOfType(InvalidParametersException.class)
                    .isThrownBy(() -> TransactionIdOrHashParameter.valueOf(transactionId));
            return;
        }

        final var parameter = assertDoesNotThrow(() -> TransactionIdOrHashParameter.valueOf(transactionId));
        assertThat(parameter)
                .isNotNull()
                .isInstanceOf(TransactionIdParameter.class)
                .isEqualTo(new TransactionIdParameter(EntityId.of(3), Instant.ofEpochSecond(1234567890, 123)));
    }
}
