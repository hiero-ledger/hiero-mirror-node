// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.parser.record;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataRetrievalFailureException;
import org.springframework.jdbc.core.JdbcOperations;

@ExtendWith(MockitoExtension.class)
final class RecordFileWatermarkTrackerTest {

    @InjectMocks
    private RecordFileWatermarkTracker recordFileWatermarkTracker;

    @Mock
    private JdbcOperations jdbcOperations;

    @Test
    void advancesWatermarkWhenWorkersHaveCommitted() {
        when(jdbcOperations.queryForObject(contains("pg_extension"), eq(Boolean.class)))
                .thenReturn(true);
        when(jdbcOperations.queryForObject(contains("pg_dist_transaction"), eq(Long.class)))
                .thenReturn(0L);

        recordFileWatermarkTracker.onRecordFileParsed(new RecordFileParsedEvent(this, 42L));

        verify(jdbcOperations).update(contains("record_file_watermark"), eq(42L), eq(42L));
        verify(jdbcOperations, never()).queryForObject(contains("recover_prepared_transactions"), eq(Integer.class));
    }

    @Test
    void advancesWatermarkAfterPreparedTransactionIsRecovered() {
        when(jdbcOperations.queryForObject(contains("pg_extension"), eq(Boolean.class)))
                .thenReturn(true);
        when(jdbcOperations.queryForObject(contains("pg_dist_transaction"), eq(Long.class)))
                .thenReturn(1L, 0L);
        when(jdbcOperations.queryForObject(contains("recover_prepared_transactions"), eq(Integer.class)))
                .thenReturn(1);

        recordFileWatermarkTracker.onRecordFileParsed(new RecordFileParsedEvent(this, 42L));

        verify(jdbcOperations).queryForObject(contains("recover_prepared_transactions"), eq(Integer.class));
        verify(jdbcOperations).update(contains("record_file_watermark"), eq(42L), eq(42L));
    }

    @Test
    void doesNotAdvanceWatermarkWhenPreparedTransactionRemains() {
        when(jdbcOperations.queryForObject(contains("pg_extension"), eq(Boolean.class)))
                .thenReturn(true);
        when(jdbcOperations.queryForObject(contains("pg_dist_transaction"), eq(Long.class)))
                .thenReturn(1L);
        when(jdbcOperations.queryForObject(contains("recover_prepared_transactions"), eq(Integer.class)))
                .thenReturn(0);

        recordFileWatermarkTracker.onRecordFileParsed(new RecordFileParsedEvent(this, 42L));

        verify(jdbcOperations).queryForObject(contains("recover_prepared_transactions"), eq(Integer.class));
        verify(jdbcOperations, never()).update(anyString(), anyLong(), anyLong());
    }

    @Test
    void advancesWatermarkWithoutWorkerCheckWhenCitusIsAbsent() {
        when(jdbcOperations.queryForObject(contains("pg_extension"), eq(Boolean.class)))
                .thenReturn(false);

        recordFileWatermarkTracker.onRecordFileParsed(new RecordFileParsedEvent(this, 7L));

        verify(jdbcOperations).update(contains("record_file_watermark"), eq(7L), eq(7L));
        verify(jdbcOperations, never()).queryForObject(contains("pg_dist_transaction"), eq(Long.class));
    }

    @Test
    void logsAndContinuesWhenUpdateFails() {
        when(jdbcOperations.queryForObject(contains("pg_extension"), eq(Boolean.class)))
                .thenReturn(false);
        doThrow(new DataRetrievalFailureException("db down"))
                .when(jdbcOperations)
                .update(contains("record_file_watermark"), eq(7L), eq(7L));

        recordFileWatermarkTracker.onRecordFileParsed(new RecordFileParsedEvent(this, 7L));

        verify(jdbcOperations).update(contains("record_file_watermark"), eq(7L), eq(7L));
    }
}
