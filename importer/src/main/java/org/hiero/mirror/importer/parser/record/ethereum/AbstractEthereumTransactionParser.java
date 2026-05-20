// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.parser.record.ethereum;

import static org.apache.commons.lang3.ArrayUtils.EMPTY_BYTE_ARRAY;

import com.esaulpaugh.headlong.rlp.RLPItem;
import com.esaulpaugh.headlong.util.Integers;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.ArrayUtils;
import org.bouncycastle.jcajce.provider.digest.Keccak;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.hiero.mirror.common.domain.file.FileData;
import org.hiero.mirror.common.domain.transaction.AccessList;
import org.hiero.mirror.common.domain.transaction.EthereumTransaction;
import org.hiero.mirror.importer.exception.InvalidEthereumBytesException;
import org.hiero.mirror.importer.repository.FileDataRepository;
import org.hiero.mirror.importer.service.ContractBytecodeService;
import org.hiero.mirror.importer.util.Utility;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@RequiredArgsConstructor
abstract class AbstractEthereumTransactionParser implements EthereumTransactionParser {

    private final ContractBytecodeService contractBytecodeService;
    private final FileDataRepository fileDataRepository;
    private final Logger log = LoggerFactory.getLogger(getClass());

    @Override
    public final byte[] getHash(
            byte[] callData,
            EntityId callDataId,
            long consensusTimestamp,
            byte[] transactionBytes,
            boolean useCurrentState) {
        // Note if callData is not empty, callDataId should be ignored, and directly calculate the hash over the saved
        // original raw bytes
        if (ArrayUtils.isNotEmpty(callData) || EntityId.isEmpty(callDataId)) {
            return getHash(transactionBytes);
        }

        try {
            var ethereumTransaction = decode(transactionBytes);
            if (!CollectionUtils.isEmpty(ethereumTransaction.getAccessList())) {
                log.warn("Re-encoding ethereum transaction at {} with access list is unsupported", consensusTimestamp);
                return EMPTY_BYTE_ARRAY;
            }

            callData = getCallData(callDataId, consensusTimestamp, useCurrentState);
            if (callData == null) {
                Utility.handleRecoverableError(
                        "Failed to read call data from file {} for ethereum transaction at {}",
                        callDataId,
                        consensusTimestamp);
                return EMPTY_BYTE_ARRAY;
            }

            ethereumTransaction.setCallData(callData);
            return getHash(encode(ethereumTransaction));
        } catch (Exception e) {
            Utility.handleRecoverableError("Failed to get hash for ethereum transaction at {}.", consensusTimestamp, e);
            return EMPTY_BYTE_ARRAY;
        }
    }

    protected abstract byte[] encode(EthereumTransaction ethereumTransaction);

    protected static List<AccessList> parseAccessList(RLPItem rlpAccessList, String transactionTypeName) {
        if (!rlpAccessList.isList()) {
            throw new InvalidEthereumBytesException(transactionTypeName, "Access list is not a list");
        }

        final var accessListEntries = rlpAccessList.asRLPList().elements();
        final var accessList = new ArrayList<AccessList>(accessListEntries.size());

        for (final var entry : accessListEntries) {
            if (!entry.isList()) {
                throw new InvalidEthereumBytesException(transactionTypeName, "Access list entry is not a list");
            }

            final var entryProperties = entry.asRLPList().elements();
            if (entryProperties.size() != 2) {
                throw new InvalidEthereumBytesException(
                        transactionTypeName,
                        String.format("Access list entry size was %d but expected 2", entryProperties.size()));
            }

            final var address = HexFormat.of().formatHex(entryProperties.get(0).data());
            final var storageKeysItem = entryProperties.get(1);
            if (!storageKeysItem.isList()) {
                throw new InvalidEthereumBytesException(
                        transactionTypeName, "Access list entry storage keys is not a list");
            }
            final var storageKeyItems = storageKeysItem.asRLPList().elements();
            final var storageKeys = new ArrayList<String>(storageKeyItems.size());
            for (final var key : storageKeyItems) {
                storageKeys.add(HexFormat.of().formatHex(key.data()));
            }

            accessList.add(new AccessList(address, storageKeys));
        }

        return accessList;
    }

    protected static byte[] getValue(EthereumTransaction ethereumTransaction) {
        // Value (BigInteger 0) is stored as a 1-byte array [0] in EthereumTransaction, in the RPL encoded raw bytes,
        // it's an empty array, so re-encoding it to get the correct raw bytes for hashing
        return Integers.toBytesUnsigned(new BigInteger(ethereumTransaction.getValue()));
    }

    private static byte[] getHash(byte[] rawBytes) {
        return new Keccak.Digest256().digest(rawBytes);
    }

    private byte[] getCallData(EntityId callDataId, long consensusTimestamp, boolean useCurrentState) {
        return useCurrentState
                ? contractBytecodeService.get(callDataId)
                : fileDataRepository
                        .getFileAtTimestamp(callDataId.getId(), consensusTimestamp)
                        .map(FileData::getFileData)
                        .map(Utility::decodeBytecode)
                        .orElse(null);
    }
}
