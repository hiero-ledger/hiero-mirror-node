// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.state.keyvalue;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.hedera.hapi.node.base.ScheduleID;
import com.hedera.hapi.node.scheduled.SchedulableTransactionBody;
import com.hedera.node.app.hapi.utils.CommonPbjConverters;
import java.util.List;
import java.util.Optional;
import org.hiero.mirror.common.domain.DomainBuilder;
import org.hiero.mirror.common.domain.entity.EntityType;
import org.hiero.mirror.web3.common.ContractCallContext;
import org.hiero.mirror.web3.repository.ScheduleRepository;
import org.hiero.mirror.web3.repository.TransactionSignatureRepository;
import org.hiero.mirror.web3.state.CommonEntityAccessor;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ScheduleReadableKVStateTest {

    private static final long TIMESTAMP = 1_234L;
    private static MockedStatic<ContractCallContext> contextMockedStatic;

    @InjectMocks
    private ScheduleReadableKVState scheduleReadableKVState;

    @Mock
    private ScheduleRepository scheduleRepository;

    @Mock
    private CommonEntityAccessor commonEntityAccessor;

    @Mock
    private TransactionSignatureRepository transactionSignatureRepository;

    @Spy
    private ContractCallContext contractCallContext;

    private DomainBuilder domainBuilder;
    private org.hiero.mirror.common.domain.entity.Entity entity;
    private ScheduleID scheduleId;

    @BeforeAll
    static void initStaticMocks() {
        contextMockedStatic = mockStatic(ContractCallContext.class);
    }

    @AfterAll
    static void closeStaticMocks() {
        contextMockedStatic.close();
    }

    @BeforeEach
    void setup() {
        domainBuilder = new DomainBuilder();
        entity = domainBuilder
                .entity()
                .customize(e -> e.type(EntityType.SCHEDULE))
                .get();
        scheduleId = ScheduleID.newBuilder()
                .shardNum(entity.getShard())
                .realmNum(entity.getRealm())
                .scheduleNum(entity.getNum())
                .build();
        contextMockedStatic.when(ContractCallContext::get).thenReturn(contractCallContext);
    }

    @Test
    void historicalReadUsesImmutableScheduleAndTimestampedSignatures() {
        final var timestamp = Optional.of(TIMESTAMP);
        final var schedule = buildSchedule();
        when(contractCallContext.getTimestamp()).thenReturn(timestamp);
        when(commonEntityAccessor.get(entity.toEntityId(), timestamp)).thenReturn(Optional.of(entity));
        when(scheduleRepository.findByIdAndTimestamp(entity.getId(), TIMESTAMP)).thenReturn(Optional.of(schedule));
        when(transactionSignatureRepository.findByEntityIdAndConsensusTimestampLessThanEqual(
                        entity.toEntityId(), TIMESTAMP))
                .thenReturn(List.of());

        final var result = scheduleReadableKVState.readFromDataSource(scheduleId);
        assertThat(result)
                .returns(
                        SchedulableTransactionBody.DEFAULT,
                        com.hedera.hapi.node.state.schedule.Schedule::scheduledTransaction)
                .returns(scheduleId, com.hedera.hapi.node.state.schedule.Schedule::scheduleId);
        assertThat(result.signatories()).isEmpty();
        verify(scheduleRepository).findByIdAndTimestamp(entity.getId(), TIMESTAMP);
        verify(transactionSignatureRepository)
                .findByEntityIdAndConsensusTimestampLessThanEqual(entity.toEntityId(), TIMESTAMP);
        verifyNoMoreInteractions(scheduleRepository);
    }

    @Test
    void latestReadUsesFindByIdAndEntitySignatures() {
        final var schedule = buildSchedule();
        when(contractCallContext.getTimestamp()).thenReturn(Optional.empty());
        when(commonEntityAccessor.get(entity.toEntityId(), Optional.empty())).thenReturn(Optional.of(entity));
        when(scheduleRepository.findById(entity.getId())).thenReturn(Optional.of(schedule));
        when(transactionSignatureRepository.findByEntityId(entity.toEntityId())).thenReturn(List.of());

        final var result = scheduleReadableKVState.readFromDataSource(scheduleId);
        assertThat(result)
                .returns(
                        SchedulableTransactionBody.DEFAULT,
                        com.hedera.hapi.node.state.schedule.Schedule::scheduledTransaction)
                .returns(scheduleId, com.hedera.hapi.node.state.schedule.Schedule::scheduleId);
        assertThat(result.signatories()).isEmpty();
        verify(scheduleRepository).findById(entity.getId());
        verify(transactionSignatureRepository).findByEntityId(entity.toEntityId());
        verifyNoMoreInteractions(scheduleRepository);
    }

    private org.hiero.mirror.common.domain.schedule.Schedule buildSchedule() {
        return domainBuilder
                .schedule()
                .customize(s -> s.scheduleId(entity.getId())
                        .creatorAccountId(entity.toEntityId())
                        .payerAccountId(entity.toEntityId())
                        .transactionBody(CommonPbjConverters.asBytes(
                                SchedulableTransactionBody.PROTOBUF, SchedulableTransactionBody.DEFAULT)))
                .get();
    }
}
