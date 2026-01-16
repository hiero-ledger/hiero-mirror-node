// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.evm.exception;

import java.io.Serial;
import org.hiero.mirror.common.exception.MirrorNodeException;

@SuppressWarnings("java:S110")
public abstract class EvmException extends MirrorNodeException {

    @Serial
    private static final long serialVersionUID = 4884858477272676840L;

    protected EvmException(String message) {
        super(message);
    }
}
