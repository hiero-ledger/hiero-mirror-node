// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.parser.record.ethereum;

import com.esaulpaugh.headlong.rlp.RLPDecoder;
import com.esaulpaugh.headlong.rlp.RLPEncoder;
import com.esaulpaugh.headlong.util.Integers;
import jakarta.inject.Named;
import java.util.List;
import org.hiero.mirror.common.domain.transaction.EthereumTransaction;
import org.hiero.mirror.importer.exception.InvalidEthereumBytesException;
import org.hiero.mirror.importer.repository.FileDataRepository;
import org.hiero.mirror.importer.service.ContractBytecodeService;

@Named
public final class Eip2930EthereumTransactionParser extends AbstractEthereumTransactionParser {

    public static final int EIP2930_TYPE_BYTE = 1;
    private static final byte[] EIP2930_TYPE_BYTES = Integers.toBytes(EIP2930_TYPE_BYTE);
    private static final String TRANSACTION_TYPE_NAME = "EIP2930";
    private static final int EIP2930_TYPE_RLP_ITEM_COUNT = 11;

    public Eip2930EthereumTransactionParser(
            ContractBytecodeService contractBytecodeService, FileDataRepository fileDataRepository) {
        super(contractBytecodeService, fileDataRepository);
    }

    @Override
    public EthereumTransaction decode(byte[] transactionBytes) {
        var decoder = RLPDecoder.RLP_STRICT.sequenceIterator(transactionBytes);
        var type = decoder.next().asByte();
        if (type != EIP2930_TYPE_BYTE) {
            throw new InvalidEthereumBytesException(
                    TRANSACTION_TYPE_NAME,
                    String.format("First byte was %s but should be %s", type, EIP2930_TYPE_BYTE));
        }

        var eip2930RlpItem = decoder.next();
        if (!eip2930RlpItem.isList()) {
            throw new InvalidEthereumBytesException(TRANSACTION_TYPE_NAME, "Second RLPItem was not a list");
        }

        var rlpItems = eip2930RlpItem.asRLPList().elements();
        if (rlpItems.size() != EIP2930_TYPE_RLP_ITEM_COUNT) {
            throw new InvalidEthereumBytesException(
                    TRANSACTION_TYPE_NAME,
                    String.format(
                            "RLP list size was %d but expected %d", rlpItems.size(), EIP2930_TYPE_RLP_ITEM_COUNT));
        }

        var ethereumTransaction = EthereumTransaction.builder()
                .chainId(rlpItems.get(0).data())
                .nonce(rlpItems.get(1).asLong())
                .gasPrice(rlpItems.get(2).asBytes())
                .gasLimit(rlpItems.get(3).asLong())
                .toAddress(rlpItems.get(4).data())
                .value(rlpItems.get(5).asBigInt().toByteArray())
                .callData(rlpItems.get(6).data())
                .accessList(rlpItems.get(7).data())
                .recoveryId((int) rlpItems.get(8).asByte())
                .signatureR(rlpItems.get(9).data())
                .signatureS(rlpItems.get(10).data())
                .type(EIP2930_TYPE_BYTE);

        return ethereumTransaction.build();
    }

    @Override
    protected byte[] encode(EthereumTransaction ethereumTransaction) {
        return RLPEncoder.sequence(
                EIP2930_TYPE_BYTES,
                List.of(
                        ethereumTransaction.getChainId(),
                        Integers.toBytes(ethereumTransaction.getNonce()),
                        ethereumTransaction.getGasPrice(),
                        Integers.toBytes(ethereumTransaction.getGasLimit()),
                        ethereumTransaction.getToAddress(),
                        getValue(ethereumTransaction),
                        ethereumTransaction.getCallData(),
                        List.of(/*accessList*/ ),
                        Integers.toBytes(ethereumTransaction.getRecoveryId()),
                        ethereumTransaction.getSignatureR(),
                        ethereumTransaction.getSignatureS()));
    }
}
