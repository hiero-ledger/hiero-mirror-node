// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.reader.record.sidecar;

import jakarta.inject.Named;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import org.hiero.mirror.common.domain.transaction.SidecarFile;
import org.hiero.mirror.importer.domain.StreamFileData;
import org.hiero.mirror.importer.exception.InvalidStreamFileException;

@Named
public class SidecarFileReaderImpl implements SidecarFileReader {

    @Override
    public void read(SidecarFile sidecarFile, StreamFileData streamFileData) {
        try (var digestInputStream = new DigestInputStream(
                streamFileData.getInputStream(),
                MessageDigest.getInstance(sidecarFile.getHashAlgorithm().getName()))) {
            var protoSidecarFile = com.hedera.services.stream.proto.SidecarFile.parseFrom(digestInputStream);
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
