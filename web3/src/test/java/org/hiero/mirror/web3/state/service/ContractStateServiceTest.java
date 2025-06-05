// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.state.service;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.hiero.mirror.web3.evm.config.EvmConfiguration.CACHE_MANAGER_CONTRACT_SLOT_TRACKING;
import static org.hiero.mirror.web3.evm.config.EvmConfiguration.CACHE_MANAGER_CONTRACT_STATE;
import static org.hiero.mirror.web3.evm.config.EvmConfiguration.CACHE_NAME;
import static org.hiero.mirror.web3.evm.config.EvmConfiguration.CACHE_NAME_CONTRACT_SLOT_TRACKING;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.hiero.mirror.common.domain.contract.ContractState;
import org.hiero.mirror.common.domain.entity.Entity;
import org.hiero.mirror.common.domain.entity.EntityType;
import org.hiero.mirror.web3.Web3IntegrationTest;
import org.hiero.mirror.web3.repository.ContractStateRepository;
import org.hiero.mirror.web3.repository.properties.CacheProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCache;

@RequiredArgsConstructor
class ContractStateServiceTest extends Web3IntegrationTest {

    private static final String EXPECTED_SLOT_VALUE = "test-value";

    @Qualifier(CACHE_MANAGER_CONTRACT_STATE)
    private final CacheManager cacheManagerContractState;

    @Qualifier(CACHE_MANAGER_CONTRACT_SLOT_TRACKING)
    private final CacheManager cacheManagerContractSlot;

    private final CacheProperties cacheProperties;
    private final ContractStateService contractStateService;
    private final ContractStateRepository contractStateRepository;

    @BeforeEach
    void cleanUp() {
        cacheManagerContractState.getCache(CACHE_NAME).clear();
        cacheManagerContractSlot.getCache(CACHE_NAME_CONTRACT_SLOT_TRACKING).clear();
    }

    @Test
    void verifyCacheReturnsValuesAfterDeletion() {
        // Given
        final var contract = persistContract();
        final var size = 90;
        persistContractStates(contract.getId(), size);

        List<ContractState> states = new ArrayList<>();
        final var targetSlots = 10;
        for (int i = size; i < size + targetSlots; i++) {
            states.add(persistContractState(contract.getId(), i));
        }

        // When
        for (ContractState state : states) {
            final var result = contractStateService.findStorage(contract.getId(), state.getSlot());
            assertThat(result.get()).isEqualTo(state.getValue());
        }
        final var slotsCache = getSlotsCache();
        final var slotsOfCache = (Set<String>) slotsCache.asMap().get(contract.getId());
        assertThat(getCacheSizeContractState()).isEqualTo(targetSlots);
        assertThat(getCacheSizeContractSlot()).isEqualTo(1);
        assertThat(slotsOfCache.size()).isEqualTo(targetSlots);
        contractStateRepository.deleteAll();

        // Then
        // verify get from cache
        for (ContractState state : states) {
            final var result = contractStateService.findStorage(contract.getId(), state.getSlot());
            assertThat(result.get()).isEqualTo(state.getValue());
        }
    }

    @Test
    void verifyTheOldestEntryInTheCacheIsDeleted() {
        // Given
        final var maxCacheSize = cacheProperties.getCachedSlotsMaxSize();
        final var contract = persistContract();

        List<ContractState> states = new ArrayList<>();
        for (int i = 0; i < maxCacheSize; i++) {
            states.add(persistContractState(contract.getId(), i));
        }

        // When
        for (ContractState state : states) {
            final var result = contractStateService.findStorage(contract.getId(), state.getSlot());
            assertThat(result.get()).isEqualTo(state.getValue());
        }

        final var slotsCache = getSlotsCache();
        final var slotsOfCache = (LinkedHashSet<String>) slotsCache.asMap().get(contract.getId());
        final var firstSlotEntryKey = encodeSlotKey(states.get(0).getSlot());
        final var secondSlotEntryKey = encodeSlotKey(states.get(1).getSlot());
        assertThat(slotsOfCache.getFirst()).isEqualTo(firstSlotEntryKey);

        // Persist additional slot and verify that it replaces the oldest entry in the cache.
        final var additionalContractState = persistContractState(contract.getId(), maxCacheSize + 1);
        final var additionalSlotEntryKey = encodeSlotKey(additionalContractState.getSlot());
        final var result = contractStateService.findStorage(contract.getId(), additionalContractState.getSlot());

        // Then
        assertThat(result.get()).isEqualTo(additionalContractState.getValue());
        assertThat(slotsOfCache.contains(firstSlotEntryKey)).isFalse();
        assertThat(slotsOfCache.contains(secondSlotEntryKey)).isTrue();
        assertThat(slotsOfCache.contains(additionalSlotEntryKey)).isTrue();
        assertThat(slotsOfCache.getFirst()).isEqualTo(secondSlotEntryKey);
    }

