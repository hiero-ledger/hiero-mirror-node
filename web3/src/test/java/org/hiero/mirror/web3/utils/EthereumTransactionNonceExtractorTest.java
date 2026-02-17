// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.utils;

import static org.assertj.core.api.Assertions.assertThat;

import com.esaulpaugh.headlong.rlp.RLPEncoder;
import com.esaulpaugh.headlong.util.Integers;
import java.util.List;
import org.apache.commons.codec.DecoderException;
import org.apache.commons.codec.binary.Hex;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

final class EthereumTransactionNonceExtractorTest {

    // the f8 byte indicates the "Long List" prefix,
    // the 6c byte indicates the length of the RLP-encoded transaction (108 bytes)
    // the rest is the RLP-encoded transaction data, where the nonce is the first element (09)
    private static final String LEGACY_EIP155_RAW_TX =
            "f86c098504a817c800825208943535353535353535353535353535353535353535880de0b6b3a76400008025a028ef61340bd939bc2195fe537567866003e1a15d3c71ff63e1590620aa636276a067cbe9d8997f761aecb703304b3800ccf555c9f3dc64214b297fb1966a3b6d83";

    // 01 - The Transaction Type identifier(EIP2930)
    // f8 - The "Long List" prefix
    // 73 - The length indicator (115 bytes)
    private static final String EIP2930_RAW_TX =
            "01f87382012a82160c85a54f4c3c00832dc6c094000000000000000000000000000000000000052d8502540be40083123456c001a0abb9e9c510716df2988cf626734ee50dcd9f41d30d638220712b5fe33fe4c816a0249a72e1479b61e00d4f20308577bb63167d71b26138ee5229ca1cb3c49a2e53";

    private static final String EIP1559_RAW_TX =
            "02f87082012a022f2f83018000947e3a9eaf9bcc39e2ffa38eb30bf7a93feacbc181880de0b6b3a764000083123456c001a0df48f2efd10421811de2bfb125ab75b2d3c44139c4642837fb1fccce911fd479a01aaf7ae92bee896651dfc9d99ae422a296bf5d9f1ca49b2d96d82b79eb112d66";

    @Test
    void extractsNonceFromLegacyTransaction() throws DecoderException {
        final var txBytes = Hex.decodeHex(LEGACY_EIP155_RAW_TX);
        assertThat(EthereumTransactionNonceExtractor.extractNonce(txBytes)).isEqualTo(9L);
    }

    @Test
    void extractsNonceFromEip2930Transaction() throws DecoderException {
        final var txBytes = Hex.decodeHex(EIP2930_RAW_TX);
        assertThat(EthereumTransactionNonceExtractor.extractNonce(txBytes)).isEqualTo(5644L);
    }

    @Test
    void extractsNonceFromEip1559Transaction() throws DecoderException {
        final var txBytes = Hex.decodeHex(EIP1559_RAW_TX);
        assertThat(EthereumTransactionNonceExtractor.extractNonce(txBytes)).isEqualTo(2L);
    }

    @Test
    void extractsNonceFromEip7702Transaction() throws DecoderException {
        final var txBytes = buildEip7702RawTx(1L);
        assertThat(EthereumTransactionNonceExtractor.extractNonce(txBytes)).isEqualTo(1L);
    }

    @ParameterizedTest
    @CsvSource({"0, 0", "42, 42", "12345, 12345"})
    void extractsNonceFromEip7702TransactionWithVariousNonces(long nonce, long expected) throws DecoderException {
        final var txBytes = buildEip7702RawTx(nonce);
        assertThat(EthereumTransactionNonceExtractor.extractNonce(txBytes)).isEqualTo(expected);
    }

    @Test
    void extractsNonceFromEip7702TransactionWithEmptyAuthorizationList() throws DecoderException {
        final var txBytes = buildEip7702RawTxWithEmptyAuthList(7L);
        assertThat(EthereumTransactionNonceExtractor.extractNonce(txBytes)).isEqualTo(7L);
    }

    @Test
    void returnsNullForMalformedEip7702Transaction() {
        // Type byte 4 but payload is too short (empty list) - parser would throw, extractor returns null
        final var malformedTx = RLPEncoder.sequence(Integers.toBytes(4), List.of());
        assertThat(EthereumTransactionNonceExtractor.extractNonce(malformedTx)).isNull();
    }

    @Test
    void returnsNullForNullInput() {
        assertThat(EthereumTransactionNonceExtractor.extractNonce(null)).isNull();
    }

    @Test
    void returnsNullForEmptyInput() {
        assertThat(EthereumTransactionNonceExtractor.extractNonce(new byte[0])).isNull();
    }

    @Test
    void returnsNullForTooShortInput() {
        assertThat(EthereumTransactionNonceExtractor.extractNonce(new byte[1])).isNull();
    }

    private static byte[] buildEip7702RawTx(long nonce) throws DecoderException {
        final var chainId = Hex.decodeHex("80");
        final var fee = Hex.decodeHex("2f");
        final var toAddress = Hex.decodeHex("7e3a9eaf9bcc39e2ffa38eb30bf7a93feacbc181");
        final var value = Hex.decodeHex("0de0b6b3a7640000");
        final var callData = Hex.decodeHex("123456");
        final var authChainId = Hex.decodeHex("0123");
        final var signatureR = Hex.decodeHex("df48f2efd10421811de2bfb125ab75b2d3c44139c4642837fb1fccce911fd479");
        final var signatureS = Hex.decodeHex("1aaf7ae92bee896651dfc9d99ae422a296bf5d9f1ca49b2d96d82b79eb112d66");
        final var gasLimit = 98_304L;
        final var authNonce = 2L;

        final var authorizationTuple = List.of(
                authChainId, toAddress, Integers.toBytes(authNonce), Integers.toBytes(0), signatureR, signatureS);
        final var authorizationList = List.of(authorizationTuple);

        final var rlpList = List.of(
                chainId,
                Integers.toBytes(nonce),
                fee,
                fee,
                Integers.toBytes(gasLimit),
                toAddress,
                value,
                callData,
                List.of(),
                authorizationList,
                Integers.toBytes(1),
                signatureR,
                signatureS);

        return RLPEncoder.sequence(Integers.toBytes(4), rlpList);
    }

    private static byte[] buildEip7702RawTxWithEmptyAuthList(long nonce) throws DecoderException {
        final var chainId = Hex.decodeHex("80");
        final var fee = Hex.decodeHex("2f");
        final var toAddress = Hex.decodeHex("7e3a9eaf9bcc39e2ffa38eb30bf7a93feacbc181");
        final var value = Hex.decodeHex("0de0b6b3a7640000");
        final var callData = Hex.decodeHex("123456");
        final var signatureR = Hex.decodeHex("df48f2efd10421811de2bfb125ab75b2d3c44139c4642837fb1fccce911fd479");
        final var signatureS = Hex.decodeHex("1aaf7ae92bee896651dfc9d99ae422a296bf5d9f1ca49b2d96d82b79eb112d66");
        final var gasLimit = 98_304L;

        final var rlpList = List.of(
                chainId,
                Integers.toBytes(nonce),
                fee,
                fee,
                Integers.toBytes(gasLimit),
                toAddress,
                value,
                callData,
                List.of(),
                List.of(),
                Integers.toBytes(1),
                signatureR,
                signatureS);

        return RLPEncoder.sequence(Integers.toBytes(4), rlpList);
    }
}
