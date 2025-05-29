// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.state.service;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.hiero.mirror.web3.evm.config.EvmConfiguration.CACHE_MANAGER_CONTRACT_STATE;
import static org.hiero.mirror.web3.evm.config.EvmConfiguration.CACHE_NAME;

import java.nio.ByteBuffer;
import java.util.Optional;
import java.util.stream.StreamSupport;
import lombok.RequiredArgsConstructor;
import org.hiero.mirror.common.domain.contract.ContractState;
import org.hiero.mirror.common.domain.entity.EntityType;
import org.hiero.mirror.web3.Web3IntegrationTest;
import org.hiero.mirror.web3.common.ContractCallContext;
import org.hiero.mirror.web3.evm.properties.MirrorNodeEvmProperties;
import org.hiero.mirror.web3.repository.ContractStateRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCache;

@RequiredArgsConstructor
class ContractStateServiceTest extends Web3IntegrationTest {

    private static final byte[] EMPTY_BYTE_ARRAY = new byte[32];
    private static final String EXPECTED_SLOT_VALUE = "test-value";

    @Qualifier(CACHE_MANAGER_CONTRACT_STATE)
    private final CacheManager cacheManager;

    private final ContractStateService contractStateService;
    private final ContractStateRepository contractStateRepository;
    private final MirrorNodeEvmProperties mirrorNodeEvmProperties;

    @BeforeEach
    void cleanUp() {
        cacheManager.getCache(CACHE_NAME).clear();
        mirrorNodeEvmProperties.setBulkLoadStorage(true);
    }

    @Test
    void testFindSlotValueHappyPath() {
        // Given
        final var contract = domainBuilder
                .entity()
                .customize(e -> e.type(EntityType.CONTRACT))
                .persist();
        final var contractState = persistContractState(contract.getId(), 0);

        // When
        Optional<byte[]> result = contractStateService.findSlotValue(contract.toEntityId(), contractState.getSlot());

        // Then
        assertThat(result).isPresent().contains(contractState.getValue());
        assertThat(getCacheSize()).isEqualTo(1);
    }

    @Test
    void testFindSlotValueNotPresentInBulkLoad() {
        // Given
        final var contract = domainBuilder
                .entity()
                .customize(e -> e.type(EntityType.CONTRACT))
                .persist();
        final var firstContractState = persistContractState(contract.getId(), 0);

        // When
        Optional<byte[]> result =
                contractStateService.findSlotValue(contract.toEntityId(), firstContractState.getSlot());

        // Add new cache value after the bulk has already passed
        final var secondContractState = persistContractState(contract.getId(), 1);
        Optional<byte[]> result2 =
                contractStateService.findSlotValue(contract.toEntityId(), secondContractState.getSlot());

        // Then
        assertThat(result).isPresent().contains(firstContractState.getValue());
        assertThat(result2).isPresent().contains(secondContractState.getValue());
        assertThat(getCacheSize()).isEqualTo(2);
    }

    @Test
    void testFindSlotOutsideOfBulkLoad() {
        // Given
        int outsideBulkLoadIndex = 5001;
        final var contract = domainBuilder
                .entity()
                .customize(e -> e.type(EntityType.CONTRACT))
                .persist();
        persistContractStates(contract.getId(), outsideBulkLoadIndex);

        final var contractStateOutsideOfRange = persistContractState(contract.getId(), outsideBulkLoadIndex);

        // When
        Optional<byte[]> result =
                contractStateService.findSlotValue(contract.toEntityId(), contractStateOutsideOfRange.getSlot());
        assertThat(result).isPresent().contains((EXPECTED_SLOT_VALUE + outsideBulkLoadIndex).getBytes());

        contractStateRepository.delete(contractStateOutsideOfRange);

        Optional<byte[]> result2 =
                contractStateService.findSlotValue(contract.toEntityId(), contractStateOutsideOfRange.getSlot());
        // Then
        assertThat(result2).isPresent().contains((EXPECTED_SLOT_VALUE + outsideBulkLoadIndex).getBytes());
    }

