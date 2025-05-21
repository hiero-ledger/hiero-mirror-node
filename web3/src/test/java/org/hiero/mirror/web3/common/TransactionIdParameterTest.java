// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import java.time.Instant;
import java.util.stream.Stream;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.hiero.mirror.web3.exception.InvalidParametersException;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.test.context.junit.jupiter.SpringExtension;

@ExtendWith(SpringExtension.class)
class TransactionIdParameterTest {

    private static Stream<Arguments> provideTransactionIds() {
        return Stream.of(
                Arguments.of("0.0.3-1234567890-123", true, null),
                Arguments.of("0.0.3--1234567890-123", false, null),
                Arguments.of("0.0.3-1234567890--123", false, null),
                Arguments.of("0.0.3-1234567890-1234567890", false, null),
                Arguments.of("0.0.3-12345678901234567890-123", false, null),
                Arguments.of(
                        "%d.0.3-1234567890-123".formatted(Long.MAX_VALUE), false, InvalidParametersException.class),
                Arguments.of(
                        "0.%d.3-1234567890-123".formatted(Long.MAX_VALUE), false, InvalidParametersException.class),
                Arguments.of(
                        "0.0.%d-1234567890-123".formatted(Long.MAX_VALUE), false, InvalidParametersException.class),
                Arguments.of("0.0.3-%d-%d".formatted(Long.MAX_VALUE, Long.MAX_VALUE), false, null),
                Arguments.of(null, false, null));
    }

    @ParameterizedTest
    @MethodSource("provideTransactionIds")
    void testParseTransactionId(
            String transactionId, boolean isValidTransactionId, Class<? extends Exception> expectedException) {
        if (!isValidTransactionId) {
            if (expectedException != null) {
                assertThatExceptionOfType(expectedException)
                        .isThrownBy(() -> TransactionIdParameter.valueOf(transactionId))
                        .withMessageContaining(
                                "Invalid entity ID: %s".formatted(transactionId.split("-")[0]));
                return;
            }
            assertThat(TransactionIdParameter.valueOf(transactionId)).isNull();
            return;
        }

        final var parameter = assertDoesNotThrow(() -> TransactionIdParameter.valueOf(transactionId));
        assertThat(parameter)
                .isNotNull()
                .isInstanceOf(TransactionIdParameter.class)
                .isEqualTo(new TransactionIdParameter(EntityId.of(3), Instant.ofEpochSecond(1234567890, 123)));
    }
}
