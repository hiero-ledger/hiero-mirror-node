// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.parser.record.ethereum;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hiero.mirror.common.util.DomainUtils.EMPTY_BYTE_ARRAY;
import static org.hiero.mirror.importer.parser.domain.RecordItemBuilder.LONDON_RAW_TX;

import com.esaulpaugh.headlong.rlp.RLPEncoder;
import com.esaulpaugh.headlong.util.Integers;
import java.util.List;
import org.bouncycastle.util.encoders.Hex;
import org.hiero.mirror.common.domain.transaction.Authorization;
import org.hiero.mirror.common.domain.transaction.EthereumTransaction;
import org.hiero.mirror.importer.exception.InvalidEthereumBytesException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

@ExtendWith(OutputCaptureExtension.class)
class Eip7702EthereumTransactionParserTest extends AbstractEthereumTransactionParserTest {

    private static final String CHAIN_ID_HEX = "80";
    private static final String FEE_HEX = "2f";
    private static final String TO_ADDRESS_HEX = "7e3a9eaf9bcc39e2ffa38eb30bf7a93feacbc181";
    private static final String VALUE_HEX = "0de0b6b3a7640000";
    private static final String CALL_DATA_HEX = "123456";
    private static final String AUTH_CHAIN_ID_HEX = "0123";
    private static final String SIGNATURE_R_HEX = "df48f2efd10421811de2bfb125ab75b2d3c44139c4642837fb1fccce911fd479";
    private static final String SIGNATURE_S_HEX = "1aaf7ae92bee896651dfc9d99ae422a296bf5d9f1ca49b2d96d82b79eb112d66";
    private static final long GAS_LIMIT = 98_304L;
    private static final long NONCE = 1L;
    private static final long AUTH_NONCE = 2L;

    static final byte[] EIP7702_RAW_TX;

    static {
        EIP7702_RAW_TX = RLPEncoder.sequence(
                Integers.toBytes(4),
                List.of(
                        Hex.decode(CHAIN_ID_HEX),
                        Integers.toBytes(NONCE),
                        Hex.decode(FEE_HEX),
                        Hex.decode(FEE_HEX),
                        Integers.toBytes(GAS_LIMIT),
                        Hex.decode(TO_ADDRESS_HEX),
                        Hex.decode(VALUE_HEX),
                        Hex.decode(CALL_DATA_HEX),
                        List.of(),
                        List.of(List.of(
                                Hex.decode(AUTH_CHAIN_ID_HEX),
                                Hex.decode(TO_ADDRESS_HEX),
                                Integers.toBytes(AUTH_NONCE),
                                Integers.toBytes(0),
                                Hex.decode(SIGNATURE_R_HEX),
                                Hex.decode(SIGNATURE_S_HEX))),
                        Integers.toBytes(1),
                        Hex.decode(SIGNATURE_R_HEX),
                        Hex.decode(SIGNATURE_S_HEX)));
    }

    public Eip7702EthereumTransactionParserTest(Eip7702EthereumTransactionParser ethereumTransactionParser) {
        super(ethereumTransactionParser);
    }

    @Override
    public byte[] getTransactionBytes() {
        return EIP7702_RAW_TX;
    }

    @Test
    void decodeWrongType() {
        var ethereumTransactionBytes = RLPEncoder.sequence(Integers.toBytes(2), new Object[] {});

        assertThatThrownBy(() -> ethereumTransactionParser.decode(ethereumTransactionBytes))
                .isInstanceOf(InvalidEthereumBytesException.class)
                .hasMessage("Unable to decode EIP7702 ethereum transaction bytes, First byte was 2 but should be 4");
    }

    @Test
    void decodeNonListRlpItem() {
        var ethereumTransactionBytes = RLPEncoder.sequence(Integers.toBytes(4), Integers.toBytes(1));

        assertThatThrownBy(() -> ethereumTransactionParser.decode(ethereumTransactionBytes))
                .isInstanceOf(InvalidEthereumBytesException.class)
                .hasMessage("Unable to decode EIP7702 ethereum transaction bytes, Second RLPItem was not a list");
    }

