// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.common.util;

import java.util.Collection;
import org.jspecify.annotations.NullMarked;
import org.springframework.stereotype.Component;

/**
 * When running as native image, many SPEL functions do not work correctly.
 * This class provides a way to make sure methods called in SPEL function correctly
 **/
@Component("spelHelper")
@NullMarked
public final class SpelHelper {

    public boolean isNullOrEmpty(Collection<?> value) {
        return value == null || value.isEmpty();
    }
}
