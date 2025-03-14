// SPDX-License-Identifier: Apache-2.0

package com.hedera.mirror.restjava.common;

import com.google.common.base.Splitter;
import com.hedera.mirror.common.CommonProperties;
import com.hedera.mirror.common.domain.entity.EntityId;
import java.util.List;
import java.util.Objects;
import org.apache.commons.lang3.StringUtils;

public record EntityIdRangeParameter(RangeOperator operator, Long value) implements RangeParameter<Long> {

    public static final EntityIdRangeParameter EMPTY = new EntityIdRangeParameter(null, EntityId.EMPTY);

    public EntityIdRangeParameter(RangeOperator operator, EntityId entityId) {
        this(operator, entityId.getId());
    }

    public static EntityIdRangeParameter valueOf(String entityIdRangeParam) {
        if (StringUtils.isBlank(entityIdRangeParam)) {
            return EMPTY;
        }

        var splitVal = entityIdRangeParam.split(":");
        return switch (splitVal.length) {
            case 1 -> new EntityIdRangeParameter(RangeOperator.EQ, getEntityId(splitVal[0]));
            case 2 -> new EntityIdRangeParameter(RangeOperator.of(splitVal[0]), getEntityId(splitVal[1]));
            default -> throw new IllegalArgumentException(
                    "Invalid range operator %s. Should have format rangeOperator:Id".formatted(entityIdRangeParam));
        };
    }

    private static EntityId getEntityId(String entityId) {
        List<Long> parts = Splitter.on('.')
                .splitToStream(Objects.requireNonNullElse(entityId, ""))
                .map(Long::valueOf)
                .filter(n -> n >= 0)
                .toList();

        if (parts.size() != StringUtils.countMatches(entityId, ".") + 1) {
            throw new IllegalArgumentException("Invalid entity ID: " + entityId);
        }

        var properties = CommonProperties.getInstance();
        return switch (parts.size()) {
            case 1 -> EntityId.of(properties.getShard(), properties.getRealm(), parts.get(0));
            case 2 -> EntityId.of(properties.getShard(), parts.get(0), parts.get(1));
            case 3 -> EntityId.of(parts.get(0), parts.get(1), parts.get(2));
            default -> throw new IllegalArgumentException("Invalid entity ID: " + entityId);
        };
    }
}
