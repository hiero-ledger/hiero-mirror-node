// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.migration;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import org.hiero.mirror.common.domain.balance.AccountBalanceFile;
import org.hiero.mirror.common.domain.transaction.RecordFile;
import org.hiero.mirror.importer.exception.ImporterException;
import org.hiero.mirror.importer.parser.balance.BalanceStreamFileListener;
import org.hiero.mirror.importer.repository.AccountBalanceFileRepository;
import org.hiero.mirror.importer.repository.RecordFileRepository;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

abstract class TimeSensitiveBalanceMigration extends RepeatableMigration
        implements BalanceStreamFileListener, TransactionSynchronization {

    private static final long EXECUTED = -1L;
    private static final long NO_BALANCE_FILE = 0L;
    private final ObjectProvider<AccountBalanceFileRepository> accountBalanceFileRepositoryProvider;
    private final ObjectProvider<RecordFileRepository> recordFileRepositoryProvider;
    private final AtomicLong firstConsensusTimestamp = new AtomicLong(NO_BALANCE_FILE);

    protected TimeSensitiveBalanceMigration(
            Map<String, MigrationProperties> migrationPropertiesMap,
            ObjectProvider<AccountBalanceFileRepository> accountBalanceFileRepositoryProvider,
            ObjectProvider<RecordFileRepository> recordFileRepositoryProvider) {
        super(migrationPropertiesMap);
        this.accountBalanceFileRepositoryProvider = accountBalanceFileRepositoryProvider;
        this.recordFileRepositoryProvider = recordFileRepositoryProvider;
    }

    @Override
    public void onEnd(AccountBalanceFile accountBalanceFile) throws ImporterException {
        try {
            if (firstConsensusTimestamp.get() == EXECUTED) {
                return;
            }

            // Check if this is the first account balance file after importer startup
            if (firstConsensusTimestamp.get() == NO_BALANCE_FILE) {
                // Set current file timestamp to firstConsensusTimestamp.
                if (accountBalanceFileRepositoryProvider
                        .getObject()
                        .findLatestBefore(accountBalanceFile.getConsensusTimestamp())
                        .isEmpty()) {
                    firstConsensusTimestamp.set(accountBalanceFile.getConsensusTimestamp());
                } else {
                    // Set firstConsensusTimestamp to -1 to add an early return in case of existing account balance
                    // files.
                    firstConsensusTimestamp.set(EXECUTED);
                    return;
                }
            }

            // Check if at-least one recordFile after the account balance file has been parsed,the migration will then
            // update rows
            if (recordFileRepositoryProvider
                    .getObject()
                    .findLatest()
                    .map(RecordFile::getConsensusEnd)
                    .filter(timestamp -> timestamp >= firstConsensusTimestamp.get())
                    .isPresent()) {
                TransactionSynchronizationManager.registerSynchronization(this);
                doMigrate();
            }
        } catch (IOException e) {
            log.error(
                    "Error executing the migration again after consensus_timestamp {}",
                    accountBalanceFile.getConsensusTimestamp());
        }
    }

    @Override
    public void afterCommit() {
        firstConsensusTimestamp.set(EXECUTED);
    }
}
