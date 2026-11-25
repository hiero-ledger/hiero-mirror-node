// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.exception;

import java.io.Serial;
import java.util.List;
import lombok.Getter;
import org.hiero.mirror.rest.model.ActionResponse;

@Getter
@SuppressWarnings("java:S110")
public class TraceTimeoutException extends Web3Exception {

    public static final String MESSAGE = "Execution timeout exceeded";

    @Serial
    private static final long serialVersionUID = 1L;

    private final transient List<ActionResponse> actionResponses;

    /** Keeps the collected actions as they are. Nothing adds to them once the trace has timed out. */
    public TraceTimeoutException(final List<ActionResponse> actionResponses) {
        super(MESSAGE);
        this.actionResponses = actionResponses;
    }
}
