// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hiero.mirror.web3.evm.config.EvmConfiguration.CACHE_NAME;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.IntStream;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.hiero.mirror.web3.Web3Properties;
import org.hiero.mirror.web3.repository.ContractStateRepository;
import org.hiero.mirror.web3.repository.properties.CacheProperties;
import org.hiero.mirror.web3.state.ContractSlotValue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.dao.QueryTimeoutException;

@ExtendWith(MockitoExtension.class)
class ContractStateServiceImplTest {

    private static final String CACHE_SPEC = "maximumSize=10000";
    private static final EntityId CONTRACT_ID = EntityId.of(0L, 0L, 1L);

    @Mock
    private ContractStateRepository contractStateRepository;

    @Mock
    private CacheProperties cacheProperties;

    @Mock
    private Web3Properties web3Properties;

    private ContractStateServiceImpl contractStateService;

    @BeforeEach
    void setUp() {
        final var cacheManagerContractSlots = new CaffeineCacheManager();
        cacheManagerContractSlots.setCacheNames(Set.of(CACHE_NAME));
        cacheManagerContractSlots.setCacheSpecification(CACHE_SPEC);

        final var cacheManagerContractState = new CaffeineCacheManager();
        cacheManagerContractState.setCacheNames(Set.of(CACHE_NAME));
        cacheManagerContractState.setCacheSpecification(CACHE_SPEC);

        when(cacheProperties.isEnableBatchContractSlotCaching()).thenReturn(true);
        when(cacheProperties.getSlotsPerContract()).thenReturn(CACHE_SPEC);

        contractStateService = new ContractStateServiceImpl(
                cacheManagerContractSlots,
                cacheManagerContractState,
                cacheProperties,
                contractStateRepository,
                web3Properties);
    }

    @Test
    void verifySlotsCachedBeforeTimeoutAreNotRequeriedOnSecondRead() {
        // Given: 10 slots, batchSize=1 so every slot occupies its own batch chunk.
        when(web3Properties.getBatchSize()).thenReturn(1);

        final var slotCount = 10;
        final var slotKeys = IntStream.range(0, slotCount)
                .mapToObj(ContractStateServiceImplTest::generateSlotKey)
                .toList();
        final var slotValues = IntStream.range(0, slotCount)
                .mapToObj(i -> ("slot-value-" + i).getBytes())
                .toList();

        final Map<ByteBuffer, byte[]> slotDataMap = new HashMap<>();
        for (var i = 0; i < slotCount; i++) {
            slotDataMap.put(ByteBuffer.wrap(slotKeys.get(i)), slotValues.get(i));
        }

        final var fifthSlotKey = slotKeys.get(4);

        // Stub: throw QueryTimeoutException when the 5th slot appears in a batch; return matching
        // ContractSlotValues for all other batches. The AtomicBoolean lets the second read
        // succeed by disabling the throw before re-querying slots 5–10.
        final var throwTimeout = new AtomicBoolean(true);
        doAnswer(invocation -> {
                    final byte[][] batch = invocation.getArgument(1);
                    if (throwTimeout.get() && containsSlotKey(batch, fifthSlotKey)) {
                        throw new QueryTimeoutException("Simulated DB timeout");
                    }
                    return Arrays.stream(batch)
                            .filter(s -> slotDataMap.containsKey(ByteBuffer.wrap(s)))
                            .map(s -> new ContractSlotValue(s, slotDataMap.get(ByteBuffer.wrap(s))))
                            .toList();
                })
                .when(contractStateRepository)
                .findStorageBatch(anyLong(), any());

        // When: first read — slots 1–4 are found and cached
        for (var i = 0; i < 4; i++) {
            final var key = slotKeys.get(i);
            final var expected = slotValues.get(i);
            assertThat(contractStateService.findStorage(CONTRACT_ID, key)).get().isEqualTo(expected);
        }

        // Disable the timeout and reset recorded interactions before the second read.
        throwTimeout.set(false);
        clearInvocations(contractStateRepository);
        final var captor = ArgumentCaptor.forClass(byte[][].class);

        // Second read — all 10 values must be returned correctly.
        // Slots 1–4 hit the contractStateCache early return; slots 5–10 reach the repository.
        for (var i = 0; i < slotCount; i++) {
            final var key = slotKeys.get(i);
            final var expected = slotValues.get(i);
            assertThat(contractStateService.findStorage(CONTRACT_ID, key)).get().isEqualTo(expected);
        }

        // Capture every batch query that reached the repository during the second read.
        verify(contractStateRepository, atLeastOnce()).findStorageBatch(anyLong(), captor.capture());
        final var allCapturedSlots =
                captor.getAllValues().stream().flatMap(Arrays::stream).toList();

        // Then: slots 1–4 must not appear in any captured batch — they were served from cache.
        for (var i = 0; i < 4; i++) {
            final var cachedSlotKey = slotKeys.get(i);
            assertThat(allCapturedSlots.stream().anyMatch(s -> Arrays.equals(s, cachedSlotKey)))
                    .as("slot %d should not be re-queried", i + 1)
                    .isFalse();
        }
    }

    private static byte[] generateSlotKey(final int index) {
        final var key = new byte[32];
        final var indexBytes = ByteBuffer.allocate(4).putInt(index).array();
        System.arraycopy(indexBytes, 0, key, 0, indexBytes.length);
        return key;
    }

    private static boolean containsSlotKey(final byte[][] batch, final byte[] target) {
        for (final var slot : batch) {
            if (Arrays.equals(slot, target)) {
                return true;
            }
        }
        return false;
    }
}
