// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.parser.record.ethereum;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hiero.mirror.common.util.DomainUtils.EMPTY_BYTE_ARRAY;
import static org.hiero.mirror.importer.parser.domain.RecordItemBuilder.LONDON_RAW_TX;

import com.esaulpaugh.headlong.rlp.RLPEncoder;
import com.esaulpaugh.headlong.util.Integers;
import org.bouncycastle.util.encoders.Hex;
import org.hiero.mirror.common.domain.transaction.EthereumTransaction;
import org.hiero.mirror.importer.exception.InvalidEthereumBytesException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

@ExtendWith(OutputCaptureExtension.class)
class Eip2930EthereumTransactionParserTest extends AbstractEthereumTransactionParserTest {

    public static final byte[] EIP_2930_RAW_TX = Hex.decode(
            "01f87382012a82160c85a54f4c3c00832dc6c094000000000000000000000000000000000000052d8502540be40083123456c001a0abb9e9c510716df2988cf626734ee50dcd9f41d30d638220712b5fe33fe4c816a0249a72e1479b61e00d4f20308577bb63167d71b26138ee5229ca1cb3c49a2e53");

    public Eip2930EthereumTransactionParserTest(Eip2930EthereumTransactionParser ethereumTransactionParser) {
        super(ethereumTransactionParser);
    }

    @Override
    public byte[] getTransactionBytes() {
        return EIP_2930_RAW_TX;
    }

    @Test
    void decodeEip1559Type() {
        var ethereumTransactionBytes = RLPEncoder.sequence(Integers.toBytes(2), new Object[] {});

        assertThatThrownBy(() -> ethereumTransactionParser.decode(ethereumTransactionBytes))
                .isInstanceOf(InvalidEthereumBytesException.class)
                .hasMessage("Unable to decode EIP2930 ethereum transaction bytes, First byte was 2 but should be 1");
    }

    @Test
    void decodeNonListRlpItem() {
        var ethereumTransactionBytes = RLPEncoder.sequence(Integers.toBytes(1), Integers.toBytes(1));

        assertThatThrownBy(() -> ethereumTransactionParser.decode(ethereumTransactionBytes))
                .isInstanceOf(InvalidEthereumBytesException.class)
                .hasMessage("Unable to decode EIP2930 ethereum transaction bytes, Second RLPItem was not a list");
    }

    @Test
    void decodeIncorrectRlpItemListSize() {
        var ethereumTransactionBytes = RLPEncoder.sequence(Integers.toBytes(1), new Object[] {});

        assertThatThrownBy(() -> ethereumTransactionParser.decode(ethereumTransactionBytes))
                .isInstanceOf(InvalidEthereumBytesException.class)
                .hasMessage("Unable to decode EIP2930 ethereum transaction bytes, RLP list size was 0 but expected 11");
    }

    @Test
    void getHashIncorrectTransactionType(CapturedOutput capturedOutput) {
        // given, when
        var actual = ethereumTransactionParser.getHash(
                EMPTY_BYTE_ARRAY, domainBuilder.entityId(), domainBuilder.timestamp(), LONDON_RAW_TX, true);

        // then
        softly.assertThat(actual).isEmpty();
        softly.assertThat(capturedOutput).contains("Unable to decode EIP2930 ethereum transaction bytes");
    }

    @Override
    protected void validateEthereumTransaction(EthereumTransaction ethereumTransaction) {
        assertThat(ethereumTransaction)
                .isNotNull()
                .returns(Eip2930EthereumTransactionParser.EIP2930_TYPE_BYTE, EthereumTransaction::getType)
                .returns(Hex.decode("012a"), EthereumTransaction::getChainId)
                .returns(5644L, EthereumTransaction::getNonce)
                .returns(Hex.decode("a54f4c3c00"), EthereumTransaction::getGasPrice)
                .returns(null, EthereumTransaction::getMaxPriorityFeePerGas)
                .returns(null, EthereumTransaction::getMaxFeePerGas)
                .returns(3_000_000L, EthereumTransaction::getGasLimit)
                .returns(Hex.decode("000000000000000000000000000000000000052d"), EthereumTransaction::getToAddress)
                .returns(Hex.decode("02540be400"), EthereumTransaction::getValue)
                .returns(Hex.decode("123456"), EthereumTransaction::getCallData)
                .returns(Hex.decode(""), EthereumTransaction::getAccessList)
                .returns(1, EthereumTransaction::getRecoveryId)
                .returns(null, EthereumTransaction::getSignatureV)
                .returns(
                        Hex.decode("abb9e9c510716df2988cf626734ee50dcd9f41d30d638220712b5fe33fe4c816"),
                        EthereumTransaction::getSignatureR)
                .returns(
                        Hex.decode("249a72e1479b61e00d4f20308577bb63167d71b26138ee5229ca1cb3c49a2e53"),
                        EthereumTransaction::getSignatureS);
    }
}
