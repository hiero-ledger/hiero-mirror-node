// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.common.util;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes;
import org.jspecify.annotations.NullMarked;

/**
 * When running as native image, many SPEL functions do not work correctly.
 * This class provides a way to make sure methods called in SPEL function correctly
 **/
@NullMarked
public final class SpelHelper {

    public boolean isNullOrEmpty(Object value) {
        if (value == null) {
            return true;
        } else if (value instanceof Optional<?> optional) {
            return optional.isEmpty();
        } else if (value instanceof Collection<?> collection) {
            return collection.isEmpty();
        } else if (value instanceof Map<?, ?> map) {
            return map.isEmpty();
        }

        return false;
    }

    public Bytes getCacheKey(byte[] value) {
        return value == null ? Bytes.EMPTY : Bytes.wrap(value);
    }

    // A List's equals/hashCode compares its elements positionally, so this can't collide the way concatenating the
    // two arrays into a single Bytes could if their lengths varied.
    public List<Bytes> getCacheKey(byte[] first, byte[] second) {
        return List.of(getCacheKey(first), getCacheKey(second));
    }
}
