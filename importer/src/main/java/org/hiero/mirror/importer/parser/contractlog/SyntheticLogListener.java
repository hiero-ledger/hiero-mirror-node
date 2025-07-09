// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.parser.contractlog;

import static org.hiero.mirror.common.util.DomainUtils.fromTrimmedEvmAddress;
import static org.hiero.mirror.common.util.DomainUtils.toEvmAddress;
import static org.hiero.mirror.common.util.DomainUtils.trim;

import com.github.benmanes.caffeine.cache.CacheLoader;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.CaffeineSpec;
import com.github.benmanes.caffeine.cache.LoadingCache;
import com.google.common.collect.Iterators;
import io.micrometer.core.annotation.Timed;
import jakarta.inject.Named;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Supplier;
import lombok.CustomLog;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.ArrayUtils;
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
        final var logUpdaters = parserContext.getTransient(SyntheticLogUpdater.class);
        final var keys = parserContext.getEvmAddressLookupIds();
        final var entityMap = getEvmCache().getAll(keys);

        for (var updater : logUpdaters) {
            updater.updateContractLog(entityMap);
        }
    }

    @Override
    public void onContractLog(ContractLog contractLog) {
        if (contractLog.isSyntheticTransfer()) {
            var updater = toLogUpdater(contractLog);
            if (updater != null) {
                updater.populateSearchIds();
                parserContext.addTransient(updater);
            }
        }
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
                        return getCacheMisses(keys);
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
            final var keyIterator = keys.iterator();
            final var batches = Iterators.partition(keyIterator, MAX_CACHE_LOAD_ENTRIES);

            while (batches.hasNext()) {
                List<? extends Long> batch = batches.next();
                processBatch(loadedFromDb, entityRepository.findEvmAddressesByIds(batch));
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
                return new SyntheticLogUpdater(senderId, receiverId, contractLog);
            }
        }
        return null;
    }

    @RequiredArgsConstructor
    class SyntheticLogUpdater {
        private final EntityId sender;
        private final EntityId receiver;
        private final ContractLog contractLog;

        public void populateSearchIds() {
            if (!EntityId.isEmpty(receiver)) {
                parserContext.getEvmAddressLookupIds().add(receiver.getId());
            }

            if (!EntityId.isEmpty(sender)) {
                parserContext.getEvmAddressLookupIds().add(sender.getId());
            }
        }

        public void updateContractLog(Map<Long, byte[]> entityEvmAddresses) {
            updateTopicField(
                    sender, entityEvmAddresses.get(sender.getId()), contractLog::setTopic1, contractLog::getTopic1);
            updateTopicField(
                    receiver, entityEvmAddresses.get(receiver.getId()), contractLog::setTopic2, contractLog::getTopic2);
        }

        public void updateTopicField(
                final EntityId key,
                final byte[] cachedResult,
                final Consumer<byte[]> setter,
                final Supplier<byte[]> defaultValue) {
            if (!EntityId.isEmpty(key)) {
                if (cachedResult != null) {
                    setter.accept(cachedResult);
                } else {
                    var contextEntity = parserContext.get(Entity.class, key.getId());
                    if (contextEntity != null && !ArrayUtils.isEmpty(contextEntity.getEvmAddress())) {
                        var trimmedEvmAddress = trim(contextEntity.getEvmAddress());
                        getEvmCache().put(key.getId(), trimmedEvmAddress);
                        setter.accept(trimmedEvmAddress);
                    } else {
                        getEvmCache().put(key.getId(), defaultValue.get());
                    }
                }
            }
        }
    }
}
