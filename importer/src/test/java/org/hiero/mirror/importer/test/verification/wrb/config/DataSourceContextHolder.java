// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.test.verification.wrb.config;

public final class DataSourceContextHolder {

    public static final String RECORDSTREAM = "recordStream";
    public static final String WRB = "wrb";

    private static final ThreadLocal<String> CONTEXT = new ThreadLocal<>();

    public static void set(String datasource) {
        CONTEXT.set(datasource);
    }

    public static String get() {
        return CONTEXT.get();
    }

    public static void clear() {
        CONTEXT.remove();
    }
}
