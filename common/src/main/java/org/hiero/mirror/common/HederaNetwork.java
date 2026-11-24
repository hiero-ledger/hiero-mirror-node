// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.common;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum HederaNetwork {
    DEMO(0),
    MAINNET(1779296400389248896L),
    PREVIEWNET(0),
    TESTNET(1777482002529719510L),
    OTHER(0);

    // The consensus timestamp at which consensus nodes enabled simple fees on the network
    private final long simpleFeesTimestamp;
}
