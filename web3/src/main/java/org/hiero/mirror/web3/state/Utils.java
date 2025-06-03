// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.state;

import com.hedera.hapi.node.base.Key;
import com.hedera.hapi.node.base.KeyList;
import com.hedera.hapi.node.base.Timestamp;
import com.hedera.pbj.runtime.ParseException;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import java.nio.ByteBuffer;
import java.time.Instant;
import java.util.Arrays;
import lombok.experimental.UtilityClass;
import org.hyperledger.besu.datatypes.Address;

@UtilityClass
public class Utils {

    public static final long DEFAULT_AUTO_RENEW_PERIOD = 7776000L;
    public static final int EVM_ADDRESS_LEN = 20;
    public static final Key EMPTY_KEY_LIST =
            Key.newBuilder().keyList(KeyList.DEFAULT).build();

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

    public boolean isMirror(final Address address) {
        return address != null && isMirror(address.toArrayUnsafe());
    }

    public static boolean isMirror(final byte[] address) {
        if (address == null || address.length != EVM_ADDRESS_LEN) {
            return false;
        }

        if (Arrays.equals(address, Address.ZERO.toArrayUnsafe())) {
            return true;
        }

        var wrapper = ByteBuffer.wrap(address);

        return wrapper.getInt() == 0 && wrapper.getLong() == 0;
    }
}