    @Test
    void decodeIncorrectRlpItemListSize() {
        var ethereumTransactionBytes = RLPEncoder.sequence(Integers.toBytes(4), new Object[] {});

        assertThatThrownBy(() -> ethereumTransactionParser.decode(ethereumTransactionBytes))
                .isInstanceOf(InvalidEthereumBytesException.class)
                .hasMessage("Unable to decode EIP7702 ethereum transaction bytes, RLP list size was 0 but expected 13");
    }

    @Test
    void decodeAuthorizationListNotAList() {
        var transactionData = List.of(
                Hex.decode(CHAIN_ID_HEX),
                Integers.toBytes(NONCE),
                Hex.decode(FEE_HEX),
                Hex.decode(FEE_HEX),
                Integers.toBytes(GAS_LIMIT),
                Hex.decode(TO_ADDRESS_HEX),
                Hex.decode(VALUE_HEX),
                Hex.decode(CALL_DATA_HEX),
                List.of(),
                Hex.decode("01"),
                Integers.toBytes(1),
                Hex.decode(SIGNATURE_R_HEX),
                Hex.decode(SIGNATURE_S_HEX));
        var ethereumTransactionBytes = RLPEncoder.sequence(Integers.toBytes(4), transactionData);

        assertThatThrownBy(() -> ethereumTransactionParser.decode(ethereumTransactionBytes))
                .isInstanceOf(InvalidEthereumBytesException.class)
                .hasMessage("Unable to decode EIP7702 ethereum transaction bytes, Authorization list is not a list");
    }

    @Test
    void getHashIncorrectTransactionType(CapturedOutput capturedOutput) {
        // given, when
        var actual = ethereumTransactionParser.getHash(
                EMPTY_BYTE_ARRAY, domainBuilder.entityId(), domainBuilder.timestamp(), LONDON_RAW_TX, true);

        // then
        softly.assertThat(actual).isEmpty();
        softly.assertThat(capturedOutput).contains("Unable to decode EIP7702 ethereum transaction bytes");
    }

    @Override
    protected void validateEthereumTransaction(EthereumTransaction ethereumTransaction) {
        assertThat(ethereumTransaction)
                .isNotNull()
                .returns(Eip7702EthereumTransactionParser.EIP7702_TYPE_BYTE, EthereumTransaction::getType)
                .returns(Hex.decode(CHAIN_ID_HEX), EthereumTransaction::getChainId)
                .returns(NONCE, EthereumTransaction::getNonce)
                .returns(null, EthereumTransaction::getGasPrice)
                .returns(Hex.decode(FEE_HEX), EthereumTransaction::getMaxPriorityFeePerGas)
                .returns(Hex.decode(FEE_HEX), EthereumTransaction::getMaxFeePerGas)
                .returns(GAS_LIMIT, EthereumTransaction::getGasLimit)
                .returns(Hex.decode(TO_ADDRESS_HEX), EthereumTransaction::getToAddress)
                .returns(Hex.decode(VALUE_HEX), EthereumTransaction::getValue)
                .returns(Hex.decode(CALL_DATA_HEX), EthereumTransaction::getCallData)
                .returns(Hex.decode(""), EthereumTransaction::getAccessList)
                .returns(1, EthereumTransaction::getRecoveryId)
                .returns(null, EthereumTransaction::getSignatureV)
                .returns(Hex.decode(SIGNATURE_R_HEX), EthereumTransaction::getSignatureR)
                .returns(Hex.decode(SIGNATURE_S_HEX), EthereumTransaction::getSignatureS);

        // Validate authorization list
        assertThat(ethereumTransaction.getAuthorizationList()).isNotNull().hasSize(1);

        var authorization = ethereumTransaction.getAuthorizationList().get(0);
        assertThat(authorization)
                .isNotNull()
                .returns(AUTH_CHAIN_ID_HEX, Authorization::getChainId)
                .returns(TO_ADDRESS_HEX, Authorization::getAddress)
                .returns(AUTH_NONCE, Authorization::getNonce)
                .returns(0, Authorization::getYParity)
                .returns(SIGNATURE_R_HEX, Authorization::getR)
                .returns(SIGNATURE_S_HEX, Authorization::getS);
    }
}
