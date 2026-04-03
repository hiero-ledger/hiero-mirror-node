// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.restjava.service.fee;

import static com.hedera.hapi.util.HapiUtils.functionOf;
import static com.hedera.node.app.workflows.standalone.TransactionExecutors.TRANSACTION_EXECUTORS;

import com.hedera.hapi.node.base.HederaFunctionality;
import com.hedera.hapi.node.base.SignatureMap;
import com.hedera.hapi.node.base.Transaction;
import com.hedera.hapi.node.transaction.SignedTransaction;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.hapi.util.UnknownHederaFunctionality;
import com.hedera.node.app.fees.FeeManager;
import com.hedera.node.app.service.entityid.impl.AppEntityIdFactory;
import com.hedera.node.app.spi.fees.FeeContext;
import com.hedera.node.app.spi.fees.SimpleFeeCalculator;
import com.hedera.node.app.spi.fees.SimpleFeeContext;
import com.hedera.node.app.spi.workflows.QueryContext;
import com.hedera.node.app.workflows.standalone.ExecutorComponent;
import com.hedera.node.config.types.StreamMode;
import com.hedera.pbj.runtime.ParseException;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import jakarta.inject.Named;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import lombok.CustomLog;
import org.hiero.hapi.fees.FeeResult;
import org.hiero.hapi.fees.FeeScheduleUtils;
import org.hiero.hapi.fees.HighVolumePricingCalculator;
import org.hiero.hapi.support.fees.FeeSchedule;
import org.hiero.mirror.common.domain.SystemEntity;
import org.hiero.mirror.rest.model.FeeEstimateMode;
import org.hiero.mirror.restjava.repository.FileDataRepository;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;

@CustomLog
@Named
public class FeeEstimationService {

    private final ExecutorComponent executor;
    private final FeeEstimationState feeEstimationState;
    private final FileDataRepository fileDataRepository;
    private final long feeScheduleFileId;
    private final FeeTopicStore feeTopicStore;
    private final FeeTokenStore feeTokenStore;
    private final AtomicLong lastFeeScheduleTimestamp;
    private final AtomicReference<FeeSchedule> currentFeeSchedule;
    private final FeeManager feeManager;

    public FeeEstimationService(
            final FeeEstimationState feeEstimationState,
            final FileDataRepository fileDataRepository,
            final SystemEntity systemEntity,
            final FeeTopicStore feeTopicStore,
            final FeeTokenStore feeTokenStore) {
        this.feeEstimationState = feeEstimationState;
        this.fileDataRepository = fileDataRepository;
        this.feeScheduleFileId = systemEntity.simpleFeeScheduleFile().getId();
        this.feeTopicStore = feeTopicStore;
        this.feeTokenStore = feeTokenStore;
        this.currentFeeSchedule = new AtomicReference<>(FeeSchedule.DEFAULT);
        this.lastFeeScheduleTimestamp = new AtomicLong(Long.MIN_VALUE);

        final var config = newFeeContext(TransactionBody.DEFAULT).configuration();
        this.executor = TRANSACTION_EXECUTORS.newExecutorComponent(
                feeEstimationState,
                FeeEstimationFeeContext.FEE_PROPERTIES,
                null,
                Set.of(),
                new AppEntityIdFactory(config));
        executor.stateNetworkInfo().initFrom(feeEstimationState);
        executor.initializer().initialize(feeEstimationState, StreamMode.BOTH);
        this.feeManager = Objects.requireNonNull(executor.feeManager());
    }

    @Async
    @EventListener(ApplicationReadyEvent.class)
    public void initFeeSchedule() {
        refreshStateCalculator();
    }

    @Scheduled(fixedDelayString = "${hiero.mirror.rest-java.fee.refresh-interval:PT10M}")
    public void refreshStateCalculator() {
        final var latestTimestamp =
                fileDataRepository.getLatestTimestamp(feeScheduleFileId).orElse(Long.MIN_VALUE);
        if (latestTimestamp > lastFeeScheduleTimestamp.get()) {
            log.info(
                    "Rebuilding the fee calculator after detecting a simple fee schedule change at {}",
                    latestTimestamp);
            lastFeeScheduleTimestamp.set(latestTimestamp);
            fileDataRepository
                    .getFileAtTimestamp(feeScheduleFileId, 0L, Long.MAX_VALUE)
                    .ifPresent(fileData -> {
                        feeManager.updateSimpleFees(Bytes.wrap(fileData.getFileData()));
                        try {
                            currentFeeSchedule.set(
                                    FeeSchedule.PROTOBUF.parseStrict(Bytes.wrap(fileData.getFileData())));
                        } catch (ParseException e) {
                            log.warn("Failed to parse fee schedule for high volume multiplier lookup", e);
                        }
                    });
        }
    }

