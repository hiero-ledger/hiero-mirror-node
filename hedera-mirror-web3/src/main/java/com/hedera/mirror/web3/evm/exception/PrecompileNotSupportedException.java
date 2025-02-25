// SPDX-License-Identifier: Apache-2.0

package com.hedera.mirror.web3.evm.exception;

@SuppressWarnings("java:S110")
public class PrecompileNotSupportedException extends EvmException {

    public PrecompileNotSupportedException(String message) {
        super(message);
    }
}
