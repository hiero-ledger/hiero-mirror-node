// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.evm.exception;

import java.io.Serial;

@SuppressWarnings("java:S110")
public class ParsingException extends EvmException {

    @Serial
    private static final long serialVersionUID = 8069853495811050775L;

    public ParsingException(String message) {
        super(message);
    }
}
