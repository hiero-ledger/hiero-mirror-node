// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.exception;

import java.io.Serial;
import org.hiero.mirror.web3.evm.exception.EvmException;

@SuppressWarnings("java:S110")
public class EntityNotFoundException extends EvmException {

    @Serial
    private static final long serialVersionUID = -3067964948484169965L;

    public EntityNotFoundException(String message) {
        super(message);
    }
}
