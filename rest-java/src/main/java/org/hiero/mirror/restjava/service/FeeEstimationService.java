// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.restjava.service;

import com.hedera.hapi.node.base.Transaction;
import com.hedera.hapi.node.transaction.SignedTransaction;
import com.hedera.node.app.config.ConfigProviderImpl;
import com.hedera.node.app.fees.StandaloneFeeCalculator;
import com.hedera.node.app.fees.StandaloneFeeCalculatorImpl;
import com.hedera.node.app.service.entityid.impl.AppEntityIdFactory;
import com.hedera.node.app.service.file.impl.schemas.V0490FileSchema;
import com.hedera.node.app.workflows.standalone.TransactionExecutors;
import com.hedera.pbj.runtime.ParseException;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import jakarta.inject.Named;
import java.io.IOException;
import java.util.Map;
import org.hiero.hapi.fees.FeeResult;
import org.hiero.hapi.support.fees.FeeSchedule;
import org.hiero.mirror.common.domain.SystemEntity;
import org.hiero.mirror.rest.model.FeeEstimateMode;
import org.hiero.mirror.restjava.repository.FileDataRepository;

@Named
public final class FeeEstimationService {

    private static final Map<String, String> INTRINSIC_CONFIG = Map.of("fees.simpleFeesEnabled", "true");

    private final StandaloneFeeCalculator intrinsicCalculator;

    FeeEstimationService(FileDataRepository fileDataRepository, SystemEntity systemEntity) {
        this.intrinsicCalculator =
                buildIntrinsicCalculator(loadSimpleFeeScheduleBytes(fileDataRepository, systemEntity));
    }

    public FeeResult estimateFees(Transaction transaction, FeeEstimateMode mode) {
        if (mode == FeeEstimateMode.STATE) {
            throw new IllegalArgumentException("State-based fee estimation is not supported");
        }
        try {
            Transaction pbjTxn;
            if (transaction.signedTransactionBytes().length() > 0) {
                pbjTxn = transaction;
            } else if (transaction.bodyBytes().length() > 0) {
                var signedTxn = SignedTransaction.newBuilder()
                        .bodyBytes(transaction.bodyBytes())
                        .build();
                pbjTxn = Transaction.newBuilder()
                        .signedTransactionBytes(SignedTransaction.PROTOBUF.toBytes(signedTxn))
                        .build();
            } else {
                throw new IllegalArgumentException("Transaction must contain body bytes or signed transaction bytes");
            }
            return intrinsicCalculator.calculateIntrinsic(pbjTxn);
        } catch (ParseException e) {
            throw new IllegalArgumentException("Unable to parse transaction", e);
        } catch (NullPointerException e) {
            throw new IllegalArgumentException("Unknown transaction type", e);
        }
    }

    private static Bytes loadSimpleFeeScheduleBytes(FileDataRepository repo, SystemEntity systemEntity) {
        var fileId = systemEntity.simpleFeeScheduleFile().getId();
        return repo.getFileAtTimestamp(fileId, 0L, Long.MAX_VALUE)
                .map(fd -> Bytes.wrap(fd.getFileData()))
                .orElseGet(() -> {
                    try (var in = V0490FileSchema.class.getResourceAsStream("/genesis/simpleFeesSchedules.json")) {
                        if (in == null) {
                            throw new IllegalStateException("Bundled simpleFeesSchedules.json not found on classpath");
                        }
                        var schedule = V0490FileSchema.parseSimpleFeesSchedules(in.readAllBytes());
                        return FeeSchedule.PROTOBUF.toBytes(schedule);
                    } catch (IOException e) {
                        throw new IllegalStateException("Failed to load bundled simpleFeesSchedules.json", e);
                    }
                });
    }

    private static StandaloneFeeCalculator buildIntrinsicCalculator(Bytes simpleFeeBytes) {
        var state = new FeeEstimationState(simpleFeeBytes);
        var properties = TransactionExecutors.Properties.newBuilder()
                .state(state)
                .appProperties(INTRINSIC_CONFIG)
                .build();
        var config = new ConfigProviderImpl(false, null, INTRINSIC_CONFIG).getConfiguration();
        return new StandaloneFeeCalculatorImpl(state, properties, new AppEntityIdFactory(config));
    }
}