    @Test
    void testBulkLoadThenDeleteAllAndVerifyResultIsReturnedFromCache() {
        // Given
        long size = 5;
        final var contract = domainBuilder
                .entity()
                .customize(e -> e.type(EntityType.CONTRACT))
                .persist();
        final byte[] value = EXPECTED_SLOT_VALUE.getBytes();
        final byte[] slotKey1 = EMPTY_BYTE_ARRAY;
        slotKey1[0] = 0x01;

        for (int i = 0; i < size; i++) {
            final byte[] slotKey = EMPTY_BYTE_ARRAY;
            slotKey[0] = (byte) i;
            domainBuilder
                    .contractState()
                    .customize(
                            cs -> cs.contractId(contract.getId()).slot(slotKey).value(value))
                    .persist();
        }

        // This will bulk load all slots in cache
        // When
        Optional<byte[]> result = contractStateService.findSlotValue(contract.toEntityId(), slotKey1);
        assertThat(result).isPresent().contains(value);
        assertThat(getCacheSize()).isEqualTo(size);

        contractStateRepository.deleteAll();
        final long countAfterDeleted = StreamSupport.stream(
                        contractStateRepository.findAll().spliterator(), false)
                .count();
        assertThat(countAfterDeleted).isZero();
        Optional<byte[]> resultFromCache = contractStateService.findSlotValue(contract.toEntityId(), slotKey1);
        assertThat(resultFromCache).isPresent().contains(value);
    }

    @Test
    void testBulkLoadStorageWithFeatureFlagDisabled() {
        // Given
        final var contract = domainBuilder
                .entity()
                .customize(e -> e.type(EntityType.CONTRACT))
                .persist();

        persistContractStates(contract.getId(), 256);
        mirrorNodeEvmProperties.setBulkLoadStorage(false);

        // When
        Optional<byte[]> result = contractStateService.findSlotValue(contract.toEntityId(), generateSlotKey(0));

        // Then
        assertThat(result).isPresent().contains((EXPECTED_SLOT_VALUE + 0).getBytes());
        assertThat(getCacheSize()).isEqualTo(1);
    }

    @Test
    void testFindSlotValueHistorical() {
        // Given
        final var consensusTimestamp = 123456789L;
        final var contract = domainBuilder
                .entity()
                .customize(e -> e.type(EntityType.CONTRACT).createdTimestamp(consensusTimestamp))
                .persist();
        final byte[] slotKey = EMPTY_BYTE_ARRAY;
        final byte[] value = EXPECTED_SLOT_VALUE.getBytes();

        domainBuilder
                .contractStateChange()
                .customize(cs -> cs.contractId(contract.getId())
                        .slot(slotKey)
                        .valueRead(value)
                        .valueWritten(null)
                        .consensusTimestamp(consensusTimestamp))
                .persist();

        // When
        Optional<byte[]> result =
                contractStateService.findStorageByBlockTimestamp(contract.toEntityId(), slotKey, consensusTimestamp);

        // Then
        assertThat(result).isPresent().contains(value);
        assertThat(getCacheSize()).isZero();
    }

    @Test
    void testCacheDoesNotIncreaseInSize() throws InterruptedException {
        // Given
        final int cacheLimit = 5000;
        final var contract = domainBuilder
                .entity()
                .customize(e -> e.type(EntityType.CONTRACT))
                .persist();
        persistContractStates(contract.getId(), 6500);

        // When
        Optional<byte[]> result1 = contractStateService.findSlotValue(contract.toEntityId(), generateSlotKey(0));

        ContractCallContext.get().clear();
        Thread.sleep(2500);

        Optional<byte[]> result2 = contractStateService.findSlotValue(contract.toEntityId(), generateSlotKey(1));

        // Then
        final var expectedValue1 = (EXPECTED_SLOT_VALUE + 0).getBytes();
        final var expectedValue2 = (EXPECTED_SLOT_VALUE + 1).getBytes();
        assertThat(result1).isPresent().contains(expectedValue1);
        assertThat(result2).isPresent().contains(expectedValue2);
        assertThat(getCacheSize()).isEqualTo(cacheLimit);
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

    private int getCacheSize() {
        return ((CaffeineCache) cacheManager.getCache(CACHE_NAME))
                .getNativeCache()
                .asMap()
                .size();
    }
}
