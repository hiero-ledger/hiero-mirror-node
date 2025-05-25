// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.exception;

import java.io.Serial;

public class ThrottleException extends Web3Exception {

    @Serial
    private static final long serialVersionUID = -6398771245848012115L;

    public ThrottleException(String message) {
        super(message);
    }
}
