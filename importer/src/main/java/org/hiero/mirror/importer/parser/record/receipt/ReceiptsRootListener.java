// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.parser.record.receipt;

import jakarta.inject.Named;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.ArrayUtils;
import org.hiero.mirror.common.domain.contract.ContractLog;
import org.hiero.mirror.common.domain.contract.ContractResult;
import org.hiero.mirror.common.domain.entity.Entity;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.hiero.mirror.common.domain.transaction.EthereumTransaction;
import org.hiero.mirror.common.domain.transaction.RecordFile;
import org.hiero.mirror.importer.parser.record.RecordStreamFileListener;
import org.hiero.mirror.importer.parser.record.entity.EntityProperties;
import org.hiero.mirror.importer.parser.record.entity.ParserContext;
import org.hiero.mirror.importer.repository.EntityRepository;
import org.springframework.core.annotation.Order;

/**
 * Computes a block's Ethereum receipts-trie root at the record-file boundary and sets it on the {@link RecordFile}
 * before it is persisted.
 */
@Named
@Order(2)
@RequiredArgsConstructor
class ReceiptsRootListener implements RecordStreamFileListener {

    private final EntityProperties entityProperties;
    private final EntityRepository entityRepository;
    private final ParserContext parserContext;
    private final ReceiptAssembler receiptAssembler;
    private final ReceiptRootCalculator receiptRootCalculator;

    @Override
    public void onEnd(final RecordFile recordFile) {
        if (!entityProperties.getPersist().isContractResults()) {
            return;
        }

        final var recordFiles = parserContext.get(RecordFile.class);
        if (recordFiles.isEmpty()) {
            return;
        }

        final var contractResults = parserContext.get(ContractResult.class);
        final var contractLogs = parserContext.get(ContractLog.class);
        final var types = ethereumTransactionTypes();
        final var evmAddresses = resolveEvmAddresses(contractLogs);

        if (recordFiles.size() == 1) {
            recordFiles
                    .iterator()
                    .next()
                    .setReceiptsRoot(computeRoot(contractResults, contractLogs, types, evmAddresses));
            return;
        }

        final var consensusEndByStart = new TreeMap<Long, Long>();
        for (final var file : recordFiles) {
            consensusEndByStart.put(file.getConsensusStart(), file.getConsensusEnd());
        }

        final var resultsByFile = ReceiptBlockUtils.groupByBlock(
                contractResults, consensusEndByStart, ContractResult::getConsensusTimestamp);
        final var logsByFile =
                ReceiptBlockUtils.groupByBlock(contractLogs, consensusEndByStart, ContractLog::getConsensusTimestamp);

        for (final var file : recordFiles) {
            final var start = file.getConsensusStart();
            file.setReceiptsRoot(computeRoot(
                    resultsByFile.getOrDefault(start, List.of()),
                    logsByFile.getOrDefault(start, List.of()),
                    types,
                    evmAddresses));
        }
    }

    private byte[] computeRoot(
            final Collection<ContractResult> contractResults,
            final Collection<ContractLog> contractLogs,
            final Map<Long, Integer> types,
            final Map<Long, byte[]> evmAddresses) {
        return receiptRootCalculator.calculate(
                receiptAssembler.assemble(contractResults, contractLogs, types, evmAddresses));
    }

    private Map<Long, Integer> ethereumTransactionTypes() {
        final var types = new HashMap<Long, Integer>();
        for (final var ethereumTransaction : parserContext.get(EthereumTransaction.class)) {
            if (ethereumTransaction.getType() != null) {
                types.put(ethereumTransaction.getConsensusTimestamp(), ethereumTransaction.getType());
            }
        }

        return types;
    }

    private Map<Long, byte[]> resolveEvmAddresses(final Collection<ContractLog> contractLogs) {
        final var ids = new LinkedHashSet<Long>();
        for (final var contractLog : contractLogs) {
            if (!EntityId.isEmpty(contractLog.getContractId())) {
                ids.add(contractLog.getContractId().getId());
            }
        }

        if (ids.isEmpty()) {
            return Map.of();
        }

        final var addresses = new HashMap<Long, byte[]>(ids.size());
        for (final var mapping : entityRepository.findEvmAddressesByIds(ids)) {
            if (!ArrayUtils.isEmpty(mapping.getEvmAddress())) {
                addresses.put(mapping.getId(), mapping.getEvmAddress());
            }
        }

        for (final var id : ids) {
            if (!addresses.containsKey(id)) {
                final var entity = parserContext.get(Entity.class, id);
                if (entity != null && !ArrayUtils.isEmpty(entity.getEvmAddress())) {
                    addresses.put(id, entity.getEvmAddress());
                }
            }
        }

        return addresses;
    }
}
