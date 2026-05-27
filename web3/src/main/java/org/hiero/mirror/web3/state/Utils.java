// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.state;

import static org.hiero.mirror.web3.validation.HexValidator.HEX_PREFIX;

import com.hedera.hapi.node.base.ContractID;
import com.hedera.hapi.node.base.FileID;
import com.hedera.hapi.node.base.Key;
import com.hedera.hapi.node.base.KeyList;
import com.hedera.hapi.node.base.Timestamp;
import com.hedera.hapi.node.state.contract.SlotValue;
import com.hedera.pbj.runtime.ParseException;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import java.math.BigInteger;
import java.time.Instant;
import lombok.experimental.UtilityClass;
import org.apache.tuweni.bytes.Bytes32;
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
        if (contractID.hasEvmAddress()) {
            return HEX_PREFIX + contractID.evmAddress().toHex().toLowerCase();
        }
        return HEX_PREFIX + String.format("%040x", contractID.contractNum());
    }

    /**
     * Normalizes a hex-encoded storage slot key to a 64-character lowercase hex string (32 bytes, left-padded, no {@code 0x} prefix).
     */
    public static String normalizeStorageSlot(@NonNull String hexSlot) {
        return DomainUtils.bytesToHex(DomainUtils.leftPadBytes(hexStringToBytes(hexSlot), Bytes32.SIZE));
    }

    /** Decodes a hex string (with or without {@code 0x} prefix) to a byte array. */
    public static byte[] hexStringToBytes(@NonNull String hex) {
        var h = hex.startsWith(HEX_PREFIX) || hex.startsWith("0X") ? hex.substring(2) : hex;
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

    /** Parses a hex-encoded string (with or without {@code 0x} prefix) into tinybars, clamped to {@link Long#MAX_VALUE}. */
    public static long parseHex(@NonNull String hex) {
        var hexWithoutPrefix = hex.startsWith(HEX_PREFIX) || hex.startsWith("0X") ? hex.substring(2) : hex;
        if (hexWithoutPrefix.isEmpty()) {
            return 0L;
        }
        try {
            final var bigInt = new BigInteger(hexWithoutPrefix, 16);
            return bigInt.compareTo(BigInteger.valueOf(Long.MAX_VALUE)) > 0 ? Long.MAX_VALUE : bigInt.longValue();
        } catch (NumberFormatException e) {
            return 0L;
        }
    }

    /** Converts a hex value string to a {@link SlotValue} (32-byte left-padded). */
    public static SlotValue hexToSlotValue(@NonNull String hexValue) {
        var valueBytes = hexStringToBytes(hexValue);
        return new SlotValue(Bytes.wrap(DomainUtils.leftPadBytes(valueBytes, Bytes32.SIZE)), Bytes.EMPTY, Bytes.EMPTY);
    }

    public static FileID toFileID(final EntityId entityId) {
        return FileID.newBuilder()
                .shardNum(entityId.getShard())
                .realmNum(entityId.getRealm())
                .fileNum(entityId.getNum())
                .build();
    }
}
