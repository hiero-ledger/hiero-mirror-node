// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.util;

import jakarta.inject.Named;
import lombok.CustomLog;

/**
 * ShutdownHelper helps in shutting down the threads cleanly when JVM is doing down.
 * <p>
 * At some point when the mirror node process is going down, 'stopping' flag will be set to true. Since that point, all
 * threads will have fixed time (say 5 seconds), to stop gracefully. Therefore, long living and heavy lifting threads
 * should regularly probe for this flag.
 */
@CustomLog
@Named
public class ShutdownHelper {

    private static volatile boolean stopping;

    private ShutdownHelper() {
        Runtime.getRuntime().addShutdownHook(new Thread(ShutdownHelper::onExit));
    }

    public static boolean isStopping() {
        return stopping;
    }

    private static void onExit() {
        stopping = true;
        log.info("Shutting down.......waiting 10s for internal processes to stop.");
        try {
            Thread.sleep(10L * 1000L);
        } catch (InterruptedException e) {
            log.warn("Interrupted when waiting for shutdown...", e);
            Thread.currentThread().interrupt();
        }
    }
}
