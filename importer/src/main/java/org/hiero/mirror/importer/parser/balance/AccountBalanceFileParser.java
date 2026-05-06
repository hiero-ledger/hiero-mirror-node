// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.parser.balance;

import static org.hiero.mirror.importer.config.DateRangeCalculator.DateRangeFilter;

import io.micrometer.core.instrument.MeterRegistry;
import jakarta.inject.Named;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import org.hiero.mirror.common.domain.StreamType;
import org.hiero.mirror.common.domain.balance.AccountBalance;
import org.hiero.mirror.common.domain.balance.AccountBalanceFile;
import org.hiero.mirror.common.domain.balance.TokenBalance;
import org.hiero.mirror.importer.config.DateRangeCalculator;
import org.hiero.mirror.importer.parser.AbstractStreamFileParser;
import org.hiero.mirror.importer.parser.batch.BatchPersister;
import org.hiero.mirror.importer.repository.StreamFileRepository;
import org.springframework.resilience.annotation.Retryable;
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
    @Retryable(
            delayString = "#{@balanceParserProperties.getRetry().getMinBackoff().toMillis()}",
            excludes = OutOfMemoryError.class,
            maxDelayString = "#{@balanceParserProperties.getRetry().getMaxBackoff().toMillis()}",
            maxRetriesString = "#{@balanceParserProperties.getRetry().getMaxAttempts() - 1}",
            multiplierString = "#{@balanceParserProperties.getRetry().getMultiplier()}")
    @Transactional(timeoutString = "#{@balanceParserProperties.getTransactionTimeout().toSeconds()}")
    public synchronized void parse(AccountBalanceFile accountBalanceFile) {
        super.parse(accountBalanceFile);
    }

    @Override
    @Retryable(
            delayString = "#{@balanceParserProperties.getRetry().getMinBackoff().toMillis()}",
            excludes = OutOfMemoryError.class,
            maxDelayString = "#{@balanceParserProperties.getRetry().getMaxBackoff().toMillis()}",
            maxRetriesString = "#{@balanceParserProperties.getRetry().getMaxAttempts() - 1}",
            multiplierString = "#{@balanceParserProperties.getRetry().getMultiplier()}")
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
