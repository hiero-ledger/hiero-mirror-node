// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.common.domain.token;

import java.util.Arrays;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum TokenSupplyTypeEnum {
    INFINITE(0),
    FINITE(1);

    private final int id;

    private static final Map<Integer, TokenSupplyTypeEnum> ID_MAP = Arrays.stream(values())
            .collect(Collectors.toUnmodifiableMap(TokenSupplyTypeEnum::getId, Function.identity()));

    public static TokenSupplyTypeEnum fromId(int id) {
        return ID_MAP.getOrDefault(id, INFINITE);
    }
}
