// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hiero.mirror.common.util.SignatureUtils.EC_DOMAIN_PARAMETERS;

import com.hedera.node.app.hapi.utils.ethereum.CodeDelegation;
import com.hedera.node.app.hapi.utils.ethereum.EthTxData;
import com.hedera.node.app.hapi.utils.ethereum.EthTxData.EthTransactionType;
import com.hedera.node.app.hapi.utils.ethereum.EthTxSigs;
import java.math.BigInteger;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;
import lombok.SneakyThrows;
import org.apache.tuweni.bytes.Bytes32;
import org.bouncycastle.jcajce.provider.digest.Keccak;
import org.bouncycastle.util.BigIntegers;
import org.hiero.mirror.common.util.SignatureUtils;
import org.hyperledger.besu.crypto.SECPPrivateKey;
import org.hyperledger.besu.crypto.SECPSignature;
import org.hyperledger.besu.crypto.SignatureAlgorithm;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

final class EthTxSigsSubstituteTest {

    private static final BigInteger CURVE_N = EC_DOMAIN_PARAMETERS.getN();
    private static final BigInteger HALF_N = CURVE_N.shiftRight(1);
    private static final boolean IN_NATIVE_IMAGE = System.getProperty("org.graalvm.nativeimage.imagecode") != null;
    private static final String OTHER_KEY = "other key";
    private static final BigInteger PRIVATE_KEY =
            new BigInteger("c85ef7d79691fe79573b1a7064c19c1a9819ebdbd1faaab1a8ec92344438aaf4", 16);
    private static final String SIGNER = "signer";
    private static final byte[] SIGNER_ADDRESS = SignatureUtils.recoverAddressFromPubKey(
            EC_DOMAIN_PARAMETERS.getG().multiply(PRIVATE_KEY).normalize().getEncoded(true));
    private static final byte[] TO = HexFormat.of().parseHex("00000000000000000000000000000000000004e2");

