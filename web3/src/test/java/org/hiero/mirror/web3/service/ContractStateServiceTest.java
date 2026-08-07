// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.service;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import org.apache.commons.codec.DecoderException;
import org.apache.commons.codec.binary.Hex;
import org.apache.commons.lang3.RandomUtils;
import org.hiero.mirror.common.domain.contract.ContractState;
import org.hiero.mirror.common.domain.entity.Entity;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.hiero.mirror.common.domain.entity.EntityType;
import org.hiero.mirror.web3.Web3IntegrationTest;
import org.hiero.mirror.web3.common.ContractCallContext;
import org.hiero.mirror.web3.repository.ContractStateRepository;
import org.hiero.mirror.web3.repository.EntityRepository;
import org.hiero.mirror.web3.repository.properties.CacheProperties;
import org.hiero.mirror.web3.state.ContractSlotValue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

@RequiredArgsConstructor
final class ContractStateServiceTest extends Web3IntegrationTest {

    private static final String EXPECTED_SLOT_VALUE = "test-value";

    private final CacheProperties cacheProperties;
    private final ContractStateService contractStateService;
    private final EntityRepository entityRepository;

    @MockitoSpyBean
    private ContractStateRepository contractStateRepository;

    @BeforeEach
    void setup() {
        cacheProperties.setEnableBatchContractSlotCaching(true);
        cacheProperties.setMaxSlotKeysPerBatch(200);
        cacheProperties.setMaxSlotsPerContract(1500);
        clearInvocations(contractStateRepository);
    }

    @Test
    void verifyCacheReturnsValuesAfterDeletion() {
        // Given
        final var contract = persistContract();
        final var contractId = contract.toEntityId();
        final var contractStates = persistContractStates(contract.getId(), 10);
        findStorage(contract, contractStates);
        clearInvocations(contractStateRepository);

        // When
        contractStateRepository.deleteAll();

        // Then - values are still served from cache without hitting the DB again
        findStorage(contract, contractStates);
        verify(contractStateRepository, never()).findStorageBatch(eq(contractId.getId()), any());
        verify(contractStateRepository, never()).findStorage(eq(contractId.getId()), any());
    }

    @Test
    void verifyPreviouslySearchedSlotsArePrefetchedInLaterBatches() throws InterruptedException {
        // Given
        final var contract = persistContract();
        final var contractId = contract.toEntityId();
        final var firstSlot = generateSlotKey(1);
        final var secondSlot = generateSlotKey(2);

        // When
        assertThat(findStorage(contractId, firstSlot)).isEmpty();
        // Wait for the first slot value to expire so it becomes a prefetch candidate again.
        Thread.sleep(3000);
        assertThat(findStorage(contractId, secondSlot)).isEmpty();

        // Then - the second DB search also includes the first previously searched slot
        final var slotsCaptor = ArgumentCaptor.forClass(byte[][].class);
        verify(contractStateRepository, times(2)).findStorageBatch(eq(contractId.getId()), slotsCaptor.capture());

        final var firstBatch = slotsCaptor.getAllValues().getFirst();
        final var secondBatch = slotsCaptor.getAllValues().get(1);
        assertThat(containsSlot(firstBatch, firstSlot)).isTrue();
        assertThat(containsSlot(secondBatch, secondSlot)).isTrue();
        assertThat(containsSlot(secondBatch, firstSlot)).isTrue();
    }

    @Test
    void verifyPrefetchedSlotValuesAreCached() throws InterruptedException {
        // Given
        final var contract = persistContract();
        final var contractId = contract.toEntityId();
        final var firstState = persistContractState(contract.getId(), 1);
        final var secondState = persistContractState(contract.getId(), 2);

        // Prime the tracked slots of the contract so both are prefetched together below.
        assertThat(findStorage(contractId, firstState.getSlot())).isPresent();
        assertThat(findStorage(contractId, secondState.getSlot())).isPresent();
        Thread.sleep(3000);
        clearInvocations(contractStateRepository);

        // When - a single lookup prefetches both slots
        assertThat(findStorage(contractId, firstState.getSlot())).get().isEqualTo(firstState.getValue());

        // Then - the prefetched value is served from the cache instead of a second query
        assertThat(findStorage(contractId, secondState.getSlot())).get().isEqualTo(secondState.getValue());
        verify(contractStateRepository, times(1)).findStorageBatch(eq(contractId.getId()), any());
    }

