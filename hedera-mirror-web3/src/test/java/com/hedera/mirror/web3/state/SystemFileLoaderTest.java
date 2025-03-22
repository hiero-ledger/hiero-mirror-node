// SPDX-License-Identifier: Apache-2.0

package com.hedera.mirror.web3.state;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.hedera.hapi.node.base.CurrentAndNextFeeSchedule;
import com.hedera.hapi.node.base.FeeSchedule;
import com.hedera.hapi.node.base.FileID;
import com.hedera.hapi.node.base.NodeAddressBook;
import com.hedera.hapi.node.base.ServicesConfigurationList;
import com.hedera.hapi.node.state.file.File;
import com.hedera.hapi.node.transaction.ExchangeRate;
import com.hedera.hapi.node.transaction.ExchangeRateSet;
import com.hedera.hapi.node.transaction.ThrottleDefinitions;
import com.hedera.mirror.common.domain.file.FileData;
import com.hedera.mirror.web3.evm.properties.MirrorNodeEvmProperties;
import com.hedera.mirror.web3.repository.FileDataRepository;
import com.hedera.pbj.runtime.Codec;
import com.hedera.pbj.runtime.ParseException;
import com.hedera.pbj.runtime.io.ReadableSequentialData;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SystemFileLoaderTest {

    private static final long TEST_FILE_ID = 100L;
    private static final FileID TEST_FILE_ID_PROTO =
            FileID.newBuilder().fileNum(TEST_FILE_ID).build();
    private static final File TEST_SYSTEM_FILE = File.newBuilder()
            .fileId(TEST_FILE_ID_PROTO)
            .contents(Bytes.wrap(new byte[] {4, 5, 6}))
            .build();
    private static final long TEST_FILE_ID_THROTTLES = 123L;
    private static final FileID TEST_FILE_ID_THROTTLES_PROTO =
            FileID.newBuilder().fileNum(TEST_FILE_ID_THROTTLES).build();
    private static final byte[] VALID_DATA = "valid data".getBytes();
    private static final byte[] CORRUPT_DATA = "corrupt".getBytes();
    private static final FileData validFileData =
            FileData.builder().consensusTimestamp(200L).fileData(VALID_DATA).build();
    private static final FileData corruptFileData =
            FileData.builder().consensusTimestamp(300L).fileData(CORRUPT_DATA).build();
    private static final com.hederahashgraph.api.proto.java.ThrottleDefinitions throttleDefinitions =
            com.hederahashgraph.api.proto.java.ThrottleDefinitions.newBuilder()
                    .addThrottleBuckets(com.hederahashgraph.api.proto.java.ThrottleBucket.newBuilder()
                            .build())
                    .build();

    private static final FileData throttleDefinitionsFileData = FileData.builder()
            .consensusTimestamp(200L)
            .fileData(throttleDefinitions.toByteArray())
            .build();

    @Mock
    private FileDataRepository fileDataRepository;

    private SystemFileLoader systemFileLoader;
    private Codec<ThrottleDefinitions> mockCodec;

    @BeforeEach
    void setup() {
        systemFileLoader = spy(new SystemFileLoader(new MirrorNodeEvmProperties(), fileDataRepository));
        mockCodec = mock(Codec.class);
    }

    @Test
    void loadNonSystemFile() {
        var file = systemFileLoader.load(fileId(1000));
        assertThat(file).isNull();
    }

    @Test
    void loadAddressBook() throws Exception {
        var fileId = fileId(101);
        var file = systemFileLoader.load(fileId);
        assertFile(file, fileId);
        var nodeAddressBook = NodeAddressBook.PROTOBUF.parse(file.contents());
        assertThat(nodeAddressBook).isNotNull().isEqualTo(NodeAddressBook.DEFAULT);
    }

    @Test
    void loadNodeDetails() throws Exception {
        var fileId = fileId(102);
        var file = systemFileLoader.load(fileId);
        assertFile(file, fileId);
        var nodeAddressBook = NodeAddressBook.PROTOBUF.parse(file.contents());
        assertThat(nodeAddressBook).isNotNull().isEqualTo(NodeAddressBook.DEFAULT);
    }

    @Test
    void loadFeeSchedule() throws Exception {
        var fileId = fileId(111);
        var file = systemFileLoader.load(fileId);
        assertFile(file, fileId);
        var feeSchedule = CurrentAndNextFeeSchedule.PROTOBUF.parse(file.contents());
        assertThat(feeSchedule).isNotNull().isNotEqualTo(CurrentAndNextFeeSchedule.DEFAULT);
        assertThat(feeSchedule.currentFeeSchedule())
                .isNotNull()
                .extracting(FeeSchedule::transactionFeeSchedule, InstanceOfAssertFactories.LIST)
                .hasSizeGreaterThanOrEqualTo(72);
    }

    @Test
    void loadExchangeRate() throws Exception {
        var fileId = fileId(112);
        var file = systemFileLoader.load(fileId);
        assertFile(file, fileId);
        var exchangeRateSet = ExchangeRateSet.PROTOBUF.parse(file.contents());
        assertThat(exchangeRateSet).isNotNull().isNotEqualTo(ExchangeRateSet.DEFAULT);
        assertThat(exchangeRateSet.currentRate()).isNotNull().isNotEqualTo(ExchangeRate.DEFAULT);
    }

    @Test
    void loadNetworkProperties() throws Exception {
        var fileId = fileId(121);
        var file = systemFileLoader.load(fileId);
        assertFile(file, fileId);
        var networkProperties = ServicesConfigurationList.PROTOBUF.parse(file.contents());
        assertThat(networkProperties).isNotNull().isEqualTo(ServicesConfigurationList.DEFAULT);
    }

    @Test
    void loadHapiPermissions() throws Exception {
        var fileId = fileId(122);
        var file = systemFileLoader.load(fileId);
        assertFile(file, fileId);
        var networkProperties = ServicesConfigurationList.PROTOBUF.parse(file.contents());
        assertThat(networkProperties).isNotNull().isEqualTo(ServicesConfigurationList.DEFAULT);
    }

    @Test
    void loadThrottleDefinitions() throws Exception {
        var fileId = fileId(123);
        var file = systemFileLoader.load(fileId);
        assertFile(file, fileId);
        var throttleDefinitions = ThrottleDefinitions.PROTOBUF.parse(file.contents());
        assertThat(throttleDefinitions).isNotNull().isNotEqualTo(ThrottleDefinitions.DEFAULT);
        assertThat(throttleDefinitions.throttleBuckets()).hasSizeGreaterThanOrEqualTo(5);
    }

    @Test
    void loadWithRetry() throws Exception {
        long currentNanos = 350L;
        when(fileDataRepository.getFileAtTimestamp(TEST_FILE_ID, currentNanos))
                .thenReturn(Optional.of(corruptFileData));
        when(fileDataRepository.getFileAtTimestamp(TEST_FILE_ID, 299L)).thenReturn(Optional.of(validFileData));
        when(mockCodec.parse(any(ReadableSequentialData.class)))
                .thenThrow(new ParseException("Invalid data"))
                .thenReturn(ThrottleDefinitions.newBuilder().build());

        final var actual = systemFileLoader.loadWithRetry(TEST_FILE_ID, TEST_FILE_ID_PROTO, currentNanos, mockCodec);

        assertThat(actual).isNotNull();
        assertThat(actual.contents()).isEqualTo(Bytes.wrap(VALID_DATA));
        assertThat(actual.fileId()).isEqualTo(TEST_FILE_ID_PROTO);
        verify(fileDataRepository, times(2)).getFileAtTimestamp(eq(TEST_FILE_ID), anyLong());
    }

    @Test
    void loadWithRetryCorruptDataResolveToSystemFile() throws Exception {
        when(fileDataRepository.getFileAtTimestamp(eq(TEST_FILE_ID), anyLong()))
                .thenReturn(Optional.of(corruptFileData));
        when(systemFileLoader.load(TEST_FILE_ID_PROTO)).thenReturn(TEST_SYSTEM_FILE);
        when(mockCodec.parse(any(ReadableSequentialData.class))).thenThrow(new ParseException("Invalid data"));

        final var actual = systemFileLoader.loadWithRetry(TEST_FILE_ID, TEST_FILE_ID_PROTO, 350L, mockCodec);

        assertThat(actual).isEqualTo(TEST_SYSTEM_FILE);
        verify(fileDataRepository, times(10)).getFileAtTimestamp(eq(TEST_FILE_ID), anyLong());
    }

    @Test
    void loadWithRetrySuccessfully() throws Exception {
        when(fileDataRepository.getFileAtTimestamp(eq(TEST_FILE_ID_THROTTLES), anyLong()))
                .thenReturn(Optional.of(validFileData));
        when(mockCodec.parse(any(ReadableSequentialData.class)))
                .thenReturn(ThrottleDefinitions.newBuilder().build());

        final var actual =
                systemFileLoader.loadWithRetry(TEST_FILE_ID_THROTTLES, TEST_FILE_ID_THROTTLES_PROTO, 250L, mockCodec);

        assertThat(actual).isNotNull();
        assertThat(actual.contents()).isEqualTo(Bytes.wrap(VALID_DATA));
        assertThat(actual.fileId()).isEqualTo(TEST_FILE_ID_THROTTLES_PROTO);
        verify(fileDataRepository, times(1)).getFileAtTimestamp(eq(TEST_FILE_ID_THROTTLES), anyLong());
    }

    @Test
    void loadWithRetryNoData() {
        when(fileDataRepository.getFileAtTimestamp(eq(TEST_FILE_ID), anyLong())).thenReturn(Optional.empty());
        when(systemFileLoader.load(TEST_FILE_ID_PROTO)).thenReturn(TEST_SYSTEM_FILE);

        final var actual = systemFileLoader.loadWithRetry(TEST_FILE_ID, TEST_FILE_ID_PROTO, 100L, mockCodec);

        assertThat(actual).isEqualTo(TEST_SYSTEM_FILE);
        verify(fileDataRepository, times(1)).getFileAtTimestamp(eq(TEST_FILE_ID), anyLong());
    }

    @Test
    void loadWithRetryAllRetriesFailing() throws Exception {
        when(fileDataRepository.getFileAtTimestamp(eq(TEST_FILE_ID), anyLong()))
                .thenReturn(Optional.of(corruptFileData));
        when(systemFileLoader.load(TEST_FILE_ID_PROTO)).thenReturn(TEST_SYSTEM_FILE);
        when(mockCodec.parse(any(ReadableSequentialData.class))).thenThrow(new ParseException("Invalid data"));

        final var actual = systemFileLoader.loadWithRetry(TEST_FILE_ID, TEST_FILE_ID_PROTO, 350L, mockCodec);

        assertThat(actual).isEqualTo(TEST_SYSTEM_FILE);
        verify(fileDataRepository, times(10)).getFileAtTimestamp(eq(TEST_FILE_ID), anyLong());
    }

    @Test
    void loadThrottlesWithRetry() {
        when(fileDataRepository.getFileAtTimestamp(eq(TEST_FILE_ID_THROTTLES), anyLong()))
                .thenReturn(Optional.of(throttleDefinitionsFileData));

        final var actual = systemFileLoader.loadThrottles(TEST_FILE_ID_THROTTLES, TEST_FILE_ID_THROTTLES_PROTO, 250L);

        assertThat(actual).isNotNull();
        assertThat(actual.contents()).isEqualTo(Bytes.wrap(throttleDefinitionsFileData.getFileData()));
        assertThat(actual.fileId()).isEqualTo(TEST_FILE_ID_THROTTLES_PROTO);
        verify(fileDataRepository, times(1)).getFileAtTimestamp(eq(TEST_FILE_ID_THROTTLES), anyLong());
    }

    private FileID fileId(long fileNum) {
        return FileID.newBuilder().fileNum(fileNum).build();
    }

    private void assertFile(File file, FileID fileId) {
        assertThat(file)
                .isNotNull()
                .returns(fileId, File::fileId)
                .returns(false, File::deleted)
                .matches(f -> f.contents() != null)
                .matches(f -> Instant.ofEpochSecond(f.expirationSecondSupplier().get())
                        .isAfter(Instant.now().plus(92, ChronoUnit.DAYS)));
    }
}
