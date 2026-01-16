// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.common.exception;

import java.io.Serial;

public abstract class MirrorNodeException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 8757494818600695310L;

    protected MirrorNodeException(String message) {
        super(message);
    }

    protected MirrorNodeException(Throwable throwable) {
        super(throwable);
    }

    protected MirrorNodeException(String message, Throwable throwable) {
        super(message, throwable);
    }
}
