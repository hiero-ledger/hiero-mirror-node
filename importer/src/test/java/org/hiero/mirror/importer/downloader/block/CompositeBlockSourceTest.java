// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.downloader.block;

import static org.mockito.Mock.Strictness.LENIENT;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.hiero.mirror.common.domain.StreamType;
import org.hiero.mirror.common.domain.transaction.BlockSourceType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
final class CompositeBlockSourceTest {

    @Mock
    private BlockFileSource blockFileSource;

    @Mock
    private BlockNodeSubscriber blockNodeSubscriber;

    @Mock(strictness = LENIENT)
    private CutoverService cutoverService;

    private BlockProperties properties;
    private CompositeBlockSource source;
    private Map<BlockSourceType, BlockSource> sources;

    @BeforeEach
    void setup() {
        properties = new BlockProperties();
        properties.setEnabled(true);
        properties.setNodes(List.of(new BlockNodeProperties()));
        source = new CompositeBlockSource(blockFileSource, blockNodeSubscriber, cutoverService, properties);
        sources = Map.of(
                BlockSourceType.AUTO,
                blockNodeSubscriber,
                BlockSourceType.BLOCK_NODE,
                blockNodeSubscriber,
                BlockSourceType.FILE,
                blockFileSource);
        doReturn(true).when(cutoverService).isActive(StreamType.BLOCK);
    }

    @Test
    void disabled() {
        // given
        doReturn(false).when(cutoverService).isActive(StreamType.BLOCK);

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
    }

    @Test
    void getAutoNoBlockNodes() {
        // given
        properties.setNodes(Collections.emptyList());

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

        // when
        source.get();

        // then
        verify(blockFileSource).get();
        verify(blockNodeSubscriber, times(3)).get();

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

        // when
        source.get();

        // then
        verify(blockFileSource, times(4)).get();
        verify(blockNodeSubscriber, times(4)).get();

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
    }
}
