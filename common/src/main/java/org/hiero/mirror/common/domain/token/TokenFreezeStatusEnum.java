// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.common.domain.token;

import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Arrays;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum TokenFreezeStatusEnum {
    NOT_APPLICABLE(0),
    FROZEN(1),
    UNFROZEN(2);

    private final int id;

    private static final Map<Integer, TokenFreezeStatusEnum> ID_MAP =
            Arrays.stream(values()).collect(Collectors.toUnmodifiableMap(e -> e.id, Function.identity()));

    public static TokenFreezeStatusEnum fromId(int id) {
        return ID_MAP.getOrDefault(id, NOT_APPLICABLE);
    }

    /** Ordinal stored in {@code smallint} database columns. */
    public int getDbId() {
        return id;
    }

    @JsonValue
    public String getId() {
        return String.valueOf(id);
    }
}
