// SPDX-License-Identifier: Apache-2.0

package com.hedera.services.txns.util;

import java.util.function.Supplier;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * This is a modified copy of the PRNGLogic class from the hedera-services repository.
 *
 * The main differences from the original version are as follows:
 * - RunningHashLeafSupplier returns the latest RecordFile's running hash.
 * - Removed unused logic.
 */
public class PrngLogic {
    private static final Logger log = LogManager.getLogger(PrngLogic.class);

    public static final byte[] MISSING_BYTES = new byte[0];
    private final Supplier<byte[]> runningHashLeafSupplier;

    public PrngLogic(final Supplier<byte[]> runningHashLeafSupplier) {
        this.runningHashLeafSupplier = runningHashLeafSupplier;
    }

    public final byte[] getLatestRecordRunningHashBytes() {
        final byte[] latestRunningHashBytes;
        latestRunningHashBytes = runningHashLeafSupplier.get();
        if (latestRunningHashBytes == null || isZeroedHash(latestRunningHashBytes)) {
            log.info("No record running hash available to generate random number");
            return MISSING_BYTES;
        }
        return latestRunningHashBytes;
    }

    private boolean isZeroedHash(final byte[] hashBytes) {
        for (final byte b : hashBytes) {
            if (b != 0) {
                return false;
            }
        }
        return true;
    }
}
