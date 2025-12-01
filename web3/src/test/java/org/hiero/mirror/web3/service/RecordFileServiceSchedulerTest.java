// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.service;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.hiero.mirror.web3.Web3IntegrationTest;
import org.hiero.mirror.web3.Web3Properties;
import org.hiero.mirror.web3.repository.RecordFileRepository;
import org.hiero.mirror.web3.viewmodel.BlockType;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@RequiredArgsConstructor
final class RecordFileServiceSchedulerTest extends Web3IntegrationTest {

    @InjectMocks
    private final RecordFileService recordFileService;

    @MockitoBean
    private RecordFileRepository recordFileRepository;

    private final Web3Properties web3Properties;

    @Test
    void fetchLatestRecordFileDoesNotCallFindLatestWhenDisabled() {
        // given scheduler is disabled
        web3Properties.setSchedulerEnabled(false);

        final var record =
                domainBuilder.recordFile().customize(f -> f.index(100L)).get();

        when(recordFileRepository.findLatest()).thenReturn(Optional.of(record));

        // when scheduler method is called
        ((RecordFileServiceImpl) recordFileService).fetchLatestRecordFile();

        // then findLatest is not called
        verify(recordFileRepository, times(0)).findLatest();

        // when
        final var latestBlock = recordFileService.findByBlockType(BlockType.LATEST);

        // then findLatest is called once
        assertThat(latestBlock).isNotNull().contains(record);
        verify(recordFileRepository, times(1)).findLatest();
    }

    @Test
    void fetchLatestRecordFileCallsFindLatestWhenEnabled() {
        // given scheduler is enabled
        web3Properties.setSchedulerEnabled(true);

        final var record =
                domainBuilder.recordFile().customize(f -> f.index(100L)).get();

        when(recordFileRepository.findLatest()).thenReturn(Optional.of(record));

        // when scheduler method is called
        ((RecordFileServiceImpl) recordFileService).fetchLatestRecordFile();

        // then findLatest is called once
        verify(recordFileRepository, times(1)).findLatest();

        // and verify findLatest is not called by findByBlockType, which means cache value is used
        final var latestBlock = recordFileService.findByBlockType(BlockType.LATEST);
        assertThat(latestBlock).isNotNull().contains(record);
        verify(recordFileRepository, times(1)).findLatest();
    }
}
