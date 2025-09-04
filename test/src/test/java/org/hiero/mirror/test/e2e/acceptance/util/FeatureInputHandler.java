// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.test.e2e.acceptance.util;

import java.time.DateTimeException;
import java.time.Instant;

public class FeatureInputHandler {
    public static Instant messageQueryDateStringToInstant(String date) {
        return messageQueryDateStringToInstant(date, Instant.now());
    }

    public static Instant messageQueryDateStringToInstant(String date, Instant referenceInstant) {
        Instant refDate;
        try {
            refDate = Instant.parse(date);
        } catch (DateTimeException dtex) {
            refDate = referenceInstant.plusSeconds(Long.parseLong(date));
        }

        return refDate;
    }
}
