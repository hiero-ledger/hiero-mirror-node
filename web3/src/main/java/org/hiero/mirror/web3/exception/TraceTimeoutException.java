// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.exception;

import java.io.Serial;
import java.util.List;
import lombok.Getter;
import org.hiero.mirror.rest.model.ActionResponse;
import org.jspecify.annotations.Nullable;

@Getter
@SuppressWarnings("java:S110")
public class TraceTimeoutException extends Web3Exception {

    public static final String MESSAGE = "Execution timeout exceeded";

    @Serial
    private static final long serialVersionUID = 1L;

    private final transient List<ActionResponse> actionResponses;

    public TraceTimeoutException(@Nullable final ActionResponse actionResponse) {
        this(actionResponse == null ? List.of() : List.of(actionResponse));
    }

    public TraceTimeoutException(final List<ActionResponse> actionResponses) {
        super(MESSAGE);
        this.actionResponses = List.copyOf(actionResponses);
    }
}
