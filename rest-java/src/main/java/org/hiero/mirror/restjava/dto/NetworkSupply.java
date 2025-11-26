// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.restjava.dto;

public record NetworkSupply(long releasedSupply, long consensusTimestamp, long totalSupply) {
    public static final long TOTAL_SUPPLY = 5_000_000_000_000_000_000L;
}
