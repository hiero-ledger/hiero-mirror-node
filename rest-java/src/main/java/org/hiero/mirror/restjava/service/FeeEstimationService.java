// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.restjava.service;

import com.hedera.node.app.config.ConfigProviderImpl;
import com.hedera.node.app.fees.StandaloneFeeCalculator;
import com.hedera.node.app.fees.StandaloneFeeCalculatorImpl;
import com.hedera.node.app.service.entityid.impl.AppEntityIdFactory;
import com.hedera.node.app.workflows.standalone.TransactionExecutors;
import com.hedera.pbj.runtime.ParseException;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.hederahashgraph.api.proto.java.Transaction;
import jakarta.inject.Named;
import java.util.List;
import java.util.Map;
import org.hiero.hapi.fees.FeeResult;
import org.hiero.mirror.rest.model.FeeEstimate;
import org.hiero.mirror.rest.model.FeeEstimateMode;
import org.hiero.mirror.rest.model.FeeEstimateNetwork;
import org.hiero.mirror.rest.model.FeeEstimateResponse;
import org.hiero.mirror.rest.model.FeeExtra;

@Named
final class FeeEstimationService {

    private static final Map<String, String> INTRINSIC_CONFIG = Map.of("fees.simpleFeesEnabled", "true");

    private final StandaloneFeeCalculator intrinsicCalculator;

    FeeEstimationService() {
        this.intrinsicCalculator = buildIntrinsicCalculator();
    }

    @SuppressWarnings("deprecation")
    public FeeEstimateResponse estimateFees(Transaction transaction, FeeEstimateMode mode) {
        if (mode == FeeEstimateMode.STATE) {
            throw new IllegalArgumentException("State-based fee estimation is not supported");
        }
        try {
            if (transaction.getBodyBytes().isEmpty()
                    && transaction.getSignedTransactionBytes().isEmpty()) {
                throw new IllegalArgumentException("Transaction must contain body bytes or signed transaction bytes");
            }
            com.hedera.hapi.node.base.Transaction pbjTxn;
            if (!transaction.getSignedTransactionBytes().isEmpty()) {
                pbjTxn = com.hedera.hapi.node.base.Transaction.PROTOBUF.parse(Bytes.wrap(transaction.toByteArray()));
            } else {
                var signedTxn = com.hedera.hapi.node.transaction.SignedTransaction.newBuilder()
                        .bodyBytes(Bytes.wrap(transaction.getBodyBytes().toByteArray()))
                        .build();
                pbjTxn = com.hedera.hapi.node.base.Transaction.newBuilder()
                        .signedTransactionBytes(
                                com.hedera.hapi.node.transaction.SignedTransaction.PROTOBUF.toBytes(signedTxn))
                        .build();
            }
            return toResponse(intrinsicCalculator.calculateIntrinsic(pbjTxn));
        } catch (ParseException e) {
            throw new IllegalArgumentException("Unable to parse transaction", e);
        } catch (NullPointerException e) {
            throw new IllegalArgumentException("Unknown transaction type", e);
        }
    }

    private static StandaloneFeeCalculator buildIntrinsicCalculator() {
        var state = new FeeEstimationState();
        var properties = TransactionExecutors.Properties.newBuilder()
                .state(state)
                .appProperties(INTRINSIC_CONFIG)
                .build();
        var config = new ConfigProviderImpl(false, null, INTRINSIC_CONFIG).getConfiguration();
        return new StandaloneFeeCalculatorImpl(state, properties, new AppEntityIdFactory(config));
    }

    private static FeeEstimateResponse toResponse(FeeResult r) {
        return new FeeEstimateResponse()
                .node(new FeeEstimate()
                        .base(r.getNodeBaseFeeTinycents())
                        .extras(r.getNodeExtraDetails().stream()
                                .map(FeeEstimationService::toExtra)
                                .toList()))
                .network(new FeeEstimateNetwork()
                        .multiplier(r.getNetworkMultiplier())
                        .subtotal(r.getNetworkTotalTinycents()))
                .service(new FeeEstimate()
                        .base(r.getServiceBaseFeeTinycents())
                        .extras(r.getServiceExtraDetails().stream()
                                .map(FeeEstimationService::toExtra)
                                .toList()))
                .total(r.totalTinycents())
                .notes(List.of());
    }

    private static FeeExtra toExtra(FeeResult.FeeDetail d) {
        return new FeeExtra()
                .name(d.name())
                .count((int) d.used())
                .feePerUnit(d.perUnit())
                .included((int) d.included())
                .charged((int) d.charged())
                .subtotal(d.perUnit() * d.charged());
    }
}
