// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.reader.record;

import com.google.common.base.Stopwatch;
import jakarta.inject.Named;
import java.io.DataInputStream;
import java.io.IOException;
import lombok.CustomLog;
import lombok.RequiredArgsConstructor;
import org.hiero.mirror.common.domain.transaction.RecordFile;
import org.hiero.mirror.importer.domain.StreamFileData;
import org.hiero.mirror.importer.exception.InvalidStreamFileException;
import org.hiero.mirror.importer.exception.StreamFileReaderException;
import org.jspecify.annotations.NullMarked;
import org.springframework.context.annotation.Primary;

@CustomLog
@Named
@NullMarked
@Primary
@RequiredArgsConstructor
public class CompositeRecordFileReader implements RecordFileReader {

    private final RecordFileReaderImplV1 version1Reader;
    private final RecordFileReaderImplV2 version2Reader;
    private final RecordFileReaderImplV5 version5Reader;
    private final ProtoRecordFileReader version6Reader;

    @Override
    public RecordFile read(StreamFileData streamFileData) {
        long count = 0;
        Stopwatch stopwatch = Stopwatch.createStarted();
        String filename = streamFileData.getFilename();
        int version = 0;

        try (DataInputStream dis = new DataInputStream(streamFileData.getInputStream())) {
            RecordFileReader reader;
            version = dis.readInt();

            switch (version) {
                case 1:
                    reader = version1Reader;
                    break;
                case 2:
                    reader = version2Reader;
                    break;
                case 5:
                    reader = version5Reader;
                    break;
                case 6:
                    reader = version6Reader;
                    break;
                default:
                    throw new InvalidStreamFileException(
                            String.format("Unsupported record file version %d in file %s", version, filename));
            }

            RecordFile recordFile = reader.read(streamFileData);
            count = recordFile.getCount();
            return recordFile;
        } catch (IOException e) {
            throw new StreamFileReaderException("Error reading record file " + filename, e);
        } finally {
            log.debug(
                    "Read {} items {}successfully from v{} record file {} in {}",
                    count,
                    count != 0 ? "" : "un",
                    version,
                    filename,
                    stopwatch);
        }
    }
}
