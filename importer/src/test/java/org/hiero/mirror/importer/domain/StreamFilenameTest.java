// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hiero.mirror.importer.domain.StreamFilename.FileType.DATA;
import static org.hiero.mirror.importer.domain.StreamFilename.FileType.SIDECAR;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.hiero.mirror.common.domain.StreamType;
import org.hiero.mirror.importer.exception.InvalidStreamFileException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

final class StreamFilenameTest {

    @Test
    void fromBlockNumber() {
        var streamFilename = StreamFilename.from(0);
        assertThat(streamFilename)
                .returns("gz", StreamFilename::getCompressor)
                .returns("blk", s -> s.getExtension().getName())
                .returns("000000000000000000000000000000000000.blk.gz", StreamFilename::getFilename)
                .returns("000000000000000000000000000000000000.blk.gz", StreamFilename::getFilePath)
                .returns("blk.gz", StreamFilename::getFullExtension)
                .returns(null, StreamFilename::getPath)
                .returns(StreamType.BLOCK, StreamFilename::getStreamType);
    }

    @Test
    void fromNegativeBlockNumber() {
        assertThatThrownBy(() -> StreamFilename.from(-1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Block number must be non-negative");
    }

    @ParameterizedTest(name = "Create StreamFilename from {0}")
    @CsvSource({
        // @formatter:off
        "2020-06-03T16_45_00.1Z_Balances.csv_sig,, csv_sig, SIGNATURE, csv_sig, 2020-06-03T16:45:00.1Z, BALANCE",
        "2020-06-03T16_45_00.1Z_Balances.pb_sig,, pb_sig, SIGNATURE, pb_sig, 2020-06-03T16:45:00.1Z, BALANCE",
        "2020-06-03T16_45_00.1Z_Balances.pb_sig.gz, gz, pb_sig, SIGNATURE, pb_sig.gz, 2020-06-03T16:45:00.1Z, "
                + "BALANCE",
        "2020-06-03T16_45_00.1Z_Balances.pb_sig.xz, xz, pb_sig, SIGNATURE, pb_sig.xz, 2020-06-03T16:45:00.1Z, "
                + "BALANCE",
        "2020-06-03T16_45_00.1Z_Balances.csv,, csv, DATA, csv, 2020-06-03T16:45:00.1Z, BALANCE",
        "2020-06-03T16_45_00.1Z_Balances.pb.gz, gz, pb, DATA, pb.gz, 2020-06-03T16:45:00.1Z, BALANCE",
        "2020-06-03T16_45_00.1Z.rcd_sig,,  rcd_sig, SIGNATURE, rcd_sig, 2020-06-03T16:45:00.1Z, RECORD",
        "2020-06-03T16_45_00.1Z.rcd,, rcd, DATA, rcd, 2020-06-03T16:45:00.1Z, RECORD",
        "000000000000000000000000000007647866.blk,, blk, DATA, blk,, BLOCK",
        "000000000000000000000000000007647866.blk.gz, gz, blk, DATA, blk.gz,, BLOCK"
        // @formatter:on
    })
    void newStreamFile(
            String filename,
            String compressor,
            String extension,
            StreamFilename.FileType fileType,
            String fullExtension,
            Instant instant,
            StreamType streamType) {
        StreamFilename streamFilename = StreamFilename.from(filename);
        String[] fields = {
            "filename",
            "compressor",
            "extension.name",
            "fileType",
            "fullExtension",
            "instant",
            "sidecarId",
            "streamType"
        };
        Object[] expected = {filename, compressor, extension, fileType, fullExtension, instant, null, streamType};

        assertThat(streamFilename).extracting(fields).containsExactly(expected);
    }

    @ParameterizedTest
    @CsvSource({
        "2020-06-03T16_45_00Z_01.rcd, 01",
        "2020-06-03T16_45_00.123Z_02.rcd, 02",
        "2020-06-03T16_45_00.123Z_02.rcd.gz, 02",
    })
    void newStreamFileFromSidecarRecordFilename(String filename, String expectedSidecarId) {
        StreamFilename streamFilename = StreamFilename.from(filename);
        assertThat(streamFilename)
                .returns(SIDECAR, StreamFilename::getFileType)
                .returns(expectedSidecarId, StreamFilename::getSidecarId);
    }

    @ParameterizedTest(name = "Exception creating StreamFilename from \"{0}\"")
    @ValueSource(
            strings = {
                "2020-06-03_Balances.csv_sig",
                "2020-06-03T16_45_00.1Z",
                "2020-06-03T16_45_00.1Z.stream",
                "2020-06-03T16_45_00.1Z.csv_sig",
                "2020-06-03T16_45_00"
            })
    void newStreamFileFromInvalidFilename(String filename) {
        assertThrows(InvalidStreamFileException.class, () -> StreamFilename.from(filename));
    }

    @ParameterizedTest(name = "Get filename from streamType {0}, fileType {1}, and instant {2}")
    @CsvSource({
        "BALANCE, DATA, 2020-06-03T16:45:00.123Z, 2020-06-03T16_45_00.123Z_Balances.",
        "BALANCE, SIGNATURE, 2020-06-03T16:45:00.123Z, 2020-06-03T16_45_00.123Z_Balances.",
        "RECORD, DATA, 2020-06-03T16:45:00.123Z, 2020-06-03T16_45_00.123Z.",
        "RECORD, SIGNATURE, 2020-06-03T16:45:00.123Z, 2020-06-03T16_45_00.123Z."
    })
    void getFilename(StreamType streamType, StreamFilename.FileType fileType, Instant instant, String expectedPrefix) {
        String filename = StreamFilename.getFilename(streamType, fileType, instant);
        String extension = filename.substring(filename.lastIndexOf('.') + 1);

        Set<StreamType.Extension> extensions =
                fileType == DATA ? streamType.getDataExtensions() : streamType.getSignatureExtensions();
        List<String> names =
                extensions.stream().map(StreamType.Extension::getName).toList();
        assertThat(filename).startsWith(expectedPrefix);
        assertThat(extension).isIn(names);
    }

    @ParameterizedTest(name = "Get the filename after \"{0}\"")
    @CsvSource({
        "2020-06-03T16_45_00.1Z_Balances.csv_sig.gz, 2020-06-03T16_45_00.1Z_Balances_",
        "2020-06-03T16_45_00.1Z_Balances.csv_sig, 2020-06-03T16_45_00.1Z_Balances_",
        "2020-06-03T16_45_00.1Z_Balances.pb_sig, 2020-06-03T16_45_00.1Z_Balances_",
        "2020-06-03T16_45_00.1Z_Balances.csv, 2020-06-03T16_45_00.1Z_Balances_",
        "2020-06-03T16_45_00.1Z_Balances.pb, 2020-06-03T16_45_00.1Z_Balances_",
        "2020-06-03T16_45_00.1Z.rcd_sig, 2020-06-03T16_45_00.1Z_",
        "2020-06-03T16_45_00.1Z.rcd, 2020-06-03T16_45_00.1Z_"
    })
    void getFilenameAfter(String filename, String expected) {
        StreamFilename streamFilename = StreamFilename.from(filename);
        assertThat(streamFilename.getFilenameAfter()).isEqualTo(expected);
    }

    @Test
    void getInstantThrows() {
        var streamFilename = StreamFilename.from("000000000000000000000000000007647866.blk.gz");
        assertThatThrownBy(streamFilename::getInstant).isInstanceOf(IllegalStateException.class);
    }

    @ParameterizedTest
    @CsvSource({
        "2020-06-03T16_45_00.100200345Z.rcd.gz, 1, 2020-06-03T16_45_00.100200345Z_01.rcd.gz",
        "2020-06-03T16_45_00.100200345Z_02.rcd.gz, 3, 2020-06-03T16_45_00.100200345Z_03.rcd.gz",
    })
    void getSidecarFilename(String filename, int id, String expected) {
        StreamFilename streamFilename = StreamFilename.from(filename);
        assertThat(streamFilename.getSidecarFilename(id)).isEqualTo(expected);
    }

    @ParameterizedTest
    @ValueSource(strings = {"2020-06-03T16_45_00.1Z_Balances.csv", "2020-06-03T16_45_00.100200345Z.rcd_sig"})
    void getSidecarFilenameThrows(String filename) {
        StreamFilename streamFilename = StreamFilename.from(filename);
        assertThrows(IllegalArgumentException.class, () -> streamFilename.getSidecarFilename(1));
    }

    @ParameterizedTest
    @CsvSource({
        "2020-06-03T16_45_00.100200345Z_Balances.csv, false",
        "2020-06-03T16_45_00.100200345Z_Balances.csv_sig, false",
        "2020-06-03T16_45_00.100200345Z_Balances.pb.gz, true",
        "2020-06-03T16_45_00.100200345Z_Balances.pb_sig.gz, true",
        "2020-06-03T16_45_00.100200345Z.rcd.gz, true",
        "2020-06-03T16_45_00.100200345Z.rcd, false",
        "2020-06-03T16_45_00.100200345Z.rcd_sig, false",
        "2020-06-03T16_45_00.100200345Z_02.rcd.gz, true"
    })
    void isCompressed(String filename, boolean compressed) {
        assertThat(StreamFilename.from(filename).isCompressed()).isEqualTo(compressed);
    }

    @ParameterizedTest
    @CsvSource({
        "2020-06-03T16_45_00.100200345Z.rcd.gz, , /, 2020-06-03T16_45_00.100200345Z.rcd.gz",
        "2020-06-03T16_45_00.100200345Z.rcd.gz, some/path, /, some/path/2020-06-03T16_45_00.100200345Z.rcd.gz",
        "2020-06-03T16_45_00.100200345Z.rcd.gz, some\\path, \\, some\\path\\2020-06-03T16_45_00.100200345Z.rcd.gz",
        "2020-06-03T16_45_00.100200345Z_02.rcd.gz, mainnet/0/3/record, /, mainnet/0/3/record/sidecar/2020-06-03T16_45_00.100200345Z_02.rcd.gz"
    })
    void getPathProperties(String filename, String path, String pathSeparator, String expectedFilePath) {
        StreamFilename streamFilename = StreamFilename.from(path, filename, pathSeparator);
        assertThat(streamFilename.getFilename()).isEqualTo(filename);
        assertThat(streamFilename.getPath()).isEqualTo(path);
        assertThat(streamFilename.getPathSeparator()).isEqualTo(pathSeparator);
        assertThat(streamFilename.getFilePath()).isEqualTo(expectedFilePath);

        if (streamFilename.getFileType() != SIDECAR) {
            StreamFilename streamFilenameFromPath = StreamFilename.from(expectedFilePath, pathSeparator);
            assertThat(streamFilenameFromPath.getFilename()).isEqualTo(filename);
            assertThat(streamFilenameFromPath.getPath()).isEqualTo(path);
            assertThat(streamFilenameFromPath.getPathSeparator()).isEqualTo(pathSeparator);
        }
    }

    @ParameterizedTest
    @CsvSource({
        "2020-06-03T16_45_00.100200345Z_Balances.csv_sig, , \\, 2020-06-03T16_45_00.100200345Z_Balances.csv",
        "2020-06-03T16_45_00.100200345Z.rcd_sig, mainnet/0/9/record, /, 2020-06-03T16_45_00.100200345Z.rcd"
    })
    void getDataFilename(String sigFilename, String path, String pathSeparator, String dataFilename) {
        StreamFilename sigStreamFilename = StreamFilename.from(path, sigFilename, pathSeparator);
        StreamFilename dataStreamFilename = StreamFilename.from(sigStreamFilename, dataFilename);

        assertThat(dataStreamFilename.getFilename()).isEqualTo(dataFilename);
        assertThat(dataStreamFilename.getPath()).isEqualTo(sigStreamFilename.getPath());
        assertThat(dataStreamFilename.getPathSeparator()).isEqualTo(sigStreamFilename.getPathSeparator());
        assertThat(dataStreamFilename).isNotEqualTo(sigStreamFilename);
    }

    @ParameterizedTest
    @CsvSource(
            textBlock =
                    """
            accountBalances/balance0.0.3/2020-06-03T16_45_00.100200345Z_Balances.csv_sig, false
            recordstreams/record0.0.5/2020-06-03T16_45_00.100200345Z.rcd_sig, false
            mainnet/0/0/balance/2020-06-03T16_45_00.100200345Z_Balances.csv_sig, true
            mainnet/0/0/record/2020-06-03T16_45_00.100200345Z.rcd_sig, true""")
    void isNodeId(String filename, boolean nodeId) {
        var streamFilename = StreamFilename.from(filename);
        assertThat(streamFilename.isNodeId()).isEqualTo(nodeId);
    }

    // Exercise lombok @NonNull for code coverage
    @Test
    void ensureNonNull() {
        assertThrows(NullPointerException.class, () -> StreamFilename.from(null));
        assertThrows(NullPointerException.class, () -> StreamFilename.from((String) null, "/"));
        assertThrows(NullPointerException.class, () -> StreamFilename.from("somePath", null));
        assertThrows(NullPointerException.class, () -> StreamFilename.from((StreamFilename) null, "someFilename"));
        assertThrows(NullPointerException.class, () -> StreamFilename.from("somePath", null, "\\"));
        assertThrows(InvalidStreamFileException.class, () -> StreamFilename.from("somePath", "someFilename", null));
    }
}
