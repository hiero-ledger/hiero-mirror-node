package com.hedera.mirror.web3.state.keyvalue;

import com.hedera.hapi.node.base.ScheduleID;
import com.hedera.hapi.node.base.TransactionID;
import com.hedera.hapi.node.scheduled.SchedulableTransactionBody;
import com.hedera.hapi.node.state.schedule.Schedule;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.mirror.common.domain.entity.Entity;
import com.hedera.mirror.common.domain.entity.EntityType;
import com.hedera.mirror.web3.common.ContractCallContext;
import com.hedera.mirror.web3.repository.ScheduleRepository;
import com.hedera.mirror.web3.state.CommonEntityAccessor;
import com.hedera.node.app.service.schedule.impl.schemas.V0490ScheduleSchema;
import com.hedera.pbj.runtime.ParseException;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.hedera.services.utils.EntityIdUtils;
import jakarta.annotation.Nonnull;
import jakarta.inject.Named;

@Named
public class ScheduleReadableKVState extends AbstractReadableKVState<ScheduleID, Schedule> {

    private final ScheduleRepository scheduleRepository;

    private final CommonEntityAccessor commonEntityAccessor;

    protected ScheduleReadableKVState(ScheduleRepository scheduleRepository, CommonEntityAccessor commonEntityAccessor) {
        super(V0490ScheduleSchema.SCHEDULES_BY_ID_KEY);
        this.scheduleRepository = scheduleRepository;
        this.commonEntityAccessor = commonEntityAccessor;
    }

    @Override
    protected Schedule readFromDataSource(@Nonnull ScheduleID key) {
        final var scheduleId = EntityIdUtils.toEntityId(key);

        final var timestamp = ContractCallContext.get().getTimestamp();
        final var entity = commonEntityAccessor.get(scheduleId, timestamp).orElse(null);

        if (entity == null || entity.getType() != EntityType.SCHEDULE) {
            return null;
        }

        return scheduleRepository.findById(scheduleId.getId())
                .map(schedule -> {
                    try {
                        return mapToSchedule(schedule, key, entity);
                    } catch (ParseException e) {
                        throw new RuntimeException(e);
                    }
                })
                .orElse(null);
    }

    private Schedule mapToSchedule(final com.hedera.mirror.common.domain.schedule.Schedule schedule, final ScheduleID scheduleID,
            final Entity entity) throws ParseException {
        return Schedule.newBuilder()
                .scheduleId(scheduleID)
                .payerAccountId(EntityIdUtils.toAccountId(schedule.getPayerAccountId()))
                .schedulerAccountId(EntityIdUtils.toAccountId(schedule.getCreatorAccountId()))
                // up to this point it should be correct
                .deleted(entity.getDeleted())
                .memo(entity.getMemo())
               // .adminKey(entity.getKey())

                .scheduledTransaction(SchedulableTransactionBody.PROTOBUF.parse(Bytes.wrap(schedule.getTransactionBody())))
                .originalCreateTransaction(TransactionBody.newBuilder().transactionID(TransactionID.newBuilder()
                                .accountID(EntityIdUtils.toAccountId(schedule.getCreatorAccountId()))))
                //.signatories(entity.getKey())
                .signatories()
                .build();
    }
}
