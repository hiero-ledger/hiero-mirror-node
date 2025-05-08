// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.downloader.block;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CompositeBlockStreamSourceTest {

    @Mock
    private BlockFileSource blockFileSource;

    private BlockStreamProperties properties;
    private CompositeBlockStreamSource source;

    @BeforeEach
    void setup() {
        properties = new BlockStreamProperties();
        properties.setEnabled(true);
        source = new CompositeBlockStreamSource(blockFileSource, properties);
    }

    @Test
    void disabled() {
        // given
        properties.setEnabled(false);

        // when
        source.get();

        // then
        verifyNoInteractions(blockFileSource);
    }

    @Test
    void get() {
        // given, when
        source.get();

        // then
        verify(blockFileSource).get();
    }

    @Test
    void getWithException() {
        // given
        doThrow(new RuntimeException()).when(blockFileSource).get();

        // when
        source.get();

        // then
        verify(blockFileSource).get();
    }
}
