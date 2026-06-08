// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.service;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.assertj.core.api.InstanceOfAssertFactories.LIST;
import static org.awaitility.Awaitility.await;
import static org.hiero.mirror.web3.evm.config.EvmConfiguration.CACHE_MANAGER_CONTRACT_SLOTS;
import static org.hiero.mirror.web3.evm.config.EvmConfiguration.CACHE_MANAGER_CONTRACT_STATE;
import static org.hiero.mirror.web3.evm.config.EvmConfiguration.CACHE_NAME;

import com.hedera.hapi.node.base.ContractID;
import com.hedera.hapi.node.state.contract.SlotKey;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import org.apache.commons.codec.DecoderException;
import org.apache.commons.codec.binary.Hex;
import org.apache.commons.lang3.RandomUtils;
import org.awaitility.Durations;
import org.hiero.mirror.common.domain.contract.ContractState;
import org.hiero.mirror.common.domain.entity.Entity;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.hiero.mirror.common.domain.entity.EntityType;
import org.hiero.mirror.web3.Web3IntegrationTest;
import org.hiero.mirror.web3.Web3Properties;
import org.hiero.mirror.web3.common.ContractCallContext;
import org.hiero.mirror.web3.repository.ContractStateRepository;
import org.hiero.mirror.web3.repository.EntityRepository;
import org.hiero.mirror.web3.repository.properties.CacheProperties;
import org.hiero.mirror.web3.state.keyvalue.ContractStorageReadableKVState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.cache.Cache;
import org.springframework.cache.caffeine.CaffeineCache;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.cache.interceptor.SimpleKey;

@RequiredArgsConstructor
final class ContractStateServiceTest extends Web3IntegrationTest {

    private static final String EXPECTED_SLOT_VALUE = "test-value";

    @Qualifier(CACHE_MANAGER_CONTRACT_SLOTS)
    private final CaffeineCacheManager cacheManagerContractSlots;

    @Qualifier(CACHE_MANAGER_CONTRACT_STATE)
    private final CaffeineCacheManager cacheManagerContractState;

    private final CacheProperties cacheProperties;
    private final ContractStateService contractStateService;
    private final ContractStateRepository contractStateRepository;
    private final EntityRepository entityRepository;
    private final Web3Properties web3Properties;

    @BeforeEach
    void setup() {
        cacheProperties.setEnableBatchContractSlotCaching(true);
        web3Properties.setBatchSize(100);
    }

    @Test
    void verifyBatchLoadsAllPendingSlotsInSingleQuery() {
        // Given a contract and slots that are requested before they exist in the DB
        final var contract = persistContract();
        final var contractId = contract.toEntityId();
        final var slotA = generateSlotKey(1);
        final var slotB = generateSlotKey(2);
        final var slotC = generateSlotKey(3);

        // Requesting the slots while they are missing keeps their keys in the slots cache for the next batch
        assertThat(contractStateService.findStorage(contractId, slotA)).isEmpty();
        assertThat(contractStateService.findStorage(contractId, slotB)).isEmpty();
        assertThat(getCachedSlots(contract))
                .asInstanceOf(LIST)
                .contains(ByteBuffer.wrap(slotA), ByteBuffer.wrap(slotB));

        // When the values are later persisted and any one of the pending slots is requested
        final byte[] valueA = (EXPECTED_SLOT_VALUE + "A").getBytes();
        final byte[] valueB = (EXPECTED_SLOT_VALUE + "B").getBytes();
        final byte[] valueC = (EXPECTED_SLOT_VALUE + "C").getBytes();
        persistContractState(contract.getId(), slotA, valueA);
        persistContractState(contract.getId(), slotB, valueB);
        persistContractState(contract.getId(), slotC, valueC);

        final var result = contractStateService.findStorage(contractId, slotC);

        // Then the requested slot and all previously pending slots are loaded in a single batch and cached
        assertThat(result).get().isEqualTo(valueC);
        assertThat(getCachedState(contractId, slotA)).isEqualTo(valueA);
        assertThat(getCachedState(contractId, slotB)).isEqualTo(valueB);
        assertThat(getCachedState(contractId, slotC)).isEqualTo(valueC);

        // And all found slot keys are evicted from the slots cache
        assertThat(getCachedSlots(contract))
                .asInstanceOf(LIST)
                .doesNotContain(ByteBuffer.wrap(slotA), ByteBuffer.wrap(slotB), ByteBuffer.wrap(slotC));
    }

