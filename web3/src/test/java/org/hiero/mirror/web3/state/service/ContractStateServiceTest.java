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
        final byte[] slotKey = new byte[32];
        final byte[] value = "test-value".getBytes();

        domainBuilder
                .contractState()
                .customize(cs -> cs.contractId(contract.getId()).slot(slotKey).value(value))
                .persist();

        // When
        Optional<byte[]> result = contractStateService.findSlotValue(contract.toEntityId(), slotKey);

        // Then
        assertThat(result).isPresent().contains(value);

        final var cacheSize = ((CaffeineCache) cacheManager.getCache(CACHE_NAME))
                .getNativeCache()
                .asMap()
                .size();
        assertThat(cacheSize).isEqualTo(1);
    }

    @Test
    void testFindSlotValueNotPresentInBulkLoad() {
        // Given
        final var contract = domainBuilder
                .entity()
                .customize(e -> e.type(EntityType.CONTRACT))
                .persist();
        final byte[] slotKey1 = new byte[32];
        slotKey1[0] = 0x01;
        final byte[] value = "test-value".getBytes();

        domainBuilder
                .contractState()
                .customize(cs -> cs.contractId(contract.getId()).slot(slotKey1).value(value))
                .persist();

        // When
        Optional<byte[]> result = contractStateService.findSlotValue(contract.toEntityId(), slotKey1);

        // Add new cache value after the bulk has already passed
        final byte[] value2 = "test-value2".getBytes();
        final byte[] slotKey2 = new byte[32];
        slotKey1[0] = 0x02;
        domainBuilder
                .contractState()
                .customize(cs -> cs.contractId(contract.getId()).slot(slotKey2).value(value2))
                .persist();
        Optional<byte[]> result2 = contractStateService.findSlotValue(contract.toEntityId(), slotKey2);

        // Then
        assertThat(result).isPresent().contains(value);
        assertThat(result2).isPresent().contains(value2);

        final var cacheSize = ((CaffeineCache) cacheManager.getCache(CACHE_NAME))
                .getNativeCache()
                .asMap()
                .size();
        assertThat(cacheSize).isEqualTo(2);
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
        assertThat(result).isPresent().contains(("test-value" + outsideBulkLoadIndex).getBytes());

        contractStateRepository.delete(contractStateOutsideOfRange);

        Optional<byte[]> result2 =
                contractStateService.findSlotValue(contract.toEntityId(), contractStateOutsideOfRange.getSlot());
        // Then
        assertThat(result2).isPresent().contains(("test-value" + outsideBulkLoadIndex).getBytes());
    }

    @Test
    void testBulkLoadThenDeleteAllAndVerifyResultIsReturnedFromCache() {
        // Given
        long size = 5;
        final var contract = domainBuilder
                .entity()
                .customize(e -> e.type(EntityType.CONTRACT))
                .persist();
        final byte[] value = "test-value".getBytes();
        final byte[] slotKey1 = new byte[32];
        slotKey1[0] = 0x01;

        for (int i = 0; i < size; i++) {
            final byte[] slotKey = new byte[32];
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

        final var cacheSize = ((CaffeineCache) cacheManager.getCache(CACHE_NAME))
                .getNativeCache()
                .asMap()
                .size();
        assertThat(cacheSize).isEqualTo(size);

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
        Optional<byte[]> result = contractStateService.findSlotValue(contract.toEntityId(), new byte[32]);

        // Then
        assertThat(result).isPresent().contains(("test-value0").getBytes());
        final var cacheSize = ((CaffeineCache) cacheManager.getCache(CACHE_NAME))
                .getNativeCache()
                .asMap()
                .size();
        assertThat(cacheSize).isEqualTo(1);
    }

    @Test
    void testFindSlotValueHistorical() {
        // Given
        final var consensusTimestamp = 123456789L;
        final var contract = domainBuilder
                .entity()
                .customize(e -> e.type(EntityType.CONTRACT).createdTimestamp(consensusTimestamp))
                .persist();
        final byte[] slotKey = new byte[32];
        final byte[] value = "test-value".getBytes();

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

        final var cacheSize = ((CaffeineCache) cacheManager.getCache(CACHE_NAME))
                .getNativeCache()
                .asMap()
                .size();
        assertThat(cacheSize).isZero();
    }

    @Test
    void testCacheDoesNotIncreaseInSize() throws InterruptedException {
        // Given
        final var contract = domainBuilder
                .entity()
                .customize(e -> e.type(EntityType.CONTRACT))
                .persist();
        persistContractStates(contract.getId(), 6500);

        // When
        Optional<byte[]> result1 = contractStateService.findSlotValue(contract.toEntityId(), new byte[32]);

        ContractCallContext.get().clear();
        Thread.sleep(2500);

        Optional<byte[]> result2 = contractStateService.findSlotValue(contract.toEntityId(), new byte[32]);

        // Then
        final var expectedValue = "test-value0".getBytes();
        assertThat(result1).isPresent().contains(expectedValue);
        assertThat(result2).isPresent().contains(expectedValue);

        final var cacheSize = ((CaffeineCache) cacheManager.getCache(CACHE_NAME))
                .getNativeCache()
                .asMap()
                .size();
        assertThat(cacheSize).isEqualTo(5000);
    }

    private void persistContractStates(final long contractId, final int size) {
        for (int i = 0; i < size; i++) {
            persistContractState(contractId, i);
        }
    }

    private ContractState persistContractState(final long contractId, final int index) {
        final byte[] slotKey = new byte[32];
        final byte[] indexBytes = ByteBuffer.allocate(4).putInt(index).array();
        System.arraycopy(indexBytes, 0, slotKey, 0, indexBytes.length);

        final byte[] value = ("test-value" + index).getBytes();

        return domainBuilder
                .contractState()
                .customize(cs -> cs.contractId(contractId).slot(slotKey).value(value))
                .persist();
    }
}