    public FeeResult estimateFees(@NonNull final Transaction transaction, @NonNull final FeeEstimateMode mode) {
        try {
            final var txContext = new TransactionFeeContext(transaction);
            final var context = mode == FeeEstimateMode.STATE
                    ? txContext.withFeeContext(newFeeContext(txContext.body()))
                    : txContext;
            final SimpleFeeCalculator calculator = Objects.requireNonNull(feeManager.getSimpleFeeCalculator());
            final var result = calculator.calculateTxFee(context.body(), context);
            result.setHighVolumeMultiplier(lookupMaxMultiplier(context.functionality()));
            return result;
        } catch (ParseException e) {
            throw new IllegalArgumentException("Unable to parse transaction", e);
        } catch (NullPointerException e) {
            throw new IllegalArgumentException("Unknown transaction type", e);
        }
    }

    private FeeEstimationFeeContext newFeeContext(final TransactionBody body) {
        return new FeeEstimationFeeContext(body, feeTopicStore, feeTokenStore);
    }

    private long lookupMaxMultiplier(final HederaFunctionality functionality) {
        final var def = FeeScheduleUtils.lookupServiceFee(currentFeeSchedule.get(), functionality);
        if (def != null && def.highVolumeRates() != null) {
            final int maxRaw = def.highVolumeRates().maxMultiplier();
            if (maxRaw > HighVolumePricingCalculator.HIGH_VOLUME_MULTIPLIER_SCALE) {
                return maxRaw;
            }
        }
        return HighVolumePricingCalculator.DEFAULT_HIGH_VOLUME_MULTIPLIER;
    }

    @SuppressWarnings("NullAway")
    private static final class TransactionFeeContext implements SimpleFeeContext {

        private final Transaction transaction;
        private final TransactionBody body;
        private final int numTxnSignatures;

        @Nullable
        private final FeeContext feeContext;

        TransactionFeeContext(final Transaction transaction) throws ParseException {
            this(transaction, null);
        }

        TransactionFeeContext(final Transaction transaction, @Nullable final FeeContext feeContext)
                throws ParseException {
            this.transaction = transaction;
            this.feeContext = feeContext;
            if (transaction.signedTransactionBytes().length() > 0) {
                final var signedTransaction = SignedTransaction.PROTOBUF.parse(transaction.signedTransactionBytes());
                this.body = TransactionBody.PROTOBUF.parse(signedTransaction.bodyBytes());
                this.numTxnSignatures = signedTransaction
                        .sigMapOrElse(SignatureMap.DEFAULT)
                        .sigPair()
                        .size();
            } else if (transaction.bodyBytes().length() > 0) {
                this.body = TransactionBody.PROTOBUF.parse(transaction.bodyBytes());
                this.numTxnSignatures =
                        transaction.sigMapOrElse(SignatureMap.DEFAULT).sigPair().size();
            } else {
                throw new IllegalArgumentException("Transaction must contain body bytes or signed transaction bytes");
            }
        }

        private TransactionFeeContext(
                final Transaction transaction,
                final int numTxnSignatures,
                final TransactionBody body,
                @Nullable final FeeContext feeContext) {
            this.transaction = transaction;
            this.numTxnSignatures = numTxnSignatures;
            this.body = body;
            this.feeContext = feeContext;
        }

        TransactionFeeContext withFeeContext(@Nullable final FeeContext feeContext) {
            return new TransactionFeeContext(transaction, numTxnSignatures, body, feeContext);
        }

        @Override
        public int numTxnSignatures() {
            return numTxnSignatures;
        }

        @Override
        public int numTxnBytes() {
            return Transaction.PROTOBUF.measureRecord(transaction);
        }

        @Override
        @Nullable
        public FeeContext feeContext() {
            return feeContext;
        }

        @Override
        @Nullable
        public QueryContext queryContext() {
            return null;
        }

        @Override
        public HederaFunctionality functionality() {
            try {
                return functionOf(body);
            } catch (UnknownHederaFunctionality e) {
                throw new IllegalStateException(e);
            }
        }

        @Override
        public int getHighVolumeThrottleUtilization(final HederaFunctionality functionality) {
            return 0;
        }

        @Override
        @NonNull
        public TransactionBody body() {
            return body;
        }
    }
}
