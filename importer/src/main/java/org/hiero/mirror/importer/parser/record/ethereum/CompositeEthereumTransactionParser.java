// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.parser.record.ethereum;

import com.esaulpaugh.headlong.rlp.RLPDecoder;
import jakarta.inject.Named;
import lombok.CustomLog;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.ArrayUtils;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.hiero.mirror.common.domain.transaction.EthereumTransaction;
import org.hiero.mirror.importer.exception.InvalidDatasetException;
import org.springframework.context.annotation.Primary;

@CustomLog
@Named
@Primary
@RequiredArgsConstructor
public class CompositeEthereumTransactionParser implements EthereumTransactionParser {

    private final LegacyEthereumTransactionParser legacyEthereumTransactionParser;
    private final Eip2930EthereumTransactionParser eip2930EthereumTransactionParser;
    private final Eip1559EthereumTransactionParser eip1559EthereumTransactionParser;
    private final Eip7702EthereumTransactionParser eip7702EthereumTransactionParser;

    @Override
    public EthereumTransaction decode(byte[] transactionBytes) {
        var ethereumTransactionParser = getEthereumTransactionParser(transactionBytes);
        return ethereumTransactionParser.decode(transactionBytes);
    }

    @Override
    public byte[] getHash(
            byte[] callData,
            EntityId callDataId,
            long consensusTimestamp,
            byte[] transactionBytes,
            boolean useCurrentState) {
        try {
            var parser = getEthereumTransactionParser(transactionBytes);
            return parser.getHash(callData, callDataId, consensusTimestamp, transactionBytes, useCurrentState);
        } catch (Exception e) {
            log.warn("Failed to calculate hash for ethereum transaction at {}", consensusTimestamp, e);
            return ArrayUtils.EMPTY_BYTE_ARRAY;
        }
    }

    private EthereumTransactionParser getEthereumTransactionParser(byte[] transactionBytes) {
        if (ArrayUtils.isEmpty(transactionBytes) || transactionBytes.length < 2) {
            throw new InvalidDatasetException("Ethereum transaction bytes length is less than 2 bytes in length");
        }

        var decoder = RLPDecoder.RLP_STRICT.sequenceIterator(transactionBytes);
        var firstRlpItem = decoder.next();

        // legacy transactions are encoded as a list
        if (firstRlpItem.isList()) {
            return legacyEthereumTransactionParser;
        }

        // typed transactions encode the type in the first byte
        var legacyRlpItemByte = firstRlpItem.asByte();
        if (legacyRlpItemByte == Eip2930EthereumTransactionParser.EIP2930_TYPE_BYTE) {
            return eip2930EthereumTransactionParser;
        } else if (legacyRlpItemByte == Eip1559EthereumTransactionParser.EIP1559_TYPE_BYTE) {
            return eip1559EthereumTransactionParser;
        } else if (legacyRlpItemByte == Eip7702EthereumTransactionParser.EIP7702_TYPE_BYTE) {
            return eip7702EthereumTransactionParser;
        }
        throw new InvalidDatasetException("Unsupported Ethereum transaction data type");
    }
}
