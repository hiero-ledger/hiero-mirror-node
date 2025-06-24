// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.parser.contractlog;

import static org.hiero.mirror.common.util.DomainUtils.*;
import static org.hiero.mirror.importer.config.CacheConfiguration.CACHE_EVM_ADDRESS;
import static org.hiero.mirror.importer.config.CacheConfiguration.CACHE_NAME;
import static org.hiero.mirror.importer.parser.contractlog.AbstractSyntheticContractLog.TRANSFER_SIGNATURE;

import io.micrometer.core.annotation.Timed;
import jakarta.inject.Named;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.CustomLog;
import org.hiero.mirror.common.domain.contract.ContractLog;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.hiero.mirror.common.domain.transaction.RecordFile;
import org.hiero.mirror.importer.parser.record.RecordStreamFileListener;
import org.hiero.mirror.importer.parser.record.entity.EntityListener;
import org.hiero.mirror.importer.parser.record.entity.EntityProperties;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

@Named
@Order(2)
@CustomLog
public class SyntheticLogListener implements EntityListener, RecordStreamFileListener {
    private static final String LOOKUP_QUERY =
            "select evm_address, num from entity where id in (:ids) and length(evm_address) > 0";
    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final Cache evmCache;
    private final EntityProperties entityProperties;
    private final List<SyntheticLogUpdater> syntheticLogParticipants = new ArrayList<>();
    private final Set<EntityId> evmAddressLookupIds = new HashSet<>();

    public SyntheticLogListener(
            NamedParameterJdbcTemplate jdbcTemplate,
            EntityProperties entityProperties,
            @Qualifier(CACHE_EVM_ADDRESS) CacheManager cacheManager) {
        this.jdbcTemplate = jdbcTemplate;
        this.evmCache = cacheManager.getCache(CACHE_NAME);
        this.entityProperties = entityProperties;
    }

    @Override
    @Timed
    public void onEnd(RecordFile recordFile) {
        updateSyntheticContractLogs();
        syntheticLogParticipants.clear();
        evmAddressLookupIds.clear();
    }

    @Override
    public void onContractLog(ContractLog contractLog) {
        if (entityProperties.getPersist().isSyntheticContractLogEvmAddressLookup()
                && contractLog.getTopic0() == TRANSFER_SIGNATURE) {
            var senderId = fromTrimmedEvmAddress(contractLog.getTopic1());
            var receiverId = fromTrimmedEvmAddress(contractLog.getTopic2());
            if (!(senderId == null && receiverId == null)) {
                var updater = new SyntheticLogUpdater(senderId, receiverId, contractLog);
                syntheticLogParticipants.add(updater);
                evmAddressLookupIds.addAll(updater.getSearchIds());
            }
        }
    }

    private void updateSyntheticContractLogs() {
        var entityMap = lookupEvmAddresses();
        syntheticLogParticipants.forEach(updater -> updater.updateContractLog(entityMap));
    }

    private Map<Long, byte[]> lookupEvmAddresses() {
        Map<Long, byte[]> result = new HashMap<>(evmAddressLookupIds.size());
        Set<EntityId> missing = new HashSet<>();

        try {
            // Try cache for all entity IDs
            for (var entityId : evmAddressLookupIds) {
                var num = entityId.getNum();
                var cached = evmCache.get(num, byte[].class);
                if (cached != null) {
                    result.put(num, cached);
                } else {
                    missing.add(entityId);
                }
            }

            // Query all missing Keys
            var dbResults = findEvmAddressesByIds(missing).stream()
                    .collect(Collectors.toMap(EvmAddressMapping::num, EvmAddressMapping::evmAddress));

            // Fill in results from DB (or default if still missing)
            for (var entityId : missing) {
                long num = entityId.getNum();
                byte[] evmAddress = dbResults.getOrDefault(num, trim(toEvmAddress(entityId)));
                evmCache.put(num, evmAddress);
                result.put(num, evmAddress);
            }
        } catch (Exception e) {
            log.error("Error looking up EVM addresses for entity IDs: {}", evmAddressLookupIds, e);
        }

        return result;
    }

    private List<EvmAddressMapping> findEvmAddressesByIds(Set<EntityId> ids) {
        if (ids.isEmpty()) {
            return List.of();
        }
        var params = new MapSqlParameterSource()
                .addValue("ids", ids.stream().map(EntityId::getId).toList());

        return jdbcTemplate.query(LOOKUP_QUERY, params, (rs, rowNum) -> {
            byte[] evmAddress = rs.getBytes("evm_address");
            long num = rs.getLong("num");
            return new EvmAddressMapping(evmAddress, num);
        });
    }

    record EvmAddressMapping(byte[] evmAddress, long num) {}

    private record SyntheticLogUpdater(EntityId sender, EntityId receiver, ContractLog contractLog) {

        public List<EntityId> getSearchIds() {
            var ids = new ArrayList<EntityId>();

            if (receiver != null && receiver != EntityId.EMPTY) {
                ids.add(receiver);
            }

            if (sender != null && sender != EntityId.EMPTY) {
                ids.add(sender);
            }

            return ids;
        }

        public void updateContractLog(Map<Long, byte[]> entityEvmAddresses) {
            if (!EntityId.isEmpty(sender) && entityEvmAddresses.containsKey(sender.getNum())) {
                contractLog.setTopic1(entityEvmAddresses.get(sender.getNum()));
            }

            if (!EntityId.isEmpty(receiver) && entityEvmAddresses.containsKey(receiver.getNum())) {
                contractLog.setTopic2(entityEvmAddresses.get(receiver.getNum()));
            }
        }
    }
}
