// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.parser.contractlog;

import static org.hiero.mirror.importer.parser.contractlog.AbstractSyntheticContractLog.TRANSFER_SIGNATURE;

import jakarta.inject.Named;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.hiero.mirror.common.domain.contract.ContractLog;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.hiero.mirror.common.domain.transaction.RecordFile;
import org.hiero.mirror.common.util.DomainUtils;
import org.hiero.mirror.importer.domain.EntityIdService;
import org.hiero.mirror.importer.parser.record.RecordStreamFileListener;
import org.hiero.mirror.importer.parser.record.entity.EntityListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;

@Named
@Order(2)
@RequiredArgsConstructor
@ConditionalOnProperty(
        name = "hiero.mirror.importer.parser.record.entity.persist.synthetic-contract-log-evm-address-lookup",
        havingValue = "true")
public class SyntheticLogListener implements EntityListener, RecordStreamFileListener {
    private final EntityIdService entityIdService;
    private final List<SyntheticLogUpdater> syntheticLogParticipants = new ArrayList<>();
    private final Set<Long> evmAddressLookupIds = new HashSet<>();

    // TODO ADD Metrics

    @Override
    public void onEnd(RecordFile recordFile) {
        updateSyntheticContractLogs();
        syntheticLogParticipants.clear();
        evmAddressLookupIds.clear();
    }

    @Override
    public void onContractLog(ContractLog contractLog) {
        if (contractLog.getTopic0() == TRANSFER_SIGNATURE) {
            var senderId = DomainUtils.fromEvmAddress(contractLog.getTopic1());
            var receiverId = DomainUtils.fromEvmAddress(contractLog.getTopic2());
            if (!(senderId == null && receiverId == null)) {
                var updater = new SyntheticLogUpdater(senderId, receiverId, contractLog);
                syntheticLogParticipants.add(updater);
                evmAddressLookupIds.addAll(updater.getSearchIds());
            }
        }
    }

    private void updateSyntheticContractLogs() {
        var entityMap = entityIdService.lookupEvmAddresses(evmAddressLookupIds);
        syntheticLogParticipants.forEach(updater -> updater.updateContractLog(entityMap));
    }

    private record SyntheticLogUpdater(EntityId sender, EntityId receiver, ContractLog contractLog) {

        public List<Long> getSearchIds() {
            var ids = new ArrayList<Long>();

            if (receiver != null) {
                ids.add(receiver.getId());
            }

            if (sender != null) {
                ids.add(sender.getId());
            }

            return ids;
        }

        public void updateContractLog(Map<Long, byte[]> entityEvmAddresses) {
            if (sender != null && entityEvmAddresses.containsKey(sender.getId())) {
                contractLog.setTopic1(entityEvmAddresses.get(sender.getId()));
            }

            if (receiver != null && entityEvmAddresses.containsKey(receiver.getId())) {
                contractLog.setTopic2(entityEvmAddresses.get(receiver.getId()));
            }
        }
    }
}