    @Test
    void verifyTheOldestEntryInTheSlotsCacheIsEvictedWhenMaxSizeExceeded() {
        // Given a bounded per-contract slots cache
        final int maxCacheSize = 10;
        cacheProperties.setSlotsPerContract("expireAfterAccess=2s,maximumSize=" + maxCacheSize);
        final var contract = persistContract();
        final var contractId = contract.toEntityId();

        // Requesting a slot that does not exist in the DB keeps its key in the slots cache (it is never found)
        final var oldestSlot = generateSlotKey(0);
        assertThat(contractStateService.findStorage(contractId, oldestSlot)).isEmpty();
        assertThat(getCachedSlots(contract)).asInstanceOf(LIST).hasSize(1).contains(ByteBuffer.wrap(oldestSlot));

        // When more missing slots are requested than the cache can hold
        for (int i = 1; i <= maxCacheSize; i++) {
            assertThat(contractStateService.findStorage(contractId, generateSlotKey(i)))
                    .isEmpty();
        }

        // Then the oldest entry is evicted and the cache does not exceed its maximum size

        await("cacheIsEvicted")
                .atMost(Durations.TWO_SECONDS)
                .pollInterval(Durations.ONE_HUNDRED_MILLISECONDS)
                .until(() -> getCachedSlots(contract).size() == maxCacheSize);

        assertThat(getCachedSlots(contract))
                .asInstanceOf(LIST)
                .hasSize(maxCacheSize)
                .doesNotContain(ByteBuffer.wrap(oldestSlot));
    }

