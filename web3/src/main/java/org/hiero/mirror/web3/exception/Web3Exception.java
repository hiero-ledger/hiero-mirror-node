// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.exception;

import java.io.Serial;
import org.hiero.mirror.common.exception.MirrorNodeException;

public abstract class Web3Exception extends MirrorNodeException {

    @Serial
    private static final long serialVersionUID = 2799138943840105243L;

    protected Web3Exception(String message) {
        super(message);
    }

    protected Web3Exception(Throwable throwable) {
        super(throwable);
    }
}
