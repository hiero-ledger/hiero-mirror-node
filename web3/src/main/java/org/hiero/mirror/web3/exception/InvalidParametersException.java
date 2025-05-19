// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.exception;

import java.io.Serial;

@SuppressWarnings("java:S110")
public class InvalidParametersException extends InvalidInputException {

    @Serial
    private static final long serialVersionUID = -1728472056521963582L;

    public InvalidParametersException(String message) {
        super(message);
    }
}
