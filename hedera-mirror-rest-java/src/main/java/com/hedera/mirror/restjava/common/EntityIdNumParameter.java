// SPDX-License-Identifier: Apache-2.0

package com.hedera.mirror.restjava.common;

import com.hedera.mirror.common.domain.entity.EntityId;
import java.util.regex.Pattern;

public record EntityIdNumParameter(EntityId id) implements EntityIdParameter {

    public static final String ENTITY_ID_REGEX = "^((\\d{1,5})\\.)?((\\d{1,5})\\.)?(\\d{1,10})$";
    public static final Pattern ENTITY_ID_PATTERN = Pattern.compile(ENTITY_ID_REGEX);

    public static EntityIdNumParameter valueOf(String id) {
        var matcher = ENTITY_ID_PATTERN.matcher(id);

        if (!matcher.matches()) {
            return null;
        }

        var properties = PROPERTIES.get();
        long shard = properties.getShard();
        long realm = properties.getRealm();
        String realmString;

        if ((realmString = matcher.group(4)) != null) {
            realm = Long.parseLong(realmString);
            shard = Long.parseLong(matcher.group(2));
        } else if ((realmString = matcher.group(2)) != null) {
            realm = Long.parseLong(realmString);
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
