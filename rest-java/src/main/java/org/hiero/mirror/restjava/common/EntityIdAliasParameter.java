// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.restjava.common;

import com.google.common.io.BaseEncoding;
import java.util.regex.Pattern;
import org.hiero.mirror.common.CommonProperties;
import org.jspecify.annotations.Nullable;

@SuppressWarnings("java:S6218")
public record EntityIdAliasParameter(long shard, long realm, byte[] alias) implements EntityIdParameter {

    public static final String ALIAS_REGEX = "^((\\d{1,5})\\.)?((\\d{1,5})\\.)?([A-Z2-7]{40,70})$";
    public static final Pattern ALIAS_PATTERN = Pattern.compile(ALIAS_REGEX);
    private static final BaseEncoding BASE32 = BaseEncoding.base32().omitPadding();

    static @Nullable EntityIdAliasParameter valueOfNullable(String id) {
        var aliasMatcher = ALIAS_PATTERN.matcher(id);

        if (!aliasMatcher.matches()) {
            return null;
        }

        var properties = CommonProperties.getInstance();
        long shard = properties.getShard();
        long realm = properties.getRealm();
        String realmString;

        if ((realmString = aliasMatcher.group(4)) != null) {
            realm = Long.parseLong(realmString);
            shard = Long.parseLong(aliasMatcher.group(2));
        } else if ((realmString = aliasMatcher.group(2)) != null) {
            realm = Long.parseLong(realmString);
        }

        var alias = BASE32.decode(aliasMatcher.group(5));
        return new EntityIdAliasParameter(shard, realm, alias);
    }

    public static EntityIdAliasParameter valueOf(String id) {
        final var result = valueOfNullable(id);

        if (result == null) {
            throw new IllegalArgumentException("Invalid alias");
        }

        return result;
    }
}
