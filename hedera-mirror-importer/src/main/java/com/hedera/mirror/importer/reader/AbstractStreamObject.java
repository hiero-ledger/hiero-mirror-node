// SPDX-License-Identifier: Apache-2.0

package com.hedera.mirror.importer.reader;

import com.hedera.mirror.importer.exception.InvalidStreamFileException;
import java.io.DataInputStream;
import java.io.IOException;
import lombok.Data;
import lombok.RequiredArgsConstructor;

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
