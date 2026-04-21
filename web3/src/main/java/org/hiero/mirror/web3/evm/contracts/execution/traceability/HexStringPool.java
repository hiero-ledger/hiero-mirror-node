// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.evm.contracts.execution.traceability;

import java.util.HashMap;
import java.util.Map;
import org.apache.tuweni.bytes.Bytes;

/**
 * Request-scoped deduplication for {@link Bytes} to hex {@link String} used when mapping opcode trace
 * entries to the REST API model. Bounded to avoid unbounded heap growth on adversarial traces.
 */
public final class HexStringPool {

    /** Match prior singleton cache scale; cap avoids retaining too many distinct words per request. */
    private static final int MAX_ENTRIES = 1600;

    private static final String ZERO_WORD_HEX = Bytes.wrap(new byte[32]).toHexString();

    private final Map<Bytes, String> hexByBytes = new HashMap<>(256);

    /**
     * Returns the canonical {@code 0x}-prefixed hex string for the given bytes, reusing instances when possible.
     */
    public String hex(final Bytes bytes) {
        if (bytes == null) {
            return null;
        }
        if (bytes.size() == 32 && isZeroWord(bytes)) {
            return ZERO_WORD_HEX;
        }
        var existing = hexByBytes.get(bytes);
        if (existing != null) {
            return existing;
        }
        var encoded = bytes.toHexString();
        if (hexByBytes.size() < MAX_ENTRIES) {
            hexByBytes.put(bytes, encoded);
        }
        return encoded;
    }

    private static boolean isZeroWord(final Bytes bytes) {
        for (int i = 0; i < 32; i++) {
            if (bytes.get(i) != 0) {
                return false;
            }
        }
        return true;
    }
}
