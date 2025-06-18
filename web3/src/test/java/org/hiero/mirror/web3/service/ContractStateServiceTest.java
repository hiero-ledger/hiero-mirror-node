// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.service;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.assertj.core.api.InstanceOfAssertFactories.LIST;
import static org.awaitility.Awaitility.await;
import static org.hiero.mirror.web3.evm.config.EvmConfiguration.CACHE_MANAGER_CONTRACT_SLOTS;
import static org.hiero.mirror.web3.evm.config.EvmConfiguration.CACHE_MANAGER_SLOTS_PER_CONTRACT;
import static org.hiero.mirror.web3.evm.config.EvmConfiguration.CACHE_NAME;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.awaitility.Durations;
import org.hiero.mirror.common.domain.contract.ContractState;
import org.hiero.mirror.common.domain.entity.Entity;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.hiero.mirror.common.domain.entity.EntityType;
import org.hiero.mirror.web3.Web3IntegrationTest;
import org.hiero.mirror.web3.repository.ContractStateRepository;
import org.hiero.mirror.web3.repository.EntityRepository;
import org.hiero.mirror.web3.repository.properties.CacheProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.cache.caffeine.CaffeineCache;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.testcontainers.shaded.org.apache.commons.lang3.RandomUtils;

@RequiredArgsConstructor
final class ContractStateServiceTest extends Web3IntegrationTest {

    private static final String EXPECTED_SLOT_VALUE = "test-value";

    @Qualifier(CACHE_MANAGER_CONTRACT_SLOTS)
    private final CaffeineCacheManager cacheManagerContractSlots;

    @Qualifier(CACHE_MANAGER_SLOTS_PER_CONTRACT)
    private final CaffeineCacheManager cacheManagerSlotsPerContract;

    private final CacheProperties cacheProperties;
    private final ContractStateService contractStateService;
    private final ContractStateRepository contractStateRepository;
    private final EntityRepository entityRepository;

    @BeforeEach
    void setup() {
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
        final int maxCacheSize = 10;
        cacheProperties.setSlotsPerContract("expireAfterAccess=2s,maximumSize=" + maxCacheSize);
        cacheManagerSlotsPerContract.setCacheSpecification(cacheProperties.getSlotsPerContract());
        final var contract = persistContract();

        final var contractState = persistContractStates(contract.getId(), 1).getFirst();
        final var slot = ByteBuffer.wrap(contractState.getSlot());
        final var result = contractStateService.findStorage(contract.toEntityId(), contractState.getSlot());

        final var cachedSlots = getCachedSlots(contract);
        assertThat(result).get().isEqualTo(contractState.getValue());
        assertThat(cachedSlots).asInstanceOf(LIST).hasSize(1).contains(slot);

        // When
        final var contractStates = persistContractStates(contract.getId(), maxCacheSize);
        findStorage(contract, contractStates);

        // Then
        getSlotsPerContractCache().cleanUp();

        await("cacheIsEvicted")
                .atMost(Durations.TWO_SECONDS)
                .pollInterval(Durations.ONE_HUNDRED_MILLISECONDS)
                .until(() -> getCachedSlots(contract).size() == maxCacheSize);

        final var finalCachedSlots = getCachedSlots(contract);

        assertThat(finalCachedSlots).asInstanceOf(LIST).hasSize(maxCacheSize).doesNotContain(slot);
    }

    @Test
    void verifyCacheKeysAreNotDuplicated() {
        // Given
        final var contract = persistContract();
        final var contractState = persistContractStates(contract.getId(), 1).getFirst();
        final var result = contractStateService.findStorage(contract.toEntityId(), contractState.getSlot());

        var cachedSlots = getCachedSlots(contract);
        assertThat(result).get().isEqualTo(contractState.getValue());
        assertThat(cachedSlots.size()).isEqualTo(1);
        assertThat(cachedSlots.contains(ByteBuffer.wrap(contractState.getSlot())))
                .isTrue();

        // When
        final var result2 = contractStateService.findStorage(contract.toEntityId(), contractState.getSlot());

        // Then
        assertThat(result2).get().isEqualTo(contractState.getValue());
        assertThat(cachedSlots.size()).isEqualTo(1);
        assertThat(cachedSlots.contains(ByteBuffer.wrap(contractState.getSlot())))
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
        assertThat(result).get().isEqualTo(contractState.getValue());
        assertThat(getCacheSizeContractSlot()).isEqualTo(flagEnabled ? contractSlotsCount : 0);
    }

