// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.test.e2e.acceptance.client;

import com.hedera.hashgraph.sdk.PrecheckStatusException;
import com.hedera.hashgraph.sdk.Status;
import java.io.Serial;
import org.springframework.core.retry.RetryException;

public final class NetworkException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 4080400972797042474L;

    private final Status transactionStatus;

    public NetworkException(final String message) {
        super(message);
        transactionStatus = null;
    }

    public NetworkException(final String message, final Throwable cause) {
        super(message, cause);
        transactionStatus = null;
    }

    public NetworkException(final String message, final Status transactionStatus) {
        super(message);
        this.transactionStatus = transactionStatus;
    }

    public Status getTransactionStatus() {
        if (transactionStatus != null) {
            return transactionStatus;
        }

        final var cause = getCause();
        if (cause instanceof RetryException retryException
                && retryException.getCause() instanceof PrecheckStatusException pse) {
            return pse.status;
        }

        return null;
    }
}
