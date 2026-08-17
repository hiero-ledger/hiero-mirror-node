// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.InstanceOfAssertFactories.BYTE_ARRAY;
import static org.hiero.mirror.web3.evm.config.EvmConfiguration.CACHE_NAME;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

import com.github.benmanes.caffeine.cache.Caffeine;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.hiero.mirror.web3.repository.ContractStateRepository;
import org.hiero.mirror.web3.repository.properties.CacheProperties;
import org.hiero.mirror.web3.state.ContractSlotValue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.cache.caffeine.CaffeineCacheManager;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ContractStateServiceImplTest {

    private static final EntityId CONTRACT_ID = EntityId.of(0, 0, 1500);

    @Mock
    private ContractStateRepository contractStateRepository;

    // Fake DB: slot -> value. Slots absent from this map behave as empty (no row returned).
    private final Map<ByteBuffer, byte[]> db = new HashMap<>();
    // Records the number of slots passed to each findStorageBatch invocation.
    private final List<Integer> batchSlotCounts = new ArrayList<>();

    private CaffeineCacheManager valueCacheManager;
    private CacheProperties cacheProperties;
    private ContractStateServiceImpl service;

    @BeforeEach
    void setUp() {
        valueCacheManager = namedCacheManager();
        cacheProperties = new CacheProperties();
        service = new ContractStateServiceImpl(
                namedCacheManager(),
                valueCacheManager,
                dynamicCacheManager(),
                cacheProperties,
                contractStateRepository);

        when(contractStateRepository.findStorageBatch(anyLong(), any())).thenAnswer(invocation -> {
            final List<byte[]> slots = invocation.getArgument(1);
            batchSlotCounts.add(slots.size());
            final var rows = new ArrayList<ContractSlotValue>();
            for (final var slot : slots) {
                final var value = db.get(ByteBuffer.wrap(slot));
                if (value != null) {
                    rows.add(new ContractSlotValue(slot, value));
                }
            }
            return rows;
        });
    }

    @Test
    void sequentialDistinctSlotReadsFetchEachSlotOnce() {
        // Given a contract with N distinct, populated slots
        final int n = 25;
        final var slots = populateSlots(n);

        // When each slot is read for the first time, in sequence (cold value cache)
        for (int i = 0; i < n; i++) {
            assertThat(service.findStorage(CONTRACT_ID, slots.get(i)))
                    .get(BYTE_ARRAY)
                    .isEqualTo(("value" + (i + 1)).getBytes());
        }

        // Then each distinct slot is fetched from the DB exactly once across all batches → O(n).
        // Before the fix the batch re-fetched the whole accumulated set: 1+2+...+n = n(n+1)/2.
        assertThat(batchSlotCounts).hasSize(n).allMatch(count -> count == 1);
        assertThat(batchSlotCounts.stream().mapToInt(Integer::intValue).sum()).isEqualTo(n);
    }

    @Test
    void coldValueCacheBulkLoadsAllKnownSlotsInOneQuery() {
        // Given the slot keys are already known (queried once) and their values are then evicted
        final int n = 10;
        final var slots = populateSlots(n);
        for (final var slot : slots) {
            service.findStorage(CONTRACT_ID, slot); // warms the slot-key cache and the value cache
        }
        valueCacheManager.getCache(CACHE_NAME).clear(); // emulate the short-lived value cache expiring
        batchSlotCounts.clear();

        // When the first slot is read again after the values expired
        assertThat(service.findStorage(CONTRACT_ID, slots.getFirst()))
                .get(BYTE_ARRAY)
                .isEqualTo("value1".getBytes());

        // Then the single batch bulk-loads every known slot in one query (the intended warm-key behavior)...
        assertThat(batchSlotCounts).containsExactly(n);

        // ...and the remaining slots are served from the freshly reloaded cache with no further queries.
        for (int i = 1; i < n; i++) {
            assertThat(service.findStorage(CONTRACT_ID, slots.get(i)))
                    .get(BYTE_ARRAY)
                    .isEqualTo(("value" + (i + 1)).getBytes());
        }
        assertThat(batchSlotCounts).containsExactly(n);
    }

    @Test
    void emptySlotIsNegativelyCachedAndNotRefetched() {
        final var slot = slotKey(1); // absent from the fake DB -> empty

        // First read triggers a batch, resolves empty, and negatively caches the slot.
        assertThat(service.findStorage(CONTRACT_ID, slot)).isEmpty();
        assertThat(batchSlotCounts).hasSize(1);

        // Second read must hit the negative cache -> no further batch.
        assertThat(service.findStorage(CONTRACT_ID, slot)).isEmpty();
        assertThat(batchSlotCounts).hasSize(1);
    }

    private List<byte[]> populateSlots(final int n) {
        final var slots = new ArrayList<byte[]>(n);
        for (int i = 1; i <= n; i++) {
            final var slot = slotKey(i);
            db.put(ByteBuffer.wrap(slot), ("value" + i).getBytes());
            slots.add(slot);
        }
        return slots;
    }

    private static CaffeineCacheManager namedCacheManager() {
        final var manager = new CaffeineCacheManager();
        manager.setCacheNames(Set.of(CACHE_NAME));
        manager.setCaffeine(Caffeine.newBuilder().maximumSize(100_000));
        return manager;
    }

    private static CaffeineCacheManager dynamicCacheManager() {
        final var manager = new CaffeineCacheManager(); // dynamic cache creation per contract id
        manager.setCaffeine(Caffeine.newBuilder().maximumSize(10_000));
        return manager;
    }

    private static byte[] slotKey(final int index) {
        final var slot = new byte[32];
        final var indexBytes = ByteBuffer.allocate(Integer.BYTES).putInt(index).array();
        System.arraycopy(indexBytes, 0, slot, 0, indexBytes.length);
        return slot;
    }
}