    @Test
    void verifyTheCorrectEntriesExistInTheCache() {
        // Given
        final int maxCacheSize = 10;
        cacheProperties.setSlotsPerContract("expireAfterAccess=2s,maximumSize=" + maxCacheSize);
        cacheManagerSlotsPerContract.setCacheSpecification(cacheProperties.getSlotsPerContract());
        final var contract = persistContract();
        final var contractStates = persistContractStates(contract.getId(), maxCacheSize);

        // Read slots 1, 2, 3
        final var firstThreeSlots = contractStates.subList(0, 3);
        findStorage(contract, firstThreeSlots);
        var cachedSlots = getCachedSlots(contract);

        // Verify the cache contains only 0, 1, 2 slots
        assertThat(cachedSlots.size()).isEqualTo(3);
        assertThat(cachedSlots.containsAll(firstThreeSlots.stream()
                        .map(contractState -> ByteBuffer.wrap(contractState.getSlot()))
                        .toList()))
                .isTrue();

        // Read slots 3, 4, 5
        final var secondThreeSlots = contractStates.subList(3, 6);
        findStorage(contract, secondThreeSlots);
        cachedSlots = getCachedSlots(contract);

        // Verify the cache contains 0, 1, 2, 3, 4, 5 slots
        assertThat(cachedSlots.size()).isEqualTo(6);
        assertThat(cachedSlots.containsAll(firstThreeSlots.stream()
                        .map(contractState -> ByteBuffer.wrap(contractState.getSlot()))
                        .toList()))
                .isTrue();
        assertThat(cachedSlots.containsAll(secondThreeSlots.stream()
                        .map(contractState -> ByteBuffer.wrap(contractState.getSlot()))
                        .toList()))
                .isTrue();

        // Delete slots 0, 1, 2 from the cache
        for (int i = 0; i < 3; i++) {
            final var slotExistsInCache = cacheManagerSlotsPerContract
                    .getCache(contract.toEntityId().toString())
                    .evictIfPresent(ByteBuffer.wrap(firstThreeSlots.get(i).getSlot()));
            assertThat(slotExistsInCache).isTrue();
        }
        await("cacheIsEvicted")
                .atMost(Durations.TWO_SECONDS)
                .pollInterval(Durations.ONE_HUNDRED_MILLISECONDS)
                .until(() -> getCachedSlots(contract).size() == secondThreeSlots.size());

        cachedSlots = getCachedSlots(contract);
        assertThat(cachedSlots.containsAll(firstThreeSlots.stream()
                        .map(contractState -> ByteBuffer.wrap(contractState.getSlot()))
                        .toList()))
                .isFalse();
        assertThat(cachedSlots.containsAll(secondThreeSlots.stream()
                        .map(contractState -> ByteBuffer.wrap(contractState.getSlot()))
                        .toList()))
                .isTrue();

        // Read slots 6, 7, 8, 9
        final var lastFourSlots = contractStates.subList(6, 10);
        findStorage(contract, lastFourSlots);
        cachedSlots = getCachedSlots(contract);

        // Verify the cache contains 3, 4, 5, 6, 7, 8, 9 slots
        assertThat(cachedSlots.size()).isEqualTo(7);
        assertThat(cachedSlots.containsAll(secondThreeSlots.stream()
                        .map(contractState -> ByteBuffer.wrap(contractState.getSlot()))
                        .toList()))
                .isTrue();
        assertThat(cachedSlots.containsAll(lastFourSlots.stream()
                        .map(contractState -> ByteBuffer.wrap(contractState.getSlot()))
                        .toList()))
                .isTrue();
    }

