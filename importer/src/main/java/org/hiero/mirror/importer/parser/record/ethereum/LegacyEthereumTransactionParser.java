// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.parser.record.ethereum;

import com.esaulpaugh.headlong.rlp.RLPDecoder;
import com.esaulpaugh.headlong.rlp.RLPEncoder;
import com.esaulpaugh.headlong.util.Integers;
import jakarta.inject.Named;
import java.math.BigInteger;
import org.hiero.mirror.common.domain.transaction.EthereumTransaction;
import org.hiero.mirror.importer.exception.InvalidEthereumBytesException;
import org.hiero.mirror.importer.repository.FileDataRepository;
import org.hiero.mirror.importer.service.ContractBytecodeService;

@Named
public final class LegacyEthereumTransactionParser extends AbstractEthereumTransactionParser {

    public static final int LEGACY_TYPE_BYTE = 0;
    private static final int LEGACY_TYPE_RLP_ITEM_COUNT = 9;
    private static final String TRANSACTION_TYPE_NAME = "Legacy";

    public LegacyEthereumTransactionParser(
            ContractBytecodeService contractBytecodeService, FileDataRepository fileDataRepository) {
        super(contractBytecodeService, fileDataRepository);
    }

    @Override
    public EthereumTransaction decode(byte[] transactionBytes) {
        var decoder = RLPDecoder.RLP_STRICT.sequenceIterator(transactionBytes);
        var legacyRlpItem = decoder.next();
        var rlpItems = legacyRlpItem.asRLPList().elements();
        if (rlpItems.size() != LEGACY_TYPE_RLP_ITEM_COUNT) {
            throw new InvalidEthereumBytesException(
                    TRANSACTION_TYPE_NAME,
                    String.format(
                            "RLPItem list size was %s " + "but should be %s",
                            rlpItems.size(), LEGACY_TYPE_RLP_ITEM_COUNT));
        }

        var ethereumTransaction = EthereumTransaction.builder()
                .nonce(rlpItems.get(0).asLong())
                .gasPrice(rlpItems.get(1).asBytes())
                .gasLimit(rlpItems.get(2).asLong())
                .toAddress(rlpItems.get(3).data())
                .value(rlpItems.get(4).asBigInt().toByteArray())
                .callData(rlpItems.get(5).data())
                .type(LEGACY_TYPE_BYTE);

        var v = rlpItems.get(6).asBytes();
        BigInteger vBi = new BigInteger(1, v);
        ethereumTransaction
                .signatureV(v)
                .signatureR(rlpItems.get(7).data())
                .signatureS(rlpItems.get(8).data())
                .recoveryId(vBi.testBit(0) ? 0 : 1);

        if (vBi.compareTo(BigInteger.valueOf(34)) > 0) {
            ethereumTransaction.chainId(
                    vBi.subtract(BigInteger.valueOf(35)).shiftRight(1).toByteArray());
        }

        return ethereumTransaction.build();
    }

    @Override
    protected byte[] encode(EthereumTransaction ethereumTransaction) {
        return RLPEncoder.list(
                Integers.toBytes(ethereumTransaction.getNonce()),
                ethereumTransaction.getGasPrice(),
                Integers.toBytes(ethereumTransaction.getGasLimit()),
                ethereumTransaction.getToAddress(),
                getValue(ethereumTransaction),
                ethereumTransaction.getCallData(),
                ethereumTransaction.getSignatureV(),
                ethereumTransaction.getSignatureR(),
                ethereumTransaction.getSignatureS());
    }
}