    @Test
    void verifyCachedSlotsAreNotAddedToLaterBatches() {
        // Given
        final var contract = persistContract();
        final var contractId = contract.toEntityId();
        final var cachedState = persistContractState(contract.getId(), 1);
        final var otherSlot = generateSlotKey(2);

        assertThat(findStorage(contractId, cachedState.getSlot())).isPresent();
        clearInvocations(contractStateRepository);

        // When - a slot is searched while the previously searched one is still cached
        assertThat(findStorage(contractId, otherSlot)).isEmpty();

        // Then - the cached slot is not queried again
        final var slotsCaptor = ArgumentCaptor.forClass(byte[][].class);
        verify(contractStateRepository, times(1)).findStorageBatch(eq(contractId.getId()), slotsCaptor.capture());
        assertThat(containsSlot(slotsCaptor.getValue(), cachedState.getSlot())).isFalse();
    }

    @Test
    void verifyCacheKeysAreNotDuplicated() {
        // Given
        final var contract = persistContract();
        final var contractId = contract.toEntityId();
        final var contractState = persistContractStates(contract.getId(), 1).getFirst();

        // When
        final var result = findStorage(contractId, contractState.getSlot());
        clearInvocations(contractStateRepository);
        final var result2 = findStorage(contractId, contractState.getSlot());

        // Then
        assertThat(result).get().isEqualTo(contractState.getValue());
        assertThat(result2).get().isEqualTo(contractState.getValue());
        verify(contractStateRepository, never()).findStorageBatch(eq(contractId.getId()), any());
        verify(contractStateRepository, never()).findStorage(eq(contractId.getId()), any());
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void verifyBatchFlagPropertyWorks(final boolean flagEnabled) {
        // Given
        cacheProperties.setEnableBatchContractSlotCaching(flagEnabled);
        final var contract = persistContract();
        final var contractId = contract.toEntityId();
        final var contractState = persistContractState(contract.getId(), 0);

        // When
        final var result = findStorage(contractId, contractState.getSlot());

        // Then
        assertThat(result).get().isEqualTo(contractState.getValue());
        if (flagEnabled) {
            verify(contractStateRepository, times(1)).findStorageBatch(eq(contractId.getId()), any());
            verify(contractStateRepository, never()).findStorage(eq(contractId.getId()), any());
        } else {
            verify(contractStateRepository, times(1)).findStorage(eq(contractId.getId()), any());
            verify(contractStateRepository, never()).findStorageBatch(eq(contractId.getId()), any());
        }
    }

    @Test
    void verifyMissingSlotValueIsCachedAsEmpty() {
        // Given
        final var contract = persistContract();
        final var contractId = contract.toEntityId();
        final var missingSlot = generateSlotKey(42);

        // When
        assertThat(findStorage(contractId, missingSlot)).isEmpty();
        clearInvocations(contractStateRepository);
        final var secondLookup = findStorage(contractId, missingSlot);

        // Then
        assertThat(secondLookup).isEmpty();
        verify(contractStateRepository, never()).findStorageBatch(eq(contractId.getId()), any());
        verify(contractStateRepository, never()).findStorage(eq(contractId.getId()), any());
    }

    @Test
    void verifyBatchQueryIsCappedAtMaxSlotKeysPerBatch() throws InterruptedException {
        // Given
        final int maxSlotKeysPerBatch = 2;
        final int slotCount = 5;
        cacheProperties.setMaxSlotKeysPerBatch(maxSlotKeysPerBatch);

        final var contract = persistContract();
        final var contractId = contract.toEntityId();

        for (int i = 0; i < slotCount; i++) {
            assertThat(findStorage(contractId, generateSlotKey(i))).isEmpty();
        }

        // Wait for contract state cache entries to expire so the next lookups hit the DB again.
        Thread.sleep(3000);
        clearInvocations(contractStateRepository);

        final var contractStates = new ArrayList<ContractState>();
        for (int i = 0; i < slotCount; i++) {
            contractStates.add(persistContractState(contract.getId(), i));
        }

        // When
        findStorage(contract, contractStates);

        // Then - batches stay within the configured cap
        verify(contractStateRepository, atLeastOnce())
                .findStorageBatch(eq(contractId.getId()), argThat(slots -> slots.length <= maxSlotKeysPerBatch));
        verify(contractStateRepository, never())
                .findStorageBatch(eq(contractId.getId()), argThat(slots -> slots.length > maxSlotKeysPerBatch));
    }

    @Test
    void verifySlotBeyondBatchCapStillReturnsCorrectValue() {
        // Given
        final int maxSlotKeysPerBatch = 2;
        cacheProperties.setMaxSlotKeysPerBatch(maxSlotKeysPerBatch);

        final var contract = persistContract();
        final var contractId = contract.toEntityId();

        for (int i = 0; i < maxSlotKeysPerBatch; i++) {
            assertThat(findStorage(contractId, generateSlotKey(100 + i))).isEmpty();
        }

        final var contractState = persistContractState(contract.getId(), 1);
        clearInvocations(contractStateRepository);

        // When
        final var result = findStorage(contractId, contractState.getSlot());

        // Then
        assertThat(result).get().isEqualTo(contractState.getValue());
        verify(contractStateRepository, never())
                .findStorageBatch(eq(contractId.getId()), argThat(slots -> slots.length > maxSlotKeysPerBatch));
    }

    @Test
    void verifyTheCorrectEntriesExistInTheCache() throws InterruptedException {
        // Given
        final var contract = persistContract();
        final var contractId = contract.toEntityId();
        final var contractStates = persistContractStates(contract.getId(), 6);

        // When - read slots in two groups
        final var firstThreeSlots = contractStates.subList(0, 3);
        findStorage(contract, firstThreeSlots);
        // Wait for cached values to expire so previously searched slots are prefetched again.
        Thread.sleep(3000);
        clearInvocations(contractStateRepository);

        final var secondThreeSlots = contractStates.subList(3, 6);
        findStorage(contract, secondThreeSlots);

        // Then - later batches prefetch previously searched slots
        final var slotsCaptor = ArgumentCaptor.forClass(byte[][].class);
        verify(contractStateRepository, atLeastOnce()).findStorageBatch(eq(contractId.getId()), slotsCaptor.capture());

        final var searchedSlots = new HashSet<ByteBuffer>();
        for (final var batch : slotsCaptor.getAllValues()) {
            for (final var slot : batch) {
                searchedSlots.add(ByteBuffer.wrap(slot));
            }
        }

        assertThat(searchedSlots.containsAll(firstThreeSlots.stream()
                        .map(state -> ByteBuffer.wrap(state.getSlot()))
                        .toList()))
                .isTrue();
        assertThat(searchedSlots.containsAll(secondThreeSlots.stream()
                        .map(state -> ByteBuffer.wrap(state.getSlot()))
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
        assertThat(findStorageByBlockTimestamp(
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
        assertThat(findStorageByBlockTimestamp(
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
        assertThat(findStorageByBlockTimestamp(
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

        // When
        final var executor = Executors.newFixedThreadPool(2);
        final var future1 = executor.submit(() -> findStorage(contractId, slot1));
        final var future2 = executor.submit(() -> findStorage(contractId, slot2));

        final var result1 = future1.get(2, TimeUnit.SECONDS);
        final var result2 = future2.get(2, TimeUnit.SECONDS);

        // Then
        assertThat(result1).get().isEqualTo(value1);
        assertThat(result2).get().isEqualTo(value2);

        executor.shutdown();
    }

    @Test
    void verifyConcurrentSameSlotLookupsCoalesceToSingleDbCall() throws Exception {
        // Given
        final var contract = persistContract();
        final var slot = generateSlotKey(1);
        final byte[] value = Hex.decodeHex("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa");
        final var contractId = contract.toEntityId();

        final var enteredBatch = new CountDownLatch(1);
        final var releaseBatch = new CountDownLatch(1);
        doAnswer(invocation -> {
                    enteredBatch.countDown();
                    if (!releaseBatch.await(2, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("Timed out waiting to release batch query");
                    }
                    return List.of(new ContractSlotValue(slot, value));
                })
                .when(contractStateRepository)
                .findStorageBatch(anyLong(), any());

        // When
        final int threadCount = 8;
        final var executor = Executors.newFixedThreadPool(threadCount);
        final var futures = new ArrayList<Future<Optional<byte[]>>>();
        for (int i = 0; i < threadCount; i++) {
            futures.add(executor.submit(() -> findStorage(contractId, slot)));
        }

        assertThat(enteredBatch.await(2, TimeUnit.SECONDS)).isTrue();
        Thread.sleep(100);
        releaseBatch.countDown();

        // Then
        for (final var future : futures) {
            assertThat(future.get(2, TimeUnit.SECONDS)).get().isEqualTo(value);
        }
        verify(contractStateRepository, times(1)).findStorageBatch(eq(contractId.getId()), any());
        verify(contractStateRepository, never()).findStorage(eq(contractId.getId()), any());
        executor.shutdown();
    }

    @Test
    void verifyConcurrentSameMissingSlotLookupsCoalesceToSingleDbCall() throws Exception {
        // Given
        final var contract = persistContract();
        final var missingSlot = generateSlotKey(99);
        final var contractId = contract.toEntityId();

        final var enteredBatch = new CountDownLatch(1);
        final var releaseBatch = new CountDownLatch(1);
        doAnswer(invocation -> {
                    enteredBatch.countDown();
                    if (!releaseBatch.await(2, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("Timed out waiting to release batch query");
                    }
                    return List.of();
                })
                .when(contractStateRepository)
                .findStorageBatch(anyLong(), any());

        // When
        final int threadCount = 8;
        final var executor = Executors.newFixedThreadPool(threadCount);
        final var futures = new ArrayList<Future<Optional<byte[]>>>();
        for (int i = 0; i < threadCount; i++) {
            futures.add(executor.submit(() -> findStorage(contractId, missingSlot)));
        }

        assertThat(enteredBatch.await(2, TimeUnit.SECONDS)).isTrue();
        Thread.sleep(100);
        releaseBatch.countDown();

        // Then
        for (final var future : futures) {
            assertThat(future.get(2, TimeUnit.SECONDS)).isEmpty();
        }
        verify(contractStateRepository, times(1)).findStorageBatch(eq(contractId.getId()), any());
        verify(contractStateRepository, never()).findStorage(eq(contractId.getId()), any());
        executor.shutdown();
    }

    @Test
    void verifyConcurrentSameSlotLookupsCoalesceToSingleDbCallWhenBatchDisabled() throws Exception {
        // Given
        cacheProperties.setEnableBatchContractSlotCaching(false);
        final var contract = persistContract();
        final var slot = generateSlotKey(1);
        final byte[] value = Hex.decodeHex("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa");
        final var contractId = contract.toEntityId();

        final var enteredLoad = new CountDownLatch(1);
        final var releaseLoad = new CountDownLatch(1);
        doAnswer(invocation -> {
                    enteredLoad.countDown();
                    if (!releaseLoad.await(2, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("Timed out waiting to release storage query");
                    }
                    return Optional.of(value);
                })
                .when(contractStateRepository)
                .findStorage(eq(contractId.getId()), any());

        final int threadCount = 8;
        final var executor = Executors.newFixedThreadPool(threadCount);
        final var futures = new ArrayList<Future<Optional<byte[]>>>();
        for (int i = 0; i < threadCount; i++) {
            futures.add(executor.submit(() -> findStorage(contractId, slot)));
        }

        assertThat(enteredLoad.await(2, TimeUnit.SECONDS)).isTrue();
        Thread.sleep(100);
        releaseLoad.countDown();

        for (final var future : futures) {
            assertThat(future.get(2, TimeUnit.SECONDS)).get().isEqualTo(value);
        }
        verify(contractStateRepository, times(1)).findStorage(eq(contractId.getId()), any());
        verify(contractStateRepository, never()).findStorageBatch(anyLong(), any());
        executor.shutdown();
    }

    @Test
    void verifyConcurrentBatchSlotLoadingReturnsCorrectValuesWithFourConcurrentValues() throws Exception {
        // Given
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

        // When - first parallel lookup
        final List<Future<Optional<byte[]>>> firstFutures = new ArrayList<>();
        for (var slot : slots) {
            firstFutures.add(executor.submit(() -> findStorage(contractId, slot)));
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
            secondFutures.add(executor.submit(() -> findStorage(contractId, slot)));
        }

        final List<Optional<byte[]>> secondResults = new ArrayList<>();
        for (var future : secondFutures) {
            secondResults.add(future.get(2, TimeUnit.SECONDS));
        }

        // Then
        for (int i = 0; i < 4; i++) {
            assertThat(secondResults.get(i)).get().isEqualTo(values.get(i));
        }
        executor.shutdown();
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

        // When
        final var result1 = findStorage(contractId, slot1);
        final var result2 = findStorage(contractId, slot2);

        Thread.sleep(6000);

        final var result1Again = findStorage(contractId, slot1);
        final var result2Again = findStorage(contractId, slot2);

        // Then
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
        assertThat(findStorageByBlockTimestamp(
                        EntityId.of(contractStateChange.getContractId()),
                        contractStateChange.getSlot(),
                        contractStateChange.getConsensusTimestamp()))
                .get()
                .isEqualTo(contractStateChange.getValueRead());
    }

    @Test
    void verifyHistoricalCacheReturnsValuesWithoutHittingDbAgain() {
        // Given
        final var contractStateChange = domainBuilder.contractStateChange().persist();
        final var contractId = EntityId.of(contractStateChange.getContractId());

        assertThat(findStorageByBlockTimestamp(
                        contractId, contractStateChange.getSlot(), contractStateChange.getConsensusTimestamp()))
                .get()
                .isEqualTo(contractStateChange.getValueWritten());
        clearInvocations(contractStateRepository);

        // When
        final var secondLookup = findStorageByBlockTimestamp(
                contractId, contractStateChange.getSlot(), contractStateChange.getConsensusTimestamp());

        // Then
        assertThat(secondLookup).get().isEqualTo(contractStateChange.getValueWritten());
        verify(contractStateRepository, never()).findStorageBatchByBlockTimestamp(anyLong(), any(), anyLong());
        verify(contractStateRepository, never()).findStorageByBlockTimestamp(anyLong(), any(), anyLong());
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void verifyHistoricalBatchFlagPropertyWorks(final boolean flagEnabled) {
        // Given
        cacheProperties.setEnableBatchContractSlotCaching(flagEnabled);
        final var contractStateChange = domainBuilder.contractStateChange().persist();
        final var contractId = EntityId.of(contractStateChange.getContractId());

        // When
        final var result = findStorageByBlockTimestamp(
                contractId, contractStateChange.getSlot(), contractStateChange.getConsensusTimestamp());

        // Then
        assertThat(result).get().isEqualTo(contractStateChange.getValueWritten());
        if (flagEnabled) {
            verify(contractStateRepository, times(1))
                    .findStorageBatchByBlockTimestamp(eq(contractId.getId()), any(), anyLong());
            verify(contractStateRepository, never()).findStorageByBlockTimestamp(anyLong(), any(), anyLong());
        } else {
            verify(contractStateRepository, times(1))
                    .findStorageByBlockTimestamp(eq(contractId.getId()), any(), anyLong());
            verify(contractStateRepository, never()).findStorageBatchByBlockTimestamp(anyLong(), any(), anyLong());
        }
    }

    @Test
    void verifyPreviouslySearchedHistoricalSlotsArePrefetchedInLaterBatches() {
        // Given
        final var firstChange = domainBuilder.contractStateChange().persist();
        final var secondChange = domainBuilder
                .contractStateChange()
                .customize(cs -> cs.contractId(firstChange.getContractId()))
                .persist();
        final var contractId = EntityId.of(firstChange.getContractId());

        // Prime tracked slots at the earlier timestamp (cached under a different historical key).
        assertThat(findStorageByBlockTimestamp(contractId, firstChange.getSlot(), firstChange.getConsensusTimestamp()))
                .isPresent();
        clearInvocations(contractStateRepository);

        // When - a later lookup at a different timestamp prefetches the previously tracked slot
        final var laterTimestamp = secondChange.getConsensusTimestamp();
        assertThat(findStorageByBlockTimestamp(contractId, secondChange.getSlot(), laterTimestamp))
                .isPresent();

        // Then
        final var slotsCaptor = ArgumentCaptor.forClass(byte[][].class);
        verify(contractStateRepository, times(1))
                .findStorageBatchByBlockTimestamp(eq(contractId.getId()), slotsCaptor.capture(), eq(laterTimestamp));

        assertThat(containsSlot(slotsCaptor.getValue(), secondChange.getSlot())).isTrue();
        assertThat(containsSlot(slotsCaptor.getValue(), firstChange.getSlot())).isTrue();
    }

    @Test
    void verifyMissingHistoricalSlotValueIsCachedAsEmpty() {
        // Given
        final var contractStateChange = domainBuilder.contractStateChange().persist();
        final var contractId = EntityId.of(contractStateChange.getContractId());
        final var missingSlot = generateSlotKey(42);
        final var timestamp = contractStateChange.getConsensusTimestamp();

        // When
        assertThat(findStorageByBlockTimestamp(contractId, missingSlot, timestamp))
                .isEmpty();
        clearInvocations(contractStateRepository);
        final var secondLookup = findStorageByBlockTimestamp(contractId, missingSlot, timestamp);

        // Then
        assertThat(secondLookup).isEmpty();
        verify(contractStateRepository, never()).findStorageBatchByBlockTimestamp(anyLong(), any(), anyLong());
        verify(contractStateRepository, never()).findStorageByBlockTimestamp(anyLong(), any(), anyLong());
    }

    @Test
    void verifyTheCorrectEntriesExistInTheCacheAfterContractAndStatesDeletion() {
        // Given
        final var contract = persistContract();
        final var contractId = contract.toEntityId();
        final var contractStates = persistContractStates(contract.getId(), 10);

        // When
        findStorage(contract, contractStates);
        entityRepository.deleteAll();

        // Then - cached values remain available after contract deletion
        findStorage(contract, contractStates);
        clearInvocations(contractStateRepository);
        contractStateRepository.deleteAll();

        // And after contract state deletion as well
        findStorage(contract, contractStates);
        verify(contractStateRepository, never()).findStorageBatch(eq(contractId.getId()), any());
        verify(contractStateRepository, never()).findStorage(eq(contractId.getId()), any());
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

    private byte[] generateSlotKey(final int index) {
        final byte[] slotKey = new byte[32];
        final byte[] indexBytes = ByteBuffer.allocate(4).putInt(index).array();
        System.arraycopy(indexBytes, 0, slotKey, 0, indexBytes.length);
        return slotKey;
    }

    private Optional<byte[]> findStorage(final EntityId contractId, final byte[] key) {
        return ContractCallContext.run(ctx -> contractStateService.findStorage(contractId, key));
    }

    private Optional<byte[]> findStorageByBlockTimestamp(
            final EntityId contractId, final byte[] key, final long blockTimestamp) {
        return ContractCallContext.run(
                ctx -> contractStateService.findStorageByBlockTimestamp(contractId, key, blockTimestamp));
    }

    private void findStorage(final Entity contract, final List<ContractState> slotKeyValuePairs) {
        for (final var state : slotKeyValuePairs) {
            final var result = findStorage(contract.toEntityId(), state.getSlot());
            assertThat(result.get()).isEqualTo(state.getValue());
        }
    }

    private static boolean containsSlot(final byte[][] slots, final byte[] expected) {
        return Arrays.stream(slots).anyMatch(slot -> Arrays.equals(slot, expected));
    }
}
