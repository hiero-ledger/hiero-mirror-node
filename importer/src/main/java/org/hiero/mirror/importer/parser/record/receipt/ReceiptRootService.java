// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.parser.record.receipt;

import jakarta.inject.Named;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.ArrayUtils;
import org.hiero.mirror.common.domain.contract.ContractLog;
import org.hiero.mirror.common.domain.contract.ContractResult;
import org.hiero.mirror.common.domain.entity.Entity;
import org.hiero.mirror.common.domain.transaction.EthereumTransaction;
import org.hiero.mirror.common.domain.transaction.RecordFile;
import org.hiero.mirror.importer.parser.record.entity.EntityListener;
import org.hiero.mirror.importer.parser.record.entity.EntityProperties;
import org.hiero.mirror.importer.parser.record.entity.ParserContext;
import org.hiero.mirror.importer.repository.EntityRepository;

/**
 * Streams each block's contract results and logs into a per-block {@link ReceiptRoot} as record items are processed,
 * and assigns {@code receiptsRoot} to every completed record file at the end of the parse. Streaming per record file
 * keeps the computation correct for multi-file batches without regrouping, since the single flush at the end of a
 * batch only sees the accumulated parser context.
 */
@Named
@RequiredArgsConstructor
public class ReceiptRootService implements EntityListener {

    private final EntityProperties entityProperties;
    private final EntityRepository entityRepository;
    private final ParserContext parserContext;

    // Not a map since record files parsed in one batch may still be value-equal when completed
    private final List<CompletedBlock> completed = new ArrayList<>();
    private final Map<Long, Integer> transactionTypes = new HashMap<>();
    private ReceiptRoot current = new ReceiptRoot();

    @Override
    public boolean isEnabled() {
        return entityProperties.getPersist().isContractResults();
    }

    @Override
    public void onContractResult(final ContractResult contractResult) {
        // Child (internal) contract results are excluded to match the relay, which computed the receipts root from
        // the REST API's default internal=false view (transaction_nonce = 0); their gas does not accumulate either
        if (contractResult.getTransactionNonce() == 0) {
            current.add(contractResult);
        }
    }

    @Override
    public void onContractLog(final ContractLog contractLog) {
        current.add(contractLog);
    }

    @Override
    public void onEthereumTransaction(final EthereumTransaction ethereumTransaction) {
        if (ethereumTransaction.getType() != null) {
            transactionTypes.put(ethereumTransaction.getConsensusTimestamp(), ethereumTransaction.getType());
        }
    }

    /**
     * Completes the record file currently being parsed: its streamed receipts are set aside for
     * {@link #updateReceiptsRoots()} and a fresh {@link ReceiptRoot} starts collecting the next file's.
     */
    public void complete(final RecordFile recordFile) {
        if (!isEnabled()) {
            return;
        }

        completed.add(new CompletedBlock(recordFile, current));
        current = new ReceiptRoot();
    }

    /**
     * Computes and assigns {@code receiptsRoot} on every completed record file. Must run after the synthetic log
     * topics and blooms have been finalized, since receipts are encoded from the (by then updated) domain objects.
     */
    public void updateReceiptsRoots() {
        try {
            if (completed.isEmpty()) {
                return;
            }

            final var evmAddresses = resolveEvmAddresses();
            for (final var block : completed) {
                block.recordFile.setReceiptsRoot(block.receiptRoot.getRootHash(transactionTypes, evmAddresses));
            }
        } finally {
            clear();
        }
    }

    /**
     * Discards all streamed state; invoked when a parse attempt ends so a retry starts clean.
     */
    public void clear() {
        completed.clear();
        transactionTypes.clear();
        current = new ReceiptRoot();
    }

    private Map<Long, byte[]> resolveEvmAddresses() {
        final var ids = new LinkedHashSet<Long>();
        for (final var block : completed) {
            ids.addAll(block.receiptRoot.getContractIds());
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

        // entities created within the parsed batch aren't visible in the repository yet
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

    private record CompletedBlock(RecordFile recordFile, ReceiptRoot receiptRoot) {}
}
