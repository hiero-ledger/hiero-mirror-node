// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.restjava.parameter;

import java.util.regex.Pattern;
import lombok.SneakyThrows;
import org.apache.commons.codec.DecoderException;
import org.apache.commons.codec.binary.Hex;
import org.hiero.mirror.common.CommonProperties;
import org.jspecify.annotations.Nullable;

@SuppressWarnings("java:S6218")
public record EntityIdEvmAddressParameter(long shard, long realm, byte[] evmAddress) implements EntityIdParameter {

    public static final String EVM_ADDRESS_REGEX = "^(((\\d{1,5})\\.)?((\\d{1,5})\\.)?|0x)?([A-Fa-f0-9]{40})$";
    public static final Pattern EVM_ADDRESS_PATTERN = Pattern.compile(EVM_ADDRESS_REGEX);

    @SneakyThrows(DecoderException.class)
    static @Nullable EntityIdEvmAddressParameter valueOfNullable(String id) {
        var evmMatcher = EVM_ADDRESS_PATTERN.matcher(id);

        if (!evmMatcher.matches()) {
            return null;
        }

        var properties = CommonProperties.getInstance();
        long shard = properties.getShard();
        long realm = properties.getRealm();
        String realmString;

        if ((realmString = evmMatcher.group(5)) != null) {
            realm = Long.parseLong(realmString);
            shard = Long.parseLong(evmMatcher.group(3));
        } else if ((realmString = evmMatcher.group(3)) != null) {
            realm = Long.parseLong(realmString);
        }

        var evmAddress = Hex.decodeHex(evmMatcher.group(6));
        return new EntityIdEvmAddressParameter(shard, realm, evmAddress);
    }

    public static EntityIdEvmAddressParameter valueOf(String id) {
        final var result = valueOfNullable(id);

        if (result == null) {
            throw new IllegalArgumentException("Invalid EVM Address");
        }

        return result;
    }
}