    @Test
    void verifyFoundSlotValueIsCachedAndSlotKeyEvicted() {
        // Given
        final var contract = persistContract();
        final var contractId = contract.toEntityId();
        final var contractState = persistContractState(contract.getId(), 1);

        // When
        final var result = contractStateService.findStorage(contractId, contractState.getSlot());

        // Then the value is cached in the contract state cache and the slot key is evicted from the slots cache
        assertThat(result).get().isEqualTo(contractState.getValue());
        assertThat(getCachedState(contractId, contractState.getSlot())).isEqualTo(contractState.getValue());
        assertThat(getCachedSlots(contract))
                .asInstanceOf(LIST)
                .doesNotContain(ByteBuffer.wrap(contractState.getSlot()));

        // When the same slot is requested again
        final var result2 = contractStateService.findStorage(contractId, contractState.getSlot());

        // Then it is served from the contract state cache and the slot key remains evicted from the slots cache
        assertThat(result2).get().isEqualTo(contractState.getValue());
        assertThat(getCachedSlots(contract))
                .asInstanceOf(LIST)
                .doesNotContain(ByteBuffer.wrap(contractState.getSlot()));
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
    void verifyMissingSlotKeyRemainsInSlotsCacheWhileFoundSlotKeyIsEvicted() {
        // Given a contract with one existing slot
        final var contract = persistContract();
        final var contractId = contract.toEntityId();
        final var existingState = persistContractState(contract.getId(), 1);
        final var missingSlot = generateSlotKey(999);

        // When a missing slot is requested, its key stays in the slots cache (it is never found)
        assertThat(contractStateService.findStorage(contractId, missingSlot)).isEmpty();
        assertThat(getCachedSlots(contract)).asInstanceOf(LIST).contains(ByteBuffer.wrap(missingSlot));

        // When an existing slot is requested, its value is cached and the key evicted from the slots cache
        assertThat(contractStateService.findStorage(contractId, existingState.getSlot()))
                .get()
                .isEqualTo(existingState.getValue());
        assertThat(getCachedState(contractId, existingState.getSlot())).isEqualTo(existingState.getValue());

        // Then only the missing slot key remains in the slots cache
        final var cachedSlots = getCachedSlots(contract);
        assertThat(cachedSlots).asInstanceOf(LIST).contains(ByteBuffer.wrap(missingSlot));
        assertThat(cachedSlots).asInstanceOf(LIST).doesNotContain(ByteBuffer.wrap(existingState.getSlot()));
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
    void verifyWarmStorageKeysCachesDiscoveredSlots() {
        // Given
        final var contract = persistContract();
        final var contractId = contract.toEntityId();
        final var contractStates = persistContractStates(contract.getId(), 3);

        final var context = ContractCallContext.get();
        context.setStorageDiscoveryModeFinished(true);
        final var readCache = context.getReadCacheState(ContractStorageReadableKVState.STATE_ID);
        for (final var state : contractStates) {
            readCache.put(toSlotKey(contractId, state.getSlot()), Bytes.EMPTY);
        }

        // When
        contractStateService.warmStorageKeys(contractId);

        // Then
        for (final var state : contractStates) {
            assertThat(getCachedState(contractId, state.getSlot())).isEqualTo(state.getValue());
        }
    }

    @Test
    void verifyWarmStorageKeysDoesNotCacheWhenDiscoveryNotFinished() {
        // Given
        final var contract = persistContract();
        final var contractId = contract.toEntityId();
        final var contractStates = persistContractStates(contract.getId(), 3);

        final var context = ContractCallContext.get();
        // storageDiscoveryModeFinished defaults to false, so the discovered keys are ignored
        final var readCache = context.getReadCacheState(ContractStorageReadableKVState.STATE_ID);
        for (final var state : contractStates) {
            readCache.put(toSlotKey(contractId, state.getSlot()), Bytes.EMPTY);
        }

        // When
        contractStateService.warmStorageKeys(contractId);

        // Then
        for (final var state : contractStates) {
            assertThat(getCachedState(contractId, state.getSlot())).isNull();
        }
    }

    @Test
    void verifyWarmStorageKeysIgnoresSlotsFromOtherContracts() {
        // Given
        final var contract = persistContract();
        final var contractId = contract.toEntityId();
        final var matchingState = persistContractState(contract.getId(), 1);

        final var otherContract = persistContract();
        final var otherContractId = otherContract.toEntityId();
        final var otherState = persistContractState(otherContract.getId(), 2);

        final var context = ContractCallContext.get();
        context.setStorageDiscoveryModeFinished(true);
        final var readCache = context.getReadCacheState(ContractStorageReadableKVState.STATE_ID);
        readCache.put(toSlotKey(contractId, matchingState.getSlot()), Bytes.EMPTY);
        readCache.put(toSlotKey(otherContractId, otherState.getSlot()), Bytes.EMPTY);

        // When loading only for the first contract
        contractStateService.warmStorageKeys(contractId);

        // Then only the first contract's slot is cached
        assertThat(getCachedState(contractId, matchingState.getSlot())).isEqualTo(matchingState.getValue());
        assertThat(getCachedState(otherContractId, otherState.getSlot())).isNull();
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
    void verifyConcurrentBatchSlotLoadingReturnsCorrectValues() throws Exception {
        // Given
        final var contract = persistContract();
        final var slot1 = generateSlotKey(1);
        final var slot2 = generateSlotKey(2);

        final byte[] value1 = Hex.decodeHex("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa");
        final byte[] value2 = Hex.decodeHex("bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb");

        persistContractState(contract.getId(), slot1, value1);
        persistContractState(contract.getId(), slot2, value2);

        final var contractId = contract.toEntityId();

        // When: run two parallel lookups
        final var executor = Executors.newFixedThreadPool(2);
        final var future1 = executor.submit(() -> contractStateService.findStorage(contractId, slot1));
        final var future2 = executor.submit(() -> contractStateService.findStorage(contractId, slot2));

        final var result1 = future1.get(2, TimeUnit.SECONDS);
        final var result2 = future2.get(2, TimeUnit.SECONDS);

        // Then
        assertThat(result1).get().isEqualTo(value1);
        assertThat(result2).get().isEqualTo(value2);

        executor.shutdown();
    }

    @Test
    void verifyConcurrentBatchSlotLoadingReturnsCorrectValuesWithFourConcurrentValues() throws Exception {
        // Given
        try {
            final int maxCacheSize = 3;
            cacheProperties.setSlotsPerContract("expireAfterAccess=10s,maximumSize=" + maxCacheSize);
            final var contract = persistContract();

            final var slots = List.of(generateSlotKey(1), generateSlotKey(2), generateSlotKey(3), generateSlotKey(4));

            final var values = List.of(
                    Hex.decodeHex("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"),
                    Hex.decodeHex("bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"),
                    Hex.decodeHex("cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc"),
                    Hex.decodeHex("dddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddd"));

            final var contractId = contract.toEntityId();
            for (int i = 0; i < 4; i++) {
                persistContractState(contract.getId(), slots.get(i), values.get(i));
            }

            final var executor = Executors.newFixedThreadPool(4);

            // First parallel lookup
            final List<Future<Optional<byte[]>>> firstFutures = new ArrayList<>();
            for (var slot : slots) {
                firstFutures.add(executor.submit(() -> contractStateService.findStorage(contractId, slot)));
            }

            final List<Optional<byte[]>> firstResults = new ArrayList<>();
            for (var future : firstFutures) {
                firstResults.add(future.get(2, TimeUnit.SECONDS));
            }

            for (int i = 0; i < 4; i++) {
                assertThat(firstResults.get(i)).get().isEqualTo(values.get(i));
            }

            // Wait for contract state cache to expire
            Thread.sleep(6000);

            final List<Future<Optional<byte[]>>> secondFutures = new ArrayList<>();
            for (var slot : slots) {
                secondFutures.add(executor.submit(() -> contractStateService.findStorage(contractId, slot)));
            }

            final List<Optional<byte[]>> secondResults = new ArrayList<>();
            for (var future : secondFutures) {
                secondResults.add(future.get(2, TimeUnit.SECONDS));
            }

            for (int i = 0; i < 4; i++) {
                assertThat(secondResults.get(i)).get().isEqualTo(values.get(i));
            }
            executor.shutdown();
        } finally {
            // reset cache
            final int initialSize = 10;
            cacheProperties.setSlotsPerContract("expireAfterAccess=2s,maximumSize=" + initialSize);
        }
    }

    @Test
    void verifyBatchSlotLoadingReturnsCorrectValuesSequentially() throws InterruptedException, DecoderException {
        // Given
        final var contract = persistContract();
        final var slot1 = generateSlotKey(1);
        final var slot2 = generateSlotKey(2);

        final byte[] value1 = Hex.decodeHex("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa");
        final byte[] value2 = Hex.decodeHex("bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb");

        persistContractState(contract.getId(), slot1, value1);
        persistContractState(contract.getId(), slot2, value2);

        final var contractId = contract.toEntityId();

        // When: read both slots one after the other
        final var result1 = contractStateService.findStorage(contractId, slot1);
        final var result2 = contractStateService.findStorage(contractId, slot2);

        Thread.sleep(6000);

        final var result1Again = contractStateService.findStorage(contractId, slot1);
        final var result2Again = contractStateService.findStorage(contractId, slot2);

        // Then: both should return the correct values
        assertThat(result1).get().isEqualTo(value1);
        assertThat(result2).get().isEqualTo(value2);
        assertThat(result1Again).get().isEqualTo(value1);
        assertThat(result2Again).get().isEqualTo(value2);
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

    @ParameterizedTest
    @ValueSource(ints = {50, 100, 150})
    void verifyBatchQueryReturnsAllValuesForDifferentBatchSizes(final int batchSize) {
        // Given: 200 slots. Reset slotsPerContract cache spec explicitly: prior tests may have reduced maximumSize to
        // 10.
        web3Properties.setBatchSize(batchSize);
        cacheProperties.setSlotsPerContract("expireAfterAccess=5m,maximumSize=1500");

        final int slotCount = 200;
        final var contract = persistContract();
        final var contractStates = persistContractStates(contract.getId(), slotCount);

        // When / Then: all slot values are returned correctly on the first read
        findStorage(contract, contractStates);

        // Validate cached slot values survive DB deletion (served from contractStateCache)
        contractStateRepository.deleteAll();
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

    private void persistContractState(final long contractId, final byte[] slotKey, final byte[] value) {
        domainBuilder
                .contractState()
                .customize(cs -> cs.contractId(contractId).slot(slotKey).value(value))
                .persist();
    }

    private SlotKey toSlotKey(final EntityId contractId, final byte[] slot) {
        final var contractID = ContractID.newBuilder()
                .shardNum(contractId.getShard())
                .realmNum(contractId.getRealm())
                .contractNum(contractId.getNum())
                .build();
        return new SlotKey(contractID, Bytes.wrap(slot));
    }

    private Cache getContractStateCache() {
        return cacheManagerContractState.getCache(CACHE_NAME);
    }

    private byte[] getCachedState(final EntityId contractId, final byte[] slot) {
        return getContractStateCache().get(new SimpleKey(contractId, slot), byte[].class);
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
                                .map(ByteBuffer.class::cast)
                                .toList()
                : List.of();
    }

    public void findStorage(Entity contract, List<ContractState> slotKeyValuePairs) {
        for (final var state : slotKeyValuePairs) {
            final var result = contractStateService.findStorage(contract.toEntityId(), state.getSlot());
            assertThat(result).contains(state.getValue());
        }
    }
}
