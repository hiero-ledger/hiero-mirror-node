// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.reader.record.sidecar;

import static org.hiero.mirror.common.util.DomainUtils.parseProtobuf;

import jakarta.inject.Named;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import lombok.RequiredArgsConstructor;
import org.hiero.mirror.common.domain.transaction.SidecarFile;
import org.hiero.mirror.importer.domain.StreamFileData;
import org.hiero.mirror.importer.downloader.CommonDownloaderProperties;
import org.hiero.mirror.importer.exception.InvalidStreamFileException;

@Named
@RequiredArgsConstructor
public class SidecarFileReaderImpl implements SidecarFileReader {

    private final CommonDownloaderProperties commonDownloaderProperties;

    @Override
    public void read(SidecarFile sidecarFile, StreamFileData streamFileData) {
        try (final var digestInputStream = new DigestInputStream(
                streamFileData.getInputStream(),
                MessageDigest.getInstance(sidecarFile.getHashAlgorithm().getName()))) {
            final int maxSize = (int) commonDownloaderProperties.getMaxSize().toBytes();
            final var protoSidecarFile =
                    parseProtobuf(digestInputStream, com.hedera.services.stream.proto.SidecarFile::parseFrom, maxSize);
            var bytes = streamFileData.getBytes();
            sidecarFile.setActualHash(digestInputStream.getMessageDigest().digest());
            sidecarFile.setBytes(bytes);
            sidecarFile.setCount(protoSidecarFile.getSidecarRecordsCount());
            sidecarFile.setRecords(protoSidecarFile.getSidecarRecordsList());
            sidecarFile.setSize(bytes.length);
        } catch (InvalidStreamFileException e) {
            throw e;
        } catch (Exception e) {
            throw new InvalidStreamFileException("Error reading sidecar file " + sidecarFile.getName(), e);
        }
    }
}
