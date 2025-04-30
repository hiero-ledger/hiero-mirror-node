package com.hedera.mirror.web3.state.keyvalue;

import com.hedera.hapi.node.base.Key;
import com.hedera.hapi.node.base.ScheduleID;
import com.hedera.hapi.node.base.TransactionID;
import com.hedera.hapi.node.scheduled.SchedulableTransactionBody;
import com.hedera.hapi.node.state.schedule.Schedule;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.mirror.common.domain.entity.Entity;
import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.common.domain.entity.EntityType;
import com.hedera.mirror.web3.common.ContractCallContext;
import com.hedera.mirror.web3.repository.ScheduleRepository;
import com.hedera.mirror.web3.repository.TransactionSignatureRepository;
import com.hedera.mirror.web3.state.CommonEntityAccessor;
import com.hedera.node.app.service.schedule.impl.schemas.V0490ScheduleSchema;
import com.hedera.pbj.runtime.ParseException;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.hedera.services.utils.EntityIdUtils;
import jakarta.annotation.Nonnull;
import jakarta.inject.Named;
import java.util.List;
import java.util.stream.Collectors;

@Named
public class ScheduleReadableKVState extends AbstractReadableKVState<ScheduleID, Schedule> {

    private final ScheduleRepository scheduleRepository;

    private final CommonEntityAccessor commonEntityAccessor;

    private final TransactionSignatureRepository transactionSignatureRepository;

    protected ScheduleReadableKVState(ScheduleRepository scheduleRepository, CommonEntityAccessor commonEntityAccessor,
            TransactionSignatureRepository transactionSignatureRepository) {
        super(V0490ScheduleSchema.SCHEDULES_BY_ID_KEY);
        this.scheduleRepository = scheduleRepository;
        this.commonEntityAccessor = commonEntityAccessor;
        this.transactionSignatureRepository = transactionSignatureRepository;
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
                .deleted(entity.getDeleted())
                .memo(entity.getMemo())
                .scheduledTransaction(SchedulableTransactionBody.PROTOBUF.parse(Bytes.wrap(schedule.getTransactionBody())))
                .originalCreateTransaction(TransactionBody.newBuilder().transactionID(TransactionID.newBuilder()
                                .accountID(EntityIdUtils.toAccountId(schedule.getCreatorAccountId()))))
                .signatories(getSignatories(schedule.getScheduleId()))
                .build();
    }

    private List<Key> getSignatories(final Long scheduleId) {
        // Here we should also start taking into a account the timestamp and make historical query
        final var signatures = transactionSignatureRepository.findByEntityId(EntityId.of(scheduleId));

        return signatures.stream()
                .map(signature -> {
                    Key.KeyOneOfType keyType = fromProtoOrdinal(signature.getType());

                    return switch (keyType) {
                        // Might need to add logic for KEY_LIST and THRESHOLD_KEY check
                        // private static void accumulateNewSignatories( in AbstractScheduleHandler
                        case ED25519 -> Key.newBuilder()
                                .ed25519(Bytes.wrap(signature.getPublicKeyPrefix()))
                                .build();

                        case ECDSA_SECP256K1 -> Key.newBuilder()
                                .ecdsaSecp256k1(Bytes.wrap(signature.getPublicKeyPrefix()))
                                .build();
                        default -> throw new IllegalArgumentException("Unsupported key type in getSignatories: " + keyType);
                    };
                })
                .collect(Collectors.toList());
    }

    /**
     * Converts a proto ordinal to the corresponding Key.KeyOneOfType enum value.
     *
     * @param protoOrdinal the proto ordinal to convert
     * @return the corresponding Key.KeyOneOfType enum value
     * @throws IllegalArgumentException if the proto ordinal does not correspond to any Key.KeyOneOfType
     */
    private Key.KeyOneOfType fromProtoOrdinal(int protoOrdinal) {
        for (Key.KeyOneOfType kind : Key.KeyOneOfType.values()) {
            if (kind.protoOrdinal() == protoOrdinal) {
                return kind;
            }
        }
        throw new IllegalArgumentException("Unknown protoOrdinal: " + protoOrdinal);
    }

}
