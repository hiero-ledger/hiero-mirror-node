// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.downloader.block;

import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;
import org.hiero.mirror.common.domain.transaction.BlockFile;
import org.hiero.mirror.common.domain.transaction.BlockSourceType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CompositeBlockSourceTest {

    @Mock
    private BlockFileSource blockFileSource;

    @Mock
    private BlockNodeSubscriber blockNodeSubscriber;

    @Mock
    private BlockStreamVerifier blockStreamVerifier;

    private BlockProperties properties;
    private CompositeBlockSource source;
    private Map<BlockSourceType, BlockSource> sources;

    @BeforeEach
    void setup() {
        properties = new BlockProperties();
        properties.setEnabled(true);
        properties.setNodes(List.of(new BlockNodeProperties()));
        source = new CompositeBlockSource(blockFileSource, blockNodeSubscriber, blockStreamVerifier, properties);
        sources = Map.of(
                BlockSourceType.AUTO,
                blockNodeSubscriber,
                BlockSourceType.BLOCK_NODE,
                blockNodeSubscriber,
                BlockSourceType.FILE,
                blockFileSource);
    }

    @Test
    void disabled() {
        // given
        properties.setEnabled(false);

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
        doReturn(Optional.of(blockFile(2, true))).when(blockStreamVerifier).getLastBlockFile();

        // when
        source.get();

        // then
        verify(blockFileSource).get();
        verifyNoInteractions(blockNodeSubscriber);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("provideAutoSwitchBlockFile")
    void getAutoSwitchOnError(String filename, BlockFile blockFile) {
        // given
        doReturn(Optional.of(blockFile)).when(blockStreamVerifier).getLastBlockFile();
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

    @Test
    void getAutoWithLastBlockStreamed() {
        // given
        doReturn(Optional.of(blockFile(5, false))).when(blockStreamVerifier).getLastBlockFile();
        doThrow(new RuntimeException()).when(blockNodeSubscriber).get();

        // when
        for (int i = 0; i < 4; i++) {
            source.get();
        }

        // then
        verifyNoInteractions(blockFileSource);
        verify(blockNodeSubscriber, times(4)).get();
    }

    private static Stream<Arguments> provideAutoSwitchBlockFile() {
        return Stream.of(
                Arguments.of(null, BlockStreamVerifier.EMPTY),
                Arguments.of(
                        "2022-07-13T08_46_11.304284003Z.rcd.gz",
                        BlockFile.builder()
                                .index(100L)
                                .name("2022-07-13T08_46_11.304284003Z.rcd.gz")
                                .build()),
                Arguments.of(
                        "000000000000000000000000000000000077.blk.gz",
                        BlockFile.builder()
                                .index(77L)
                                .name("000000000000000000000000000000000077.blk.gz")
                                .build()));
    }

    private BlockFile blockFile(long blockNumber, boolean gzipped) {
        return BlockFile.builder()
                .name(BlockFile.getFilename(blockNumber, gzipped))
                .index(blockNumber)
                .build();
    }
}
