// SPDX-License-Identifier: Apache-2.0

package com.hedera.mirror.monitor.validator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class AccountIdValidatorTest {

    private final AccountIdValidator validator = new AccountIdValidator(1, 2);

    @ParameterizedTest
    @CsvSource(textBlock = """
            0.0.2, 1.2.2
            1.2.5, 1.2.5
            """)
    void validate(String accountId, String expected) {
        assertThat(validator.validate(accountId)).isEqualTo(expected);
    }

    @ParameterizedTest
    @ValueSource(strings = {"0.2.2", "1.0.2", "0.0.3"})
    void validateThrows(String accountId) {
        assertThatThrownBy(() -> validator.validate(accountId)).isInstanceOf(IllegalArgumentException.class);
    }
}
