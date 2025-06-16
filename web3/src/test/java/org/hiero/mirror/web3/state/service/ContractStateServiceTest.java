// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.state.service;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.hiero.mirror.web3.evm.config.EvmConfiguration.CACHE_MANAGER_CONTRACT_SLOTS;
import static org.hiero.mirror.web3.evm.config.EvmConfiguration.CACHE_MANAGER_CONTRACT_STATE;
import static org.hiero.mirror.web3.evm.config.EvmConfiguration.CACHE_NAME;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.hiero.mirror.common.domain.contract.ContractState;
import org.hiero.mirror.common.domain.entity.Entity;
import org.hiero.mirror.common.domain.entity.EntityType;
import org.hiero.mirror.web3.Web3IntegrationTest;
import org.hiero.mirror.web3.repository.ContractStateRepository;
import org.hiero.mirror.web3.repository.properties.CacheProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCache;
import org.testcontainers.shaded.org.apache.commons.lang3.RandomUtils;

@RequiredArgsConstructor
class ContractStateServiceTest extends Web3IntegrationTest {

    private static final String EXPECTED_SLOT_VALUE = "test-value";

    @Qualifier(CACHE_MANAGER_CONTRACT_STATE)
    private final CacheManager cacheManagerContractState;

    @Qualifier(CACHE_MANAGER_CONTRACT_SLOTS)
    private final CacheManager cacheManagerContractSlots;

    private final CacheProperties cacheProperties;
    private final ContractStateService contractStateService;
    private final ContractStateRepository contractStateRepository;

    @BeforeEach
    void setup() {
        cacheManagerContractState.getCache(CACHE_NAME).clear();
        cacheManagerContractSlots.getCache(CACHE_NAME).clear();
        cacheProperties.setEnableBatchContractSlotCaching(true);
    }

    @Test
    void verifyCacheReturnsValuesAfterDeletion() {
        // Given
        final var contract = persistContract();
        var cachedSlots = getCachedSlots(contract);
        assertThat(cachedSlots.size()).isEqualTo(0);

        final var contractStates = persistContractStates(contract.getId(), 10);
        findStorage(contract, contractStates);
        cachedSlots = getCachedSlots(contract);
        assertThat(cachedSlots.size()).isEqualTo(10);
        // When
        contractStateRepository.deleteAll();
        // Then
        cachedSlots = getCachedSlots(contract);
        assertThat(cachedSlots.size()).isEqualTo(10);
    }

    @Test
    void verifyTheOldestEntryInTheCacheIsDeleted() {
        // Given
        final var maxCacheSize =
                Integer.parseInt(cacheProperties.getSlotsPerContract().replaceAll(".*maximumSize=(\\d+).*", "$1"));
        final var contract = persistContract();

        final var firstContractState =
                persistContractStates(contract.getId(), 1).getFirst();
        contractStateService.findStorage(contract.toEntityId(), firstContractState.getSlot());

        var cachedSlots = getCachedSlots(contract);
        assertThat(cachedSlots.size()).isEqualTo(1);
        assertThat(cachedSlots.contains(ByteBuffer.wrap(firstContractState.getSlot())))
                .isTrue();

        List<ContractState> contractStates = persistContractStates(contract.getId(), maxCacheSize);
        findStorage(contract, contractStates);

        cachedSlots = getCachedSlots(contract);
        assertThat(cachedSlots.size()).isEqualTo(maxCacheSize);
        assertThat(!cachedSlots.contains(ByteBuffer.wrap(firstContractState.getSlot())))
                .isTrue();
    }

    @Test
    void verifyCacheKeysAreNotDuplicated() {
        // Given
        final var contract = persistContract();
        final var firstContractState =
                persistContractStates(contract.getId(), 1).getFirst();
        contractStateService.findStorage(contract.toEntityId(), firstContractState.getSlot());

        var cachedSlots = getCachedSlots(contract);
        assertThat(cachedSlots.size()).isEqualTo(1);
        assertThat(cachedSlots.contains(ByteBuffer.wrap(firstContractState.getSlot())))
                .isTrue();
        // When
        contractStateService.findStorage(contract.toEntityId(), firstContractState.getSlot());
        // Then
        assertThat(cachedSlots.size()).isEqualTo(1);
        assertThat(cachedSlots.contains(ByteBuffer.wrap(firstContractState.getSlot())))
                .isTrue();
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void verifyBatchFlagPropertyWorks(final boolean flagEnabled) {
        // Given
        cacheProperties.setEnableBatchContractSlotCaching(flagEnabled);

        final var contractSlotsCount = 1;
        final var contract = persistContract();
        final var contractState = persistContractState(contract.getId(), 0);

        // When
        final var result = contractStateService.findStorage(contract.toEntityId(), contractState.getSlot());

        // Then
        // Assure that the slots cache is filled only when the flag is enabled.
        assertThat(getCacheSizeContractSlot()).isEqualTo(flagEnabled ? contractSlotsCount : 0);
    }

    private Entity persistContract() {
        return domainBuilder
                .entity()
                .customize(e -> e.type(EntityType.CONTRACT))
                .persist();
    }

    private List<ContractState> persistContractStates(final long contractId, final int size) {
        List<ContractState> contractStates = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            contractStates.add(persistContractState(contractId, RandomUtils.nextInt()));
        }
        return contractStates;
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

    private int getCacheSizeContractSlot() {
        return getSlotsCache().asMap().size();
    }

    private com.github.benmanes.caffeine.cache.Cache<Object, Object> getSlotsCache() {
        return ((CaffeineCache) cacheManagerContractSlots.getCache(CACHE_NAME)).getNativeCache();
    }

    public List<ByteBuffer> getCachedSlots(Entity contract) {
        var slotsCache = getSlotsCache();
        var slotsPerContractCache = slotsCache.asMap().get(contract.toEntityId());
        return slotsPerContractCache != null
                ? ((CaffeineCache) slotsPerContractCache)
                        .getNativeCache().asMap().keySet().stream()
                                .map(slot -> (ByteBuffer) slot)
                                .collect(Collectors.toList())
                : List.of();
    }

    public void findStorage(Entity contract, List<ContractState> slotKeyValuePairs) {
        for (ContractState state : slotKeyValuePairs) {
            final var result = contractStateService.findStorage(contract.toEntityId(), state.getSlot());
            assertThat(result.get()).isEqualTo(state.getValue());
        }
    }
}
