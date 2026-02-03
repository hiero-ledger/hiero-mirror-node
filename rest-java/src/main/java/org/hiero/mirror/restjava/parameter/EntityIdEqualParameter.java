// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.restjava.parameter;

import com.google.common.base.Splitter;
import java.util.List;
import java.util.Objects;
import org.apache.commons.lang3.StringUtils;
import org.hiero.mirror.common.CommonProperties;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.hiero.mirror.restjava.common.RangeOperator;

/**
 * Entity ID parameter that only accepts equality values (no range operators like gt:, lt:).
 * Accepts formats: "100", "0.0.100", "eq:200", "eq:0.0.200"
 */
public record EntityIdEqualParameter(EntityId entityId) {

    public static final EntityIdEqualParameter EMPTY = new EntityIdEqualParameter(EntityId.EMPTY);

    public static EntityIdEqualParameter valueOf(String entityIdParam) {
        if (StringUtils.isBlank(entityIdParam)) {
            return EMPTY;
        }

        var splitVal = entityIdParam.split(":");
        return switch (splitVal.length) {
            case 1 -> {
                // Simple format: "100" or "0.0.100"
                yield new EntityIdEqualParameter(parseEntityId(splitVal[0]));
            }
            case 2 -> {
                // Format with operator: "eq:100" or "eq:0.0.100"
                var operator = RangeOperator.of(splitVal[0]);
                if (operator != RangeOperator.EQ) {
                    throw new IllegalArgumentException(
                            "Only equality operator 'eq:' is allowed for file.id parameter. Range operators like 'gt:', 'lt:', etc. are not supported.");
                }
                yield new EntityIdEqualParameter(parseEntityId(splitVal[1]));
            }
            default -> throw new IllegalArgumentException("Invalid entity ID format. Should be 'ID' or 'eq:ID'");
        };
    }

    private static EntityId parseEntityId(String entityId) {
        List<Long> parts = Splitter.on('.')
                .splitToStream(Objects.requireNonNullElse(entityId, ""))
                .map(Long::valueOf)
                .filter(n -> n >= 0)
                .toList();

        if (parts.size() != StringUtils.countMatches(entityId, ".") + 1) {
            throw new IllegalArgumentException("Invalid entity ID");
        }

        var properties = CommonProperties.getInstance();
        return switch (parts.size()) {
            case 1 -> EntityId.of(properties.getShard(), properties.getRealm(), parts.get(0));
            case 2 -> EntityId.of(properties.getShard(), parts.get(0), parts.get(1));
            case 3 -> EntityId.of(parts.get(0), parts.get(1), parts.get(2));
            default -> throw new IllegalArgumentException("Invalid entity ID");
        };
    }
}
