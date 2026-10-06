// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.reader.block.hash;

import static org.hiero.mirror.common.util.DomainUtils.createSha256Digest;
import static org.hiero.mirror.common.util.DomainUtils.createSha384Digest;

import java.security.MessageDigest;

final class ShaMessageDigestFactory {

    static final int SHA_256_SIZE = 32;
    static final int SHA_384_SIZE = 48;

    static MessageDigest createMessageDigest(final int digestSize) {
        return switch (digestSize) {
            case SHA_256_SIZE -> createSha256Digest();
            case SHA_384_SIZE -> createSha384Digest();
            default -> throw new IllegalArgumentException("Unsupported digest size " + digestSize);
        };
    }
}
