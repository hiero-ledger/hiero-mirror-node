// SPDX-License-Identifier: Apache-2.0

package com.hedera.mirror.restjava.exception;

import com.hedera.mirror.common.exception.MirrorNodeException;
import java.io.Serial;

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
