// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.grpc.repository;

import java.util.Collection;
import org.springframework.stereotype.Component;

// TODO move to common
/**
 * When running as native image, many SPEL functions do not work correctly.
 * This class provides a way to make sure meethods called in SPEL function correctly
 * */
@Component("cacheHelper")
final class CacheHelper {

    public boolean isNullOrEmpty(Collection<?> value) {
        return value == null || value.isEmpty();
    }
}
