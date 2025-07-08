// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.parser.contractlog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hiero.mirror.common.util.DomainUtils.trim;
import static org.hiero.mirror.importer.parser.contractlog.AbstractSyntheticContractLog.TRANSFER_SIGNATURE;
import static org.hiero.mirror.importer.parser.contractlog.SyntheticLogListener.MAX_CACHE_LOAD_ENTRIES;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.mockito.ArgumentMatchers.anyIterable;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.google.common.primitives.Longs;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import org.hiero.mirror.common.domain.DomainBuilder;
import org.hiero.mirror.common.domain.contract.ContractLog;
import org.hiero.mirror.common.domain.entity.Entity;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.hiero.mirror.importer.config.CacheProperties;
import org.hiero.mirror.importer.domain.EvmAddressMapping;
import org.hiero.mirror.importer.parser.record.entity.ParserContext;
import org.hiero.mirror.importer.repository.EntityRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("unchecked")
class SyntheticLogListenerTest {
    private static final DomainBuilder domainBuilder = new DomainBuilder();

    @Mock
    private EntityRepository entityRepository;

    @Mock
    private ParserContext parserContext;

    private SyntheticLogListener listener;

    private static final EntityId ENTITY_1 = domainBuilder.entityId();
    private static final EntityId ENTITY_2 = domainBuilder.entityId();
    private static final EntityId ENTITY_3 = domainBuilder.entityId();

    private static final byte[] LONG_ZERO_1 = Longs.toByteArray(ENTITY_1.getNum());
    private static final byte[] LONG_ZERO_2 = Longs.toByteArray(ENTITY_2.getNum());
    private static final byte[] LONG_ZERO_3 = Longs.toByteArray(ENTITY_3.getNum());

    private static final byte[] TRIMMED_LONG_ZERO_1 = trim(LONG_ZERO_1);
    private static final byte[] TRIMMED_LONG_ZERO_2 = trim(LONG_ZERO_2);
    private static final byte[] TRIMMED_LONG_ZERO_3 = trim(LONG_ZERO_3);

    private static final byte[] EVM_1 = domainBuilder.evmAddress();
    private static final byte[] EVM_2 = domainBuilder.evmAddress();
    private static final byte[] EVM_3 = domainBuilder.evmAddress();

    @BeforeEach
    void setup() {
        listener = new SyntheticLogListener(parserContext, new CacheProperties(), entityRepository);
    }

    @Test
    void cachesDbResults() {
        final var contractLog = domainBuilder
                .contractLog()
                .customize(cl -> cl.topic0(TRANSFER_SIGNATURE)
                        .topic1(LONG_ZERO_1)
                        .topic2(LONG_ZERO_2)
                        .syntheticTransfer(true))
                .get();

        final var evmAddressMappings =
                List.of(new EvmAddressMapping(EVM_1, ENTITY_1.getId()), new EvmAddressMapping(EVM_2, ENTITY_2.getId()));
        when(entityRepository.findEvmAddressesByIds(Set.of(ENTITY_1.getId(), ENTITY_2.getId())))
                .thenReturn(evmAddressMappings);
        when(parserContext.get(ContractLog.class)).thenReturn(List.of(contractLog));

        assertArrayEquals(LONG_ZERO_1, contractLog.getTopic1());
        assertArrayEquals(LONG_ZERO_2, contractLog.getTopic2());

        listener.onEnd(domainBuilder.recordFile().get());

        verify(entityRepository, times(1)).findEvmAddressesByIds(Set.of(ENTITY_1.getId(), ENTITY_2.getId()));

        assertArrayEquals(EVM_1, contractLog.getTopic1());
        assertArrayEquals(EVM_2, contractLog.getTopic2());

        reset(parserContext, entityRepository);

        final var contractLog2 = domainBuilder
                .contractLog()
                .customize(cl -> cl.topic0(TRANSFER_SIGNATURE)
                        .topic1(LONG_ZERO_1)
                        .topic2(LONG_ZERO_2)
                        .syntheticTransfer(true))
                .get();
        when(parserContext.get(ContractLog.class)).thenReturn(List.of(contractLog2));

        assertArrayEquals(LONG_ZERO_1, contractLog2.getTopic1());
        assertArrayEquals(LONG_ZERO_2, contractLog2.getTopic2());

        listener.onEnd(domainBuilder.recordFile().get());

        verifyNoInteractions(entityRepository);
        assertArrayEquals(EVM_1, contractLog2.getTopic1());
        assertArrayEquals(EVM_2, contractLog2.getTopic2());
    }

