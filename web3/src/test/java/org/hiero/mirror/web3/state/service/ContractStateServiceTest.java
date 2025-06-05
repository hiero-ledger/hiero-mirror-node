// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.state.service;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.hiero.mirror.web3.evm.config.EvmConfiguration.CACHE_MANAGER_CONTRACT_SLOT_TRACKING;
import static org.hiero.mirror.web3.evm.config.EvmConfiguration.CACHE_MANAGER_CONTRACT_STATE;
import static org.hiero.mirror.web3.evm.config.EvmConfiguration.CACHE_NAME;
import static org.hiero.mirror.web3.evm.config.EvmConfiguration.CACHE_NAME_CONTRACT_SLOT_TRACKING;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.hiero.mirror.common.domain.contract.ContractState;
import org.hiero.mirror.common.domain.entity.EntityType;
import org.hiero.mirror.web3.Web3IntegrationTest;
import org.hiero.mirror.web3.repository.ContractStateRepository;
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
        final var contract = domainBuilder
                .entity()
                .customize(e -> e.type(EntityType.CONTRACT))
                .persist();
        int size = 90;
        persistContractStates(contract.getId(), size);

        List<ContractState> states = new ArrayList<>();
        int targetSlots = 10;
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
}
