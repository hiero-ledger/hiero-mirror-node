// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.evm.properties;

import com.hedera.node.app.service.contract.impl.hevm.HederaEvmBlocks;
import com.hedera.node.app.service.evm.contracts.execution.HederaBlockValues;
import jakarta.inject.Named;
import java.time.Instant;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.hiero.mirror.web3.common.ContractCallContext;
import org.hiero.mirror.web3.evm.exception.MissingResultException;
import org.hiero.mirror.web3.repository.RecordFileRepository;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.evm.frame.BlockValues;
import org.hyperledger.besu.evm.frame.MessageFrame;

@Named
@RequiredArgsConstructor
public class StaticBlockMetaSource implements HederaEvmBlocks {
    private final RecordFileRepository recordFileRepository;

    @Override
    public Hash blockHashOf(final MessageFrame frame, long blockNo) {
        final var recordFile = recordFileRepository.findByIndex(blockNo);
        return recordFile
                .map(rf -> ethHashFrom(rf.getHash()))
                .orElseThrow(() -> new MissingResultException(String.format("No record file with index: %d", blockNo)));
    }

    @Override
    public BlockValues blockValuesOf(long gasLimit) {
        var recordFile = ContractCallContext.get().getRecordFile();
        if (Objects.isNull(recordFile)) {
            recordFile = recordFileRepository
                    .findLatest()
                    .orElseThrow(() -> new MissingResultException("No record file available."));
        }
        return new HederaBlockValues(
                gasLimit, recordFile.getIndex(), Instant.ofEpochSecond(0, recordFile.getConsensusStart()));
    }

    public static Hash ethHashFrom(final String hash) {
        return Hash.fromHexString(StringUtils.substring(hash, 0, 64));
    }
}
