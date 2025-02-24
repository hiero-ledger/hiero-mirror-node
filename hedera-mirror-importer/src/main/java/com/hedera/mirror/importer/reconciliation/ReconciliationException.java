// SPDX-License-Identifier: Apache-2.0

package com.hedera.mirror.importer.reconciliation;

import com.hedera.mirror.common.domain.job.ReconciliationStatus;
import com.hedera.mirror.importer.exception.ImporterException;
import lombok.Getter;

@Getter
@SuppressWarnings("java:S110")
class ReconciliationException extends ImporterException {

    private static final long serialVersionUID = -1037307345641558766L;

    private final ReconciliationStatus status;

    ReconciliationException(ReconciliationStatus status, Object... arguments) {
        super(String.format(status.getMessage(), arguments));
        this.status = status;
    }
}
