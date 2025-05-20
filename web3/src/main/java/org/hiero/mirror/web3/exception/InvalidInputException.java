// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.exception;

import java.io.Serial;

@SuppressWarnings("java:S110")
public abstract class InvalidInputException extends Web3Exception {

    @Serial
    private static final long serialVersionUID = -5018557225908411121L;

    protected InvalidInputException(String message) {
        super(message);
    }
}
