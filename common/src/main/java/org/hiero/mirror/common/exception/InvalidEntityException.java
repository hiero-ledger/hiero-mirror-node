// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.common.exception;

import java.io.Serial;

public class InvalidEntityException extends MirrorNodeException {

    @Serial
    private static final long serialVersionUID = 1988238764876411857L;

    public InvalidEntityException(String message) {
        super(message);
    }
}
