// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.common;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import java.util.stream.Stream;
import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.test.context.junit.jupiter.SpringExtension;

@ExtendWith(SpringExtension.class)
class TransactionHashParameterTest {

    private static Stream<Arguments> provideEthHashes() {
        return Stream.of(
                Arguments.of("0x1234567890abcdef1234567890abcdef1234567890abcdef1234567890abcdef", true),
                Arguments.of("1234567890abcdef1234567890abcdef1234567890abcdef1234567890abcdef", true),
                Arguments.of("0xGHIJKL", false),
                Arguments.of(null, false));
    }

    @ParameterizedTest
    @MethodSource("provideEthHashes")
    void testParseTransactionHash(String hash, boolean isValidHash) {
        if (!isValidHash) {
            final var parameter = assertDoesNotThrow(() -> TransactionHashParameter.valueOf(hash));
            assertThat(parameter).isNull();
            return;
        }

        final var parameter = assertDoesNotThrow(() -> TransactionHashParameter.valueOf(hash));
        assertThat(parameter)
                .isNotNull()
                .isInstanceOf(TransactionHashParameter.class)
                .isEqualTo(new TransactionHashParameter(Bytes.fromHexString(hash)));
    }
}
