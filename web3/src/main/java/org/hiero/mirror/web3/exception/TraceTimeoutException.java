// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.exception;

import java.io.Serial;
import lombok.Getter;
import org.hiero.mirror.rest.model.TracerResponse;
import org.jspecify.annotations.Nullable;

@Getter
@SuppressWarnings("java:S110")
public class TraceTimeoutException extends Web3Exception {

    public static final String MESSAGE = "Execution timeout exceeded";

    @Serial
    private static final long serialVersionUID = 1L;

    @Nullable
    private final transient TracerResponse tracerResponse;

    public TraceTimeoutException() {
        this(null);
    }

    public TraceTimeoutException(@Nullable final TracerResponse tracerResponse) {
        super(MESSAGE);
        this.tracerResponse = tracerResponse;
    }
}
