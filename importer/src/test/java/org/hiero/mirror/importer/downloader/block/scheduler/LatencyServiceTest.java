// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.downloader.block.scheduler;

import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.google.common.collect.Range;
import java.time.Duration;
import java.util.List;
import org.hiero.mirror.importer.downloader.block.BlockNode;
import org.hiero.mirror.importer.downloader.block.cutover.CutoverService;
import org.hiero.mirror.importer.reader.block.BlockStreamReader;
import org.jspecify.annotations.NullUnmarked;
import org.junit.jupiter.api.AutoClose;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@NullUnmarked
final class LatencyServiceTest {

    @Mock
    private CutoverService cutoverService;

    @AutoClose
    private LatencyService latencyService;

    @BeforeEach
    void setup() {
        latencyService =
                new LatencyService(mock(BlockStreamReader.class), cutoverService, new LatencyServiceProperties());
    }

    @Test
    void schedule() {
        // given
        final var blockNode = mock(BlockNode.class);
        doReturn(Range.closed(0L, Long.MAX_VALUE)).when(blockNode).getBlockRange();
        doReturn(0L).when(cutoverService).getNextBlockNumber();

        // when
        latencyService.setNodes(List.of(blockNode));
        latencyService.schedule();

        // then
        await().atMost(Duration.ofSeconds(2))
                .pollInterval(Duration.ofMillis(10))
                .untilAsserted(() -> verify(blockNode).streamBlocks(anyLong(), anyLong(), any(), any()));
        verify(cutoverService).getNextBlockNumber();
        verify(blockNode).getBlockRange();

        // when schedule again
        latencyService.schedule();

        // then already measured block should be skipped
        await().atMost(Duration.ofSeconds(2))
                .pollInterval(Duration.ofMillis(10))
                .untilAsserted(() -> verify(cutoverService, times(2)).getNextBlockNumber());
        verify(blockNode).getBlockRange();
        verify(blockNode).streamBlocks(anyLong(), anyLong(), any(), any());

        // when next block advances
        doReturn(1L).when(cutoverService).getNextBlockNumber();
        latencyService.schedule();

        // then
        await().atMost(Duration.ofSeconds(2))
                .pollInterval(Duration.ofMillis(10))
                .untilAsserted(() -> verify(cutoverService, times(3)).getNextBlockNumber());
        verify(blockNode, times(2)).getBlockRange();
        verify(blockNode, times(2)).streamBlocks(anyLong(), anyLong(), any(), any());
    }

    @Test
    void scheduleBlockNodeSkipped() {
        // given
        final var blockNode = mock(BlockNode.class);
        doReturn(Range.closedOpen(0L, 1L)).when(blockNode).getBlockRange();
        final var latency = mock(Latency.class);
        doReturn(latency).when(blockNode).getLatency();
        doReturn(1L).when(cutoverService).getNextBlockNumber();

        // when, then
        latencyService.setNodes(List.of(blockNode));
        await().atMost(Duration.ofSeconds(2))
                .pollInterval(Duration.ofMillis(10))
                .untilAsserted(() -> {
                    latencyService.schedule();
                    verify(latency, atLeast(1)).markStale();
                });
        verify(blockNode, atLeast(3)).getBlockRange();
        verify(blockNode, never()).streamBlocks(anyLong(), anyLong(), any(), any());
        verify(cutoverService, atLeast(3)).getNextBlockNumber();
    }
}
