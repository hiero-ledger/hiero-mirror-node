// SPDX-License-Identifier: Apache-2.0

package com.hedera.mirror.web3.evm.exception;

import com.hedera.mirror.common.exception.MirrorNodeException;
import java.io.Serial;

@SuppressWarnings("java:S110")
public abstract class EvmException extends MirrorNodeException {

    @Serial
    private static final long serialVersionUID = 4884858477272676840L;

    protected EvmException(String message) {
        super(message);
    }

    protected EvmException(String message, Throwable throwable) {
        super(message, throwable);
    }
}
