// SPDX-License-Identifier: Apache-2.0

package com.hedera.mirror.importer.reader.record.sidecar;

import com.hedera.mirror.common.domain.transaction.SidecarFile;
import com.hedera.mirror.importer.domain.StreamFileData;
import com.hedera.mirror.importer.exception.InvalidStreamFileException;
import jakarta.inject.Named;
import java.security.DigestInputStream;
import java.security.MessageDigest;

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
