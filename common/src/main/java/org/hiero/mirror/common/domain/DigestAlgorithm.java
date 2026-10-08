// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.common.domain;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Getter;
import org.apache.commons.codec.binary.Hex;
import org.apache.commons.lang3.StringUtils;

@Getter
@JsonFormat(shape = JsonFormat.Shape.NUMBER)
public enum DigestAlgorithm {
    SHA_384("SHA-384", 48, 0x58ff811b),
    // The type for SHA-256 is from the consensus node repo, not in use by the mirror node. Added only for consistency
    SHA_256("SHA-256", 32, 0x1c15d3fb);

    private final String name;
    private final int size;
    private final int type; // as defined in the stream file v5 format document
    private final String emptyHash;

    DigestAlgorithm(String name, int size, int type) {
        this.name = name;
        this.size = size;
        this.type = type;
        this.emptyHash = Hex.encodeHexString(new byte[size]);
    }

    public boolean isHashEmpty(String hash) {
        return StringUtils.isEmpty(hash) || hash.equals(emptyHash);
    }
}
