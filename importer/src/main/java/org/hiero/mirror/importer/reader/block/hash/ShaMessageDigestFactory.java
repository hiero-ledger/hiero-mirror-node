// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.reader.block.hash;

import static org.hiero.mirror.common.util.DomainUtils.createSha256Digest;
import static org.hiero.mirror.common.util.DomainUtils.createSha384Digest;

import java.security.MessageDigest;
import org.hiero.mirror.common.domain.DigestAlgorithm;

final class ShaMessageDigestFactory {

    static final int SHA_256_SIZE = 32;
    static final int SHA_384_SIZE = 48;

    static MessageDigest createMessageDigest(final int digestSize) {
        if (digestSize == DigestAlgorithm.SHA_256.getSize()) {
            return createSha256Digest();
        } else if (digestSize == DigestAlgorithm.SHA_384.getSize()) {
            return createSha384Digest();
        }

        throw new IllegalArgumentException("Unsupported digest size " + digestSize);
    }
}
