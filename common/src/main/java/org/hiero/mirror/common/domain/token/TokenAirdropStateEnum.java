// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.common.domain.token;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum TokenAirdropStateEnum {
    CANCELLED(0),
    CLAIMED(1),
    PENDING(2);

    private final int id;
}
