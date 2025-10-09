// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.utils;

import lombok.experimental.UtilityClass;

@UtilityClass
public class AccountDetector {
    private static final int SYSTEM_ACCOUNT_BOUNDARY = 750;
    private static final int STRICT_SYSTEM_ACCOUNT_BOUNDARY = 999;

    public static boolean isStrictSystem(long accountNum) {
        return accountNum >= 0 && accountNum <= STRICT_SYSTEM_ACCOUNT_BOUNDARY;
    }

    public static boolean isSystem(long accountNum) {
        return accountNum >= 0 && accountNum <= SYSTEM_ACCOUNT_BOUNDARY;
    }
}
