// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.restjava.service.fee;

import com.hedera.hapi.node.base.Transaction;
import jakarta.inject.Named;
import org.hiero.hapi.fees.FeeResult;
import org.hiero.mirror.common.domain.SystemEntity;
import org.hiero.mirror.rest.model.FeeEstimateMode;
import org.hiero.mirror.restjava.repository.FileDataRepository;

/**
 * Stub for consensus-node-backed fee estimation. Full wiring is exercised only once the Hedera
 * standalone calculator is integrated with mirror state; tests are currently disabled.
 */
@Named
public final class FeeEstimationService {

    public FeeEstimationService(
            FeeEstimationState feeEstimationState,
            FileDataRepository fileDataRepository,
            SystemEntity systemEntity,
            FeeTopicStore feeTopicStore,
            FeeTokenStore feeTokenStore) {}

    public void refreshStateCalculator() {
        // no-op until calculator integration lands
    }

    public FeeResult estimateFees(Transaction transaction, FeeEstimateMode mode, int highVolumeThrottleUtilization) {
        throw new UnsupportedOperationException(
                "Fee estimation service is not fully integrated; see FeeEstimationServiceTest");
    }
}
