// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.parser.record;

import jakarta.inject.Named;
import lombok.CustomLog;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcOperations;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Advances the single {@code record_file_watermark} row after a record file transaction commits, and only once every
 * Citus worker has finished that commit. A failed {@code COMMIT PREPARED} leaves a row in {@code pg_dist_transaction}
 * on the coordinator, so this check does not contact the workers unless recovery is actually required. REST reads the
 * watermark to tell a complete block from one whose distributed rows are not visible yet.
 */
@CustomLog
@Named
@RequiredArgsConstructor
public class RecordFileWatermarkTracker {

    private static final String CITUS_ENABLED_SQL = "select exists(select 1 from pg_extension where extname = 'citus')";

    private static final String PREPARED_TRANSACTION_COUNT_SQL = "select count(*) from pg_dist_transaction";

    private static final String RECOVER_PREPARED_TRANSACTIONS_SQL = "select recover_prepared_transactions()";

    private static final String UPDATE_SQL = """
            update record_file_watermark
            set consensus_end = greatest(coalesce(consensus_end, ?), ?)
            """;

    private volatile Boolean citusEnabled;

    private final JdbcOperations jdbcOperations;

    @TransactionalEventListener(value = RecordFileParsedEvent.class, phase = TransactionPhase.AFTER_COMMIT)
    public void onRecordFileParsed(RecordFileParsedEvent event) {
        final long consensusEnd = event.getConsensusEnd();
        try {
            if (!workersCommitted()) {
                log.error("Distributed data through consensus_end {} is not committed on every worker", consensusEnd);
                return;
            }
            jdbcOperations.update(UPDATE_SQL, consensusEnd, consensusEnd);
        } catch (DataAccessException e) {
            log.error("Failed to advance record_file_watermark to {}", consensusEnd, e);
        }
    }

    private boolean isCitusEnabled() {
        final var enabled = citusEnabled;
        if (enabled != null) {
            return enabled;
        }
        final var detected = Boolean.TRUE.equals(jdbcOperations.queryForObject(CITUS_ENABLED_SQL, Boolean.class));
        citusEnabled = detected;
        return detected;
    }

    private long preparedTransactionCount() {
        final var count = jdbcOperations.queryForObject(PREPARED_TRANSACTION_COUNT_SQL, Long.class);
        return count == null ? 0L : count;
    }

    private boolean workersCommitted() {
        if (!isCitusEnabled()) {
            return true;
        }
        if (preparedTransactionCount() == 0L) {
            return true;
        }
        jdbcOperations.queryForObject(RECOVER_PREPARED_TRANSACTIONS_SQL, Integer.class);
        return preparedTransactionCount() == 0L;
    }
}