    @Test
    void cachesNoDbResults() {
        final var contractLog = domainBuilder
                .contractLog()
                .customize(cl -> cl.topic0(TRANSFER_SIGNATURE)
                        .topic1(LONG_ZERO_1)
                        .topic2(LONG_ZERO_2)
                        .syntheticTransfer(true))
                .get();

        final var evmAddressMappings = Collections.<EvmAddressMapping>emptyList();
        when(entityRepository.findEvmAddressesByIds(Set.of(ENTITY_1.getId(), ENTITY_2.getId())))
                .thenReturn(evmAddressMappings);
        when(parserContext.get(ContractLog.class)).thenReturn(List.of(contractLog));

        assertArrayEquals(LONG_ZERO_1, contractLog.getTopic1());
        assertArrayEquals(LONG_ZERO_2, contractLog.getTopic2());

        listener.onEnd(domainBuilder.recordFile().get());

        verify(entityRepository, times(1)).findEvmAddressesByIds(Set.of(ENTITY_1.getId(), ENTITY_2.getId()));

        assertArrayEquals(TRIMMED_LONG_ZERO_1, contractLog.getTopic1());
        assertArrayEquals(TRIMMED_LONG_ZERO_2, contractLog.getTopic2());

        reset(parserContext, entityRepository);

        final var contractLog2 = domainBuilder
                .contractLog()
                .customize(cl -> cl.topic0(TRANSFER_SIGNATURE)
                        .topic1(LONG_ZERO_1)
                        .topic2(LONG_ZERO_2)
                        .syntheticTransfer(true))
                .get();

        when(parserContext.get(ContractLog.class)).thenReturn(List.of(contractLog2));

        assertArrayEquals(LONG_ZERO_1, contractLog2.getTopic1());
        assertArrayEquals(LONG_ZERO_2, contractLog2.getTopic2());

        listener.onEnd(domainBuilder.recordFile().get());

        verifyNoInteractions(entityRepository);

        assertArrayEquals(TRIMMED_LONG_ZERO_1, contractLog2.getTopic1());
        assertArrayEquals(TRIMMED_LONG_ZERO_2, contractLog2.getTopic2());
    }

    @Test
    void cachesWithSomeDbResults() {
        final var contractLog = domainBuilder
                .contractLog()
                .customize(cl -> cl.topic0(TRANSFER_SIGNATURE)
                        .topic1(LONG_ZERO_1)
                        .topic2(LONG_ZERO_2)
                        .syntheticTransfer(true))
                .get();

        final var evmAddressMappings = List.of(new EvmAddressMapping(EVM_1, ENTITY_1.getId()));
        when(entityRepository.findEvmAddressesByIds(Set.of(ENTITY_1.getId(), ENTITY_2.getId())))
                .thenReturn(evmAddressMappings);
        when(parserContext.get(ContractLog.class)).thenReturn(List.of(contractLog));

        assertArrayEquals(LONG_ZERO_1, contractLog.getTopic1());
        assertArrayEquals(LONG_ZERO_2, contractLog.getTopic2());

        listener.onEnd(domainBuilder.recordFile().get());

        verify(entityRepository, times(1)).findEvmAddressesByIds(Set.of(ENTITY_1.getId(), ENTITY_2.getId()));
        assertArrayEquals(EVM_1, contractLog.getTopic1());
        assertArrayEquals(TRIMMED_LONG_ZERO_2, contractLog.getTopic2());

        reset(parserContext, entityRepository);

        final var contractLog2 = domainBuilder
                .contractLog()
                .customize(cl -> cl.topic0(TRANSFER_SIGNATURE)
                        .topic1(LONG_ZERO_1)
                        .topic2(LONG_ZERO_2)
                        .syntheticTransfer(true))
                .get();
        when(parserContext.get(ContractLog.class)).thenReturn(List.of(contractLog2));

        assertArrayEquals(LONG_ZERO_1, contractLog2.getTopic1());
        assertArrayEquals(LONG_ZERO_2, contractLog2.getTopic2());

        listener.onEnd(domainBuilder.recordFile().get());

        verifyNoInteractions(entityRepository);
        assertArrayEquals(EVM_1, contractLog2.getTopic1());
        assertArrayEquals(TRIMMED_LONG_ZERO_2, contractLog2.getTopic2());
    }

