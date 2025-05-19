// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.state.keyvalue;

import com.hedera.hapi.node.base.Key;
import com.hedera.hapi.node.base.ScheduleID;
import com.hedera.hapi.node.base.TransactionID;
import com.hedera.hapi.node.scheduled.SchedulableTransactionBody;
import com.hedera.hapi.node.state.schedule.Schedule;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.mirror.common.domain.entity.Entity;
import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.common.domain.entity.EntityType;
import com.hedera.node.app.service.schedule.impl.schemas.V0490ScheduleSchema;
import com.hedera.pbj.runtime.ParseException;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.hedera.services.utils.EntityIdUtils;
import com.hederahashgraph.api.proto.java.SignaturePair;
import com.hederahashgraph.api.proto.java.SignaturePair.SignatureCase;
import jakarta.annotation.Nonnull;
import jakarta.inject.Named;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import org.hiero.mirror.web3.common.ContractCallContext;
import org.hiero.mirror.web3.repository.ScheduleRepository;
import org.hiero.mirror.web3.repository.TransactionSignatureRepository;
import org.hiero.mirror.web3.state.CommonEntityAccessor;
import org.hiero.mirror.web3.utils.Suppliers;

@Named
class ScheduleReadableKVState extends AbstractReadableKVState<ScheduleID, Schedule> {

    private final ScheduleRepository scheduleRepository;

    private final CommonEntityAccessor commonEntityAccessor;

    private final TransactionSignatureRepository transactionSignatureRepository;

    protected ScheduleReadableKVState(
            ScheduleRepository scheduleRepository,
            CommonEntityAccessor commonEntityAccessor,
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

        return scheduleRepository
                .findById(scheduleId.getId())
                .map(schedule -> {
                    try {
                        return mapToSchedule(schedule, key, entity, timestamp);
                    } catch (ParseException e) {
                        throw new IllegalArgumentException(e);
                    }
                })
                .orElse(null);
    }

    private Schedule mapToSchedule(
            final com.hedera.mirror.common.domain.schedule.Schedule schedule,
            final ScheduleID scheduleID,
            final Entity entity,
            final Optional<Long> timestamp)
            throws ParseException {
        return Schedule.newBuilder()
                .scheduleId(scheduleID)
                .payerAccountId(EntityIdUtils.toAccountId(schedule.getPayerAccountId()))
                .schedulerAccountId(EntityIdUtils.toAccountId(schedule.getCreatorAccountId()))
                .deleted(entity.getDeleted())
                .memo(entity.getMemo())
                .scheduledTransaction(
                        SchedulableTransactionBody.PROTOBUF.parse(Bytes.wrap(schedule.getTransactionBody())))
                .originalCreateTransaction(TransactionBody.newBuilder()
                        .transactionID(TransactionID.newBuilder()
                                .accountID(EntityIdUtils.toAccountId(schedule.getCreatorAccountId()))))
                .signatories(getSignatories(schedule.getScheduleId(), timestamp))
                .build();
    }

    private Supplier<List<Key>> getSignatories(final Long scheduleId, final Optional<Long> timestamp) {
        return Suppliers.memoize(() -> {
            final var entityId = EntityId.of(scheduleId);
            final var signatures = timestamp
                    .map(ts -> transactionSignatureRepository.findByEntityIdAndConsensusTimestampLessThanEqual(
                            entityId, ts))
                    .orElseGet(() -> transactionSignatureRepository.findByEntityId(entityId));

            return signatures.stream()
                    .map(signature -> {
                        final var signatureCase = SignaturePair.SignatureCase.forNumber(signature.getType());
                        return switch (signatureCase) {
                            case SignatureCase.ED25519 ->
                                Key.newBuilder()
                                        .ed25519(Bytes.wrap(signature.getPublicKeyPrefix()))
                                        .build();
                            case SignatureCase.ECDSA_SECP256K1 ->
                                Key.newBuilder()
                                        .ecdsaSecp256k1(Bytes.wrap(signature.getPublicKeyPrefix()))
                                        .build();
                            default -> null; // Skip unsupported key types
                        };
                    })
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());
        });
    }
}
