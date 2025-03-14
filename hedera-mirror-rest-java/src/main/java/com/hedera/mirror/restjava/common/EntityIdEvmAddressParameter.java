// SPDX-License-Identifier: Apache-2.0

package com.hedera.mirror.restjava.common;

import com.hedera.mirror.common.CommonProperties;
import java.util.regex.Pattern;
import lombok.SneakyThrows;
import org.apache.commons.codec.DecoderException;
import org.apache.commons.codec.binary.Hex;

@SuppressWarnings("java:S6218")
public record EntityIdEvmAddressParameter(long shard, long realm, byte[] evmAddress) implements EntityIdParameter {

    public static final String EVM_ADDRESS_REGEX = "^((\\d{1,5})\\.)?((\\d{1,5})\\.)?(0x)?([A-Fa-f0-9]{40})$";
    public static final Pattern EVM_ADDRESS_PATTERN = Pattern.compile(EVM_ADDRESS_REGEX);

    @SneakyThrows(DecoderException.class)
    public static EntityIdEvmAddressParameter valueOf(String id) {
        var evmMatcher = EVM_ADDRESS_PATTERN.matcher(id);

        if (!evmMatcher.matches()) {
            return null;
        }

        var properties = CommonProperties.getInstance();
        long shard = properties.getShard();
        long realm = properties.getRealm();
        String realmString;

        if ((realmString = evmMatcher.group(4)) != null) {
            realm = Long.parseLong(realmString);
            shard = Long.parseLong(evmMatcher.group(2));
        } else if ((realmString = evmMatcher.group(2)) != null) {
            realm = Long.parseLong(realmString);
        }

        var evmAddress = Hex.decodeHex(evmMatcher.group(6));
        return new EntityIdEvmAddressParameter(shard, realm, evmAddress);
    }
}