    @Test
    void queryNonCachedEntries() {
        final var contractLog = domainBuilder
                .contractLog()
                .customize(cl -> cl.topic0(TRANSFER_SIGNATURE)
                        .topic1(LONG_ZERO_1)
                        .topic2(LONG_ZERO_2)
                        .syntheticTransfer(true))
                .get();

        final var evmAddressMappings =
                List.of(new EvmAddressMapping(EVM_1, ENTITY_1.getId()), new EvmAddressMapping(EVM_2, ENTITY_2.getId()));
        when(entityRepository.findEvmAddressesByIds(Set.of(ENTITY_1.getId(), ENTITY_2.getId())))
                .thenReturn(evmAddressMappings);
        when(parserContext.get(ContractLog.class)).thenReturn(List.of(contractLog));

        assertArrayEquals(LONG_ZERO_1, contractLog.getTopic1());
        assertArrayEquals(LONG_ZERO_2, contractLog.getTopic2());

        listener.onEnd(domainBuilder.recordFile().get());

        verify(entityRepository, times(1)).findEvmAddressesByIds(Set.of(ENTITY_1.getId(), ENTITY_2.getId()));
        assertArrayEquals(EVM_1, contractLog.getTopic1());
        assertArrayEquals(EVM_2, contractLog.getTopic2());

        reset(parserContext, entityRepository);

        final var contractLog2 = domainBuilder
                .contractLog()
                .customize(cl -> cl.topic0(TRANSFER_SIGNATURE)
                        .topic1(LONG_ZERO_1)
                        .topic2(LONG_ZERO_3)
                        .syntheticTransfer(true))
                .get();

        final var evmAddressMappings2 = List.of(new EvmAddressMapping(EVM_3, ENTITY_3.getId()));
        when(entityRepository.findEvmAddressesByIds(Set.of(ENTITY_3.getId()))).thenReturn(evmAddressMappings2);
        when(parserContext.get(ContractLog.class)).thenReturn(List.of(contractLog2));

        assertArrayEquals(LONG_ZERO_1, contractLog2.getTopic1());
        assertArrayEquals(LONG_ZERO_3, contractLog2.getTopic2());

        listener.onEnd(domainBuilder.recordFile().get());

        verify(entityRepository, times(1)).findEvmAddressesByIds(Set.of(ENTITY_3.getId()));
        assertArrayEquals(EVM_1, contractLog2.getTopic1());
        assertArrayEquals(EVM_3, contractLog2.getTopic2());
    }

