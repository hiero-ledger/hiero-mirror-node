// SPDX-License-Identifier: Apache-2.0

package com.hedera.services.jproto;

import static com.hedera.services.utils.TxnUtils.nestJKeys;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.protobuf.ByteString;
import com.hedera.services.utils.TxnUtils;
import com.hederahashgraph.api.proto.java.Key;
import java.security.InvalidKeyException;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

class JKeyTest {

    @Test
    void positiveConvertKeyTest() {
        // given:
        final var bytes = new byte[33];
        bytes[0] = 0x02;
        final Key aKey =
                Key.newBuilder().setECDSASecp256K1(ByteString.copyFrom(bytes)).build();

        // expect:
        assertDoesNotThrow(() -> JKey.convertKey(aKey, 1));
    }

    @Test
    void negativeConvertKeyTest() {
        // given:
        var keyTooDeep = TxnUtils.nestKeys(Key.newBuilder(), JKey.MAX_KEY_DEPTH).build();

        // expect:
        assertThrows(
                InvalidKeyException.class,
                () -> JKey.convertKey(keyTooDeep, 1),
                "Exceeding max expansion depth of " + JKey.MAX_KEY_DEPTH);
    }

    @Test
    void negativeConvertJKeyTest() {
        // given:
        var jKeyTooDeep = nestJKeys(JKey.MAX_KEY_DEPTH);

        // expect:
        assertThrows(
                InvalidKeyException.class,
                () -> JKey.convertJKey(jKeyTooDeep, 1),
                "Exceeding max expansion depth of " + JKey.MAX_KEY_DEPTH);
    }

    @Test
    void convertNullJKeyTest() {
        // expect:
        var result = assertDoesNotThrow(() -> JKey.convertJKey(null, 1));
        assertEquals(JKey.createEmptyKey(), result);
    }

    @Test
    void canGetPrimitiveKeyForEd25519OrSecp256k1() {
        final var mockEd25519 = new JEd25519Key("01234578901234578901234578901".getBytes());
        final var mockSecp256k1 = new JECDSASecp256k1Key("012345789012345789012345789012".getBytes());

        assertSame(mockEd25519.getEd25519(), mockEd25519.primitiveKeyIfPresent());
        assertSame(mockSecp256k1.getECDSASecp256k1Key(), mockSecp256k1.primitiveKeyIfPresent());
        assertEquals(0, mockEd25519.getECDSASecp256k1Key().length);
        assertEquals(0, mockSecp256k1.getEd25519().length);
    }

    @Test
    void convertsECDSAsecp256k1Key() {
        ByteString edcsaSecp256K1Bytes = ByteString.copyFrom(new byte[] {0x02})
                .concat(TxnUtils.randomUtf8ByteString(JECDSASecp256k1Key.ECDSA_SECP256K1_COMPRESSED_KEY_LENGTH - 1));
        final Key aKey = Key.newBuilder().setECDSASecp256K1(edcsaSecp256K1Bytes).build();

        var validEDCSAsecp256K1Key = assertDoesNotThrow(() -> JKey.convertKey(aKey, 1));
        assertTrue(validEDCSAsecp256K1Key instanceof JECDSASecp256k1Key);
        assertEquals(33, validEDCSAsecp256K1Key.getECDSASecp256k1Key().length);
        assertTrue(validEDCSAsecp256K1Key.isValid());
        assertTrue(Arrays.equals(edcsaSecp256K1Bytes.toByteArray(), validEDCSAsecp256K1Key.getECDSASecp256k1Key()));
    }
}
