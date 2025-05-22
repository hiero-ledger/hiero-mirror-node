// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.exception;

import java.io.Serial;
import org.hiero.mirror.common.exception.MirrorNodeException;

public class RateLimitException extends MirrorNodeException {

    @Serial
    private static final long serialVersionUID = 1607151578137574468L;

    public RateLimitException(String message) {
        super(message);
    }
}
