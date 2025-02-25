// SPDX-License-Identifier: Apache-2.0

package com.hedera.mirror.common.exception;

public class InvalidEntityException extends MirrorNodeException {

    private static final long serialVersionUID = 1988238764876411857L;

    public InvalidEntityException(String message) {
        super(message);
    }
}
