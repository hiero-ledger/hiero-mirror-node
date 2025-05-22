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
public enum TokenTypeEnum {
    FUNGIBLE_COMMON(0),
    NON_FUNGIBLE_UNIQUE(1);

    private final int id;

    private static final Map<Integer, TokenTypeEnum> ID_MAP =
            Arrays.stream(values()).collect(Collectors.toUnmodifiableMap(TokenTypeEnum::getId, Function.identity()));

    public static TokenTypeEnum fromId(int id) {
        return ID_MAP.getOrDefault(id, FUNGIBLE_COMMON);
    }
}
