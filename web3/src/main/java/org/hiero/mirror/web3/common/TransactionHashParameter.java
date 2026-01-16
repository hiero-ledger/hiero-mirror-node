// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.common;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.tuweni.bytes.Bytes;
import org.springframework.util.StringUtils;

public record TransactionHashParameter(Bytes hash) implements TransactionIdOrHashParameter {

    private static final Pattern ETH_HASH_PATTERN = Pattern.compile("^(0x)?([0-9A-Fa-f]{64})$");

    public static TransactionHashParameter valueOf(String hash) {
        if (!isValidEthHash(hash)) {
            return null;
        }
        return new TransactionHashParameter(Bytes.fromHexString(hash));
    }

    private static boolean isValidEthHash(String hash) {
        if (!StringUtils.hasText(hash)) {
            return false;
        }

        Matcher matcher = ETH_HASH_PATTERN.matcher(hash);
        return matcher.matches();
    }
}
