// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.restjava.dto;

import java.math.BigInteger;

public record NetworkSupply(String releasedSupply, Long consensusTimestamp, String totalSupply) {
    public static final BigInteger TOTAL_SUPPLY = new BigInteger("5000000000000000000");

    public static NetworkSupply from(BigInteger unreleasedSupply, Long consensusTimestamp) {
        var releasedSupply = TOTAL_SUPPLY.subtract(unreleasedSupply);
        return new NetworkSupply(releasedSupply.toString(), consensusTimestamp, TOTAL_SUPPLY.toString());
    }
}
