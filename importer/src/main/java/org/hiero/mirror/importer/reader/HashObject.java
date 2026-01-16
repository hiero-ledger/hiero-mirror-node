// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.reader;

import java.io.IOException;
import lombok.EqualsAndHashCode;
import lombok.Value;
import org.hiero.mirror.common.domain.DigestAlgorithm;
import org.hiero.mirror.importer.exception.InvalidStreamFileException;

@EqualsAndHashCode(callSuper = true)
@Value
public class HashObject extends AbstractStreamObject {

    int digestType;
    byte[] hash;

    public HashObject(ValidatedDataInputStream vdis, String sectionName, DigestAlgorithm digestAlgorithm) {
        super(vdis);

        try {
            digestType = vdis.readInt(digestAlgorithm.getType(), sectionName, "hash digest type");
            int hashLength = digestAlgorithm.getSize();
            hash = vdis.readLengthAndBytes(hashLength, hashLength, false, sectionName, "hash");
        } catch (IOException e) {
            throw new InvalidStreamFileException(e);
        }
    }

    public HashObject(ValidatedDataInputStream dis, DigestAlgorithm digestAlgorithm) {
        this(dis, null, digestAlgorithm);
    }

    protected HashObject(long classId, int classVersion, int digestType, byte[] hash) {
        super(classId, classVersion);
        this.digestType = digestType;
        this.hash = hash;
    }
}
