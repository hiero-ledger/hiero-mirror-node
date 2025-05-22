// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.reader.balance.line;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.hiero.mirror.common.domain.balance.AccountBalance;
import org.hiero.mirror.importer.exception.InvalidDatasetException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class AccountBalanceLineParserV1Test {

    private static final long TIMESTAMP = 1596340377922333444L;
    private final AccountBalanceLineParserV1 parser = new AccountBalanceLineParserV1();

    @DisplayName("Parse account balance line")
    @ParameterizedTest(name = "from \"{0}\"")
    @CsvSource(
            value = {
                "'0,0,123,700';false;0;123;700",
                "' 0,0,123,700';false;0;123;700",
                "'0, 0,123,700';false;0;123;700",
                "'0,0, 123,700';false;0;123;700",
                "'0,0,123, 700';false;0;123;700",
                "'0,0,123,700 ';false;0;123;700",
                "'1,0,123,700';true;;;",
                "'x,0,123,700';true;;;",
                "'0,x,123,700';true;;;",
                "'0,0,x,700';true;;;",
                "'0,0,123,a00';true;;;",
                "'1000000000000000000000000000,0,123,700';true;;;",
                "'0,1000000000000000000000000000,123,700';true;;;",
                "'0,0,1000000000000000000000000000,700';true;;;",
                "'0,0,123,1000000000000000000000000000';true;;;",
                "'-1,0,123,700';true;;;",
                "'0,-1,123,700';true;;;",
                "'0,0,-1,700';true;;;",
                "'0,0,123,-1';true;;;",
                "'foobar';true;;;",
                "'';true;;;",
                ";true;;;"
            },
            delimiter = ';')
    void parse(String line, boolean expectThrow, Long expectedRealm, Long expectedAccount, Long expectedBalance) {
        if (!expectThrow) {
            AccountBalance accountBalance = parser.parse(line, TIMESTAMP);
            var id = accountBalance.getId();

            assertThat(accountBalance.getBalance()).isEqualTo(expectedBalance);
            assertThat(id).isNotNull();
            assertThat(id.getAccountId().getShard()).isEqualTo(0);
            assertThat(id.getAccountId().getRealm()).isEqualTo(expectedRealm);
            assertThat(id.getAccountId().getNum()).isEqualTo(expectedAccount);
            assertThat(id.getConsensusTimestamp()).isEqualTo(TIMESTAMP);
        } else {
            assertThrows(InvalidDatasetException.class, () -> parser.parse(line, TIMESTAMP));
        }
    }

    @Test
    void parseNullLine() {
        assertThrows(InvalidDatasetException.class, () -> parser.parse(null, TIMESTAMP));
    }
}
