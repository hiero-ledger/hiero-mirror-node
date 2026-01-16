// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.exception;

public class BlockNumberNotFoundException extends InvalidInputException {

    public static final String UNKNOWN_BLOCK_NUMBER = "Unknown block number";

    public BlockNumberNotFoundException() {
        super(UNKNOWN_BLOCK_NUMBER);
    }
}
