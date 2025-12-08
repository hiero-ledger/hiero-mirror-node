// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.state.keyvalue;

import static org.assertj.core.api.Assertions.assertThat;

import com.hedera.hapi.node.base.FileID;
import com.hedera.hapi.node.state.file.File;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import lombok.RequiredArgsConstructor;
import org.hiero.mirror.web3.Web3IntegrationTest;
import org.junit.jupiter.api.Test;

@RequiredArgsConstructor
class FileReadableKVStateIntegrationTest extends Web3IntegrationTest {

    private final FileReadableKVState fileReadableKVState;

    @Test
    void testGetFile() {
        // Given
        final var fileDataLatest = domainBuilder.fileData().persist();
        // persist second file data with the same entity id but older timestamp
        domainBuilder
                .fileData()
                .customize(f -> f.consensusTimestamp(fileDataLatest.getConsensusTimestamp() - 1)
                        .entityId(fileDataLatest.getEntityId()))
                .persist();

        final var entityId = fileDataLatest.getEntityId();
        FileID fileID = new FileID(entityId.getShard(), entityId.getRealm(), entityId.getNum());
        File expected = new File(fileID, () -> null, null, Bytes.wrap(fileDataLatest.getFileData()), "", false, 0);

        // When
        File actual = fileReadableKVState.readFromDataSource(fileID);
        // Then
        assertThat(actual).isEqualTo(expected);
    }
}
