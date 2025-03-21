// SPDX-License-Identifier: Apache-2.0

package com.hedera.mirror.web3.state.throttle;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.hedera.hapi.node.base.FileID;
import com.hedera.hapi.node.base.ResponseCodeEnum;
import com.hedera.hapi.node.state.file.File;
import com.hedera.hapi.node.transaction.ThrottleDefinitions;
import com.hedera.mirror.common.domain.file.FileData;
import com.hedera.mirror.web3.repository.FileDataRepository;
import com.hedera.mirror.web3.state.SystemFileLoader;
import com.hedera.node.app.spi.workflows.HandleException;
import com.hedera.node.app.throttle.ThrottleParser;
import com.hedera.node.app.throttle.ThrottleParser.ValidatedThrottles;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ThrottleDefinitionsManagerTest {

    private static final long THROTTLE_FILE_ID = 123L;
    private static final FileID THROTTLE_FILE_ID_PROTO = FileID.newBuilder().fileNum(THROTTLE_FILE_ID).build();
    private static final File systemFile = File.newBuilder()
            .contents(Bytes.wrap(new byte[]{4, 5, 6}))
            .fileId(THROTTLE_FILE_ID_PROTO)
            .build();
    private static final Bytes MOCK_ENCODED_THROTTLE_DEFS = Bytes.wrap("NOPE");
    private static final FileData validThrottleFileData = FileData.builder()
            .consensusTimestamp(200L)
            .fileData(MOCK_ENCODED_THROTTLE_DEFS.toByteArray())
            .build();
    private static final FileData corruptThrottleFileData = FileData.builder()
            .consensusTimestamp(300L)
            .fileData("corrupt".getBytes())
            .build();
    @Mock
    private FileDataRepository fileDataRepository;

    @Mock
    private SystemFileLoader systemFileLoader;

    @Mock
    private ThrottleParser throttleParser;

    @InjectMocks
    private ThrottleDefinitionsManager throttleDefinitionsManager;

    @Test
    void loadThrottlesSuccessfully() {
        when(fileDataRepository.getFileAtTimestamp(eq(THROTTLE_FILE_ID), anyLong()))
                .thenReturn(Optional.of(validThrottleFileData));
        when(throttleParser.parse(any(Bytes.class))).thenReturn(new ValidatedThrottles(ThrottleDefinitions.DEFAULT,
                ResponseCodeEnum.SUCCESS));

        final var actual = throttleDefinitionsManager.loadThrottles(THROTTLE_FILE_ID, THROTTLE_FILE_ID_PROTO, 250L);

        assertThat(actual).isNotNull();
        assertThat(actual.contents()).isEqualTo(MOCK_ENCODED_THROTTLE_DEFS);
        assertThat(actual.fileId()).isEqualTo(THROTTLE_FILE_ID_PROTO);
    }

    @Test
    void loadThrottlesWithCorruptDataResolveToSystemFile() {
        when(systemFileLoader.load(THROTTLE_FILE_ID_PROTO)).thenReturn(systemFile);
        when(fileDataRepository.getFileAtTimestamp(eq(THROTTLE_FILE_ID), anyLong()))
                .thenReturn(Optional.of(corruptThrottleFileData));
        when(throttleParser.parse(any(Bytes.class))).thenThrow(new HandleException(ResponseCodeEnum.UNPARSEABLE_THROTTLE_DEFINITIONS));

        final var actual = throttleDefinitionsManager.loadThrottles(THROTTLE_FILE_ID, THROTTLE_FILE_ID_PROTO, 350L);
        assertThat(actual).isEqualTo(systemFile);
    }

    @Test
    void loadThrottlesWithCorruptDataResolveToSystemFileThatReturnsNull() {
        when(fileDataRepository.getFileAtTimestamp(eq(THROTTLE_FILE_ID), anyLong()))
                .thenReturn(Optional.of(corruptThrottleFileData));
        when(throttleParser.parse(any(Bytes.class))).thenThrow(new HandleException(ResponseCodeEnum.UNPARSEABLE_THROTTLE_DEFINITIONS));

        final var actual = throttleDefinitionsManager.loadThrottles(THROTTLE_FILE_ID, THROTTLE_FILE_ID_PROTO, 350L);
        assertThat(actual).isEqualTo(null);
    }

    @Test
    void loadThrottlesWithRetry() {
        long currentNanos = 350L;
        when(fileDataRepository.getFileAtTimestamp(THROTTLE_FILE_ID, currentNanos))
                .thenReturn(Optional.of(corruptThrottleFileData));
        when(fileDataRepository.getFileAtTimestamp(THROTTLE_FILE_ID, 299L))
                .thenReturn(Optional.of(validThrottleFileData));
        when(throttleParser.parse(any(Bytes.class)))
                .thenThrow(new HandleException(ResponseCodeEnum.UNPARSEABLE_THROTTLE_DEFINITIONS)) // First call throws exception
                .thenReturn(new ValidatedThrottles(ThrottleDefinitions.DEFAULT, ResponseCodeEnum.SUCCESS)); // Second call returns success

        final var actual = throttleDefinitionsManager.loadThrottles(THROTTLE_FILE_ID, THROTTLE_FILE_ID_PROTO, currentNanos);

        assertThat(actual).isNotNull();
        assertThat(actual.contents()).isEqualTo(MOCK_ENCODED_THROTTLE_DEFS);
        assertThat(actual.fileId()).isEqualTo(THROTTLE_FILE_ID_PROTO);
    }

    @Test
    void loadThrottlesWithNoData() {
        when(systemFileLoader.load(THROTTLE_FILE_ID_PROTO)).thenReturn(systemFile);
        when(fileDataRepository.getFileAtTimestamp(eq(THROTTLE_FILE_ID), anyLong()))
                .thenReturn(Optional.empty());

        final var actual = throttleDefinitionsManager.loadThrottles(THROTTLE_FILE_ID, THROTTLE_FILE_ID_PROTO, 100L);

        assertThat(actual).isEqualTo(systemFile);
    }

    @Test
    void loadThrottlesWithAllRetriesFailing() {
        when(systemFileLoader.load(THROTTLE_FILE_ID_PROTO)).thenReturn(systemFile);
        when(fileDataRepository.getFileAtTimestamp(eq(THROTTLE_FILE_ID), anyLong()))
                .thenReturn(Optional.of(corruptThrottleFileData));
        when(throttleParser.parse(any(Bytes.class))).thenThrow(new HandleException(ResponseCodeEnum.UNPARSEABLE_THROTTLE_DEFINITIONS));

        final var actual = throttleDefinitionsManager.loadThrottles(THROTTLE_FILE_ID, THROTTLE_FILE_ID_PROTO, 350L);

        assertThat(actual).isEqualTo(systemFile);
    }
} 