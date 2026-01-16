// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.evm.exception;

import java.io.Serial;

@SuppressWarnings("java:S110")
public class MissingResultException extends EvmException {

    @Serial
    private static final long serialVersionUID = -3598980296959473266L;

    public MissingResultException(String message) {
        super(message);
    }
}
