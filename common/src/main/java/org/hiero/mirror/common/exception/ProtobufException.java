// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.common.exception;

import java.io.Serial;

public class ProtobufException extends MirrorNodeException {

    @Serial
    private static final long serialVersionUID = 7681126529461494921L;

    public ProtobufException(String message) {
        super(message);
    }

    public ProtobufException(String message, Throwable throwable) {
        super(message, throwable);
    }
}
