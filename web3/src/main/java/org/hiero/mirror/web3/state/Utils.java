// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.state;

import com.hedera.hapi.node.base.ContractID;
import com.hedera.hapi.node.base.FileID;
import com.hedera.hapi.node.base.Key;
import com.hedera.hapi.node.base.KeyList;
import com.hedera.hapi.node.base.Timestamp;
import com.hedera.pbj.runtime.ParseException;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import java.time.Instant;
import lombok.experimental.UtilityClass;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.hiero.mirror.common.util.DomainUtils;
import org.jspecify.annotations.NonNull;

@UtilityClass
public class Utils {

    public static final long DEFAULT_AUTO_RENEW_PERIOD = 7776000L;
    public static final int EVM_ADDRESS_LEN = 20;
    public static final Key EMPTY_KEY_LIST =
            Key.newBuilder().keyList(KeyList.DEFAULT).build();
    public static final Key DEFAULT_KEY = Key.newBuilder()
            .keyList(KeyList.newBuilder()
                    .keys(Key.newBuilder()
                            .ecdsaSecp256k1(Bytes.wrap(new byte[] {
                                2, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
                                0, 0, 0, 0,
                            }))
                            .build())
                    .build())
            .build();

    public static Key parseKey(final byte[] keyBytes) {
        try {
            if (keyBytes != null && keyBytes.length > 0) {
                return Key.PROTOBUF.parse(Bytes.wrap(keyBytes));
            }
        } catch (final ParseException e) {
            return null;
        }

        return null;
    }

    /**
     * Converts a timestamp in nanoseconds to a PBJ Timestamp object.
     *
     * @param timestamp The timestamp in nanoseconds.
     * @return The PBJ Timestamp object.
     */
    public static Timestamp convertToTimestamp(final long timestamp) {
        var instant = Instant.ofEpochSecond(0, timestamp);
        return new Timestamp(instant.getEpochSecond(), instant.getNano());
    }

    public static long getCurrentTimestamp() {
        final var now = Instant.now();
        return DomainUtils.convertToNanos(now.getEpochSecond(), now.getNano());
    }

    /**
     * Returns the normalized ({@code 0x}-prefixed, lowercase) EVM address for the given {@link ContractID}.
     */
    public static String contractIdToEvmAddressHex(@NonNull ContractID contractID) {
        if (contractID.hasEvmAddress() && contractID.evmAddress().length() == 20) {
            return "0x" + contractID.evmAddress().toHex().toLowerCase();
        } else if (contractID.hasContractNum()) {
            return "0x" + String.format("%040x", contractID.contractNum());
        }
        return null;
    }

    /**
     * Normalizes a hex-encoded storage slot key to a 64-character lowercase hex string (32 bytes, left-padded, no {@code 0x} prefix).
     */
    public static String normalizeStorageSlot(@NonNull String hexSlot) {
        var bytes = hexStringToBytes(hexSlot);
        var padded = DomainUtils.leftPadBytes(bytes, 32);
        var sb = new StringBuilder(64);
        for (byte b : padded) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    /** Decodes a hex string (with or without {@code 0x} prefix) to a byte array. */
    public static byte[] hexStringToBytes(@NonNull String hex) {
        var h = hex.startsWith("0x") || hex.startsWith("0X") ? hex.substring(2) : hex;
        if (h.isEmpty()) {
            return new byte[0];
        }
        var padded = h.length() % 2 == 0 ? h : "0" + h;
        var bytes = new byte[padded.length() / 2];
        for (var i = 0; i < bytes.length; i++) {
            bytes[i] = (byte) Integer.parseInt(padded, i * 2, i * 2 + 2, 16);
        }
        return bytes;
    }

    public static FileID toFileID(final EntityId entityId) {
        return FileID.newBuilder()
                .shardNum(entityId.getShard())
                .realmNum(entityId.getRealm())
                .fileNum(entityId.getNum())
                .build();
    }
}
