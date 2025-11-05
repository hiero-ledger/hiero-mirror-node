// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.restjava.common;

import java.util.regex.Pattern;
import org.hiero.mirror.common.CommonProperties;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.jspecify.annotations.Nullable;

public record EntityIdNumParameter(EntityId id) implements EntityIdParameter {

    private static final String ENTITY_ID_REGEX = "^((\\d{1,4})\\.)?((\\d{1,5})\\.)?(\\d{1,12})$";
    private static final Pattern ENTITY_ID_PATTERN = Pattern.compile(ENTITY_ID_REGEX);

    static @Nullable EntityIdNumParameter valueOfNullable(String id) {
        var matcher = ENTITY_ID_PATTERN.matcher(id);

        if (!matcher.matches()) {
            return null;
        }

        var properties = CommonProperties.getInstance();
        long shard = properties.getShard();
        long realm = properties.getRealm();
        var secondGroup = matcher.group(2);
        var fourthGroup = matcher.group(4);

        if (secondGroup != null && fourthGroup != null) {
            shard = Long.parseLong(secondGroup);
            realm = Long.parseLong(fourthGroup);
        } else if (secondGroup != null || fourthGroup != null) {
            realm = Long.parseLong(secondGroup != null ? secondGroup : fourthGroup);
        }

        var num = Long.parseLong(matcher.group(5));
        return new EntityIdNumParameter(EntityId.of(shard, realm, num));
    }

    public static EntityIdNumParameter valueOf(String id) {
        final var result = valueOfNullable(id);

        if (result == null) {
            throw new IllegalArgumentException("Invalid entity ID");
        }

        return result;
    }

    @Override
    public long shard() {
        return id().getShard();
    }

    @Override
    public long realm() {
        return id().getRealm();
    }
}
