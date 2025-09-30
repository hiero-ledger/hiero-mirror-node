// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.validation;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

import jakarta.validation.Payload;
import java.lang.annotation.Annotation;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class HexValidatorTest {

    private final HexValidator hexValidator = new HexValidator();

    @ParameterizedTest
    @CsvSource({
        "0,0,''",
        "0,40,0x",
        "1,3,0xa",
        "1,3,0xa1D",
        "1,3,a",
        "1,3,a1D",
        "40,40,",
        "40,40,0x00000000000000000000000000000000000007e7",
        "40,40,0x00000000000000000000000000000000000005E4",
        "40,40,00000000000000000000000000000000000001e8",
        "40,40,00000000000000000000000000000000000001E9"
    })
    void valid(int minLength, int maxLength, String value) {
        hexValidator.initialize(hex(minLength, maxLength));
        assertThat(hexValidator.isValid(value, null)).isTrue();
    }

    @ParameterizedTest
    @CsvSource({
        "0,0,1",
        "1,3,''",
        "1,3,0xa1D4",
        "2,3,a",
        "1,3,a1D4",
        "40,40,''",
        "40,40,0x00000000000000000000000000000000000004e47",
        "40,40,0x000000000000000000000000000000Z0000007e7",
        "40,40,0x0000000000000000000000000000000000007e7",
        "40,40,000000000000000000000000000000000Z0001e8",
        "40,40,00000000000000000000000000000000000001e",
        "40,40,Kp000000000000000000000000000000000001e",
        "40,40,0xzxcvbdfeqrfdseg",
        "40,40,0x000abc",
        "40,40,123589320198",
        "40,40,?$5%.xpo"
    })
    void invalid(int minLength, int maxLength, String value) {
        hexValidator.initialize(hex(minLength, maxLength));
        assertThat(hexValidator.isValid(value, null)).isFalse();
    }

    private Hex hex(int minLength, int maxLength) {
        return new Hex() {

            @Override
            public Class<? extends Annotation> annotationType() {
                return null;
            }

            @Override
            public String message() {
                return null;
            }

            @Override
            public Class<?>[] groups() {
                return new Class<?>[0];
            }

            @Override
            public Class<? extends Payload>[] payload() {
                return null;
            }

            @Override
            public long maxLength() {
                return maxLength;
            }

            @Override
            public long minLength() {
                return minLength;
            }

            @Override
            public boolean allowEmpty() {
                return false;
            }
        };
    }
}
