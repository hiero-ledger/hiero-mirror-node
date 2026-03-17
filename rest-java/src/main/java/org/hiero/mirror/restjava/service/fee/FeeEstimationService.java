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
import com.hedera.node.config.types.StreamMode;
import com.hedera.pbj.runtime.ParseException;
import com.hedera.pbj.runtime.io.buffer.BufferedData;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import jakarta.inject.Named;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import lombok.CustomLog;
import org.hiero.hapi.fees.FeeResult;
import org.hiero.mirror.common.domain.SystemEntity;
import org.hiero.mirror.rest.model.FeeEstimateMode;
import org.hiero.mirror.restjava.repository.FileDataRepository;
import org.springframework.scheduling.annotation.Scheduled;

@CustomLog
@Named
public final class FeeEstimationService {

    private final FileDataRepository fileDataRepository;
    private final SystemEntity systemEntity;
    private final FeeEstimationFeeContext feeEstimationFeeContext;
    private final FeeManager feeManager;
    private final SimpleFeeCalculator genesisCalculator;
    private final AtomicReference<SimpleFeeCalculator> feeCalculator;
    private long lastFeeScheduleTimestamp;

    @SuppressWarnings("NullAway")
    public FeeEstimationService(
            FeeEstimationState feeEstimationState,
            FileDataRepository fileDataRepository,
            SystemEntity systemEntity,
            FeeEstimationFeeContext feeEstimationFeeContext) {
        this.fileDataRepository = fileDataRepository;
        this.systemEntity = systemEntity;
        this.feeEstimationFeeContext = feeEstimationFeeContext;

        final var config = feeEstimationFeeContext.configuration();
        final var executor = TRANSACTION_EXECUTORS.newExecutorComponent(
                feeEstimationState,
                FeeEstimationFeeContext.FEE_PROPERTIES,
                null,
                Set.of(),
                new AppEntityIdFactory(config));
        executor.stateNetworkInfo().initFrom(feeEstimationState);
        executor.initializer().initialize(feeEstimationState, StreamMode.BOTH);
        this.feeManager = executor.feeManager();
        this.genesisCalculator = Objects.requireNonNull(feeManager.getSimpleFeeCalculator());

        this.lastFeeScheduleTimestamp = Long.MIN_VALUE;
        this.feeCalculator = new AtomicReference<>(genesisCalculator);
        refreshStateCalculator();
    }

    @Scheduled(fixedDelayString = "${hiero.mirror.rest-java.fee.refresh-interval:PT10M}")
    public void refreshStateCalculator() {
        final var latestTimestamp = fileDataRepository
                .getLatestTimestamp(systemEntity.simpleFeeScheduleFile().getId())
                .orElse(Long.MIN_VALUE);
        if (latestTimestamp != lastFeeScheduleTimestamp) {
            log.info("Fee schedule changed (timestamp={}), rebuilding fee calculator", latestTimestamp);
            lastFeeScheduleTimestamp = latestTimestamp;
            fileDataRepository
                    .getFileAtTimestamp(systemEntity.simpleFeeScheduleFile().getId(), 0L, Long.MAX_VALUE)
                    .ifPresent(fileData -> {
                        feeManager.updateSimpleFees(Bytes.wrap(fileData.getFileData()));
                        feeCalculator.set(Objects.requireNonNull(feeManager.getSimpleFeeCalculator()));
                    });
        }
    }

    public FeeResult estimateFees(Transaction transaction, FeeEstimateMode mode) {
        try {
            final var context = new TransactionFeeContext(
                    transaction, mode == FeeEstimateMode.STATE ? feeEstimationFeeContext : null);
            return Objects.requireNonNull(feeCalculator.get()).calculateTxFee(context.body(), context);
        } catch (ParseException e) {
            throw new IllegalArgumentException("Unable to parse transaction", e);
        } catch (NullPointerException e) {
            throw new IllegalArgumentException("Unknown transaction type", e);
        }
    }

    @SuppressWarnings("NullAway")
    private static final class TransactionFeeContext implements SimpleFeeContext {

        private final int numTxnSignatures;
        private final TransactionBody body;
        private final Transaction transaction;

        @Nullable
        private final FeeContext feeContext;

        TransactionFeeContext(Transaction transaction, @Nullable FeeContext feeContext) throws ParseException {
            this.transaction = transaction;
            this.feeContext = feeContext;
            if (transaction.signedTransactionBytes().length() > 0) {
                final var signedTransaction = SignedTransaction.PROTOBUF.parse(
                        BufferedData.wrap(transaction.signedTransactionBytes().toByteArray()));
                this.body = TransactionBody.PROTOBUF.parse(signedTransaction.bodyBytes());
                this.numTxnSignatures = signedTransaction
                        .sigMapOrElse(SignatureMap.DEFAULT)
                        .sigPair()
                        .size();
            } else if (transaction.bodyBytes().length() > 0) {
                this.body = TransactionBody.PROTOBUF.parse(transaction.bodyBytes());
                this.numTxnSignatures = 0;
            } else {
                throw new IllegalArgumentException("Transaction must contain body bytes or signed transaction bytes");
            }
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
