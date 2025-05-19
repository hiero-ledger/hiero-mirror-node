// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.restjava.exception;

import java.io.Serial;
import org.hiero.mirror.common.exception.MirrorNodeException;

abstract class RestJavaException extends MirrorNodeException {

    @Serial
    private static final long serialVersionUID = 3383312779795690341L;

    protected RestJavaException(String message) {
        super(message);
    }

    protected RestJavaException(String message, Throwable throwable) {
        super(message, throwable);
    }
}
