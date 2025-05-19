// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.evm.exception;

import java.io.Serial;

@SuppressWarnings("java:S110")
public class WrongTypeException extends EvmException {
    @Serial
    private static final long serialVersionUID = -6844339452754420145L;

    public WrongTypeException(String message) {
        super(message);
    }
}
