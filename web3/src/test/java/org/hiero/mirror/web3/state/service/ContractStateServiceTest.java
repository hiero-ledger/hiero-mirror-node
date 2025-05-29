// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.state.service;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.hiero.mirror.web3.evm.config.EvmConfiguration.CACHE_MANAGER_CONTRACT_STATE;
import static org.hiero.mirror.web3.evm.config.EvmConfiguration.CACHE_NAME;

import jakarta.annotation.Resource;
import java.util.Optional;
import org.hiero.mirror.common.domain.entity.EntityType;
import org.hiero.mirror.web3.Web3IntegrationTest;
import org.hiero.mirror.web3.repository.ContractStateRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCache;

class ContractStateServiceTest extends Web3IntegrationTest {

    @Qualifier(CACHE_MANAGER_CONTRACT_STATE)
    CacheManager cacheManager;
    @Resource
    private ContractStateService contractStateService;
    @Resource
    private ContractStateRepository contractStateRepository;

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
}
