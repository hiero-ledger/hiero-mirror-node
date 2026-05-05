// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.parser.contractlog;

import static org.hiero.mirror.common.util.DomainUtils.trim;
import static org.hiero.mirror.importer.parser.contractlog.AbstractSyntheticContractLog.TRANSFER_SIGNATURE;
import static org.hiero.mirror.importer.parser.contractlog.SyntheticContractLogServiceImpl.CONTRACT_LOG_MARKER;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.mockito.Mockito.*;

import com.google.common.primitives.Longs;
import java.util.*;
import java.util.function.BinaryOperator;
import org.hiero.mirror.common.CommonProperties;
import org.hiero.mirror.common.domain.DomainBuilder;
import org.hiero.mirror.common.domain.SystemEntity;
import org.hiero.mirror.common.domain.contract.ContractLog;
import org.hiero.mirror.common.domain.entity.Entity;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.hiero.mirror.common.util.DomainUtils;
import org.hiero.mirror.common.util.LogsBloomFilter;
import org.hiero.mirror.importer.config.CacheProperties;
import org.hiero.mirror.importer.domain.EvmAddressMapping;
import org.hiero.mirror.importer.parser.record.entity.EntityProperties;
import org.hiero.mirror.importer.parser.record.entity.ParserContext;
import org.hiero.mirror.importer.repository.EntityRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SyntheticLogListenerTest {

    private static final DomainBuilder domainBuilder = new DomainBuilder();
    private static final BinaryOperator<Entity> NO_OP_MERGE = (e1, e2) -> e1;

    private static final EntityId ENTITY_1 = domainBuilder.entityId();
    private static final EntityId ENTITY_2 = domainBuilder.entityId();
    private static final EntityId ENTITY_3 = domainBuilder.entityId();
    private static final EntityId CONTRACT_ENTITY = domainBuilder.entityId();

    private static final byte[] LONG_ZERO_1 = trim(Longs.toByteArray(ENTITY_1.getNum()));
    private static final byte[] LONG_ZERO_2 = trim(Longs.toByteArray(ENTITY_2.getNum()));
    private static final byte[] LONG_ZERO_3 = trim(Longs.toByteArray(ENTITY_3.getNum()));

    private static final byte[] EVM_1 = domainBuilder.evmAddress();
    private static final byte[] EVM_2 = domainBuilder.evmAddress();
    private static final byte[] EVM_3 = domainBuilder.evmAddress();
    private static final byte[] CONTRACT_EVM = domainBuilder.evmAddress();

    @Mock
    private EntityRepository entityRepository;

    private EntityProperties entityProperties;
    private ParserContext parserContext;
    private SyntheticLogListener listener;

    @BeforeEach
    void setup() {
        entityProperties = new EntityProperties(new SystemEntity(CommonProperties.getInstance()));
        parserContext = new ParserContext();
        listener = new SyntheticLogListener(parserContext, new CacheProperties(), entityRepository, entityProperties);
    }

    @Test
    void cachesDbResults() {
        final var contractLog = syntheticTransferLog(LONG_ZERO_1, LONG_ZERO_2);
        final var lookupIds = Set.of(ENTITY_1.getId(), ENTITY_2.getId(), CONTRACT_ENTITY.getId());
        final var mappings =
                List.of(evmMap(EVM_1, ENTITY_1), evmMap(EVM_2, ENTITY_2), evmMap(CONTRACT_EVM, CONTRACT_ENTITY));
        mockFind(lookupIds, mappings);

        listener.onContractLog(contractLog);
        assertLogHasTopics(contractLog, LONG_ZERO_1, LONG_ZERO_2);

        submitAndClear();

        verify(entityRepository, times(1)).findEvmAddressesByIds(lookupIds);
        assertLogHasTopics(contractLog, EVM_1, EVM_2);

        reset(entityRepository);

        final var contractLog2 = syntheticTransferLog(LONG_ZERO_1, LONG_ZERO_2);
        listener.onContractLog(contractLog2);
        assertLogHasTopics(contractLog2, LONG_ZERO_1, LONG_ZERO_2);

        submitAndClear();

        verifyNoInteractions(entityRepository);
        assertLogHasTopics(contractLog2, EVM_1, EVM_2);
    }

    @Test
    void cachesNoDbResults() {
        final var contractLog = syntheticTransferLog(LONG_ZERO_1, LONG_ZERO_2);
        final var lookupIds = Set.of(ENTITY_1.getId(), ENTITY_2.getId(), CONTRACT_ENTITY.getId());
        mockFind(lookupIds, List.of());

        listener.onContractLog(contractLog);
        assertLogHasTopics(contractLog, LONG_ZERO_1, LONG_ZERO_2);

        submitAndClear();

        verify(entityRepository, times(1)).findEvmAddressesByIds(lookupIds);
        assertLogHasTopics(contractLog, LONG_ZERO_1, LONG_ZERO_2);

        reset(entityRepository);

        final var contractLog2 = syntheticTransferLog(LONG_ZERO_1, LONG_ZERO_2);
        listener.onContractLog(contractLog2);

        assertLogHasTopics(contractLog2, LONG_ZERO_1, LONG_ZERO_2);

        submitAndClear();

        verifyNoInteractions(entityRepository);
        assertLogHasTopics(contractLog2, LONG_ZERO_1, LONG_ZERO_2);
    }

    @Test
    void cachesWithSomeDbResults() {
        final var contractLog = syntheticTransferLog(LONG_ZERO_1, LONG_ZERO_2);
        final var lookupIds = Set.of(ENTITY_1.getId(), ENTITY_2.getId(), CONTRACT_ENTITY.getId());
        final var mappings = List.of(evmMap(EVM_1, ENTITY_1), evmMap(CONTRACT_EVM, CONTRACT_ENTITY));
        mockFind(lookupIds, mappings);

        listener.onContractLog(contractLog);
        assertLogHasTopics(contractLog, LONG_ZERO_1, LONG_ZERO_2);

        submitAndClear();

        verify(entityRepository, times(1)).findEvmAddressesByIds(lookupIds);
        assertLogHasTopics(contractLog, EVM_1, LONG_ZERO_2);

        reset(entityRepository);
        parserContext.clear();

        final var contractLog2 = syntheticTransferLog(LONG_ZERO_1, LONG_ZERO_2);
        listener.onContractLog(contractLog2);
        assertLogHasTopics(contractLog2, LONG_ZERO_1, LONG_ZERO_2);

        listener.onEnd(domainBuilder.recordFile().get());

        verifyNoInteractions(entityRepository);
        assertLogHasTopics(contractLog2, EVM_1, LONG_ZERO_2);
    }

    @Test
    void queryNonCachedEntries() {
        final var contractLog = syntheticTransferLog(LONG_ZERO_1, LONG_ZERO_2);
        final var lookupIds = Set.of(ENTITY_1.getId(), ENTITY_2.getId(), CONTRACT_ENTITY.getId());
        final var mappings =
                List.of(evmMap(EVM_1, ENTITY_1), evmMap(EVM_2, ENTITY_2), evmMap(CONTRACT_EVM, CONTRACT_ENTITY));
        mockFind(lookupIds, mappings);

        listener.onContractLog(contractLog);
        assertLogHasTopics(contractLog, LONG_ZERO_1, LONG_ZERO_2);
        submitAndClear();

        verify(entityRepository, times(1)).findEvmAddressesByIds(lookupIds);
        assertLogHasTopics(contractLog, EVM_1, EVM_2);

        reset(entityRepository);

        final var contractLog2 = syntheticTransferLog(LONG_ZERO_1, LONG_ZERO_3);
        mockFind(Set.of(ENTITY_3.getId()), List.of(evmMap(EVM_3, ENTITY_3)));

        listener.onContractLog(contractLog2);
        assertLogHasTopics(contractLog2, LONG_ZERO_1, LONG_ZERO_3);

        submitAndClear();

        verify(entityRepository, times(1)).findEvmAddressesByIds(Set.of(ENTITY_3.getId()));
        assertLogHasTopics(contractLog2, EVM_1, EVM_3);
    }

    @Test
    void queryAndUpdateOnlySyntheticTransferLogs() {
        final var contractLog = syntheticTransferLog(LONG_ZERO_1, LONG_ZERO_2);
        final var contractLog2 = domainBuilder
                .contractLog()
                .customize(cl -> cl.topic0(TRANSFER_SIGNATURE)
                        .topic1(LONG_ZERO_1)
                        .topic2(LONG_ZERO_3)
                        .contractId(CONTRACT_ENTITY)
                        .synthetic(false))
                .get();

        listener.onContractLog(contractLog);
        listener.onContractLog(contractLog2);

        assertLogHasTopics(contractLog, LONG_ZERO_1, LONG_ZERO_2);
        assertLogHasTopics(contractLog2, LONG_ZERO_1, LONG_ZERO_3);

        final var ids = Set.of(ENTITY_1.getId(), ENTITY_2.getId(), CONTRACT_ENTITY.getId());
        final var mappings =
                List.of(evmMap(EVM_1, ENTITY_1), evmMap(EVM_2, ENTITY_2), evmMap(CONTRACT_EVM, CONTRACT_ENTITY));
        mockFind(ids, mappings);

        submitAndClear();

        verify(entityRepository, times(1)).findEvmAddressesByIds(ids);
        verifyNoMoreInteractions(entityRepository);

        assertLogHasTopics(contractLog, EVM_1, EVM_2);
        assertLogHasTopics(contractLog2, LONG_ZERO_1, LONG_ZERO_3);
    }

    @Test
    void addsNewEntityToCache() {
        final var newEvm = domainBuilder.evmAddress();
        final var entity = domainBuilder
                .entity()
                .customize(e -> e.evmAddress(newEvm).timestampRange(null))
                .get();
        final var contractLog = syntheticTransferLog(LONG_ZERO_1, LONG_ZERO_2);
        final var contractLog2 = syntheticTransferLog(Longs.toByteArray(entity.getNum()), LONG_ZERO_3);

        listener.onContractLog(contractLog);
        listener.onContractLog(contractLog2);

        assertLogHasTopics(contractLog, LONG_ZERO_1, LONG_ZERO_2);
        assertLogHasTopics(contractLog2, Longs.toByteArray(entity.getNum()), LONG_ZERO_3);

        final var ids =
                Set.of(ENTITY_1.getId(), ENTITY_2.getId(), ENTITY_3.getId(), entity.getId(), CONTRACT_ENTITY.getId());
        final var mappings = List.of(
                evmMap(EVM_1, ENTITY_1),
                evmMap(EVM_2, ENTITY_2),
                evmMap(EVM_3, ENTITY_3),
                evmMap(newEvm, entity.toEntityId()),
                evmMap(CONTRACT_EVM, CONTRACT_ENTITY));

        parserContext.merge(entity.getId(), entity, NO_OP_MERGE);
        mockFind(ids, mappings);

        submitAndClear();

        verify(entityRepository, times(1)).findEvmAddressesByIds(ids);
        verifyNoMoreInteractions(entityRepository);

        assertLogHasTopics(contractLog, EVM_1, EVM_2);
        assertLogHasTopics(contractLog2, newEvm, EVM_3);
    }

    @Test
    void nullSenderAndOrReceiver() {
        final var contractLog = syntheticTransferLog(LONG_ZERO_1, null);
        final var contractLog2 = syntheticTransferLog(null, LONG_ZERO_2);
        final var contractLog3 = syntheticTransferLog(null, null);

        listener.onContractLog(contractLog);
        listener.onContractLog(contractLog2);
        listener.onContractLog(contractLog3);

        final var ids = Set.of(ENTITY_1.getId(), ENTITY_2.getId(), CONTRACT_ENTITY.getId());
        final var mappings = List.of(evmMap(CONTRACT_EVM, CONTRACT_ENTITY));
        mockFind(ids, mappings);

        submitAndClear();

        verify(entityRepository, times(1)).findEvmAddressesByIds(ids);
        verifyNoMoreInteractions(entityRepository);

        assertLogHasTopics(contractLog, LONG_ZERO_1, null);
        assertLogHasTopics(contractLog2, null, LONG_ZERO_2);
        assertLogHasTopics(contractLog3, null, null);
    }

    @Test
    void bloomUsesContractEvmAddressFromCache() {
        final var contractEvmAddress = domainBuilder.evmAddress();
        final var contractEntityId = domainBuilder.entityId();
        final var contractLog = syntheticTransferLogWithContract(LONG_ZERO_1, LONG_ZERO_2, contractEntityId);
        contractLog.setBloom(CONTRACT_LOG_MARKER);

        final var mappings =
                List.of(evmMap(EVM_1, ENTITY_1), evmMap(EVM_2, ENTITY_2), evmMap(contractEvmAddress, contractEntityId));
        when(entityRepository.findEvmAddressesByIds(any())).thenReturn(mappings);

        listener.onContractLog(contractLog);
        submitAndClear();

        var expectedBloom = createExpectedBloom(trim(contractEvmAddress), contractLog);
        assertArrayEquals(expectedBloom, contractLog.getBloom());
    }

    @Test
    void bloomUsesContractEvmAddressFromParserContext() {
        final var contractEvmAddress = domainBuilder.evmAddress();
        final var contractEntity = domainBuilder
                .entity()
                .customize(e -> e.evmAddress(contractEvmAddress).timestampRange(null))
                .get();
        final var contractEntityId = contractEntity.toEntityId();
        final var contractLog = syntheticTransferLogWithContract(LONG_ZERO_1, LONG_ZERO_2, contractEntityId);
        contractLog.setBloom(CONTRACT_LOG_MARKER);

        final var mappings = List.of(evmMap(EVM_1, ENTITY_1), evmMap(EVM_2, ENTITY_2));
        when(entityRepository.findEvmAddressesByIds(any())).thenReturn(mappings);

        parserContext.merge(contractEntity.getId(), contractEntity, NO_OP_MERGE);

        listener.onContractLog(contractLog);
        submitAndClear();

        var expectedBloom = createExpectedBloom(trim(contractEvmAddress), contractLog);
        assertArrayEquals(expectedBloom, contractLog.getBloom());
    }

    @Test
    void bloomUsesLongZeroAddressWhenNoEvmAddress() {
        final var contractEntityId = domainBuilder.entityId();
        final var contractLog = syntheticTransferLogWithContract(LONG_ZERO_1, LONG_ZERO_2, contractEntityId);
        contractLog.setBloom(CONTRACT_LOG_MARKER);

        final var mappings = List.of(evmMap(EVM_1, ENTITY_1), evmMap(EVM_2, ENTITY_2));
        when(entityRepository.findEvmAddressesByIds(any())).thenReturn(mappings);

        listener.onContractLog(contractLog);
        submitAndClear();

        var longZeroAddress = DomainUtils.toEvmAddress(contractEntityId);
        var expectedBloom = createExpectedBloom(longZeroAddress, contractLog);
        assertArrayEquals(expectedBloom, contractLog.getBloom());
    }

    @Test
    void bloomNotUpdatedWhenNotMarker() {
        final var contractEntityId = domainBuilder.entityId();
        final var originalBloom = domainBuilder.bytes(LogsBloomFilter.BYTE_SIZE);
        final var contractLog = syntheticTransferLogWithContract(LONG_ZERO_1, LONG_ZERO_2, contractEntityId);
        contractLog.setBloom(originalBloom);

        when(entityRepository.findEvmAddressesByIds(any())).thenReturn(Collections.emptyList());

        listener.onContractLog(contractLog);
        submitAndClear();

        assertArrayEquals(originalBloom, contractLog.getBloom());
    }

    private ContractLog syntheticTransferLog(final byte[] topic1, final byte[] topic2) {
        return domainBuilder
                .contractLog()
                .customize(cl -> cl.topic0(TRANSFER_SIGNATURE)
                        .topic1(topic1)
                        .topic2(topic2)
                        .contractId(CONTRACT_ENTITY)
                        .synthetic(true))
                .get();
    }

    private ContractLog syntheticTransferLogWithContract(
            final byte[] topic1, final byte[] topic2, final EntityId contractId) {
        return domainBuilder
                .contractLog()
                .customize(cl -> cl.topic0(TRANSFER_SIGNATURE)
                        .topic1(topic1)
                        .topic2(topic2)
                        .contractId(contractId)
                        .synthetic(true))
                .get();
    }

    private byte[] createExpectedBloom(final byte[] contractAddress, final ContractLog contractLog) {
        var logsBloomFilter = new LogsBloomFilter();
        logsBloomFilter.insertAddress(contractAddress);
        logsBloomFilter.insertTopic(contractLog.getTopic0());
        logsBloomFilter.insertTopic(contractLog.getTopic1());
        logsBloomFilter.insertTopic(contractLog.getTopic2());
        logsBloomFilter.insertTopic(contractLog.getTopic3());
        return logsBloomFilter.toArrayUnsafe();
    }

    private EvmAddressMapping evmMap(final byte[] evmAddress, final EntityId entityId) {
        return new EvmAddressMapping(evmAddress, entityId.getId());
    }

    private void mockFind(final Set<Long> ids, final List<EvmAddressMapping> mappings) {
        when(entityRepository.findEvmAddressesByIds(ids)).thenReturn(mappings);
    }

    private void assertLogHasTopics(final ContractLog log, final byte[] expected1, final byte[] expected2) {
        assertArrayEquals(expected1, log.getTopic1());
        assertArrayEquals(expected2, log.getTopic2());
    }

    private void submitAndClear() {
        listener.onEnd(domainBuilder.recordFile().get());
        parserContext.clear();
    }
}
