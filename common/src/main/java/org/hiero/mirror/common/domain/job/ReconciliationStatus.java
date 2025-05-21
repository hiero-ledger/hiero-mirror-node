// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.common.domain.job;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ReconciliationStatus {
    UNKNOWN(""),
    RUNNING(""),
    SUCCESS(""),
    FAILURE_CRYPTO_TRANSFERS("Crypto transfers did not reconcile in range (%d, %d]: %s"),
    FAILURE_FIFTY_BILLION("Balance file %s does not add up to 50B: %d"),
    FAILURE_TOKEN_TRANSFERS("Token transfers did not reconcile in range (%d, %d]: %s"),
    FAILURE_UNKNOWN("Unknown error");

    private final String message;
}
