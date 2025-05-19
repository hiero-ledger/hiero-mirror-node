// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.reader.record.sidecar;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.google.protobuf.InvalidProtocolBufferException;
import org.apache.commons.compress.compressors.CompressorException;
import org.hiero.mirror.common.domain.DomainBuilder;
import org.hiero.mirror.common.domain.transaction.SidecarFile;
import org.hiero.mirror.importer.TestRecordFiles;
import org.hiero.mirror.importer.TestUtils;
import org.hiero.mirror.importer.domain.StreamFileData;
import org.hiero.mirror.importer.exception.InvalidStreamFileException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SidecarFileReaderImplTest {

    private static final String RECORD_FILENAME = "2022-07-13T08_46_11.304284003Z.rcd.gz";

    private static final String SIDECAR_FILENAME = "2022-07-13T08_46_11.304284003Z_01.rcd.gz";

    private static final String SIDECAR_FILE_PATH = "data/recordstreams/v6/record0.0.3/sidecar/" + SIDECAR_FILENAME;

    private DomainBuilder domainBuilder;

    private SidecarFileReader sidecarFileReader;

    @BeforeEach
    void beforeEach() {
        domainBuilder = new DomainBuilder();
        sidecarFileReader = new SidecarFileReaderImpl();
    }

    @Test
    void read() {
        var expected = TestRecordFiles.getAll()
                .get(RECORD_FILENAME)
                .getSidecars()
                .iterator()
                .next();
        // clear the fields SidecarFileReader fills
        var sidecar = expected.toBuilder()
                .actualHash(null)
                .bytes(null)
                .count(null)
                .size(null)
                .records(null)
                .build();
        var streamFileData = StreamFileData.from(TestUtils.getResource(SIDECAR_FILE_PATH));

        sidecarFileReader.read(sidecar, streamFileData);

        assertThat(sidecar)
                .returns(streamFileData.getBytes(), SidecarFile::getBytes)
                .isEqualTo(expected);
    }

    @Test
    void readCorruptedGzipFile() {
        var streamFileData = StreamFileData.from(SIDECAR_FILENAME, domainBuilder.bytes(256));
        var sidecar = domainBuilder.sidecarFile().get();
        assertThatThrownBy(() -> sidecarFileReader.read(sidecar, streamFileData))
                .isInstanceOf(InvalidStreamFileException.class)
                .hasCauseInstanceOf(CompressorException.class);
    }

    @Test
    void readCorruptedProtoFile() {
        var data = TestUtils.gzip(domainBuilder.bytes(256));
        var streamFileData = StreamFileData.from(SIDECAR_FILENAME, data);
        var sidecar = domainBuilder.sidecarFile().get();
        assertThatThrownBy(() -> sidecarFileReader.read(sidecar, streamFileData))
                .isInstanceOf(InvalidStreamFileException.class)
                .hasCauseInstanceOf(InvalidProtocolBufferException.class);
    }
}
