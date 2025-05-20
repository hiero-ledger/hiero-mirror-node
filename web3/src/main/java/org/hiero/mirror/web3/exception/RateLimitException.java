// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.exception;

import com.hedera.mirror.common.exception.MirrorNodeException;
import java.io.Serial;

public class RateLimitException extends MirrorNodeException {

    @Serial
    private static final long serialVersionUID = 1607151578137574468L;

    public RateLimitException(String message) {
        super(message);
    }
}
