// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.state;

import static org.hiero.mirror.web3.validation.HexValidator.HEX_PREFIX;
import static org.hiero.mirror.web3.validation.HexValidator.HEX_PREFIX_CAPITAL;

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
     * Converts a timestamp in nanoseconds (since epoch) to a Java Instant.
     *
     * @param nanos The timestamp in nanoseconds since epoch.
     * @return The Java Instant.
     */
    public static Instant convertToInstant(final long nanos) {
        return Instant.ofEpochSecond(0, nanos);
    }

    /**
     * Converts a timestamp in nanoseconds to a PBJ Timestamp object.
     *
     * @param timestamp The timestamp in nanoseconds.
     * @return The PBJ Timestamp object.
     */
    public static Timestamp convertToTimestamp(final long timestamp) {
        var instant = convertToInstant(timestamp);
        return new Timestamp(instant.getEpochSecond(), instant.getNano());
    }

    public static long getCurrentTimestamp() {
        final var now = Instant.now();
        return DomainUtils.convertToNanos(now.getEpochSecond(), now.getNano());
    }

    /** Parses a hex-encoded string (with or without {@code 0x} prefix) into tinybars, clamped to {@link Long#MAX_VALUE}. */
    public static long hexStringToLong(@NonNull String hex) {
        var start = hex.startsWith(HEX_PREFIX) || hex.startsWith(HEX_PREFIX_CAPITAL) ? 2 : 0;
        while (start < hex.length() && hex.charAt(start) == '0') {
            start++;
        }

        // Empty, prefix-only or all-zero input (e.g. "", "0x", "0", "0x0") represents the value zero. Return early
        // to avoid Long.parseUnsignedLong throwing on an empty range.
        if (start == hex.length()) {
            return 0L;
        }

        if (hex.length() - start > 16) {
            throw new NumberFormatException(
                    String.format("%s produces numeric value which is outside long range.", hex));
        }
        final var value = Long.parseUnsignedLong(hex, start, hex.length(), 16);
        if (value < 0) {
            throw new NumberFormatException(String.format("%s produces a negative numeric value.", hex));
        }
        return value;
    }

    /**
     * Compares a hex string (with or without {@code 0x}/{@code 0X} prefix) to a {@link Bytes} value
     * byte-by-byte without allocating any intermediate arrays or strings.
     */
    public static boolean hexEqualsBytes(@NonNull String hex, @NonNull Bytes bytes) {
        final int start = (hex.startsWith(HEX_PREFIX) || hex.startsWith(HEX_PREFIX_CAPITAL)) ? 2 : 0;
        final int byteCount = (int) bytes.length();

        if (hex.length() - start != byteCount * 2) {
            return false;
        }

        for (int hexPos = start, bytePos = 0; hexPos < hex.length(); hexPos += 2, bytePos++) {
            final int hi = Character.digit(hex.charAt(hexPos), 16);
            final int lo = Character.digit(hex.charAt(hexPos + 1), 16);
            if (hi < 0 || lo < 0 || (bytes.getByte(bytePos) & 0xFF) != ((hi << 4) | lo)) {
                return false;
            }
        }

        return true;
    }

    public static byte[] parseHex(String hex) {
        int i = 0;
        int len = hex.length();

        if (len >= 2 && hex.charAt(0) == '0' && (hex.charAt(1) == 'x' || hex.charAt(1) == 'X')) {
            i = 2;
        }

        int nibbles = len - i;
        byte[] result = new byte[(nibbles + 1) / 2];
        int j = 0;

        if (nibbles % 2 != 0) {
            result[j++] = (byte) parseNibble(hex.charAt(i++));
        }
        while (i < len) {
            result[j++] = (byte) ((parseNibble(hex.charAt(i++)) << 4) | parseNibble(hex.charAt(i++)));
        }
        return result;
    }

    private static int parseNibble(final char c) {
        final int digit = Character.digit(c, 16);
        if (digit < 0) {
            throw new NumberFormatException("Invalid hex character: " + c);
        }
        return digit;
    }

    public static FileID toFileID(final EntityId entityId) {
        return FileID.newBuilder()
                .shardNum(entityId.getShard())
                .realmNum(entityId.getRealm())
                .fileNum(entityId.getNum())
                .build();
    }
}
