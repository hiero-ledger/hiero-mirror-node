// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.reader.balance;

import static org.mockito.Mock.Strictness.LENIENT;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.hiero.mirror.common.domain.balance.AccountBalanceFile;
import org.hiero.mirror.importer.domain.StreamFileData;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CompositeBalanceFileReaderTest {

    private static final String BALANCE_FILENAME_PREFIX = "2021-03-15T14_30_00Z_Balances";

    @Mock(strictness = LENIENT)
    private BalanceFileReaderImplV1 readerImplV1;

    @Mock
    private BalanceFileReaderImplV2 readerImplV2;

    @Mock
    private ProtoBalanceFileReader protoBalanceFileReader;

    @InjectMocks
    private CompositeBalanceFileReader compositeBalanceFileReader;

    @Test
    void defaultsToVersion1Reader() {
        StreamFileData streamFileData = StreamFileData.from(BALANCE_FILENAME_PREFIX + ".csv", "timestamp:1");
        configMockReader(protoBalanceFileReader, streamFileData, false);
        configMockReader(readerImplV1, streamFileData, true);

        compositeBalanceFileReader.read(streamFileData);

        verify(readerImplV1, times(1)).read(streamFileData);
        verify(readerImplV2, never()).read(streamFileData);
        verify(protoBalanceFileReader, never()).read(streamFileData);
    }

    @Test
    void usesVersion2Reader() {
        StreamFileData streamFileData = StreamFileData.from(BALANCE_FILENAME_PREFIX + ".csv", "# version:2");
        configMockReader(protoBalanceFileReader, streamFileData, false);
        configMockReader(readerImplV2, streamFileData, true);

        compositeBalanceFileReader.read(streamFileData);

        verify(readerImplV2, times(1)).read(streamFileData);
        verify(readerImplV1, never()).read(streamFileData);
        verify(protoBalanceFileReader, never()).read(streamFileData);
    }

    @Test
    void usesProtoBalanceFileReader() {
        StreamFileData streamFileData =
                StreamFileData.from(BALANCE_FILENAME_PREFIX + ".pb.gz", "proto-based balance file");
        configMockReader(protoBalanceFileReader, streamFileData, true);

        compositeBalanceFileReader.read(streamFileData);

        verify(protoBalanceFileReader, times(1)).read(streamFileData);
        verify(readerImplV1, never()).read(streamFileData);
        verify(readerImplV2, never()).read(streamFileData);
    }

    private void configMockReader(BalanceFileReader reader, StreamFileData streamFileData, boolean supports) {
        if (supports) {
            when(reader.supports(streamFileData)).thenReturn(true);
            when(reader.read(streamFileData))
                    .thenReturn(AccountBalanceFile.builder().count(1L).build());
        } else {
            when(reader.supports(streamFileData)).thenReturn(false);
        }
    }
}
