// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.common.endpoint;

/**
 * Provides per-thread tracking of the current API endpoint context.
 * <p>
 * This class stores the logical endpoint (e.g., <code>/graphql</code>, <code>/importer</code>) associated
 * with the currently executing request or test, allowing downstream components such as interceptors,
 * instrumentation, or logging to access a consistent identifier for attribution purposes.
 * </p>
 */
public final class EndpointContext {

    private static final ThreadLocal<String> CURRENT_ENDPOINT = new InheritableThreadLocal<>();

    public static void setCurrentEndpoint(final String endpoint) {
        CURRENT_ENDPOINT.set(endpoint);
    }

    public static String getCurrentEndpoint() {
        return CURRENT_ENDPOINT.get();
    }

    public static void clearCurrentEndpoint() {
        CURRENT_ENDPOINT.remove();
    }
}