    @Test
    void queryAndUpdateOnlySyntheticTransferLogs() {
        final var contractLog = domainBuilder
                .contractLog()
                .customize(cl -> cl.topic0(TRANSFER_SIGNATURE)
                        .topic1(LONG_ZERO_1)
                        .topic2(LONG_ZERO_2)
                        .syntheticTransfer(true))
                .get();

        final var contractLog2 = domainBuilder
                .contractLog()
                .customize(cl -> cl.topic0(TRANSFER_SIGNATURE)
                        .topic1(LONG_ZERO_1)
                        .topic2(LONG_ZERO_3)
                        .syntheticTransfer(false))
                .get();

        when(parserContext.get(ContractLog.class)).thenReturn(List.of(contractLog, contractLog2));

        assertArrayEquals(LONG_ZERO_1, contractLog.getTopic1());
        assertArrayEquals(LONG_ZERO_2, contractLog.getTopic2());
        assertArrayEquals(LONG_ZERO_1, contractLog2.getTopic1());
        assertArrayEquals(LONG_ZERO_3, contractLog2.getTopic2());

        final var evmAddressMappings =
                List.of(new EvmAddressMapping(EVM_1, ENTITY_1.getId()), new EvmAddressMapping(EVM_2, ENTITY_2.getId()));
        when(entityRepository.findEvmAddressesByIds(Set.of(ENTITY_1.getId(), ENTITY_2.getId())))
                .thenReturn(evmAddressMappings);

        listener.onEnd(domainBuilder.recordFile().get());

        verify(entityRepository, times(1)).findEvmAddressesByIds(Set.of(ENTITY_1.getId(), ENTITY_2.getId()));
        verifyNoMoreInteractions(entityRepository);

        assertArrayEquals(EVM_1, contractLog.getTopic1());
        assertArrayEquals(EVM_2, contractLog.getTopic2());
        assertArrayEquals(LONG_ZERO_1, contractLog2.getTopic1());
        assertArrayEquals(LONG_ZERO_3, contractLog2.getTopic2());
    }

    @Test
    void addsNewEntityToCache() {
        final var entityEvmAddress = domainBuilder.evmAddress();
        final var entity = domainBuilder
                .entity()
                .customize(e -> e.evmAddress(entityEvmAddress).timestampRange(null))
                .get();
        final var contractLog = domainBuilder
                .contractLog()
                .customize(cl -> cl.topic0(TRANSFER_SIGNATURE)
                        .topic1(LONG_ZERO_1)
                        .topic2(LONG_ZERO_2)
                        .syntheticTransfer(true))
                .get();

        final var contractLog2 = domainBuilder
                .contractLog()
                .customize(cl -> cl.topic0(TRANSFER_SIGNATURE)
                        .topic1(Longs.toByteArray(entity.getNum()))
                        .topic2(LONG_ZERO_3)
                        .syntheticTransfer(true))
                .get();

        assertArrayEquals(LONG_ZERO_1, contractLog.getTopic1());
        assertArrayEquals(LONG_ZERO_2, contractLog.getTopic2());
        assertArrayEquals(Longs.toByteArray(entity.getNum()), contractLog2.getTopic1());
        assertArrayEquals(LONG_ZERO_3, contractLog2.getTopic2());

        final var evmAddressMappings = List.of(
                new EvmAddressMapping(EVM_1, ENTITY_1.getId()),
                new EvmAddressMapping(EVM_2, ENTITY_2.getId()),
                new EvmAddressMapping(EVM_3, ENTITY_3.getId()));
        when(entityRepository.findEvmAddressesByIds(Set.of(ENTITY_1.getId(), ENTITY_2.getId(), ENTITY_3.getId())))
                .thenReturn(evmAddressMappings);
        when(parserContext.get(ContractLog.class)).thenReturn(List.of(contractLog, contractLog2));
        when(parserContext.get(Entity.class, entity.getId())).thenReturn(entity);
        when(parserContext.get(Entity.class, ENTITY_1.getId())).thenReturn(null);
        when(parserContext.get(Entity.class, ENTITY_2.getId())).thenReturn(null);
        when(parserContext.get(Entity.class, ENTITY_3.getId())).thenReturn(null);

        listener.onEnd(domainBuilder.recordFile().get());

        verify(entityRepository, times(1))
                .findEvmAddressesByIds(Set.of(ENTITY_1.getId(), ENTITY_2.getId(), ENTITY_3.getId()));
        verifyNoMoreInteractions(entityRepository);

        assertArrayEquals(EVM_1, contractLog.getTopic1());
        assertArrayEquals(EVM_2, contractLog.getTopic2());

        assertArrayEquals(entityEvmAddress, contractLog2.getTopic1());
        assertArrayEquals(EVM_3, contractLog2.getTopic2());
    }

