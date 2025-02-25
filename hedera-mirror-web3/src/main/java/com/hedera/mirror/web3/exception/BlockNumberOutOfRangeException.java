// SPDX-License-Identifier: Apache-2.0

package com.hedera.mirror.web3.exception;

import java.io.Serial;

@SuppressWarnings("java:S110")
public class BlockNumberOutOfRangeException extends InvalidInputException {

    @Serial
    private static final long serialVersionUID = 9163581929850980235L;

    public BlockNumberOutOfRangeException(String message) {
        super(message);
    }
}
