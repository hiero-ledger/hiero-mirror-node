// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.reader.balance.line;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.google.common.base.Splitter;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.apache.commons.lang3.StringUtils;
import org.hiero.mirror.common.domain.balance.AccountBalance;
import org.hiero.mirror.common.domain.balance.TokenBalance;
import org.hiero.mirror.importer.exception.InvalidDatasetException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class AccountBalanceLineParserV2Test {

    private static final long TIMESTAMP = 1596340377922333444L;
    private final AccountBalanceLineParserV2 parser = new AccountBalanceLineParserV2();

    @DisplayName("Parse account balance line")
    @ParameterizedTest(name = "from \"{0}\"")
    @CsvSource(
            value = {
                "'0,0,123,700,CggKAxjsBxCEBwoHCgMY7QcQGQoKCgMY7gcQwKilBAoICgMY8gcQhAcKBwoDGPMHEBkKCgoDGPQHEMCopQQ';false;"
                        + "0;123;700;1004=900,1005=25,1006=9000000,1010=900,1011=25,1012=9000000",
                "'0,0,123,700,';false;" + "0;123;700;;",
                "'0,0,123,700,CggKAxjsBxCEBwoHCgMY7QcQGQoKCgMY7gcQwKilBAoICgMY8gcQhAcKBwoDGPMHEBkKCgo';true;;;;",
                "' 0,0,123,700,CggKAxjsBxCEBwoHCgMY7QcQGQoKCgMY7gcQwKilBAoICgMY8gcQhAcKBwoDGPMHEBkKCgoDGPQHEMCopQQ';"
                        + "false;0;123;700;1004=900,1005=25,1006=9000000,1010=900,1011=25,1012=9000000",
                "'0, 0,123,700,CggKAxjsBxCEBwoHCgMY7QcQGQoKCgMY7gcQwKilBAoICgMY8gcQhAcKBwoDGPMHEBkKCgoDGPQHEMCopQQ';"
                        + "false;0;123;700;1004=900,1005=25,1006=9000000,1010=900,1011=25,1012=9000000",
                "'0,0, 123,700,CggKAxjsBxCEBwoHCgMY7QcQGQoKCgMY7gcQwKilBAoICgMY8gcQhAcKBwoDGPMHEBkKCgoDGPQHEMCopQQ';"
                        + "false;0;123;700;1004=900,1005=25,1006=9000000,1010=900,1011=25,1012=9000000",
                "'0,0,123, 700,CggKAxjsBxCEBwoHCgMY7QcQGQoKCgMY7gcQwKilBAoICgMY8gcQhAcKBwoDGPMHEBkKCgoDGPQHEMCopQQ';"
                        + "false;0;123;700;1004=900,1005=25,1006=9000000,1010=900,1011=25,1012=9000000",
                "'0,0,123,700, CggKAxjsBxCEBwoHCgMY7QcQGQoKCgMY7gcQwKilBAoICgMY8gcQhAcKBwoDGPMHEBkKCgoDGPQHEMCopQQ';"
                        + "false;0;123;700;1004=900,1005=25,1006=9000000,1010=900,1011=25,1012=9000000",
                "'0,0,123,700,CggKAxjsBxCEBwoHCgMY7QcQGQoKCgMY7gcQwKilBAoICgMY8gcQhAcKBwoDGPMHEBkKCgoDGPQHEMCopQQ ';"
                        + "false;0;123;700;1004=900,1005=25,1006=9000000,1010=900,1011=25,1012=9000000",
                "'1,0,123,700,';true;;;;",
                "'x,0,123,700,';true;;;;",
                "'0,x,123,700,';true;;;;",
                "'0,0,x,700,';true;;;;",
                "'0,0,123,a00,';true;;;;",
                "'1000000000000000000000000000,0,123,700,';true;;;;",
                "'0,1000000000000000000000000000,123,700,';true;;;;",
                "'0,0,1000000000000000000000000000,700,';true;;;;",
                "'0,0,123,1000000000000000000000000000,';true;;;;",
                "'-1,0,123,700,';true;;;;",
                "'0,-1,123,700,';true;;;;",
                "'0,0,-1,700,';true;;;;",
                "'0,0,123,-1,';true;;;;",
                "'foobar';true;;;;",
                "'';true;;;;",
                ";true;;;;"
            },
            delimiter = ';')
    void parse(
            String line,
            boolean expectThrow,
            Long expectedRealm,
            Long expectedAccount,
            Long expectedBalance,
            String tokenBalances) {
        if (!expectThrow) {
            AccountBalance accountBalance = parser.parse(line, TIMESTAMP);
            var id = accountBalance.getId();

            assertThat(accountBalance.getBalance()).isEqualTo(expectedBalance);
            assertThat(id).isNotNull();
            assertThat(id.getAccountId().getRealm()).isEqualTo(expectedRealm);
            assertThat(id.getAccountId().getNum()).isEqualTo(expectedAccount);
            assertThat(id.getConsensusTimestamp()).isEqualTo(TIMESTAMP);

            List<TokenBalance> actualTokenBalanceList = accountBalance.getTokenBalances();
            if (StringUtils.isNotBlank(tokenBalances)) {
                Map<Long, Long> expectedTokenBalances =
                        Splitter.on(',').withKeyValueSeparator('=').split(tokenBalances).entrySet().stream()
                                .collect(Collectors.toMap(
                                        entry -> Long.parseLong(entry.getKey()),
                                        entry -> Long.parseLong(entry.getValue())));
                assertThat(actualTokenBalanceList).hasSameSizeAs(tokenBalances.split(","));
                for (int i = 0; i < actualTokenBalanceList.size(); i++) {
                    TokenBalance actualTokenBalance = actualTokenBalanceList.get(i);
                    TokenBalance.Id actualId = actualTokenBalance.getId();

                    assertThat(expectedTokenBalances)
                            .containsKey(actualId.getTokenId().getNum());
                    assertThat(actualTokenBalance.getBalance())
                            .isEqualTo(expectedTokenBalances.get(
                                    actualId.getTokenId().getNum()));
                    assertThat(actualId).isNotNull();
                    assertThat(actualId.getConsensusTimestamp()).isEqualTo(TIMESTAMP);
                    assertThat(actualId.getAccountId().getShard()).isEqualTo(0);
                    assertThat(actualId.getAccountId().getRealm()).isEqualTo(expectedRealm);
                    assertThat(actualId.getAccountId().getNum()).isEqualTo(expectedAccount);

                    assertThat(actualId.getTokenId().getShard()).isEqualTo(0);
                    assertThat(actualId.getTokenId().getRealm()).isEqualTo(expectedRealm);
                }
            } else {
                assertThat(actualTokenBalanceList).isEmpty();
            }
        } else {
            assertThrows(InvalidDatasetException.class, () -> {
                parser.parse(line, TIMESTAMP);
            });
        }
    }

    @Test
    void parseNullLine() {
        assertThrows(InvalidDatasetException.class, () -> {
            parser.parse(null, TIMESTAMP);
        });
    }
}
