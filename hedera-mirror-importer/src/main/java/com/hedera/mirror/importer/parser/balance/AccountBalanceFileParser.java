// SPDX-License-Identifier: Apache-2.0

package com.hedera.mirror.importer.parser.balance;

import static com.hedera.mirror.importer.config.DateRangeCalculator.DateRangeFilter;

import com.hedera.mirror.common.domain.StreamType;
import com.hedera.mirror.common.domain.balance.AccountBalance;
import com.hedera.mirror.common.domain.balance.AccountBalanceFile;
import com.hedera.mirror.common.domain.balance.TokenBalance;
import com.hedera.mirror.importer.config.DateRangeCalculator;
import com.hedera.mirror.importer.leader.Leader;
import com.hedera.mirror.importer.parser.AbstractStreamFileParser;
import com.hedera.mirror.importer.parser.batch.BatchPersister;
import com.hedera.mirror.importer.repository.StreamFileRepository;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.inject.Named;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.transaction.annotation.Transactional;

/**
 * Parse an account balances file and load it into the database.
 */
@Named
public class AccountBalanceFileParser extends AbstractStreamFileParser<AccountBalanceFile> {

    private final BatchPersister batchPersister;
    private final DateRangeCalculator dateRangeCalculator;

    public AccountBalanceFileParser(
            BatchPersister batchPersister,
            MeterRegistry meterRegistry,
            BalanceParserProperties parserProperties,
            StreamFileRepository<AccountBalanceFile, Long> accountBalanceFileRepository,
            DateRangeCalculator dateRangeCalculator,
            BalanceStreamFileListener streamFileListener) {
        super(meterRegistry, parserProperties, streamFileListener, accountBalanceFileRepository);
        this.batchPersister = batchPersister;
        this.dateRangeCalculator = dateRangeCalculator;
    }

    /**
     * Process the file and load all the data into the database.
     */
    @Override
    @Leader
    @Retryable(
            backoff =
                    @Backoff(
                            delayExpression = "#{@balanceParserProperties.getRetry().getMinBackoff().toMillis()}",
                            maxDelayExpression = "#{@balanceParserProperties.getRetry().getMaxBackoff().toMillis()}",
                            multiplierExpression = "#{@balanceParserProperties.getRetry().getMultiplier()}"),
            maxAttemptsExpression = "#{@balanceParserProperties.getRetry().getMaxAttempts()}")
    @Transactional(timeoutString = "#{@balanceParserProperties.getTransactionTimeout().toSeconds()}")
    public synchronized void parse(AccountBalanceFile accountBalanceFile) {
        super.parse(accountBalanceFile);
    }

    @Override
    @Leader
    @Retryable(
            backoff =
                    @Backoff(
                            delayExpression = "#{@balanceParserProperties.getRetry().getMinBackoff().toMillis()}",
                            maxDelayExpression = "#{@balanceParserProperties.getRetry().getMaxBackoff().toMillis()}",
                            multiplierExpression = "#{@balanceParserProperties.getRetry().getMultiplier()}"),
            maxAttemptsExpression = "#{@balanceParserProperties.getRetry().getMaxAttempts()}")
    @Transactional(timeoutString = "#{@balanceParserProperties.getTransactionTimeout().toSeconds()}")
    public synchronized void parse(List<AccountBalanceFile> accountBalanceFile) {
        super.parse(accountBalanceFile);
    }

    @Override
    protected void doParse(AccountBalanceFile accountBalanceFile) {
        log.info("Starting processing account balances file {}", accountBalanceFile.getName());
        DateRangeFilter filter = dateRangeCalculator.getFilter(StreamType.BALANCE);
        int batchSize = ((BalanceParserProperties) parserProperties).getBatchSize();
        var count = new AtomicLong(0L);

        if (filter.filter(accountBalanceFile.getConsensusTimestamp())) {
            List<AccountBalance> accountBalances = new ArrayList<>(batchSize);
            Map<TokenBalance.Id, TokenBalance> tokenBalances = HashMap.newHashMap(batchSize);

            accountBalanceFile.getItems().forEach(accountBalance -> {
                accountBalances.add(accountBalance);
                for (var tokenBalance : accountBalance.getTokenBalances()) {
                    if (tokenBalances.putIfAbsent(tokenBalance.getId(), tokenBalance) != null) {
                        log.warn("Skipping duplicate token balance: {}", tokenBalance);
                    }
                }

                if (accountBalances.size() >= batchSize) {
                    batchPersister.persist(accountBalances);
                    accountBalances.clear();
                }

                if (tokenBalances.size() >= batchSize) {
                    batchPersister.persist(tokenBalances.values());
                    tokenBalances.clear();
                }
                count.getAndIncrement();
            });

            batchPersister.persist(accountBalances);
            batchPersister.persist(tokenBalances.values());
        }

        accountBalanceFile.setCount(count.get());
        accountBalanceFile.setLoadEnd(System.currentTimeMillis());
    }
}