    @AfterEach
    void restoreNative() {
        useNative(true);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void recoverPublicKey(boolean useNative) {
        // Given
        useNative(useNative);
        final var message = "Hello world".getBytes();
        final var expected = EC_DOMAIN_PARAMETERS.getG().multiply(PRIVATE_KEY).normalize();
        final var signature = sign(message);

        // When
        final var recovered = EthTxSigsSubstitute.recoverPublicKey(
                signature.getRecId(), unsigned(signature.getR()), unsigned(signature.getS()), message);

        // Then
        assertThat(recovered.publicKey()).isEqualTo(expected.getEncoded(true));
        assertThat(recovered.address())
                .as("recovering the address from the recovered key matches the address from the true key")
                .isEqualTo(SignatureUtils.recoverAddressFromPubKey(expected.getEncoded(true)))
                .asHexString()
                .isEqualToIgnoringCase("CD2A3D9F938E13CD947EC05ABC7FE734DF8DD826");
    }

    @ParameterizedTest(name = "{0} native={1}")
    @MethodSource("signatureVariants")
    void extractSignatures(String description, boolean useNative, String expected, UnaryOperator<EthTxData> mutate) {
        // Given
        useNative(useNative);
        final var ethTx = mutate.apply(signedEthTx());

        // When
        final var actual = outcome(() -> EthTxSigsSubstitute.Target.extractSignatures(ethTx));

        // Then
        assertThat(actual).as(description).startsWith(expected);
        if (!IN_NATIVE_IMAGE) {
            // In a native image EthTxSigs.extractSignatures is the substitute itself, so only compare on the JVM
            assertThat(result(() -> EthTxSigsSubstitute.Target.extractSignatures(ethTx)))
                    .as("%s matches libsecp256k1", description)
                    .isEqualTo(result(() -> EthTxSigs.extractSignatures(ethTx)));
        }
    }

    // The full result: compressed key and address on success, exception type and message on failure
    private static Object result(Supplier<EthTxSigs> supplier) {
        try {
            return supplier.get();
        } catch (RuntimeException e) {
            return e.getClass().getName() + ": " + e.getMessage();
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void rejectsRAboveCurveOrderThatBesuJavaRecoveryAccepts(boolean useNative) {
        // Given an r in [N, P) that is a valid x coordinate, which Besu's Java recovery happily accepts
        useNative(useNative);
        final var ethTx = signedEthTx();
        final var r = nextXOnCurve(CURVE_N);
        final var message = EthTxSigs.calculateSignableMessage(ethTx);

        // When / Then
        assertThatThrownBy(() -> EthTxSigsSubstitute.recoverPublicKey(0, unsigned(r), ethTx.s(), message))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Curve point must be < N");
        assertThatThrownBy(() -> EthTxSigs.extractSignatures(withSignature(0, unsigned(r), ethTx.s())))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Curve point must be < N");
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void extractAuthoritySignatureInvalid(boolean useNative) {
        // Given
        useNative(useNative);
        final var delegation = new CodeDelegation(
                new byte[] {1}, TO, 0L, 0, unsigned(nextXOnCurve(CURVE_N)), unsigned(BigInteger.ONE));

        // When / Then
        assertThat(EthTxSigsSubstitute.Target.extractAuthoritySignature(delegation))
                .isEqualTo(EthTxSigs.extractAuthoritySignature(delegation))
                .isEmpty();
    }

    static Stream<Arguments> signatureVariants() {
        final var invalidX = firstXNotOnCurve();
        final var inRangeX = nextXOnCurve(CURVE_N);
        final var prime = EC_DOMAIN_PARAMETERS.getCurve().getField().getCharacteristic();
        final var aboveN = "IllegalArgumentException: Curve point must be < N";
        final var belowOne = "IllegalArgumentException: Curve point must be >= 1";
        final Stream<Arguments> variants = Stream.of(
                Arguments.of("valid", SIGNER, (UnaryOperator<EthTxData>) tx -> tx),
                Arguments.of("recId + 1", OTHER_KEY, recId(1)),
                Arguments.of("recId + 2", SIGNER, recId(2)),
                Arguments.of("recId + 3", OTHER_KEY, recId(3)),
                Arguments.of("recId + 27", OTHER_KEY, recId(27)),
                Arguments.of("recId - 1", OTHER_KEY, recId(-1)),
                Arguments.of("r zero", belowOne, r(new byte[0])),
                Arguments.of("r = N", aboveN, r(unsigned(CURVE_N))),
                Arguments.of("r in [N, P) on curve", aboveN, r(unsigned(inRangeX))),
                Arguments.of("r = P - 1", aboveN, r(unsigned(prime.subtract(BigInteger.ONE)))),
                Arguments.of(
                        "r = 2^256 - 1",
                        aboveN,
                        r(unsigned(BigInteger.TWO.pow(256).subtract(BigInteger.ONE)))),
                Arguments.of(
                        "r not on curve",
                        "IllegalArgumentException: Could not recover signature",
                        r(unsigned(invalidX))),
                Arguments.of("r with leading zero byte", "ArrayIndexOutOfBoundsException:", (UnaryOperator<EthTxData>)
                        tx -> withSignature(tx.recId(), prependZero(tx.r()), tx.s())),
                Arguments.of("s with leading zero byte", OTHER_KEY, (UnaryOperator<EthTxData>)
                        tx -> withSignature(tx.recId(), tx.r(), prependZero(tx.s()))),
                Arguments.of(
                        "r = 1 with s leading zero byte",
                        "IllegalArgumentException: Could not recover signature",
                        (UnaryOperator<EthTxData>)
                                tx -> withSignature(tx.recId(), new byte[] {1}, prependZero(tx.s()))),
                Arguments.of("s zero", belowOne, s(new byte[0])),
                Arguments.of("s = N/2", aboveN, s(unsigned(HALF_N))),
                Arguments.of("s = N - 1", aboveN, s(unsigned(CURVE_N.subtract(BigInteger.ONE)))),
                Arguments.of("s = N", aboveN, s(unsigned(CURVE_N))),
                Arguments.of("s high", aboveN, (UnaryOperator<EthTxData>) tx ->
                        withSignature(tx.recId() ^ 1, tx.r(), unsigned(CURVE_N.subtract(new BigInteger(1, tx.s()))))));
        return variants.flatMap(
                a -> Stream.of(true, false).map(n -> Arguments.of(a.get()[0], n, a.get()[1], a.get()[2])));
    }

    static void useNative(boolean useNative) {
        if (useNative) {
            EthTxSigsSubstitute.SECP256K1.maybeEnableNative();
        } else {
            EthTxSigsSubstitute.SECP256K1.disableNative();
        }
    }

    private static BigInteger firstXNotOnCurve() {
        for (var x = BigInteger.ONE; ; x = x.add(BigInteger.ONE)) {
            if (!isOnCurve(x)) {
                return x;
            }
        }
    }

    private static boolean isOnCurve(BigInteger x) {
        final var encoded = new byte[33];
        encoded[0] = 0x02;
        System.arraycopy(unsigned(x), 0, encoded, 1, 32);
        try {
            EC_DOMAIN_PARAMETERS.getCurve().decodePoint(encoded);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private static BigInteger nextXOnCurve(BigInteger start) {
        for (var x = start; ; x = x.add(BigInteger.ONE)) {
            if (isOnCurve(x)) {
                return x;
            }
        }
    }

    private static String outcome(Supplier<EthTxSigs> supplier) {
        try {
            return Arrays.equals(supplier.get().address(), SIGNER_ADDRESS) ? SIGNER : OTHER_KEY;
        } catch (RuntimeException e) {
            return e.getClass().getSimpleName() + ": " + e.getMessage();
        }
    }

    private static byte[] prependZero(byte[] bytes) {
        final var result = new byte[bytes.length + 1];
        System.arraycopy(bytes, 0, result, 1, bytes.length);
        return result;
    }

    private static UnaryOperator<EthTxData> r(byte[] r) {
        return tx -> withSignature(tx.recId(), r, tx.s());
    }

    private static UnaryOperator<EthTxData> recId(int delta) {
        return tx -> withSignature(tx.recId() + delta, tx.r(), tx.s());
    }

    private static UnaryOperator<EthTxData> s(byte[] s) {
        return tx -> withSignature(tx.recId(), tx.r(), s);
    }

    @SneakyThrows
    private static SECPSignature sign(final byte[] message) {
        final var hash = Bytes32.wrap(new Keccak.Digest256().digest(message));
        final var privateKey = SECPPrivateKey.create(PRIVATE_KEY, SignatureAlgorithm.ALGORITHM);
        final var keyPair = EthTxSigsSubstitute.SECP256K1.createKeyPair(privateKey);
        return EthTxSigsSubstitute.SECP256K1.sign(hash, keyPair);
    }

    private static EthTxData signedEthTx() {
        final var template = withSignature(0, new byte[0], new byte[0]);
        final var signature = sign(EthTxSigs.calculateSignableMessage(template));
        return withSignature(signature.getRecId(), unsigned(signature.getR()), unsigned(signature.getS()));
    }

    private static byte[] unsigned(BigInteger value) {
        return BigIntegers.asUnsignedByteArray(32, value);
    }

    private static EthTxData withSignature(int recId, byte[] r, byte[] s) {
        return new EthTxData(
                new byte[0],
                EthTransactionType.LEGACY_ETHEREUM,
                new byte[0],
                1L,
                new byte[] {1},
                null,
                null,
                21_000L,
                TO,
                BigInteger.ONE,
                new byte[0],
                null,
                null,
                null,
                null,
                recId,
                new byte[] {(byte) (27 + recId)},
                r,
                s);
    }
}
