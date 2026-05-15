// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.reader.block.record;

import com.hedera.hapi.block.stream.protoc.Block;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import lombok.SneakyThrows;
import lombok.experimental.UtilityClass;
import org.bouncycastle.util.encoders.Hex;
import org.hiero.mirror.common.domain.DigestAlgorithm;
import org.hiero.mirror.common.domain.StreamType;
import org.hiero.mirror.common.domain.transaction.BlockFile;
import org.hiero.mirror.common.domain.transaction.RecordFile;
import org.hiero.mirror.common.domain.transaction.SidecarFile;
import org.hiero.mirror.importer.TestUtils;
import org.hiero.mirror.importer.domain.StreamFileData;

@UtilityClass
public class WrappedRecordBlockTestUtils {

    // Data extracted from mainnet db, with the following differences
    // - no RecordItem list
    // - size difference, the size here is the uncompressed data size
    // - software version, many existing rows in db don't have it since the columns were added later
    public static final Map<Long, RecordFile> EXPECTED_RECORD_FILES = new TreeMap<>(Map.of(
            0L,
                    RecordFile.builder()
                            .consensusEnd(1568411631396440000L)
                            .consensusStart(1568411631396440000L)
                            .count(1L)
                            .digestAlgorithm(DigestAlgorithm.SHA_384)
                            .fileHash(
                                    "420fffe68fcd2a1eadcce589fdf9565bcf5a269d02232fe07cdc565b3b6f76ce46a9418ddc1bbe051d4894e04d091f8e")
                            .hash(
                                    "420fffe68fcd2a1eadcce589fdf9565bcf5a269d02232fe07cdc565b3b6f76ce46a9418ddc1bbe051d4894e04d091f8e")
                            .index(0L)
                            .name("2019-09-13T21_53_51.396440Z.rcd")
                            .previousHash(
                                    "000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000")
                            .size(455)
                            .version(2)
                            .build(),
            26591040L,
                    RecordFile.builder()
                            .consensusEnd(1640995201946271915L)
                            .consensusStart(1640995200252365821L)
                            .count(35L)
                            .digestAlgorithm(DigestAlgorithm.SHA_384)
                            .fileHash(
                                    "5f63f5c65e02fa305a4dadbb3a5bad781aab70b02cd6d057a2f0ba8b14e08d77cf0c7aaff1876577e787ccb32582e566")
                            .hapiVersionMajor(0)
                            .hapiVersionMinor(11)
                            .hapiVersionPatch(0)
                            .hash(
                                    "0d7773874647eddc3039fedf1d9a47aac58b7f4f4c47e77a8599456b800472cd0b55954837f03e002a217095615430b8")
                            .index(26591040L)
                            .name("2022-01-01T00_00_00.252365821Z.rcd")
                            .previousHash(
                                    "13ab802d147ef5a5a32c4bd386225e354aaa03c91122e48fea9777ad31e010de840dab29d42074fb264b5cacd1f69702")
                            .size(19622)
                            .softwareVersionMajor(0)
                            .softwareVersionMinor(11)
                            .softwareVersionPatch(0)
                            .version(5)
                            .build(),
            82297471L,
                    RecordFile.builder()
                            .consensusEnd(1753303063721549000L)
                            .consensusStart(1753303062076472454L)
                            .count(47L)
                            .digestAlgorithm(DigestAlgorithm.SHA_384)
                            .hapiVersionMajor(0)
                            .hapiVersionMinor(63)
                            .hapiVersionPatch(9)
                            .fileHash(
                                    "c998dda6ca7216881d0c0037e2b19897078a561c46354cd69176eff4bc8b65666ca2ace1abd849d0970a237d128f6352")
                            .hash(
                                    "f3a71062087f6afb70754c32cca0dcb48d297b0b909a956cd2b6d22c782ed6054742584b0465865e1fb1adcfbda7f65d")
                            .index(82297471L)
                            .name("2025-07-23T20_37_42.076472454Z.rcd")
                            .previousHash(
                                    "cbd7a318fb7d0a023632002926857a1511953f6e1a6d162df1fe8b57f97d21389094f7a2bb31edd9d9859cc38c561bdd")
                            .sidecarCount(1)
                            .sidecars(List.of(SidecarFile.builder()
                                    .consensusEnd(1753303063721549000L)
                                    .count(12)
                                    .hashAlgorithm(DigestAlgorithm.SHA_384)
                                    .hash(
                                            Hex.decode(
                                                    "e4cbf4516c964c8f9dbc5c46ee2956aa4cad468abfc74352f1146c7e40e81522e9528c943c9b9214faa43a6577d3d2f5"))
                                    .index(1)
                                    .name("2025-07-23T20_37_42.076472454Z_01.rcd")
                                    .size(41037)
                                    .types(List.of(1, 2))
                                    .build()))
                            .size(32665)
                            .softwareVersionMajor(0)
                            .softwareVersionMinor(63)
                            .softwareVersionPatch(9)
                            .version(6)
                            .build()));

    public static List<Block> readWrappedRecordBlocks() {
        return EXPECTED_RECORD_FILES.keySet().stream()
                .map(WrappedRecordBlockTestUtils::readWrappedRecordBlock)
                .toList();
    }

    @SneakyThrows
    private static Block readWrappedRecordBlock(final long blockNumber) {
        final var filename = BlockFile.getFilename(blockNumber, true);
        final var bucketFilename = StreamType.BLOCK.toBucketFilename(filename);
        final var file = TestUtils.getResource("data/wrbs/" + bucketFilename);
        final var streamFileData = StreamFileData.from(file);

        try (final var is = streamFileData.getInputStream()) {
            return Block.parseFrom(is);
        }
    }
}