    @Test
    void batchesCorrectly() {
        final var evmAddressMappings = new HashMap<Long, EvmAddressMapping>(MAX_CACHE_LOAD_ENTRIES * 2);
        final var contractLogMap = new HashMap<Long, ContractLog>(MAX_CACHE_LOAD_ENTRIES * 2);

        for (int i = 0; i < MAX_CACHE_LOAD_ENTRIES; i++) {
            var lookupIdBatch1 = domainBuilder.entityId();
            var evmAddressBatch1 = domainBuilder.evmAddress();
            var contractLogBatch1 = domainBuilder
                    .contractLog()
                    .customize(cl -> cl.topic1(Longs.toByteArray(lookupIdBatch1.getNum()))
                            .topic2(Longs.toByteArray(lookupIdBatch1.getNum()))
                            .syntheticTransfer(true))
                    .get();

            var lookupIdBatch2 = domainBuilder.entityId();
            var evmAddressBatch2 = domainBuilder.evmAddress();
            var contractLogBatch2 = domainBuilder
                    .contractLog()
                    .customize(cl -> cl.topic1(Longs.toByteArray(lookupIdBatch2.getNum()))
                            .topic2(Longs.toByteArray(lookupIdBatch2.getNum()))
                            .syntheticTransfer(true))
                    .get();

            contractLogMap.put(lookupIdBatch1.getId(), contractLogBatch1);
            contractLogMap.put(lookupIdBatch2.getId(), contractLogBatch2);

            evmAddressMappings.put(
                    lookupIdBatch1.getId(), new EvmAddressMapping(evmAddressBatch1, lookupIdBatch1.getId()));
            evmAddressMappings.put(
                    lookupIdBatch2.getId(), new EvmAddressMapping(evmAddressBatch2, lookupIdBatch2.getId()));
        }

        ArgumentCaptor<Iterable<Long>> idCaptor = ArgumentCaptor.forClass(Iterable.class);
        when(parserContext.get(ContractLog.class)).thenReturn(contractLogMap.values());
        when(parserContext.get(eq(Entity.class), anyLong())).thenReturn(null);

        when(entityRepository.findEvmAddressesByIds(anyIterable())).thenAnswer(invocation -> {
            final Iterable<? extends Long> ids = invocation.getArgument(0);
            return StreamSupport.stream(ids.spliterator(), false)
                    .map(evmAddressMappings::get)
                    .collect(Collectors.toList());
        });

        for (var entrySet : contractLogMap.entrySet()) {
            var entityId = EntityId.of(entrySet.getKey());
            var contractLog = entrySet.getValue();
            assertThat(contractLog.getTopic1()).isEqualTo(Longs.toByteArray(entityId.getNum()));
            assertThat(contractLog.getTopic1()).isEqualTo(Longs.toByteArray(entityId.getNum()));
        }

        listener.onEnd(domainBuilder.recordFile().get());

        verify(entityRepository, times(2)).findEvmAddressesByIds(idCaptor.capture());
        verifyNoMoreInteractions(entityRepository);

        List<Iterable<Long>> capturedIds = idCaptor.getAllValues();
        Iterable<Long> firstBatch = capturedIds.get(0);
        Iterable<Long> secondBatch = capturedIds.get(1);

        assertThat(firstBatch).hasSize(MAX_CACHE_LOAD_ENTRIES);
        assertThat(secondBatch).hasSize(MAX_CACHE_LOAD_ENTRIES);

        var batch1Set = new HashSet<Long>(MAX_CACHE_LOAD_ENTRIES);
        var batch2Set = new HashSet<Long>(MAX_CACHE_LOAD_ENTRIES);
        firstBatch.forEach(batch1Set::add);
        secondBatch.forEach(batch2Set::add);
        assertThat(Collections.disjoint(batch1Set, batch2Set)).isTrue();

        for (var entry : contractLogMap.entrySet()) {
            var contractLog = entry.getValue();
            var evmAddressMapping = evmAddressMappings.get(entry.getKey());

            assertThat(contractLog.getTopic1()).isEqualTo(trim(evmAddressMapping.getEvmAddress()));
            assertThat(contractLog.getTopic2()).isEqualTo(trim(evmAddressMapping.getEvmAddress()));
        }
    }
}
