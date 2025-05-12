// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.restjava.common;

import com.hedera.mirror.common.CommonProperties;
import com.hedera.mirror.common.domain.entity.EntityId;
import java.util.regex.Pattern;

public record EntityIdNumParameter(EntityId id) implements EntityIdParameter {

    private static final String ENTITY_ID_REGEX = "^((\\d{1,4})\\.)?((\\d{1,5})\\.)?(\\d{1,12})$";
    private static final Pattern ENTITY_ID_PATTERN = Pattern.compile(ENTITY_ID_REGEX);

    public static EntityIdNumParameter valueOf(String id) {
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

    @Override
    public long shard() {
        return id().getShard();
    }

    @Override
    public long realm() {
        return id().getRealm();
    }
}
