// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.downloader.block;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hiero.mirror.importer.downloader.block.CompositeBlockSource.FAILOVER_METRIC_NAME;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mock.Strictness.LENIENT;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.hiero.mirror.common.domain.StreamType;
import org.hiero.mirror.common.domain.transaction.BlockSourceType;
import org.hiero.mirror.importer.ImporterProperties;
import org.hiero.mirror.importer.ImporterProperties.HederaNetwork;
import org.hiero.mirror.importer.downloader.CommonDownloaderProperties;
import org.hiero.mirror.importer.downloader.CommonDownloaderProperties.SourceType;
import org.hiero.mirror.importer.downloader.StreamSourceProperties;
import org.hiero.mirror.importer.downloader.block.cutover.CutoverService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
final class CompositeBlockSourceTest {

    @Mock
    private BlockFileSource blockFileSource;

    @Mock(strictness = LENIENT)
    private BlockNodeSubscriber blockNodeSubscriber;

    @Mock(strictness = LENIENT)
    private CutoverService cutoverService;

    @Mock(strictness = LENIENT)
    private BlockNodeDiscoveryService blockNodeDiscoveryService;

    private CommonDownloaderProperties commonDownloaderProperties;
    private ImporterProperties importerProperties;
    private final MeterRegistry meterRegistry = new SimpleMeterRegistry();

    private BlockProperties properties;
    private CompositeBlockSource source;
    private Map<BlockSourceType, BlockSource> sources;

    @BeforeEach
    void setup() {
        importerProperties = new ImporterProperties();
        commonDownloaderProperties = new CommonDownloaderProperties(importerProperties);
        properties = new BlockProperties(importerProperties);
        properties.setEnabled(true);
        properties.setNodes(List.of(new BlockNodeProperties()));
        when(blockNodeDiscoveryService.getBlockNodes()).thenReturn(List.of(new BlockNodeProperties()));
        source = newCompositeBlockSource();
        sources = Map.of(
                BlockSourceType.AUTO,
                blockNodeSubscriber,
                BlockSourceType.BLOCK_NODE,
                blockNodeSubscriber,
                BlockSourceType.FILE,
                blockFileSource);
        doAnswer(answer -> {
                    if (answer.getArgument(1) instanceof Runnable task) {
                        task.run();
                    }

                    return null;
                })
                .when(cutoverService)
                .get(eq(StreamType.BLOCK), any(Runnable.class));
    }

    @Test
    void disabled() {
        // given
        doNothing().when(cutoverService).get(eq(StreamType.BLOCK), any(Runnable.class));

        // when
        source.get();

        // then
        verifyNoInteractions(blockFileSource);
        verifyNoInteractions(blockNodeSubscriber);
    }

    @ParameterizedTest
    @EnumSource(BlockSourceType.class)
    void get(BlockSourceType type) {
        // given
        properties.setSourceType(type);
        var delegate = sources.get(type);
        var other = delegate == blockNodeSubscriber ? blockFileSource : blockNodeSubscriber;

        // when
        source.get();

        // then
        verify(delegate).get();
        verifyNoInteractions(other);
        assertThat(failovers()).isZero();
    }

    @ParameterizedTest
    @EnumSource(
            names = {"BLOCK_NODE", "FILE"},
            value = BlockSourceType.class)
    void getWithException(BlockSourceType type) {
        // given
        properties.setSourceType(type);
        var delegate = sources.get(type);
        var other = delegate == blockNodeSubscriber ? blockFileSource : blockNodeSubscriber;
        doThrow(new RuntimeException()).when(delegate).get();

        // when
        for (int i = 0; i < 4; i++) {
            source.get();
        }

        // then
        verify(delegate, times(4)).get();
        verifyNoInteractions(other);
        assertThat(failovers()).isZero();
    }

