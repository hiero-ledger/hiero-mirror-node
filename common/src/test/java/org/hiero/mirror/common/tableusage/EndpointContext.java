// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.common.tableusage;

import lombok.experimental.UtilityClass;

/**
 * Provides per-thread tracking of the current API endpoint context.
 * <p>
 * This class stores the logical endpoint (e.g., <code>/graphql</code>, <code>/importer</code>) associated with the
 * currently executing request or test, allowing downstream components such as interceptors, instrumentation, or logging
 * to access a consistent identifier for attribution purposes.
 * </p>
 */
@UtilityClass
public class EndpointContext {

    public static final String UNKNOWN_ENDPOINT = "UNKNOWN_ENDPOINT";
    public static final String ENDPOINT = "ENDPOINT";

    private static final ThreadLocal<String> CURRENT_ENDPOINT = new InheritableThreadLocal<>();

    public static String getCurrentEndpoint() {
        return CURRENT_ENDPOINT.get();
    }

    public static void setCurrentEndpoint(final String endpoint) {
        CURRENT_ENDPOINT.set(endpoint);
    }

    public static void clearCurrentEndpoint() {
        CURRENT_ENDPOINT.remove();
    }
}