    @Test
    void verifyCacheKeysAreNotDuplicated() {
        // Given
        final var contract = persistContract();
        final var slotSizeThatWillNotBeQueried = 5;
        persistContractStates(contract.getId(), slotSizeThatWillNotBeQueried);

        List<ContractState> states = new ArrayList<>();
        final var targetSlots = 10;
        for (int i = slotSizeThatWillNotBeQueried; i < slotSizeThatWillNotBeQueried + targetSlots; i++) {
            states.add(persistContractState(contract.getId(), i));
        }

        // When
        for (ContractState state : states) {
            final var result = contractStateService.findStorage(contract.getId(), state.getSlot());
            assertThat(result.get()).isEqualTo(state.getValue());
        }
        final var slotsCache = getSlotsCache();
        final var slotsOfCache = (Set<String>) slotsCache.asMap().get(contract.getId());
        assertThat(getCacheSizeContractState()).isEqualTo(targetSlots);
        assertThat(getCacheSizeContractSlot()).isEqualTo(1);
        assertThat(slotsOfCache.size()).isEqualTo(targetSlots);

        // Then
        // If the same keys are read, verify that they will not be duplicated in the cache
        for (ContractState state : states) {
            final var result = contractStateService.findStorage(contract.getId(), state.getSlot());
            assertThat(result.get()).isEqualTo(state.getValue());
        }

        assertThat(getCacheSizeContractState()).isEqualTo(targetSlots);
        assertThat(getCacheSizeContractSlot()).isEqualTo(1);
        assertThat(slotsOfCache.size()).isEqualTo(targetSlots);
    }

    private Entity persistContract() {
        return domainBuilder
                .entity()
                .customize(e -> e.type(EntityType.CONTRACT))
                .persist();
    }

    private void persistContractStates(final long contractId, final int size) {
        for (int i = 0; i < size; i++) {
            persistContractState(contractId, i);
        }
    }

    private ContractState persistContractState(final long contractId, final int index) {
        final var slotKey = generateSlotKey(index);

        final byte[] value = (EXPECTED_SLOT_VALUE + index).getBytes();

        return domainBuilder
                .contractState()
                .customize(cs -> cs.contractId(contractId).slot(slotKey).value(value))
                .persist();
    }

    private byte[] generateSlotKey(final int index) {
        final byte[] slotKey = new byte[32];
        final byte[] indexBytes = ByteBuffer.allocate(4).putInt(index).array();
        System.arraycopy(indexBytes, 0, slotKey, 0, indexBytes.length);
        return slotKey;
    }

    private int getCacheSizeContractState() {
        return ((CaffeineCache) cacheManagerContractState.getCache(CACHE_NAME))
                .getNativeCache()
                .asMap()
                .size();
    }

    private int getCacheSizeContractSlot() {
        return getSlotsCache().asMap().size();
    }

    private com.github.benmanes.caffeine.cache.Cache<Object, Object> getSlotsCache() {
        return ((CaffeineCache) cacheManagerContractSlot.getCache(CACHE_NAME_CONTRACT_SLOT_TRACKING)).getNativeCache();
    }

    private String encodeSlotKey(final byte[] slotKey) {
        return Base64.getEncoder().encodeToString(slotKey);
    }
}
