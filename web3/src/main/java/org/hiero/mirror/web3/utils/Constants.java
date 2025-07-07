// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.utils;

import lombok.experimental.UtilityClass;

@UtilityClass
public class Constants {
    public static final String MODULARIZED_HEADER = "Is-Modularized";
    public static final String CALL_URI = "/api/v1/contracts/call";
    public static final String OPCODES_URI = "/api/v1/contracts/results/{transactionIdOrHash}/opcodes";
}
