// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.evm.contracts.operations;

import jakarta.inject.Named;
import org.apache.commons.lang3.StringUtils;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.units.bigints.UInt256;
import org.hiero.mirror.common.domain.transaction.RecordFile;
import org.hiero.mirror.web3.common.ContractCallContext;
import org.hiero.mirror.web3.evm.config.ModularizedOperation;
import org.hiero.mirror.web3.exception.BlockNumberNotFoundException;
import org.hiero.mirror.web3.repository.RecordFileRepository;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.evm.EVM;
import org.hyperledger.besu.evm.frame.BlockValues;
import org.hyperledger.besu.evm.frame.ExceptionalHaltReason;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.gascalculator.GasCalculator;
import org.hyperledger.besu.evm.operation.BlockHashOperation;

/**
 * Custom version of the Besu's BlockHashOperation class. The difference is that in the mirror node we have the block
 * hash values of all the blocks so the restriction for the latest 256 blocks is removed. The latest block value can be
 * returned as well.
 */
@Named
class MirrorBlockHashOperation extends BlockHashOperation implements ModularizedOperation {

    private final RecordFileRepository recordFileRepository;

    /**
     * Instantiates a new Block hash operation.
     *
     * @param gasCalculator the gas calculator
     */
    MirrorBlockHashOperation(GasCalculator gasCalculator, RecordFileRepository recordFileRepository) {
        super(gasCalculator);
        this.recordFileRepository = recordFileRepository;
    }

    @Override
    public OperationResult execute(final MessageFrame frame, final EVM evm) {
        final long cost = gasCalculator().getBlockHashOperationGasCost();
        if (frame.getRemainingGas() < cost) {
            return new OperationResult(cost, ExceptionalHaltReason.INSUFFICIENT_GAS);
        }

        final Bytes blockArg = frame.popStackItem().trimLeadingZeros();
        // Short-circuit if value is unreasonably large
        if (blockArg.size() > 8) {
            frame.pushStackItem(UInt256.ZERO);
            return new OperationResult(cost, null);
        }

        final long soughtBlock = blockArg.toLong();
        final BlockValues blockValues = frame.getBlockValues();
        final long currentBlockNumber = blockValues.getNumber();

        if (currentBlockNumber <= 0 || soughtBlock > currentBlockNumber) {
            frame.pushStackItem(Bytes32.ZERO);
        } else if (currentBlockNumber == soughtBlock) {
            var latestBlock = ContractCallContext.get().getRecordFile();
            if (latestBlock == null) {
                latestBlock = recordFileRepository.findLatest().orElseThrow(BlockNumberNotFoundException::new);
            }
            final var blockHash = getBlockHash(latestBlock);
            frame.pushStackItem(blockHash);
        } else {
            final Hash blockHash = getBlockHash(soughtBlock);
            frame.pushStackItem(blockHash);
        }

        return new OperationResult(cost, null);
    }

    private Hash getBlockHash(long blockNumber) {
        final var recordFile = recordFileRepository.findByIndex(blockNumber);
        return recordFile.map(this::getBlockHash).orElse(Hash.ZERO);
    }

    private Hash getBlockHash(RecordFile recordFile) {
        return Hash.fromHexString(StringUtils.substring(recordFile.getHash(), 0, 64));
    }
}
