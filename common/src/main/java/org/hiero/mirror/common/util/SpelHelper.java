// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.common.util;

import java.util.Collection;
import org.apache.tuweni.bytes.Bytes;
import org.jspecify.annotations.NullMarked;

/**
 * When running as native image, many SPEL functions do not work correctly.
 * This class provides a way to make sure methods called in SPEL function correctly
 **/
@NullMarked
public final class SpelHelper {

    public boolean isNullOrEmpty(Collection<?> value) {
        return value == null || value.isEmpty();
    }

    public Bytes getCacheKey(byte[] value) {
        return value == null ? Bytes.EMPTY : Bytes.wrap(value);
    }
}