    @Test
    void getAutoNoBlockNodes() {
        // given - discovery service returns empty (no config nodes, no discovered nodes)
        when(blockNodeDiscoveryService.getBlockNodes()).thenReturn(Collections.emptyList());

        // when
        source.get();

        // then
        verify(blockFileSource).get();
        verify(blockNodeSubscriber, never()).get();
        assertThat(failovers()).isZero();
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void getAutoNoBlockFileSource(final boolean hasBlockNodes) {
        // given
        importerProperties.setNetwork(HederaNetwork.OTHER);
        source = newCompositeBlockSource();
        if (!hasBlockNodes) {
            when(blockNodeDiscoveryService.getBlockNodes()).thenReturn(Collections.emptyList());
        }
        doThrow(new RuntimeException()).when(blockNodeSubscriber).get();

        // when
        for (int i = 0; i < 5; i++) {
            source.get();
        }

        // then
        verify(blockNodeSubscriber, times(5)).get();
        verifyNoInteractions(blockFileSource);
    }

    @Test
    void getAutoNoBucketWithLocalSource() {
        // given
        importerProperties.setNetwork(HederaNetwork.OTHER);
        final var localSource = new StreamSourceProperties();
        localSource.setType(SourceType.LOCAL);
        commonDownloaderProperties.getSources().add(localSource);
        source = newCompositeBlockSource();
        when(blockNodeDiscoveryService.getBlockNodes()).thenReturn(Collections.emptyList());

        // when
        source.get();

        // then
        verify(blockFileSource).get();
        verifyNoInteractions(blockNodeSubscriber);
    }

    @Test
    void getFileNoBucket() {
        // given
        importerProperties.setNetwork(HederaNetwork.OTHER);
        properties.setSourceType(BlockSourceType.FILE);
        source = newCompositeBlockSource();

        // when
        source.get();

        // then
        verify(blockFileSource).get();
        verifyNoInteractions(blockNodeSubscriber);
    }

    @Test
    void getAutoSwitchOnError() {
        // given
        doThrow(new RuntimeException()).when(blockFileSource).get();
        doThrow(new RuntimeException()).when(blockNodeSubscriber).get();

        // when
        for (int i = 0; i < 3; i++) {
            source.get();
        }

        // then
        verifyNoInteractions(blockFileSource);
        verify(blockNodeSubscriber, times(3)).get();
        assertThat(failovers()).isZero();

        // when
        source.get();

        // then
        verify(blockFileSource).get();
        verify(blockNodeSubscriber, times(3)).get();
        assertThat(failovers()).isOne();

        // when
        source.get();
        source.get();

        // then
        verify(blockFileSource, times(3)).get();
        verify(blockNodeSubscriber, times(3)).get();

        // when both sources have reached the health threshold, subsequent failed gets will alternate between them
        source.get();

        // then
        verify(blockFileSource, times(3)).get();
        verify(blockNodeSubscriber, times(4)).get();
        assertThat(failovers()).isOne();

        // when
        source.get();

        // then
        verify(blockFileSource, times(4)).get();
        verify(blockNodeSubscriber, times(4)).get();
        assertThat(failovers()).isEqualTo(2);

        // when blockNodeSubscriber.get() succeeds then keeps throwing
        // the successful get() should clear the unhealthy state
        doNothing().doThrow(new RuntimeException()).when(blockNodeSubscriber).get();
        for (int i = 0; i < 4; i++) {
            source.get();
        }

        // then
        verify(blockFileSource, times(4)).get();
        verify(blockNodeSubscriber, times(8)).get();

        // when
        source.get();

        // then
        verify(blockFileSource, times(5)).get();
        verify(blockNodeSubscriber, times(8)).get();
        assertThat(failovers()).isEqualTo(3);
    }

    private double failovers() {
        return meterRegistry.get(FAILOVER_METRIC_NAME).counter().count();
    }

    private CompositeBlockSource newCompositeBlockSource() {
        return new CompositeBlockSource(
                blockFileSource,
                blockNodeDiscoveryService,
                blockNodeSubscriber,
                commonDownloaderProperties,
                cutoverService,
                meterRegistry,
                properties);
    }
}
