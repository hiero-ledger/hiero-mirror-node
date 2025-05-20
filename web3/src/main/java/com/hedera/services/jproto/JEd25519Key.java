// SPDX-License-Identifier: Apache-2.0

package com.hedera.services.jproto;

import com.hederahashgraph.api.proto.java.Key;
import java.util.Arrays;
import org.hiero.base.utility.CommonUtils;

/** Maps to proto Key of type ed25519. */
public class JEd25519Key extends JKey {
    public static final int ED25519_BYTE_LENGTH = 32;

    private final byte[] ed25519;

    public JEd25519Key(byte[] ed25519) {
        this.ed25519 = ed25519;
    }

    public static boolean isValidProto(final Key ed25519Key) {
        return ed25519Key.getEd25519().size() == ED25519_BYTE_LENGTH;
    }

    @Override
    public String toString() {
        return "<JEd25519Key: ed25519 hex=" + CommonUtils.hex(ed25519) + ">";
    }

    @Override
    public boolean isEmpty() {
        return ((null == ed25519) || (0 == ed25519.length));
    }

    @Override
    public boolean isValid() {
        if (isEmpty()) {
            return false;
        } else {
            return ed25519.length == ED25519_BYTE_LENGTH;
        }
    }

    @Override
    public byte[] getEd25519() {
        return ed25519;
    }

    @Override
    public boolean hasEd25519Key() {
        return true;
    }

    @Override
    public boolean equals(Object o) {
        if (o == this) {
            return true;
        }
        if (o == null || JEd25519Key.class != o.getClass()) {
            return false;
        }
        final var that = (JEd25519Key) o;
        return Arrays.equals(this.ed25519, that.ed25519);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(ed25519);
    }
}
