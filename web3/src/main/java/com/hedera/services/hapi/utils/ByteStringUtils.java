// SPDX-License-Identifier: Apache-2.0

package com.hedera.services.hapi.utils;

import com.google.protobuf.ByteString;
import com.google.protobuf.UnsafeByteOperations;
import edu.umd.cs.findbugs.annotations.NonNull;

/** Protobuf related utilities shared by client and server. */
public final class ByteStringUtils {

    private ByteStringUtils() {
        throw new UnsupportedOperationException("Utility Class");
    }

    public static ByteString wrapUnsafely(@NonNull final byte[] bytes) {
        return UnsafeByteOperations.unsafeWrap(bytes);
    }
}
