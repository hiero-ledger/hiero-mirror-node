// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.parser.balance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hiero.mirror.importer.migration.ErrataMigrationTest.BAD_TIMESTAMP1;

import java.util.Collection;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.hiero.mirror.common.domain.balance.AccountBalance;
import org.hiero.mirror.common.domain.balance.AccountBalanceFile;
import org.hiero.mirror.common.domain.balance.TokenBalance;
import org.hiero.mirror.importer.ImporterIntegrationTest;
import org.hiero.mirror.importer.ImporterProperties;
import org.hiero.mirror.importer.repository.AccountBalanceFileRepository;
import org.hiero.mirror.importer.repository.AccountBalanceRepository;
import org.hiero.mirror.importer.repository.TokenBalanceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@RequiredArgsConstructor
class AccountBalanceFileParserTest extends ImporterIntegrationTest {

    private final AccountBalanceBuilder accountBalanceBuilder;
    private final AccountBalanceFileBuilder accountBalanceFileBuilder;
    private final AccountBalanceFileParser accountBalanceFileParser;
    private final AccountBalanceFileRepository accountBalanceFileRepository;
    private final AccountBalanceRepository accountBalanceRepository;
    private final TokenBalanceRepository tokenBalanceRepository;
    private final BalanceParserProperties parserProperties;
    private final ImporterProperties importerProperties;

    @BeforeEach
    void setup() {
        accountBalanceFileParser.clear();
        parserProperties.setEnabled(true);
    }

    @Test
    void disabled() {
        // given
        parserProperties.setEnabled(false);
        var accountBalanceFile = accountBalanceFile(1);

        // when
        accountBalanceFileParser.parse(accountBalanceFile);

        // then
        assertAccountBalanceFileWhenSkipped(accountBalanceFile);
    }

    @Test
    void success() {
        // given
        var accountBalanceFile = accountBalanceFile(1);
        var items = accountBalanceFile.getItems();

        // when
        accountBalanceFileParser.parse(accountBalanceFile);

        // then
        assertAccountBalanceFile(accountBalanceFile, items);
        assertThat(accountBalanceFile.getTimeOffset()).isZero();
    }

    @Test
    void multipleBatches() {
        // given
        int batchSize = parserProperties.getBatchSize();
        parserProperties.setBatchSize(2);
        var accountBalanceFile = accountBalanceFile(1);
        var items = accountBalanceFile.getItems();

        // when
        accountBalanceFileParser.parse(accountBalanceFile);

        // then
        assertAccountBalanceFile(accountBalanceFile, items);
        parserProperties.setBatchSize(batchSize);
    }

    @Test
    void duplicateFile() {
        // given
        var accountBalanceFile = accountBalanceFile(1);
        var duplicate = accountBalanceFile(1);
        var items = accountBalanceFile.getItems();

        // when
        accountBalanceFileParser.parse(accountBalanceFile);
        accountBalanceFileParser.parse(duplicate); // Will be ignored

        // then
        assertThat(accountBalanceFileRepository.count()).isEqualTo(1L);
        assertAccountBalanceFile(accountBalanceFile, items);
    }

    @Test
    void beforeStartDate() {
        // given
        var accountBalanceFile = accountBalanceFile(-1L);

        // when
        accountBalanceFileParser.parse(accountBalanceFile);

        // then
        assertAccountBalanceFileWhenSkipped(accountBalanceFile);
    }

    @Test
    void errata() {
        // given
        var network = importerProperties.getNetwork();
        importerProperties.setNetwork(ImporterProperties.HederaNetwork.MAINNET);
        AccountBalanceFile accountBalanceFile = accountBalanceFile(BAD_TIMESTAMP1);
        var items = accountBalanceFile.getItems();

        // when
        accountBalanceFileParser.parse(accountBalanceFile);

        // then
        assertAccountBalanceFile(accountBalanceFile, items);
        assertThat(accountBalanceFile.getTimeOffset()).isEqualTo(-1);
        importerProperties.setNetwork(network);
    }

    void assertAccountBalanceFile(AccountBalanceFile accountBalanceFile, Collection<AccountBalance> accountBalances) {
        Map<TokenBalance.Id, TokenBalance> tokenBalances = accountBalances.stream()
                .map(AccountBalance::getTokenBalances)
                .flatMap(Collection::stream)
                .collect(Collectors.toMap(TokenBalance::getId, t -> t, (previous, current) -> previous));

        assertThat(accountBalanceFile.getBytes()).isNull();
        assertThat(accountBalanceFile.getItems()).isEmpty();
        assertThat(accountBalanceRepository.findAll()).containsExactlyInAnyOrderElementsOf(accountBalances);
        assertThat(tokenBalanceRepository.findAll()).containsExactlyInAnyOrderElementsOf(tokenBalances.values());

        if (parserProperties.isEnabled()) {
            assertThat(accountBalanceFileRepository.findAll())
                    .hasSize(1)
                    .first()
                    .matches(a -> a.getLoadEnd() != null)
                    .usingRecursiveComparison()
                    .ignoringFields("bytes", "items", "loadEnd")
                    .isEqualTo(accountBalanceFile);
        }
    }

    void assertAccountBalanceFileWhenSkipped(AccountBalanceFile accountBalanceFile) {
        assertThat(accountBalanceFile.getBytes()).isNull();
        assertThat(accountBalanceFile.getItems()).isEmpty();
        assertThat(accountBalanceRepository.count()).isZero();
        assertThat(tokenBalanceRepository.count()).isZero();

        if (parserProperties.isEnabled()) {
            assertThat(accountBalanceFileRepository.findAll())
                    .hasSize(1)
                    .first()
                    .matches(a -> a.getLoadEnd() != null)
                    .usingRecursiveComparison()
                    .ignoringFields("bytes", "items", "loadEnd")
                    .isEqualTo(accountBalanceFile);
        }
    }

    private AccountBalanceFile accountBalanceFile(long timestamp) {
        return accountBalanceFileBuilder
                .accountBalanceFile(timestamp)
                .accountBalance(accountBalanceBuilder
                        .accountBalance(timestamp)
                        .accountId(1000L)
                        .balance(1000L)
                        .tokenBalance(1, 10000L)
                        .tokenBalance(1, 10000L) // duplicate token balance rows should be filtered by parser
                        .build())
                .accountBalance(accountBalanceBuilder
                        .accountBalance(timestamp)
                        .accountId(2000L)
                        .balance(2000L)
                        .tokenBalance(2, 20000L)
                        .tokenBalance(2, 20000L)
                        .build())
                .accountBalance(accountBalanceBuilder
                        .accountBalance(timestamp)
                        .accountId(3000L)
                        .balance(3000L)
                        .tokenBalance(3, 30000L)
                        .tokenBalance(3, 30000L)
                        .build())
                .build();
    }
}
