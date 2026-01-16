// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.common.domain.entity;

import java.util.Arrays;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;

@Getter
@RequiredArgsConstructor
public enum EntityType {
    UNKNOWN(0),
    ACCOUNT(1),
    CONTRACT(2),
    FILE(3),
    TOPIC(4),
    TOKEN(5),
    SCHEDULE(6);

    private static final Map<Integer, EntityType> ID_MAP =
            Arrays.stream(values()).collect(Collectors.toUnmodifiableMap(EntityType::getId, Function.identity()));

    private final int id;

    public static EntityType fromId(int id) {
        return ID_MAP.getOrDefault(id, UNKNOWN);
    }

    public String toDisplayString() {
        return StringUtils.capitalize(name().toLowerCase(Locale.ENGLISH));
    }
}
