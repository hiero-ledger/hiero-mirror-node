// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.exception;

import com.hedera.mirror.common.exception.MirrorNodeException;
import java.io.Serial;

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
