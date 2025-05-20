// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.exception;

import java.io.Serial;

@SuppressWarnings("java:S110")
public class InvalidFileException extends Web3Exception {

    @Serial
    private static final long serialVersionUID = -595255800032756525L;

    public InvalidFileException(Throwable throwable) {
        super(throwable);
    }
}
