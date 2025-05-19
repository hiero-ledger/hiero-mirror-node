// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.downloader;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import org.hiero.mirror.common.domain.transaction.RecordFile;
import org.hiero.mirror.importer.addressbook.ConsensusNode;
import org.hiero.mirror.importer.domain.ConsensusNodeStub;
import org.hiero.mirror.importer.domain.StreamFileSignature;
import org.hiero.mirror.importer.domain.StreamFilename;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DownloaderTest {

    @Mock
    private Downloader<RecordFile, ?> downloader;

    @Test
    void streamFileSignatureMultiMapExpectNoDuplicate() {
        // given
        when(downloader.getStreamFileSignatureMultiMap()).thenCallRealMethod();
        var signatureFilename = StreamFilename.from("2022-01-01T00_00_00Z.rcd_sig");
        var multimap = downloader.getStreamFileSignatureMultiMap();
        var streamFileSignature = streamFileSignature(1L, signatureFilename);
        multimap.put(signatureFilename, streamFileSignature);
        // Same object
        multimap.put(signatureFilename, streamFileSignature);
        // Different object with same value
        multimap.put(signatureFilename, streamFileSignature(1L, signatureFilename));
        // Different value
        multimap.put(signatureFilename, streamFileSignature(2L, signatureFilename));

        // when
        var actual = multimap.get(signatureFilename);

        // then
        assertThat(actual)
                .map(StreamFileSignature::getNode)
                .map(ConsensusNode::getNodeId)
                .containsExactlyInAnyOrder(1L, 2L);
    }

    private StreamFileSignature streamFileSignature(long nodeId, StreamFilename streamFilename) {
        return StreamFileSignature.builder()
                .filename(streamFilename)
                .node(ConsensusNodeStub.builder().nodeId(nodeId).build())
                .build();
    }
}