    @Test
    void verifyLatestHistoricalContractSlotIsReturned() {
        // Given
        final var olderContractState = domainBuilder.contractStateChange().persist();
        final var contractStateChange = domainBuilder
                .contractStateChange()
                .customize(
                        cs -> cs.contractId(olderContractState.getContractId()).slot(olderContractState.getSlot()))
                .persist();

        // Then
        assertThat(contractStateService.findStorageByBlockTimestamp(
                        EntityId.of(olderContractState.getContractId()),
                        contractStateChange.getSlot(),
                        contractStateChange.getConsensusTimestamp()))
                .get()
                .isEqualTo(contractStateChange.getValueWritten());
    }

    @Test
    void verifyCorrectHistoricalContractSlotIsReturnedBasedOnBlock() {
        // Given
        final var olderContractState = domainBuilder.contractStateChange().persist();
        final var contractStateChange = domainBuilder
                .contractStateChange()
                .customize(
                        cs -> cs.contractId(olderContractState.getContractId()).slot(olderContractState.getSlot()))
                .persist();

        // Then
        assertThat(contractStateChange.getConsensusTimestamp() > olderContractState.getConsensusTimestamp())
                .isTrue();
        assertThat(contractStateService.findStorageByBlockTimestamp(
                        EntityId.of(olderContractState.getContractId()),
                        olderContractState.getSlot(),
                        olderContractState.getConsensusTimestamp()))
                .get()
                .isEqualTo(olderContractState.getValueWritten());
    }

    @Test
    void verifyOnlyExistingHistoricalContractSlotIsReturned() {
        // Given
        final var contractStateChange = domainBuilder.contractStateChange().persist();

        // Then
        assertThat(contractStateService.findStorageByBlockTimestamp(
                        EntityId.of(contractStateChange.getContractId()),
                        contractStateChange.getSlot(),
                        contractStateChange.getConsensusTimestamp() - 1))
                .isEmpty();
    }

    @Test
    void verifyDeletedHistoricalContractSlotIsNotReturned() {
        // Given
        final var olderContractState = domainBuilder.contractStateChange().persist();
        final var contractStateChange = domainBuilder
                .contractStateChange()
                .customize(cs -> cs.contractId(olderContractState.getContractId())
                        .slot(olderContractState.getSlot())
                        .valueWritten(null))
                .persist();

        // Then
        assertThat(contractStateChange.getConsensusTimestamp() > olderContractState.getConsensusTimestamp())
                .isTrue();
        assertThat(contractStateService.findStorageByBlockTimestamp(
                        EntityId.of(contractStateChange.getContractId()),
                        contractStateChange.getSlot(),
                        contractStateChange.getConsensusTimestamp()))
                .get()
                .isEqualTo(contractStateChange.getValueRead());
    }

    @Test
    void verifyTheCorrectEntriesExistInTheCacheAfterContractAndStatesDeletion() {
        // Given
        final int maxCacheSize = 10;
        cacheProperties.setSlotsPerContract("expireAfterAccess=2s,maximumSize=" + maxCacheSize);
        cacheManagerSlotsPerContract.setCacheSpecification(cacheProperties.getSlotsPerContract());
        final var contract = persistContract();
        final var contractStates = persistContractStates(contract.getId(), maxCacheSize);

        // When
        // Read and verify values exist in cache
        findStorage(contract, contractStates);

        // Delete contract
        entityRepository.deleteAll();

        // Then
        // Read and verify values exist in cache after contract deletion
        findStorage(contract, contractStates);

        contractStateRepository.deleteAll();

        // Read and verify values exist in cache after contract states deletion
        findStorage(contract, contractStates);
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

    private com.github.benmanes.caffeine.cache.Cache<Object, Object> getSlotsPerContractCache() {
        return ((CaffeineCache) cacheManagerSlotsPerContract.getCache(CACHE_NAME)).getNativeCache();
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
        for (final var state : slotKeyValuePairs) {
            final var result = contractStateService.findStorage(contract.toEntityId(), state.getSlot());
            assertThat(result.get()).isEqualTo(state.getValue());
        }
    }
}
