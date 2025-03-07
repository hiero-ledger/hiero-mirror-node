// SPDX-License-Identifier: Apache-2.0

package com.hedera.mirror.importer.parser.balance;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

import com.hedera.mirror.common.domain.balance.AccountBalanceFile;
import com.hedera.mirror.importer.ImporterIntegrationTest;
import com.hedera.mirror.importer.domain.StreamFileData;
import com.hedera.mirror.importer.reader.balance.BalanceFileReader;
import com.hedera.mirror.importer.repository.AccountBalanceFileRepository;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;

@Tag("performance")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@RequiredArgsConstructor
class AccountBalanceFileParserPerformanceTest extends ImporterIntegrationTest {

    private final AccountBalanceFileRepository accountBalanceFileRepository;
    private final AccountBalanceFileParser balanceFileParser;
    private final BalanceFileReader balanceFileReader;
    private final BalanceParserProperties balanceParserProperties;

    @Value("classpath:data/accountBalances/v1/performance/*.csv")
    private final Resource[] testFiles;

    private final List<AccountBalanceFile> accountBalanceFiles = new ArrayList<>();

    @BeforeAll
    void setup() throws Exception {
        balanceParserProperties.setEnabled(true);
        for (Resource resource : testFiles) {
            AccountBalanceFile accountBalanceFile = balanceFileReader.read(StreamFileData.from(resource.getFile()));
            accountBalanceFile.setNodeId(0L);
            accountBalanceFiles.add(accountBalanceFile);
        }
    }

    @Test
    @Timeout(2)
    void parse() {
        accountBalanceFiles.forEach(balanceFileParser::parse);
        assertThat(accountBalanceFileRepository.count()).isEqualTo(accountBalanceFiles.size());
    }
}
