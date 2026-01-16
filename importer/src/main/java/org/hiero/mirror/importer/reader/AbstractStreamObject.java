// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.reader;

import java.io.DataInputStream;
import java.io.IOException;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.hiero.mirror.importer.exception.InvalidStreamFileException;

@Data
@RequiredArgsConstructor
public abstract class AbstractStreamObject {

    private final long classId;
    private final int classVersion;

    protected AbstractStreamObject(DataInputStream dis) {
        try {
            classId = dis.readLong();
            classVersion = dis.readInt();
        } catch (IOException e) {
            throw new InvalidStreamFileException(e);
        }
    }
}
