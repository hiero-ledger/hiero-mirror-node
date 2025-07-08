// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.parser.contractlog;

import static org.hiero.mirror.common.util.DomainUtils.fromTrimmedEvmAddress;
import static org.hiero.mirror.common.util.DomainUtils.toEvmAddress;
import static org.hiero.mirror.common.util.DomainUtils.trim;

import com.github.benmanes.caffeine.cache.CacheLoader;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.CaffeineSpec;
import com.github.benmanes.caffeine.cache.LoadingCache;
import io.micrometer.core.annotation.Timed;
import jakarta.inject.Named;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import lombok.CustomLog;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.hiero.mirror.common.domain.contract.ContractLog;
import org.hiero.mirror.common.domain.entity.Entity;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.hiero.mirror.common.domain.transaction.RecordFile;
import org.hiero.mirror.importer.config.CacheProperties;
import org.hiero.mirror.importer.domain.EvmAddressMapping;
import org.hiero.mirror.importer.parser.record.RecordStreamFileListener;
import org.hiero.mirror.importer.parser.record.entity.EntityListener;
import org.hiero.mirror.importer.parser.record.entity.ParserContext;
import org.hiero.mirror.importer.repository.EntityRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;

@Named
@Order(2)
@CustomLog
@RequiredArgsConstructor
@ConditionalOnProperty(
        name = "hiero.mirror.importer.parser.record.entity.persist.synthetic-contract-log-evm-address-lookup",
        havingValue = "true")
final class SyntheticLogListener implements EntityListener, RecordStreamFileListener {
    static final int MAX_CACHE_LOAD_ENTRIES = 30000;

    @Getter(lazy = true)
    private final LoadingCache<Long, byte[]> evmCache = buildCache();

    private final ParserContext parserContext;
    private final CacheProperties cacheProperties;
    private final EntityRepository entityRepository;

    @Override
    @Timed
    public void onEnd(RecordFile recordFile) {
        final var candidates = parserContext.get(ContractLog.class).stream()
                .map(this::toLogUpdater)
                .filter(Objects::nonNull)
                .toList();

        final var keys = new HashSet<Long>(candidates.size());
        for (var updater : candidates) {
            keys.addAll(updater.getSearchIds());
        }

        final var entityMap = getEvmCache().getAll(keys);
        candidates.forEach(updater -> updater.updateContractLog(entityMap));
    }

    private LoadingCache<Long, byte[]> buildCache() {
        return Caffeine.from(CaffeineSpec.parse(cacheProperties.getEvmAddress()))
                .build(new CacheLoader<>() {
                    @Override
                    public byte[] load(Long key) {
                        return loadAll(Collections.singleton(key)).get(key);
                    }

                    @Override
                    public Map<Long, byte[]> loadAll(Set<? extends Long> keys) {
                        var evmAddressMapping = getCacheMisses(keys);
                        // Populate cache with entries not in cache or database
                        if (evmAddressMapping.size() != keys.size()) {
                            for (var key : keys) {
                                if (!evmAddressMapping.containsKey(key)) {
                                    evmAddressMapping.put(key, trim(toEvmAddress(EntityId.of(key))));
                                }
                            }
                        }
                        return evmAddressMapping;
                    }
                });
    }

    private Map<Long, byte[]> getCacheMisses(Set<? extends Long> keys) {
        if (keys.isEmpty()) {
            return Collections.emptyMap();
        }

        Map<Long, byte[]> loadedFromDb = new HashMap<>(keys.size());

        if (keys.size() <= MAX_CACHE_LOAD_ENTRIES) {
            processBatch(loadedFromDb, entityRepository.findEvmAddressesByIds(keys));
        } else {
            var i = 0;
            final var keyList = new ArrayList<>(keys);
            while (i < keyList.size()) {
                final var end = Math.min(i + MAX_CACHE_LOAD_ENTRIES, keyList.size());
                final var batch = keyList.subList(i, end);
                processBatch(loadedFromDb, entityRepository.findEvmAddressesByIds(batch));
                i += MAX_CACHE_LOAD_ENTRIES;
            }
        }

        return loadedFromDb;
    }

    private void processBatch(Map<Long, byte[]> result, List<EvmAddressMapping> evmAddressMappings) {
        for (var mapping : evmAddressMappings) {
            if (mapping.getEvmAddress() == null || mapping.getEvmAddress().length == 0) {
                result.put(mapping.getId(), trim(toEvmAddress(EntityId.of(mapping.getId()))));
            } else {
                result.put(mapping.getId(), trim(mapping.getEvmAddress()));
            }
        }
    }

    private SyntheticLogUpdater toLogUpdater(ContractLog contractLog) {
        if (contractLog.isSyntheticTransfer()) {
            var senderId = fromTrimmedEvmAddress(contractLog.getTopic1());
            var receiverId = fromTrimmedEvmAddress(contractLog.getTopic2());
            if (!(EntityId.isEmpty(senderId) && EntityId.isEmpty(receiverId))) {

                addNewEntityToCache(senderId, contractLog.getTopic1());
                addNewEntityToCache(receiverId, contractLog.getTopic2());

                return new SyntheticLogUpdater(senderId, receiverId, contractLog);
            }
        }
        return null;
    }

    private void addNewEntityToCache(EntityId entityId, byte[] defaultValue) {
        if (!EntityId.isEmpty(entityId)) {
            var contextEntity = parserContext.get(Entity.class, entityId.getId());
            if (contextEntity != null && !contextEntity.hasHistory()) {
                var evmAddress = contextEntity.getEvmAddress();
                if (evmAddress != null && evmAddress.length > 0) {
                    getEvmCache().put(entityId.getId(), trim(evmAddress));
                } else {
                    getEvmCache().put(entityId.getId(), defaultValue);
                }
            }
        }
    }

    private record SyntheticLogUpdater(EntityId sender, EntityId receiver, ContractLog contractLog) {

        public List<Long> getSearchIds() {
            var ids = new ArrayList<Long>();

            if (!EntityId.isEmpty(receiver)) {
                ids.add(receiver.getId());
            }

            if (!EntityId.isEmpty(sender)) {
                ids.add(sender.getId());
            }

            return ids;
        }

        public void updateContractLog(Map<Long, byte[]> entityEvmAddresses) {
            if (!EntityId.isEmpty(sender) && entityEvmAddresses.containsKey(sender.getNum())) {
                contractLog.setTopic1(entityEvmAddresses.get(sender.getId()));
            }

            if (!EntityId.isEmpty(receiver) && entityEvmAddresses.containsKey(receiver.getNum())) {
                contractLog.setTopic2(entityEvmAddresses.get(receiver.getId()));
            }
        }
    }
}
