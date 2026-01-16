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
public enum TokenPauseStatusEnum {
    NOT_APPLICABLE(0),

    PAUSED(1),

    UNPAUSED(2);

    private final int id;

    private static final Map<Integer, TokenPauseStatusEnum> ID_MAP = Arrays.stream(values())
            .collect(Collectors.toUnmodifiableMap(TokenPauseStatusEnum::getId, Function.identity()));

    public static TokenPauseStatusEnum fromId(int id) {
        return ID_MAP.getOrDefault(id, NOT_APPLICABLE);
    }
}
