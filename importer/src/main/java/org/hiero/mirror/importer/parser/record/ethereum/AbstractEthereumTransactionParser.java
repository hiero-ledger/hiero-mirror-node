// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.parser.record.ethereum;

import static org.apache.commons.lang3.ArrayUtils.EMPTY_BYTE_ARRAY;

import com.esaulpaugh.headlong.util.Integers;
import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.common.domain.transaction.EthereumTransaction;
import java.math.BigInteger;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.ArrayUtils;
import org.bouncycastle.jcajce.provider.digest.Keccak;
import org.bouncycastle.util.encoders.Hex;
import org.hiero.mirror.importer.repository.FileDataRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@RequiredArgsConstructor
abstract class AbstractEthereumTransactionParser implements EthereumTransactionParser {

    private final FileDataRepository fileDataRepository;
    private final Logger log = LoggerFactory.getLogger(getClass());

    @Override
    public final byte[] getHash(
            byte[] callData, EntityId callDataId, long consensusTimestamp, byte[] transactionBytes) {
        // Note if callData is not empty, callDataId should be ignored, and directly calculate the hash over the saved
        // original raw bytes
        if (ArrayUtils.isNotEmpty(callData) || EntityId.isEmpty(callDataId)) {
            return getHash(transactionBytes);
        }

        try {
            var ethereumTransaction = decode(transactionBytes);

            if (ArrayUtils.isNotEmpty(ethereumTransaction.getAccessList())) {
                log.warn("Re-encoding ethereum transaction at {} with access list is unsupported", consensusTimestamp);
                return EMPTY_BYTE_ARRAY;
            }

            var callDataOptional = fileDataRepository.getFileAtTimestamp(callDataId.getId(), consensusTimestamp);
            if (callDataOptional.isEmpty()) {
                log.warn("Call data not found from {} for ethereum transaction at {}", callDataId, consensusTimestamp);
                return EMPTY_BYTE_ARRAY;
            }

            ethereumTransaction.setCallData(
                    Hex.decode(stripHexPrefix(callDataOptional.get().getFileData())));
            return getHash(encode(ethereumTransaction));
        } catch (Exception e) {
            log.warn("Failed to get hash for ethereum transaction at {}.", consensusTimestamp, e);
            return EMPTY_BYTE_ARRAY;
        }
    }

    protected abstract byte[] encode(EthereumTransaction ethereumTransaction);

    protected static byte[] getHash(byte[] rawBytes) {
        return new Keccak.Digest256().digest(rawBytes);
    }

    protected static byte[] getValue(EthereumTransaction ethereumTransaction) {
        // Value (BigInteger 0) is stored as a 1-byte array [0] in EthereumTransaction, in the RPL encoded raw bytes,
        // it's an empty array, so re-encoding it to get the correct raw bytes for hashing
        return Integers.toBytesUnsigned(new BigInteger(ethereumTransaction.getValue()));
    }

    private static byte[] stripHexPrefix(byte[] data) {
        // If the first two bytes are hex prefix '0x', strip them
        if (data.length >= 2 && data[0] == (byte) 0x30 && data[1] == (byte) 0x78) {
            return ArrayUtils.subarray(data, 2, data.length);
        }

        return data;
    }
}
