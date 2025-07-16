// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.graphql.util;

import com.google.common.base.Splitter;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.function.Function;
import lombok.experimental.UtilityClass;
import org.apache.commons.codec.DecoderException;
import org.apache.commons.codec.binary.Base32;
import org.apache.commons.codec.binary.Hex;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.Strings;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.hiero.mirror.graphql.viewmodel.EntityIdInput;
import org.hiero.mirror.graphql.viewmodel.HbarUnit;
import org.hiero.mirror.graphql.viewmodel.Node;

@UtilityClass
public class GraphQlUtils {

    private static final Base32 BASE32 = new Base32();
    private static final String HEX_PREFIX = "0x";
    private static final Splitter SPLITTER = Splitter.on(':');

    public static Long convertCurrency(HbarUnit unit, Long tinybars) {
        if (tinybars == null) {
            return null;
        }

        if (unit == null) {
            return tinybars;
        }

        return switch (unit) {
            case TINYBAR -> tinybars;
            case MICROBAR -> tinybars / 100L;
            case MILIBAR -> tinybars / 100_000L;
            case HBAR -> tinybars / 100_000_000L;
            case KILOBAR -> tinybars / 100_000_000_000L;
            case MEGABAR -> tinybars / 100_000_000_000_000L;
            case GIGABAR -> tinybars / 100_000_000_000_000_000L;
        };
    }

    public static <T> T getId(Node node, Function<List<String>, T> converter) {
        var id = new String(Base64.getDecoder().decode(node.getId()), StandardCharsets.UTF_8);
        var parts = SPLITTER.splitToList(id);

        if (parts.size() <= 1) {
            throw new IllegalArgumentException("Invalid Node.id");
        }

        var clazz = node.getClass();
        if (!clazz.getSimpleName().equals(parts.get(0))) {
            throw new IllegalArgumentException("Invalid Node.id");
        }

        return converter.apply(parts.subList(1, parts.size()));
    }

    public static EntityId toEntityId(EntityIdInput entityId) {
        return EntityId.of(entityId.getShard(), entityId.getRealm(), entityId.getNum());
    }

    public static void validateOneOf(Object... values) {
        int nonNull = 0;

        for (var value : values) {
            if (value != null) {
                ++nonNull;
            }
        }

        if (nonNull != 1) {
            throw new IllegalArgumentException(
                    "Must provide exactly one input value but " + nonNull + " have been " + "provided");
        }
    }

    public static byte[] decodeBase32(String base32) {
        return BASE32.decode(base32);
    }

    public static byte[] decodeEvmAddress(String evmAddress) {
        if (evmAddress == null) {
            return ArrayUtils.EMPTY_BYTE_ARRAY;
        }

        try {
            evmAddress = Strings.CS.removeStart(evmAddress, HEX_PREFIX);
            return Hex.decodeHex(evmAddress);
        } catch (DecoderException e) {
            throw new IllegalArgumentException("Unable to decode evmAddress: " + evmAddress);
        }
    }
}
